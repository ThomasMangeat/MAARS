package org.micromanager.maarslib;

import java.awt.Color;
import java.io.IOException;

import mmcorej.CMMCore;

import org.micromanager.internal.MMStudio;
import org.micromanager.cellstateanalysis.SetOfCells;
import org.micromanager.data.Datastore;
import org.micromanager.data.DatastoreFrozenException;
import org.micromanager.data.Image;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.data.SummaryMetadata.SummaryMetadataBuilder;
import org.micromanager.internal.utils.MMScriptException;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.maars.MaarsParameters;

import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

/**
 * Acquisition calibrated for fluorescent image analysis using package
 * CellStateAnalysis
 * 
 * @author Tong LI
 *
 */
public class MaarsAcquisitionForFluoAnalysis {

	private MMStudio mm;
	private CMMCore mmc;
	private MaarsParameters parameters;
	private double positionX;
	private double positionY;

	/**
	 * Constructor :
	 * 
	 * @param mm
	 *            : MMStudio interface
	 * @param mmc
	 *            : Core object of Micro-Manager
	 * @param parameters
	 *            : parameters used for algorithm
	 * @param positionX
	 *            : x field position (can be defined by ExplorationXYPositions)
	 * @param positionY
	 *            : y field position (can be defined by ExplorationXYPositions)
	 * @param soc
	 *            : Set of cells (object from CellStateAnalysis)
	 */
	public MaarsAcquisitionForFluoAnalysis(MMStudio mm, CMMCore mmc,
			MaarsParameters parameters, double positionX, double positionY,
			SetOfCells soc) {
		this.mm = mm;
		this.mmc = mmc;
		this.parameters = parameters;
		this.positionX = positionX;
		this.positionY = positionY;
	}

