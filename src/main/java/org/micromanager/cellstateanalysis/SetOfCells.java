package org.micromanager.cellstateanalysis;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.utils.ImgUtils;

import com.thoughtworks.xstream.XStream;

import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.SpotCollection;
import fiji.plugin.trackmate.io.TmXmlWriter;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.frame.RoiManager;

/**
 * Class to manipulate a set of cells which corresponds to cells of one field
 * 
 * @author Tong LI
 *
 */
public class SetOfCells implements Iterable<Cell>, Iterator<Cell> {

	private RoiManager roiManager;
	private Roi[] roiArray;
	private int count = 0;
	private ArrayList<Cell> cellArray;
	private String rootSavingPath;
	private HashMap<String, HashMap<Integer, SpotCollection>> spotsInCells;
	// structure for features is really complex...because i need write haspmap
	// to xml, I will rewrite an xmlwrite for feature collection object for
	// exemple
	private HashMap<String, HashMap<Integer, HashMap<Integer, HashMap<String, Object>>>> featuresOfCells;
	private ArrayList<String[]> acqIDs;
	private Model trackmateModel;
	private HashMap<Integer, ImageStack> croppedStacks;

	/**
	 * Constructor
	 * 
	 * @param savingPath
	 *            :root directory of acquisitions
	 */
	public SetOfCells(String savingPath) {
		this.rootSavingPath = savingPath;
	}

	/**
	 * @param parameters
	 *            : parameters that used in
	 */
	public void loadCells(String xPos, String yPos) {
		ReportingUtils.logMessage("Loading Cells");
		roiArray = getRoisAsArray(rootSavingPath + "/movie_X" + xPos + "_Y" + yPos + "/" + "ROI.zip");
		cellArray = new ArrayList<Cell>();
		for (int i = 0; i < roiArray.length; i++) {
			cellArray.add(i, new Cell(roiArray[i], i));
		}
		ReportingUtils.logMessage("Done.");
	}

	/**
	 * Method to open ROI file and get them as ROI array
	 * 
	 * @return
	 */
	public Roi[] getRoisAsArray(String pathToRois) {

		roiManager = RoiManager.getInstance();
		if (roiManager == null) {
			roiManager = new RoiManager();
		}
		if (roiManager.getCount() == 0) {
			roiManager.runCommand("Open", pathToRois);
		}
		return roiManager.getRoisAsArray();
	}

	/**
	 * Method to get Cell corresponding to index
	 * 
	 * @param index
	 * @return Cell corresponding to index
	 */
	public Cell getCell(int index) {
		return cellArray.get(index);
	}

	/**
	 * total number of cell
	 * 
	 * @return
	 */
	public int size() {
		return cellArray.size();
	}

	public void addChSpotContainer(String channel) {
		if (spotsInCells == null) {
			spotsInCells = new HashMap<String, HashMap<Integer, SpotCollection>>();
		}
		if (!spotsInCells.containsKey(channel)) {
			spotsInCells.put(channel, new HashMap<Integer, SpotCollection>());
		}
	}

	public void putSpot(String channel, int cellNb, int frame, Spot spot) {
		if (!spotsInCells.get(channel).containsKey(cellNb)) {
			spotsInCells.get(channel).put(cellNb, new SpotCollection());
		}
		spotsInCells.get(channel).get(cellNb).add(spot, frame);
	}

	public HashMap<Integer, SpotCollection> getSpots(String channel) {
		return spotsInCells.get(channel);
	}

	public SpotCollection getSpotsOfCell(String channel, int cellNb) {
		return getSpots(channel).get(cellNb);
	}

	public Iterable<Spot> getSpotsInFrame(String channel, int cellNb, int frame) {
		if (!getSpots(channel).containsKey(cellNb)) {
			return null;
		}
		return getSpotsOfCell(channel, cellNb).iterable(frame, false);
	}

	public int getNbOfSpot(String channel, int cellNb, int frame) {
		return getSpotsOfCell(channel, cellNb).getNSpots(frame, false);
	}

	public Spot findLowestQualitySpot(String channel, int cellNb, int frame) {
		Iterable<Spot> spotsInFrame = getSpotsInFrame(channel, cellNb, frame);
		double min = 1000;
		Spot lowestQualitySpot = null;
		for (Spot s : spotsInFrame) {
			if (s.getFeature(Spot.QUALITY) < min) {
				min = s.getFeature(Spot.QUALITY);
				lowestQualitySpot = s;
			}
		}
		return lowestQualitySpot;
	}

	public void addFeaturesContainer(String channel) {
		if (this.featuresOfCells == null) {
			this.featuresOfCells = new HashMap<String, HashMap<Integer, HashMap<Integer, HashMap<String, Object>>>>();
		}
		if (!featuresOfCells.containsKey(channel)) {
			featuresOfCells.put(channel, new HashMap<Integer, HashMap<Integer, HashMap<String, Object>>>());
		}
	}

