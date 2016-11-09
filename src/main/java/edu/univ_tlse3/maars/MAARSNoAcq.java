package edu.univ_tlse3.maars;

import edu.univ_tlse3.cellstateanalysis.FluoAnalyzer;
import edu.univ_tlse3.cellstateanalysis.SetOfCells;
import edu.univ_tlse3.display.SOCVisualizer;
import edu.univ_tlse3.utils.FileUtils;
import edu.univ_tlse3.utils.ImgUtils;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.plugin.frame.RoiManager;
import ij.process.FloatProcessor;
import org.micromanager.internal.utils.ReportingUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.regex.Pattern;

/**
 * @author Tong LI, mail: tongli.bioinfo@gmail.com
 * @version Nov 22, 2015
 */
public class MAARSNoAcq implements Runnable {
   private PrintStream curr_err;
   private PrintStream curr_out;
   private MaarsParameters parameters;
   private SetOfCells soc;
   private String rootDir;
   private ArrayList<String> arrayChannels = new ArrayList<String>();
   private SOCVisualizer socVisualizer_;
   private ExecutorService es_;

   public MAARSNoAcq(MaarsParameters parameters, SOCVisualizer socVisualizer, ExecutorService es) {
      this.parameters = parameters;
      rootDir = parameters.getSavingPath();
      this.soc = new SetOfCells();
      socVisualizer_ = socVisualizer;
      es_ = es;
   }

   private ArrayList<String[]> getAcqPositions() {
      ArrayList<String[]> acqPos = new ArrayList<String[]>();
      String[] listAcqNames = new File(rootDir).list();
      String pattern = "(X)(\\d+)(_)(Y)(\\d+)(_FLUO)";
      for (String acqName : listAcqNames) {
         if (Pattern.matches(pattern, acqName)) {
            acqPos.add(new String[]{acqName.split("_", -1)[0].substring(1),
                    acqName.split("_", -1)[1].substring(1)});
         }
      }
      return acqPos;
   }