	/**
	 * Acquire specific movie
	 * 
	 * @param show
	 *            : true to see acquisition in live
	 * @param frame
	 *            :
	 * @return ImagePlus object
	 */
	public ImagePlus acquire(boolean show, int frame, String channel)
			throws MMScriptException {

		boolean save = Boolean.parseBoolean(parameters
				.getFluoParameter(MaarsParameters.SAVE_FLUORESCENT_MOVIES));

		if (!save) {
			show = false;
		}

		ReportingUtils.logMessage("Acquire movie for fluorescent analysis :");
		ReportingUtils.logMessage("________________________________");

		ReportingUtils.logMessage("Close all previous acquisitions");
		mm.getDataManager().clearPipeline();
		ReportingUtils.logMessage("... Initialize parameters :");

		String channelGroup = parameters.getChannelGroup();
		ReportingUtils.logMessage("- channel group : " + channelGroup);

		String rootDirName = parameters.getSavingPath();
		ReportingUtils.logMessage("- saving path : " + rootDirName);

		ReportingUtils.logMessage("- channel : " + channel);

		String shutter = parameters.getChShutter(channel);
		ReportingUtils.logMessage("- shutter : " + shutter);

		Color color = MaarsParameters.getColor(parameters.getChColor(channel));
		ReportingUtils.logMessage("- color : " + color.toString());

		int exposure = Integer.parseInt(parameters.getChExposure(channel));
		ReportingUtils.logMessage("- exposure : " + exposure);

		int frameNumber = Integer.parseInt(parameters
				.getFluoParameter(MaarsParameters.FRAME_NUMBER));
		ReportingUtils.logMessage("- frame number : " + frameNumber);

		double range = Double.parseDouble(parameters
				.getFluoParameter(MaarsParameters.RANGE_SIZE_FOR_MOVIE));
		ReportingUtils.logMessage("- range size : " + range);

		double step = Double.parseDouble(parameters
				.getFluoParameter(MaarsParameters.STEP));
		ReportingUtils.logMessage("- step : " + step);

		int sliceNumber = (int) Math.round(range / step);
		ReportingUtils.logMessage("- slice number : " + sliceNumber);

		String acqName = "movie_X" + Math.round(positionX) + "_Y"
				+ Math.round(positionY) + "_FLUO/" + frame + "_" + channel;

		String pathToMovie = rootDirName + "/" + acqName;
		ReportingUtils.logMessage("- acquisition name : " + acqName);

		ReportingUtils.logMessage("... Set shutter device");
		try {
			mmc.setShutterDevice(shutter);
		} catch (Exception e1) {
			ReportingUtils.logMessage("Could not set shutter device");
			e1.printStackTrace();
		}
		
		ReportingUtils.logMessage("... Set exposure");
		try {
			mmc.setExposure(exposure);
		} catch (Exception e1) {
			ReportingUtils.logMessage("could not set exposure");
			e1.printStackTrace();
		}

		ReportingUtils.logMessage("... set config");
		try {
			mmc.setConfig(channelGroup, channel);
		} catch (Exception e1) {
			ReportingUtils.logMessage("Could not set config");
			e1.printStackTrace();
		}

		ReportingUtils.logMessage("... wait for config");
		try {
			mmc.waitForConfig(channelGroup, channel);
		} catch (Exception e1) {
			ReportingUtils.logError(e1);
		}

		ReportingUtils.logMessage("... Initialize a Datastore");
		Datastore fluoDS = null;
		try {
			fluoDS = mm.getDataManager().createMultipageTIFFDatastore(
					pathToMovie, false, false);
		} catch (IOException e3) {
			ReportingUtils.logMessage("... Can not initialize Datastore");
		}

		ReportingUtils.logMessage("... Update summaryMetadata");
		SummaryMetadataBuilder summaryMD = fluoDS.getSummaryMetadata().copy();
		summaryMD = summaryMD.channelGroup(channelGroup);
		String[] channels = new String[1];
		channels[0] = channel;
		summaryMD = summaryMD.channelNames(channels);
		summaryMD = summaryMD.name(acqName);
		SummaryMetadata newSegMD = summaryMD.build();
		try {
			fluoDS.setSummaryMetadata(newSegMD);
		} catch (DatastoreFrozenException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		}
		ReportingUtils.logMessage("... set shutter open");
		try {
			mmc.setShutterOpen(true);
		} catch (Exception e1) {
			ReportingUtils.logMessage("could not open shutter");
			e1.printStackTrace();
		}

		ReportingUtils.logMessage("... get z current position");
		double zFocus = 0;
		try {
			zFocus = mmc.getPosition(mmc.getFocusDevice());
		} catch (Exception e) {
			ReportingUtils.logError(e);
		}

		ReportingUtils.logMessage("-> z focus is " + zFocus);

		ReportingUtils.logMessage("... start acquisition");
		// TODO
		// double z = zFocus - (range / 2);
		double z = zFocus - (range / 2) + 2;
		ReportingUtils.logMessage("- create imagestack");
		ImageStack imageStack = new ImageStack((int) mmc.getImageWidth(),
				(int) mmc.getImageHeight());

		for (int k = 0; k <= sliceNumber; k++) {
			try {
				mmc.setPosition(mmc.getFocusDevice(), z);
				mmc.waitForDevice(mmc.getFocusDevice());
			} catch (Exception e) {
				ReportingUtils.logError(e);
			}

			ReportingUtils.logMessage("...snap and add images");
			try {
				fluoDS.putImage(mm.getSnapLiveManager().snap(true).get(0));
			} catch (IllegalArgumentException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (DatastoreFrozenException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			mm.getSnapLiveManager().snap(false).remove(0);
			Image img = fluoDS.getImage(fluoDS.getMaxIndices());
			ReportingUtils.logMessage("Convert image to ij.ImageProcessor");
			ImageProcessor imgProcessor = mm.getDataManager().ij()
					.createProcessor(img);
			ShortProcessor shortProcessor = new ShortProcessor(
					(int) mmc.getImageWidth(), (int) mmc.getImageHeight());
			ReportingUtils.logMessage("Put pixels in new ImagePlus");
			shortProcessor.setPixels(imgProcessor.getPixels());
			ReportingUtils
					.logMessage("Add slice into a duplicate of DataStore");
			imageStack.addSlice(shortProcessor);
			z = z + step;
		}
		ImagePlus imagePlus = new ImagePlus("Maars", imageStack);
		Calibration cal = new Calibration();
		cal.setUnit("micron");
		cal.pixelWidth = mmc.getPixelSizeUm();
		cal.pixelHeight = mmc.getPixelSizeUm();
		imagePlus.setCalibration(cal);
		ReportingUtils.logMessage("--- Acquisition done.");
		fluoDS.close();
		try {
			mmc.setPosition(mmc.getFocusDevice(), zFocus);
			mmc.waitForDevice(mmc.getFocusDevice());
			mmc.setShutterOpen(false);
			mmc.waitForDevice(mmc.getShutterDevice());
		} catch (Exception e) {
			ReportingUtils
					.logMessage("could not set focus device back to position and close shutter");
			ReportingUtils.logError(e);
		}
		cal = null;
		imageStack = null;
		return imagePlus;
	}
}