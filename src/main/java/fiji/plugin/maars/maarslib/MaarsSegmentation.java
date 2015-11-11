package fiji.plugin.maars.maarslib;

import java.io.FileWriter;
import java.io.IOException;

import org.micromanager.internal.utils.ReportingUtils;

import fiji.plugin.maars.segmentPombe.ParametersProcessing;
import fiji.plugin.maars.segmentPombe.SegPombe;
import fiji.plugin.maars.segmentPombe.SegPombeParameters;
import fiji.plugin.maars.utils.FileUtils;
import ij.IJ;
import ij.ImagePlus;

/**
 * Class to segment a multiple z-stack bright field image then find and record
 * cell shape Rois
 * 
 * @author Tong LI
 *
 */
public class MaarsSegmentation {
	private MaarsParameters parameters;
	private String pathToSegMovie;
	private String pathToSegDir;
	private SegPombeParameters segPombeParam;
	private boolean roiDetected = false;

	/**
	 * Constructor :
	 * 
	 * @param parameters
	 *            : MAARS parameters (see class MaarsParameters)
	 * @param positionX
	 *            : current X coordinate of microscope's view.
	 * @param positionY
	 *            : current Y coordinate of microscope's view.
	 */
	public MaarsSegmentation(MaarsParameters parameters, double positionX,
			double positionY) {

		this.parameters = parameters;
		this.pathToSegDir = FileUtils.convertPath(parameters.getSavingPath()
				+ "/movie_X" + Math.round(positionX) + "_Y"
				+ Math.round(positionY) + "/");
		this.pathToSegMovie = FileUtils.convertPath(pathToSegDir
				+ "MMStack.ome.tif");
	}

	/**
	 * Get the parameters and use them to segment cells
	 */
	public void segmentation() {

		ReportingUtils
				.logMessage("Segmentation movie path : " + pathToSegMovie);
		ImagePlus img = null;
		if (FileUtils.isValid(pathToSegMovie)) {
			img = IJ.openImage(pathToSegMovie);
		} else {
			IJ.error("Path not valid");
		}

		segPombeParam = new SegPombeParameters();

		segPombeParam.setImageToAnalyze(img);
		segPombeParam.setSavingPath(pathToSegDir);

		segPombeParam.setFilterAbnormalShape(Boolean.parseBoolean(parameters
				.getSegmentationParameter(MaarsParameters.FILTER_SOLIDITY)));

		segPombeParam
				.setFiltrateWithMeanGrayValue(Boolean.parseBoolean(parameters
						.getSegmentationParameter(MaarsParameters.FILTER_MEAN_GREY_VALUE)));

		segPombeParam.getImageToAnalyze().getCalibration().pixelDepth = Double
				.parseDouble(parameters
						.getSegmentationParameter(MaarsParameters.STEP));

		ParametersProcessing process = new ParametersProcessing(segPombeParam);

		process.checkImgUnitsAndScale();
		process.changeScale(
				Integer.parseInt(parameters
						.getSegmentationParameter(MaarsParameters.NEW_MAX_WIDTH_FOR_CHANGE_SCALE)),
				Integer.parseInt(parameters
						.getSegmentationParameter(MaarsParameters.NEW_MAX_HEIGTH_FOR_CHANGE_SCALE)));

		segPombeParam = process.getParameters();

		segPombeParam.setSigma((int) Math.round(Double.parseDouble(parameters
				.getSegmentationParameter(MaarsParameters.CELL_SIZE))
				/ Double.parseDouble(parameters
						.getSegmentationParameter(MaarsParameters.STEP))));

		segPombeParam
				.setMinParticleSize((int) Math.round(Double.parseDouble(parameters
						.getSegmentationParameter(MaarsParameters.MINIMUM_CELL_AREA))
						/ segPombeParam.getImageToAnalyze().getCalibration().pixelWidth)
						/ segPombeParam.getImageToAnalyze().getCalibration().pixelHeight);

		segPombeParam
				.setMaxParticleSize((int) Math.round(Double.parseDouble(parameters
						.getSegmentationParameter(MaarsParameters.MAXIMUM_CELL_AREA))
						/ segPombeParam.getImageToAnalyze().getCalibration().pixelWidth)
						/ segPombeParam.getImageToAnalyze().getCalibration().pixelHeight);

		segPombeParam.setSolidityThreshold(Double.parseDouble(parameters
				.getSegmentationParameter(MaarsParameters.SOLIDITY)));

		segPombeParam.setMeanGreyValueThreshold(Double.parseDouble(parameters
				.getSegmentationParameter(MaarsParameters.MEAN_GREY_VALUE)));

		SegPombe segPombe = new SegPombe(segPombeParam);
		segPombe.createCorrelationImage();
		segPombe.convertCorrelationToBinaryImage();
		segPombe.analyseAndFilterParticles();
		segPombe.showAndSaveResultsAndCleanUp();
		if (segPombe.roiDetected()) {
			this.roiDetected = true;
			segPombe.getRoiManager().close();
		}
	}