   @Override
   public void run() {
      // Start time
      long start = System.currentTimeMillis();
      for (String[] pos : getAcqPositions()) {
         String xPos = pos[0];
         String yPos = pos[1];
         IJ.log("x : " + xPos + " y : " + yPos);
         String pathToSegDir = FileUtils.convertPath(rootDir + "/X" + xPos + "_Y" + yPos);
         String pathToFluoDir = pathToSegDir + "_FLUO/";
         String pathToSegMovie = FileUtils.convertPath(pathToSegDir + "/_1/_1_MMStack_Pos0.ome.tif");
         //update saving path
         parameters.setSavingPath(pathToSegDir);
         ImagePlus segImg = null;
         try {
            segImg = IJ.openImage(pathToSegMovie);
         } catch (Exception e) {
            e.printStackTrace();
            IJ.error("Invalid path");
         }
         // --------------------------segmentation-----------------------------//
         MaarsSegmentation ms = new MaarsSegmentation(parameters);
         ms.segmentation(segImg);
         if (ms.roiDetected()) {
            soc.reset();
            // from Roi.zip initialize a set of cell
            soc.loadCells(pathToSegDir);
            // Get the focus slice of BF image
            soc.setRoiMeasurementIntoCells(ms.getRoiMeasurements());

            Calibration bfImgCal = null;
            if (segImg != null) {
               bfImgCal = segImg.getCalibration();
            }
            // ----------------start acquisition and analysis --------//
            try {
               PrintStream ps = new PrintStream(pathToSegDir + "/CellStateAnalysis.LOG");
               curr_err = System.err;
               curr_out = System.err;
               System.setOut(ps);
               System.setErr(ps);
            } catch (FileNotFoundException e) {
               e.printStackTrace();
            }
            String[] listAcqNames = new File(pathToFluoDir).list();
            String pattern = "(\\w+)(_)(\\d+)";
            ArrayList<Integer> arrayImgFrames = new ArrayList<Integer>();
            for (String acqName : listAcqNames) {
               if (Pattern.matches(pattern, acqName)) {
                  String current_channel = acqName.split("_", -1)[0];
                  String current_frame = acqName.split("_", -1)[1];
                  if (!arrayChannels.contains(current_channel)) {
                     arrayChannels.add(current_channel);
                  }
                  if (!arrayImgFrames.contains(Integer.parseInt(current_frame))) {
                     arrayImgFrames.add(Integer.parseInt(current_frame));
                  }
               }
            }
            Collections.sort(arrayImgFrames);
            ImageStack fluoStack = new ImageStack(segImg.getWidth(), segImg.getHeight());

            ArrayList<Map<String, Future>> futureSet = new ArrayList<Map<String, Future>>();
            Future future;
            for (Integer arrayImgFrame : arrayImgFrames) {
               Map<String, Future> channelsInFrame = new HashMap<String, Future>();
               for (String channel : arrayChannels) {
                  int current_frame = arrayImgFrame;
                  IJ.log("Analysing channel " + channel + "_" + current_frame);
                  String pathToFluoMovie = pathToFluoDir + channel + "_" + current_frame + "/" + channel + "_" + current_frame + "_MMStack_Pos0.ome.tif";
                  ImagePlus fluoImage = IJ.openImage(pathToFluoMovie);
                  ImagePlus zProjectedFluoImg;
                  zProjectedFluoImg = ImgUtils.zProject(fluoImage);
                  zProjectedFluoImg.setTitle(fluoImage.getTitle() + "_" + channel + "_projected");
                  zProjectedFluoImg.setCalibration(fluoImage.getCalibration());
                  future = es_.submit(new FluoAnalyzer(zProjectedFluoImg, bfImgCal, soc, channel,
                          Integer.parseInt(parameters.getChMaxNbSpot(channel)),
                          Double.parseDouble(parameters.getChSpotRaius(channel)),
                          Double.parseDouble(parameters.getChQuality(channel)), current_frame, socVisualizer_));
                  fluoStack.addSlice(channel, zProjectedFluoImg.getProcessor().convertToFloatProcessor());
                  channelsInFrame.put(channel, future);
               }
               futureSet.add(channelsInFrame);
            }
            assert segImg != null;
            ImagePlus mergedImg = new ImagePlus("merged", fluoStack);
            mergedImg.setCalibration(segImg.getCalibration());
            assert fluoStack != null;
            mergedImg.setT(fluoStack.getSize());
            for (Map<String, Future> aFutureSet : futureSet) {
               for (String channel : aFutureSet.keySet()) {
                  try {
                     aFutureSet.get(channel).get();
                  } catch (InterruptedException e) {
                     e.printStackTrace();
                  } catch (ExecutionException e) {
                     e.printStackTrace();
                  }
               }
            }
            es_.shutdownNow();
            RoiManager.getInstance().reset();
            RoiManager.getInstance().close();
            double timeInterval = Double.parseDouble(parameters.getFluoParameter(MaarsParameters.TIME_INTERVAL));
            if (soc.size() != 0) {
               long startWriting = System.currentTimeMillis();
               Boolean splitChannel = true;
               mergedImg.getCalibration().frameInterval = timeInterval / 1000;
               MAARS.saveAll(soc, mergedImg, pathToFluoDir, splitChannel);
               MAARS.analyzeMitosisDynamic(soc, parameters, splitChannel, pathToSegDir, true);
               IJ.log("it took " + (double) (System.currentTimeMillis() - startWriting) / 1000
                       + " sec for writing results");
            }
         }
      }
      assert es_ != null;
      es_.shutdownNow();
      System.setErr(curr_err);
      System.setOut(curr_out);
      IJ.log("it took " + (double) (System.currentTimeMillis() - start) / 1000 + " sec for analysing");
   }
}