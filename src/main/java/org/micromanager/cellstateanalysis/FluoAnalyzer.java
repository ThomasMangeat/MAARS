package org.micromanager.cellstateanalysis;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.micromanager.utils.ImgUtils;

import com.google.common.collect.Iterables;

import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.SpotCollection;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.measure.ResultsTable;

/**
 * @author Tong LI, mail:tongli.bioinfo@gmail.com
 * @version Nov 19, 2015
 */
public class FluoAnalyzer implements Runnable {

	private ImagePlus fluoImage;
	private SetOfCells soc;
	private double[] factors;
	private Calibration bfImgCal;
	private SpotCollection spots;
	private String channel;
	private int maxNbSpot;
	private double radius;
	private int frame;
	private ResultsTable roiMeasurements;

	public FluoAnalyzer(ImagePlus fluoImage, Calibration bfImgCal, SetOfCells soc, String channel, int maxNbSpot,
			double radius, int frame) {
		this.fluoImage = fluoImage;
		this.soc = soc;
		this.bfImgCal = bfImgCal;
		this.channel = channel;
		this.maxNbSpot = maxNbSpot;
		this.radius = radius;
		this.frame = frame;
	}

	public void run() {
		soc.addChSpotContainer(channel);
		soc.addFeaturesContainer(channel);
		roiMeasurements = soc.getRoiMeasurement();
		// TODO project or not
		ImagePlus zProjectedFluoImg = ImgUtils.zProject(fluoImage);
		zProjectedFluoImg = ImgUtils.unitCmToMicron(zProjectedFluoImg);
		SpotsDetector detector = new SpotsDetector(zProjectedFluoImg, radius);
		Model model = detector.doDetection();
		soc.setTrackmateModel(model);
		spots = model.getSpots();

		this.factors = ImgUtils.getRescaleFactor(bfImgCal, zProjectedFluoImg.getCalibration());

		int nThread = Runtime.getRuntime().availableProcessors();
		ExecutorService es = Executors.newFixedThreadPool(nThread);
		int nbCell = soc.size();
		final int[] nbOfCellEachThread = new int[2];
		nbOfCellEachThread[0] = (int) nbCell / nThread;
		nbOfCellEachThread[1] = (int) nbOfCellEachThread[0] + nbCell % nThread;
		for (int i = 0; i < nThread; i++) {
			if (i == 0) {
				es.execute(new AnalyseBlockCells(i, nbOfCellEachThread));
			} else {
				es.execute(new AnalyseBlockCells(i, nbOfCellEachThread));
			}
		}
		es.shutdown();
		try {
			es.awaitTermination(1, TimeUnit.MINUTES);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	private class AnalyseBlockCells implements Runnable {
		final int index;
		final int[] deltas;

		public AnalyseBlockCells(int index, final int[] deltas) {
			this.index = index;
			this.deltas = deltas;
		}

		@Override
		public void run() {
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
			for (int j = begin; j < end; j++) {
				Cell cell = soc.getCell(j);
				int cellNb = cell.getCellNumber();
				Roi tmpRoi = null;
				if (factors[0] != factors[1]) {
					tmpRoi = cell.rescaleCellShapeRoi(factors);
				} else {
					tmpRoi = cell.getCellShapeRoi();
				}
				for (Spot s : spots.iterable(false)) {
					if (tmpRoi.contains(
							(int) Math.round(s.getFeature(Spot.POSITION_X) / fluoImage.getCalibration().pixelWidth),
							(int) Math.round(s.getFeature(Spot.POSITION_Y) / fluoImage.getCalibration().pixelHeight))) {
						soc.putSpot(channel, cellNb, frame, s);
						if (soc.getNbOfSpot(channel, cellNb, frame) > maxNbSpot) {
							Spot lowesetQulitySpot = soc.findLowestQualitySpot(channel, cellNb, frame);
							soc.getSpotsOfCell(channel, cellNb).remove(lowesetQulitySpot, frame);
						}
					}
				}
				Iterable<Spot> spotSet = soc.getSpotsInFrame(channel, cellNb, frame);
				int setSize = Iterables.size(spotSet);
				ComputeFeatures cptgeometry = null;
				if (setSize == 1) {
					// interphase
					cptgeometry = new ComputeFeatures();
				} else {
					cptgeometry = new ComputeFeatures(spotSet, cell.getMeasures());
				}
				soc.putFeature(channel, cellNb, frame, cptgeometry.getFeatures());
			}
		}
	}
}
