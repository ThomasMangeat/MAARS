package org.micromanager.cellstateanalysis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.math3.util.FastMath;
import org.micromanager.utils.ImgUtils;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.SpotCollection;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Line;
import ij.gui.Roi;
import ij.measure.Calibration;

/**
 * @author Tong LI, mail:tongli.bioinfo@gmail.com
 * @version Nov 19, 2015
 */
public class FluoAnalyzer implements Runnable {

	private ImagePlus fluoImage;
	private SetOfCells soc;
	private double[] factors;
	private Calibration bfImgCal;
	private Calibration fluoImgCal;
	private String channel;
	private int maxNbSpot;
	private double radius;
	private int frame;
	private double timeInterval;
	private HashSet<Integer> mitoCells;

	/**
	 * Analyze the set of cell in given the fluo image
	 * 
	 * @param fluoImage
	 *            image to analyze
	 * @param bfImgCal
	 *            bright field image calibration, need it to decide whether or
	 *            not rescale ROI
	 * @param soc
	 *            the set of cell to analyze
	 * @param channel
	 *            fluo image channel
	 * @param maxNbSpot
	 *            max number of spot in corresponding channel
	 * @param radius
	 *            radius of spot in corresponding channel
	 * @param frame
	 *            time point
	 * @param timeInterval
	 *            interval between time points
	 */
	public FluoAnalyzer(ImagePlus fluoImage, Calibration bfImgCal, SetOfCells soc, String channel, int maxNbSpot,
			double radius, int frame, double timeInterval) {
		this.fluoImage = fluoImage;
		this.fluoImgCal = fluoImage.getCalibration();
		soc.setFluoImgCalib(fluoImgCal);
		this.soc = soc;
		this.bfImgCal = bfImgCal;
		this.channel = channel;
		this.maxNbSpot = maxNbSpot;
		this.radius = radius;
		this.frame = frame;
		this.timeInterval = timeInterval;
		this.mitoCells = new HashSet<Integer>();
	}

