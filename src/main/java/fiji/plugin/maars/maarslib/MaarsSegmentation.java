package fiji.plugin.maars.maarslib;

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

	public SegPombeParameters getSegPombeParam() {
		return this.segPombeParam;
	}
}