	public void putFeature(String channel, int cellNb, int frame, HashMap<String, Object> features) {
		if (!featuresOfCells.get(channel).containsKey(cellNb)) {
			featuresOfCells.get(channel).put(cellNb, new HashMap<Integer, HashMap<String, Object>>());
		}
		if (!featuresOfCells.get(channel).get(cellNb).containsKey(frame)) {
			featuresOfCells.get(channel).get(cellNb).put(frame, new HashMap<String, Object>());
		}
		featuresOfCells.get(channel).get(cellNb).put(frame, features);
	}

	public void setRoiMeasurementIntoCells(ResultsTable rt) {
		for (Cell c : cellArray) {
			c.setRoiMeasurement(rt.getRowAsString(c.getCellNumber()));
		}
	}

	public void setTrackmateModel(Model model) {
		if (this.trackmateModel == null)
			this.trackmateModel = model;
	}

	public void writeResults() {
		croppedStacks = new HashMap<Integer, ImageStack>();
		for (String[] id : acqIDs) {
			String xPos = id[0];
			String yPos = id[1];
			String frame = id[2];
			String channel = id[3];
			String fluoDir = rootSavingPath + "/movie_X" + xPos + "_Y" + yPos + "_FLUO/";
			String croppedImgDir = fluoDir + "croppedImgs/";
			String spotsXmlDir = fluoDir + "spots/";
			String featuresXmlDir = fluoDir + "features/";
			if (!new File(croppedImgDir).exists()) {
				new File(croppedImgDir).mkdirs();
			}
			if (!new File(spotsXmlDir).exists()) {
				new File(spotsXmlDir).mkdirs();
			}
			if (!new File(featuresXmlDir).exists()) {
				new File(featuresXmlDir).mkdirs();
			}
			ImagePlus fluoImg = IJ.openImage(fluoDir + frame + "_" + channel + "/MMStack.ome.tif");
			ImagePlus zprojectImg = ImgUtils.zProject(fluoImg);
			// save cropped cells
			for (int i = 0; i < roiArray.length; i++) {
				ImagePlus croppedImg = ImgUtils.cropImgWithRoi(zprojectImg, roiArray[i]);
				if (!croppedStacks.containsKey(i)) {
					croppedStacks.put(i, croppedImg.getStack());
				} else {
					ImageStack tmpStack = croppedStacks.get(i);
					tmpStack.addSlice(croppedImg.getStack().getProcessor(1));
					croppedStacks.put(i, tmpStack);
				}
			}
			for (int j = 0; j < croppedStacks.size(); j++) {
				String pathToCroppedImg = croppedImgDir + String.valueOf(j);
				ImagePlus imp = new ImagePlus("cell_" + j, croppedStacks.get(j));
				imp.setCalibration(zprojectImg.getCalibration());
				IJ.saveAsTiff(imp, pathToCroppedImg);
			}
			System.out.println("Find " + spotsInCells.get(channel).size() + " cells with spots in channel " + channel);
			// for each cell
			File newFile = null;
			for (int cellNb : spotsInCells.get(channel).keySet()) {
				// save spots detected
				newFile = new File(spotsXmlDir + String.valueOf(cellNb) + "_" + channel + ".xml");
				TmXmlWriter spotsWriter = new TmXmlWriter(newFile);
				SpotCollection centeredSpots = new SpotCollection();
				for (Spot s : spotsInCells.get(channel).get(cellNb).iterable(false)) {
					double xPosBeforeCrop = s.getFeature(Spot.POSITION_X);
					double yPosBeforeCrop = s.getFeature(Spot.POSITION_Y);
					s.putFeature(Spot.POSITION_X,
							xPosBeforeCrop - (roiArray[cellNb].getXBase() * zprojectImg.getCalibration().pixelWidth));
					s.putFeature(Spot.POSITION_Y,
							yPosBeforeCrop - (roiArray[cellNb].getYBase() * zprojectImg.getCalibration().pixelHeight));
					centeredSpots.add(s, (int) Math.round(s.getFeature(Spot.FRAME)));
				}
				trackmateModel.setSpots(centeredSpots, false);
				spotsWriter.appendModel(trackmateModel);
				try {
					spotsWriter.writeToFile();
				} catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				// save features
				newFile = new File(featuresXmlDir + String.valueOf(cellNb) + "_" + channel + ".xml");
				XStream xStream = new XStream();
				xStream.alias("cell", java.util.HashMap.class);
				String xml = xStream.toXML(featuresOfCells.get(channel).get(cellNb));
				FileOutputStream fos = null;
				try {
					fos = new FileOutputStream(newFile);
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				try {
					fos.write(xml.getBytes());
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}

	public void addAcqIDs(String[] id) {
		if (acqIDs == null) {
			acqIDs = new ArrayList<String[]>();
		}
		acqIDs.add(id);
	}

	// iterator related
	@Override
	public Iterator<Cell> iterator() {
		resetCount();
		return this;
	}

	@Override
	public boolean hasNext() {
		return count < cellArray.size();
	}

	@Override
	public Cell next() {
		if (count >= cellArray.size())
			throw new NoSuchElementException();
		count++;
		return cellArray.get(count - 1);
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}

	public void resetCount() {
		this.count = 0;
	}

}