	/**
	 * the main, use one new thread just in order to free acquisition thread to
	 * acquire images as soon as possible
	 */
	public void run() {
		soc.addSpotContainerOf(channel);
		soc.addFeatureContainerOf(channel);
		// TODO project or not. Do not project if do 3D detection
		ImagePlus zProjectedFluoImg = ImgUtils.zProject(fluoImage);
		zProjectedFluoImg = ImgUtils.unitCmToMicron(zProjectedFluoImg);

		// Call trackmate to detect spots
		MaarsTrackmate detector = new MaarsTrackmate(zProjectedFluoImg, radius);
		Model model = detector.doDetection();
		soc.setTrackmateModel(model);
		soc.refreshSpots(getNBestqualitySpots(model.getSpots()));

		this.factors = ImgUtils.getRescaleFactor(bfImgCal, fluoImgCal);

		int nThread = Runtime.getRuntime().availableProcessors();
		ExecutorService es = Executors.newFixedThreadPool(nThread);
		int nbCell = soc.size();
		final int[] nbOfCellEachThread = new int[2];
		nbOfCellEachThread[0] = (int) nbCell / nThread;
		nbOfCellEachThread[1] = (int) nbOfCellEachThread[0] + nbCell % nThread;
		Future<?> future = null;
		for (int i = 0; i < nThread; i++) {
			// analyze every subset of cell
			future = es.submit(new AnalyseBlockCells(i, nbOfCellEachThread, mitoCells));
		}
		es.shutdown();
		try {
			future.get();
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		} catch (ExecutionException e1) {
			e1.printStackTrace();
		}
		try {
			es.awaitTermination(90, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		for (int i : mitoCells) {
			IJ.log("" + i);
		}
	}

	private SpotCollection getNBestqualitySpots(SpotCollection spots) {
		SpotCollection newSet = new SpotCollection();
		for (Spot s : spots.iterable(false)) {
			int currentFrame = (int) FastMath.round(s.getFeature(Spot.FRAME));
			newSet.add(s, currentFrame);
			if (newSet.getNSpots(currentFrame, false) > soc.size() * maxNbSpot) {
				newSet.remove(findLowestQualitySpot(newSet.iterable(currentFrame, false)), currentFrame);
			}
		}
		return newSet;
	}

	/**
	 * Get the lowest qualit spot in the frame
	 * 
	 * @param channel
	 * @param cellNb
	 * @param frame
	 * @return
	 */
	private Spot findLowestQualitySpot(Iterable<Spot> spotsInFrame) {
		double min = Double.POSITIVE_INFINITY;
		Spot lowestQualitySpot = null;
		for (Spot s : spotsInFrame) {
			if (s.getFeature(Spot.QUALITY) < min) {
				min = s.getFeature(Spot.QUALITY);
				lowestQualitySpot = s;
			}
		}
		return lowestQualitySpot;
	}

	/**
	 * analyzer of subset
	 */
	private class AnalyseBlockCells implements Runnable {
		final int index;
		final int[] deltas;
		HashSet<Integer> mitoCells;

		public AnalyseBlockCells(int index, final int[] deltas, HashSet<Integer> mitoCells) {
			this.index = index;
			this.deltas = deltas;
			this.mitoCells = mitoCells;
		}

		@Override
		public void run() {
			// distribute number of cells for each thread
			int begin = 0;
			int end = 0;
			if (index == 0) {
				if (deltas[0] != deltas[1]) {
					end = deltas[1];
				} else {
					end = deltas[0];
				}
			} else {
				begin = index * deltas[0] + (deltas[1] - deltas[0]);
				end = begin + deltas[0];
			}
			// need to be false because all spots are not visible
			ArrayList<Spot> currentThreadSpots = Lists.newArrayList(soc.getSpotsInModel().iterable(false));
			for (int j = begin; j < end; j++) {
				Cell cell = soc.getCell(j);
				int cellNb = cell.getCellNumber();
				Roi tmpRoi = null;
				if (factors[0] != 1 || factors[1] != 1) {
					tmpRoi = cell.rescaleCellShapeRoi(factors);
				} else {
					tmpRoi = cell.getCellShapeRoi();
				}
				double calibratedXBase = tmpRoi.getXBase() * fluoImgCal.pixelWidth;
				double calibratedYBase = tmpRoi.getYBase() * fluoImgCal.pixelHeight;
				ArrayList<Spot> spotsToDel = new ArrayList<Spot>();
				for (Spot s : currentThreadSpots) {
					if (tmpRoi.contains((int) Math.round(s.getFeature(Spot.POSITION_X) / fluoImgCal.pixelWidth),
							(int) Math.round(s.getFeature(Spot.POSITION_Y) / fluoImgCal.pixelHeight))) {
						soc.putSpot(channel, cellNb, frame, s);
						spotsToDel.add(s);
						if (soc.getNbOfSpot(channel, cellNb, frame) > maxNbSpot) {
							Spot lowesetQulitySpot = soc.findLowestQualitySpot(channel, cellNb, frame);
							soc.removeSpot(channel, cellNb, frame, lowesetQulitySpot);
						}
					}
				}
				// remove spots found in current cell in order to accelerate
				// iteration
				for (Spot s2del : spotsToDel) {
					currentThreadSpots.remove(s2del);
				}
				ComputeGeometry cptgeometry = new ComputeGeometry(cell.get(Cell.X_CENTROID) * fluoImgCal.pixelWidth,
						cell.get(Cell.Y_CENTROID) * fluoImgCal.pixelHeight, cell.get(Cell.MAJOR), cell.get(Cell.ANGLE),
						calibratedXBase, calibratedYBase);
				Iterable<Spot> spotSet = soc.getSpotsInFrame(channel, cellNb, frame);
				if (spotSet != null) {
					// this functions modify directly coordinates of spot in
					// soc, because it's back-up
					cptgeometry.centerSpots(spotSet);
					int setSize = Iterables.size(spotSet);
					HashMap<String, Object> geometry = new HashMap<String, Object>();
					geometry.put(ComputeGeometry.NbOfSpotDetected, setSize);
					if (setSize == 1) {
						geometry.put(ComputeGeometry.PHASE, ComputeGeometry.INTERPHASE);
					} else {
						ArrayList<Spot> poles = cptgeometry.findMostDistant2Spots(spotSet);
						geometry.put(ComputeGeometry.PHASE, ComputeGeometry.MITOSIS);
						geometry = cptgeometry.compute(geometry, poles);
						if (setSize > 2) {
							Line spLine = new Line(
									(int) FastMath
											.round(poles.get(0).getFeature(Spot.POSITION_X) / fluoImgCal.pixelWidth),
									(int) FastMath
											.round(poles.get(0).getFeature(Spot.POSITION_Y) / fluoImgCal.pixelHeight),
									(int) FastMath
											.round(poles.get(1).getFeature(Spot.POSITION_X) / fluoImgCal.pixelWidth),
									(int) FastMath
											.round(poles.get(1).getFeature(Spot.POSITION_Y) / fluoImgCal.pixelHeight));
							Line.setWidth((int) FastMath.round(1 / fluoImgCal.pixelWidth));
							for (Spot s : spotSet) {
								if (!s.equals(poles.get(0)) && !s.equals(poles.get(1))) {
									// ReportingUtils.logMessage(" " + (int)
									// FastMath
									// .round(s.getFeature(Spot.POSITION_X) /
									// fluoImgCal.pixelWidth));
									// ReportingUtils.logMessage(" " + (int)
									// FastMath
									// .round(s.getFeature(Spot.POSITION_Y) /
									// fluoImgCal.pixelHeight));
									if (spLine.contains(
											(int) FastMath.round(s.getFeature(Spot.POSITION_X) / fluoImgCal.pixelWidth),
											(int) FastMath
													.round(s.getFeature(Spot.POSITION_Y) / fluoImgCal.pixelHeight))) {
										if (!mitoCells.contains(cellNb)) {
											this.mitoCells.add(cellNb);
										}
									}
								}
							}
						}
					}
					soc.putGeometry(channel, cellNb, frame, geometry);
					// HashMap<String, Object> newGeometry = new HashMap<String,
					// Object>();
					// int lastGeoIndex = frame - 1;
					// if (frame != 0 && soc.geoFrameExists(channel, cellNb,
					// lastGeoIndex)) {
					// newGeometry = cptgeometry.addVariations(geometry,
					// soc.getGeometry(channel, cellNb, lastGeoIndex),
					// timeInterval);
					// soc.putGeometry(channel, cellNb, frame, newGeometry);
					// }
				}
			}
		}
	}
}
