package fiji.plugin.maars.cellstateanalysis;

import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.Random;

import org.micromanager.utils.ReportingUtils;

import ij.ImagePlus;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.frame.RoiManager;

/**
 * Class to manipulate a set of cells which corresponds to cells of one field
 * 
 * @author marie
 *
 */
public class SetOfCells {

	// tools to get results
	private RoiManager roiManager;
	private Roi[] roiArray;
	private ResultsTable rt;

	// Inputs
	private String pathToRois;
	private ImagePlus bfImage;
	private ImagePlus fluoImage;
	private ImagePlus correaltionImage;
	private int direction;
	// -1 -> cell bounds are black then white
	// 1 -> cell bounds are white then black

	
	private Cell[] cellArray;
	private int maxNbSpotPerCell;

	// output
	private String pathToSaveResults;

	/**
	 * Constructor 1:
	 * 
	 * @param bfImage
	 *            : image containing cells of the set (segmentation was realised
	 *            on this image)
	 * @param fluoImage
	 *            : fluorescent image used to find which cell are in a mitotic
	 *            state
	 * @param correaltionImage
	 *            : correlation image generated by segmentation process
	 * @param focusSlice
	 *            : slice number correponding to focus plane in bfImage
	 * @param direction
	 *            : -1 -> cell bounds are black then white 1 -> cell bounds are
	 *            white then black
	 * @param pathToRois
	 *            : path allowing to get ROIs generated by segmntation process
	 * @param pathToSaveResults
	 *            : path indicating where results of analysis should be stored
	 */
	public SetOfCells(ImagePlus bfImage, ImagePlus correaltionImage,
			ImagePlus fluoImage, int focusSlice, int direction,
			String pathToRois, String pathToSaveResults, int maxNbSpotPerCell) {

		try {
			PrintStream ps = new PrintStream(pathToSaveResults
					+ bfImage.getShortTitle() + "_CellStateAnalysis.LOG");
			System.setOut(ps);
			System.setErr(ps);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

		ReportingUtils.logMessage("Set of cells with fluorescent image :");
		ReportingUtils.logMessage("Get all parameters ...");
		this.bfImage = bfImage;
		this.fluoImage = fluoImage;
		this.correaltionImage = correaltionImage;
		this.pathToRois = pathToRois;
		this.pathToSaveResults = pathToSaveResults;
		this.direction = direction;
		ReportingUtils.logMessage("Done");
		ReportingUtils.logMessage("create result table");
		rt = new ResultsTable();
		ReportingUtils.logMessage("Done");

		ReportingUtils.logMessage("Get ROIs as array");
		roiArray = getRoisAsArray();
		cellArray = new Cell[roiArray.length];
		this.maxNbSpotPerCell = maxNbSpotPerCell;

		ReportingUtils.logMessage("Initialize Cells in array");
		//TODO fluoimage to split
		for (int i = 0; i < roiArray.length; i++) {
			cellArray[i] = new Cell(bfImage, correaltionImage, fluoImage,
					focusSlice, direction, roiArray[i],i, rt, maxNbSpotPerCell);
		}
		ReportingUtils.logMessage("Done.");
	}

	/**
	 * Constructor 2:
	 * 
	 * @param bfImage
	 *            : image containing cells of the set (segmentation was realised
	 *            on this image)
	 * @param correaltionImage
	 *            : correlation image generated by segmentation process
	 * @param focusSlice
	 *            : slice number correponding to focus plane in bfImage
	 * @param direction
	 *            : -1 -> cell bounds are black then white 1 -> cell bounds are
	 *            white then black
	 * @param pathToRois
	 *            : path allowing to get ROIs generated by segmntation process
	 * @param pathToSaveResults
	 *            : path indicating where results of analysis should be stored
	 */
	public SetOfCells(ImagePlus bfImage, ImagePlus correaltionImage,
			int focusSlice, int direction, String pathToRois,
			String pathToSaveResults, int maxNbSpotPerCell) {

		try {
			PrintStream ps = new PrintStream(pathToSaveResults
					+ bfImage.getShortTitle() + "_CellStateAnalysis.LOG");
			System.setOut(ps);
			System.setErr(ps);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

		ReportingUtils.logMessage("Set of cells without fluorescent image :");
		ReportingUtils.logMessage("Get all parameters ...");
		this.bfImage = bfImage;
		this.correaltionImage = correaltionImage;
		this.pathToRois = pathToRois;
		this.pathToSaveResults = pathToSaveResults;
		this.direction = direction;
		ReportingUtils.logMessage("Done");
		ReportingUtils.logMessage("create result table");
		rt = new ResultsTable();
		ReportingUtils.logMessage("Done");

		ReportingUtils.logMessage("Get ROIs as array");
		roiArray = getRoisAsArray();
		cellArray = new Cell[roiArray.length];
		this.maxNbSpotPerCell = maxNbSpotPerCell;

		// roiManager.runCommand("Delete");
		ReportingUtils.logMessage("Initialize Cells in array");
		for (int i = 0; i < roiArray.length; i++) {
			cellArray[i] = new Cell(bfImage, correaltionImage, focusSlice,
					direction, roiArray[i], i+1, rt, maxNbSpotPerCell);

			// just for test
			// roiManager.addRoi(cellArray[i].getLinearRoi());
		}
		ReportingUtils.logMessage("Done.");
	}

	/**
	 * Constructor 3:
	 * 
	 * @param cellArray
	 *            : array of cell
	 */
	public SetOfCells(Cell[] cellArray) {
		this.cellArray = cellArray;
	}

	/**
	 * Method to shuffle set of cell (put them in random order)
	 */
	public void shuffle() {

		int n = length();
		Random random = new Random();

		for (int i = 0; i < n; i++) {
			int newPosition = i + random.nextInt(n - i);
			Cell cellTemp = cellArray[i];
			cellArray[i] = cellArray[newPosition];
			cellArray[newPosition] = cellTemp;
		}
	}

	/**
	 * Method to open ROI file and get them as ROI array
	 * 
	 * @return
	 */
	public Roi[] getRoisAsArray() {

		roiManager = new RoiManager();
		roiManager.runCommand("Open", pathToRois);
		return roiManager.getRoisAsArray();
	}

	/**
	 * Method to add one of the ROI to RoiManager
	 * 
	 * @param cellIndex
	 *            : index of ROI the user want to add to the manager
	 * @param roiType
	 *            : type of ROI to add, must be "cellLinearROI" for linear ROI
	 *            and "cellShapeROI" for non-linear ones
	 */
	public void addRoiToManager(int cellIndex, String roiType) {
		if (roiType == "cellShapeROI") {
			roiManager.addRoi(cellArray[cellIndex].getCellShapeRoi());
		} else {
			if (roiType == "cellLinearROI") {
				roiManager.addRoi(cellArray[cellIndex].getLinearRoi());
			} else {
				ReportingUtils.logMessage("Not an option");
			}
		}
	}

	/**
	 * Closes RoiManager
	 */
	public void closeRoiManager() {
		roiManager.close();
	}

	/**
	 * Method to get Cell corresponding to index
	 * 
	 * @param index
	 * @return Cell corresponding to index
	 */
	public Cell getCell(int index) {
		return cellArray[index];
	}

	/**
	 * Method to get Cell index using coordinates of centroid
	 * 
	 * @param xCentroid
	 * @param yCentroid
	 * @return index of cell if there is any, -1 otherwise
	 */
	public int getCellIndex(double xCentroid, double yCentroid) {
		int index = -1;

		for (int i = 0; i < cellArray.length; i++) {
			if (cellArray[i].getMeasures().getXCentroid() == xCentroid
					&& cellArray[i].getMeasures().getYCentroid() == yCentroid) {
				index = i;
			}
		}
		return index;
	}

	/**
	 * 
	 * @return image used for segmentation
	 */
	public ImagePlus getBFImage() {
		return bfImage;
	}

	/**
	 * 
	 * @return array length
	 */
	public int length() {
		return cellArray.length;
	}

	/**
	 * 
	 * @return path where results are stored
	 */
	public String getPath() {
		return pathToSaveResults;
	}
	
	/**
	 * 
	 * @return path where results are stored
	 */
	public RoiManager getROIManager() {
		return this.roiManager;
	}
}