	/**
	 * 
	 * @return if no Roi detected
	 */
	public boolean roiDetected() {
		return this.roiDetected;
	}

//	/**
//	 * write config parameters used in current analysis
//	 * 
//	 */
//	public void writeUsedConfig() {
//		double timeInterval = Double.parseDouble(parameters
//				.getFluoParameter(MaarsParameters.TIME_INTERVAL)) / 1000;
////		int maxNbSpot = Integer.parseInt(parameters
////				.getFluoParameter(MaarsParameters.MAXIMUM_NUMBER_OF_SPOT));
//		int maxWidth = parameters.getParametersAsJsonObject()
//				.get(MaarsParameters.SEGMENTATION_PARAMETERS).getAsJsonObject()
//				.get(MaarsParameters.NEW_MAX_WIDTH_FOR_CHANGE_SCALE).getAsInt();
//		int maxHeight = parameters.getParametersAsJsonObject()
//				.get(MaarsParameters.SEGMENTATION_PARAMETERS).getAsJsonObject()
//				.get(MaarsParameters.NEW_MAX_HEIGTH_FOR_CHANGE_SCALE)
//				.getAsInt();
//		double cellSize = parameters.getParametersAsJsonObject()
//				.get(MaarsParameters.SEGMENTATION_PARAMETERS).getAsJsonObject()
//				.get(MaarsParameters.CELL_SIZE).getAsDouble();
//		double segRange = parameters.getParametersAsJsonObject()
//				.get(MaarsParameters.SEGMENTATION_PARAMETERS).getAsJsonObject()
//				.get(MaarsParameters.RANGE_SIZE_FOR_MOVIE).getAsDouble();
//		double segStep = parameters.getParametersAsJsonObject()
//				.get(MaarsParameters.SEGMENTATION_PARAMETERS).getAsJsonObject()
//				.get(MaarsParameters.STEP).getAsDouble();
//		double minCellArea = parameters.getParametersAsJsonObject()
//				.get(MaarsParameters.SEGMENTATION_PARAMETERS).getAsJsonObject()
//				.get(MaarsParameters.MINIMUM_CELL_AREA).getAsDouble();
//		double maxCellArea = parameters.getParametersAsJsonObject()
//				.get(MaarsParameters.SEGMENTATION_PARAMETERS).getAsJsonObject()
//				.get(MaarsParameters.MAXIMUM_CELL_AREA).getAsDouble();
//		double solidity = parameters.getParametersAsJsonObject()
//				.get(MaarsParameters.SEGMENTATION_PARAMETERS).getAsJsonObject()
//				.get(MaarsParameters.SOLIDITY).getAsDouble();
//		double meanGrey = parameters.getParametersAsJsonObject()
//				.get(MaarsParameters.SEGMENTATION_PARAMETERS).getAsJsonObject()
//				.get(MaarsParameters.MEAN_GREY_VALUE).getAsDouble();
//		String rootDirName = parameters.getParametersAsJsonObject()
//				.get(MaarsParameters.GENERAL_ACQUISITION_PARAMETERS)
//				.getAsJsonObject().get(MaarsParameters.SAVING_PATH)
//				.getAsString();
//		double fluoRange = parameters.getParametersAsJsonObject()
//				.get(MaarsParameters.FLUO_ANALYSIS_PARAMETERS)
//				.getAsJsonObject().get(MaarsParameters.RANGE_SIZE_FOR_MOVIE)
//				.getAsDouble();
//		double fluoStep = parameters.getParametersAsJsonObject()
//				.get(MaarsParameters.FLUO_ANALYSIS_PARAMETERS)
//				.getAsJsonObject().get(MaarsParameters.STEP).getAsDouble();
//		boolean filterUnusualShape = parameters.getParametersAsJsonObject()
//				.get(MaarsParameters.SEGMENTATION_PARAMETERS).getAsJsonObject()
//				.get(MaarsParameters.FILTER_SOLIDITY).getAsBoolean();
//		boolean filterWithMeanGrayValue = parameters
//				.getParametersAsJsonObject()
//				.get(MaarsParameters.SEGMENTATION_PARAMETERS).getAsJsonObject()
//				.get(MaarsParameters.FILTER_MEAN_GREY_VALUE).getAsBoolean();
//		FileWriter configFile = null;
//		try {
//			configFile = new FileWriter(FileUtils.convertPath(pathToSegDir
//					+ "/configUsed.txt"));
//		} catch (IOException e1) {
//			// TODO Auto-generated catch block
//			e1.printStackTrace();
//		}
//		try {
//			configFile.write("Interval between 2 frames:\t"
//					+ String.valueOf(timeInterval) + "\n"
//					+ "Max number of spot:\t" + String.valueOf(maxNbSpot)
//					+ "\n" + "Max width of bright field:\t"
//					+ String.valueOf(maxWidth) + "\n"
//					+ "Max height of bright field:\t"
//					+ String.valueOf(maxHeight) + "\n" + "Typical cell size:\t"
//					+ String.valueOf(cellSize) + "\n" + "Segmentation range:\t"
//					+ String.valueOf(segRange) + "\n"
//					+ "Segmentation step size:\t" + String.valueOf(segStep)
//					+ "\n" + "Fluo acquisition range:\t"
//					+ String.valueOf(fluoRange) + "\n"
//					+ "Fluo acquisition step size:\t"
//					+ String.valueOf(fluoStep) + "\n" + "Minimum cell area:\t"
//					+ String.valueOf(minCellArea) + "\n"
//					+ "Maximum cell area:\t" + String.valueOf(maxCellArea)
//					+ "\n" + "Solidity thresold enable:\t"
//					+ String.valueOf(filterUnusualShape) + "\n"
//					+ "Solidity thresold:\t" + String.valueOf(solidity) + "\n"
//					+ "Gray level thresold enable:\t"
//					+ String.valueOf(filterWithMeanGrayValue) + "\n"
//					+ "Gray level thresold:\t" + String.valueOf(meanGrey)
//					+ "\n" + "Root dir:\t" + rootDirName + "\n");
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//		try {
//			configFile.close();
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//	}

	public SegPombeParameters getSegPombeParam() {
		return this.segPombeParam;
	}
}
