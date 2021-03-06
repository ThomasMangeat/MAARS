package maars.headless.batchFluoAnalysis;

import ij.IJ;
import ij.ImagePlus;
import ij.measure.ResultsTable;
import ij.plugin.Duplicator;
import loci.formats.FormatException;
import loci.formats.ImageReader;
import loci.formats.MetadataTools;
import loci.formats.meta.IMetadata;
import maars.agents.Cell;
import maars.agents.DefaultSetOfCells;
import maars.cellAnalysis.FluoAnalyzer;
import maars.cellAnalysis.PythonPipeline;
import maars.display.SOCVisualizer;
import maars.io.IOUtils;
import maars.main.MaarsParameters;
import maars.main.Maars_Interface;
import maars.utils.FileUtils;
import maars.utils.ImgUtils;

import java.io.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * Created by tong on 30/06/17.
 */
public class MaarsFluoAnalysis implements Runnable{
   public static final String MITODIRNAME = "Mitosis";
   String[] posNbs_;
   MaarsParameters parameters_;
   public MaarsFluoAnalysis(MaarsParameters parameters){
      String segDir = FileUtils.convertPath(parameters.getSavingPath()) + File.separator +
            parameters.getFluoParameter(MaarsParameters.FLUO_PREFIX);
      posNbs_ = getPositionSuffix(segDir);
      parameters_ = parameters;
   }
   @Override
   public void run() {
      AtomicBoolean stop = new AtomicBoolean(false);
      PrintStream curr_err = null;
      PrintStream curr_out = null;
      DefaultSetOfCells soc;
      String fluoImgsDir= FileUtils.convertPath(parameters_.getSavingPath()) + File.separator +
            parameters_.getFluoParameter(MaarsParameters.FLUO_PREFIX) + File.separator;
      String segAnaDir = FileUtils.convertPath(parameters_.getSavingPath()) + File.separator +
            parameters_.getSegmentationParameter(MaarsParameters.SEG_PREFIX) + Maars_Interface.SEGANALYSIS_SUFFIX;
      for (String posNb:posNbs_) {
         ImagePlus concatenatedFluoImgs = null;
         soc = new DefaultSetOfCells(posNb);
         String currentPosPrefix = segAnaDir + posNb + File.separator;
         String currentZipPath = currentPosPrefix + "ROI.zip";
         if (FileUtils.exists(currentZipPath)) {
            // from Roi.zip initialize a set of cell
            soc.loadCells(currentZipPath);
            IJ.open(currentPosPrefix + "Results.csv");
            ResultsTable rt = ResultsTable.getResultsTable();
            ResultsTable.getResultsWindow().close(false);
            soc.addRoiMeasurementIntoCells(rt);
            // ----------------start acquisition and analysis --------//
            try {
               PrintStream ps = new PrintStream(parameters_.getSavingPath() + File.separator + "FluoAnalysis.LOG");
               curr_err = System.err;
               curr_out = System.err;
               System.setOut(ps);
               System.setErr(ps);
            } catch (FileNotFoundException e) {
               IOUtils.printErrorToIJLog(e);
            }
            CopyOnWriteArrayList<Map<String, Future>> tasksSet = new CopyOnWriteArrayList<>();
            concatenatedFluoImgs = processStackedImg(fluoImgsDir, posNb,
                     parameters_, soc, null, tasksSet, stop);
            concatenatedFluoImgs.getCalibration().frameInterval =
                  Double.parseDouble(parameters_.getFluoParameter(MaarsParameters.TIME_INTERVAL)) / 1000;
            Maars_Interface.waitAllTaskToFinish(tasksSet);
            if (!stop.get() && soc.size() != 0) {
               long startWriting = System.currentTimeMillis();
               ArrayList<String> arrayChannels = new ArrayList<>();
               Collections.addAll(arrayChannels, parameters_.getUsingChannels().split(",", -1));
               FileUtils.createFolder(parameters_.getSavingPath() + File.separator + parameters_.getFluoParameter(MaarsParameters.FLUO_PREFIX)
                     +Maars_Interface.FLUOANALYSIS_SUFFIX);
               IOUtils.saveAll(soc, concatenatedFluoImgs, parameters_.getSavingPath() + File.separator, parameters_.useDynamic(),
                     arrayChannels, posNb, parameters_.getFluoParameter(MaarsParameters.FLUO_PREFIX));
               IJ.log("it took " + (double) (System.currentTimeMillis() - startWriting) / 1000
                     + " sec for writing results");
               if (parameters_.useDynamic()) {
                  analyzeMitosisDynamic(soc, parameters_);
               }
            }
         }
         soc.reset();
         System.gc();
      }
      System.setErr(curr_err);
      System.setOut(curr_out);
   }

   private static ImagePlus processStackedImg(String pathToFluoImgsDir, String pos,
                                             MaarsParameters parameters, DefaultSetOfCells soc, SOCVisualizer socVisualizer,
                                             CopyOnWriteArrayList<Map<String, Future>> tasksSet, AtomicBoolean stop) {
      ImagePlus concatenatedFluoImgs = loadImgOfPosition(pathToFluoImgsDir, pos);

      String[] arrayChannels = parameters.getUsingChannels().split(",");

      int totalChannel = Integer.parseInt(concatenatedFluoImgs.getStringProperty("SizeC"));
      int totalSlice = Integer.parseInt(concatenatedFluoImgs.getStringProperty("SizeZ"));
      int totalFrame = Integer.parseInt(concatenatedFluoImgs.getStringProperty("SizeT"));

      ExecutorService es = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
      Duplicator duplicator = new Duplicator();
      for (int i = 1; i <= totalFrame; i++) {
         Map<String, Future> chAnalysisTasks = new HashMap<>();
         for (int j = 1; j <= totalChannel; j++) {
            String channel = arrayChannels[j - 1];
            IJ.log("Processing channel " + channel + "_" + i);
            ImagePlus zProjectedFluoImg = ImgUtils.zProject(
                  duplicator.run(concatenatedFluoImgs, j, j, 1, totalSlice, i, i)
                  , concatenatedFluoImgs.getCalibration());
            Future future = es.submit(new FluoAnalyzer(zProjectedFluoImg, zProjectedFluoImg.getCalibration(),
                  soc, channel, Integer.parseInt(parameters.getChMaxNbSpot(channel)),
                  Double.parseDouble(parameters.getChSpotRaius(channel)),
                  Double.parseDouble(parameters.getChQuality(channel)), i, socVisualizer,
                  parameters.useDynamic()));
            chAnalysisTasks.put(channel, future);
         }
         tasksSet.add(chAnalysisTasks);
         if (stop.get()) {
            break;
         }
      }
      System.gc();
      es.shutdown();
      if (Boolean.parseBoolean(parameters.getProjected())) {
         IJ.run(concatenatedFluoImgs, "Z Project...", "projection=[Max Intensity] all");
         return (IJ.getImage());
      }
      return concatenatedFluoImgs.duplicate();
   }

   public static String[] getPositionSuffix(String path){
      String tifName = null;
      File[] listOfFiles = new File(path).listFiles();
      for (File f:listOfFiles){
         if (f.getName().endsWith(".tif") || f.getName().endsWith(".tiff")){
            tifName = f.getName();
         }
      }
      HashMap<String, String> namesDic = populateSeriesImgNames(path + File.separator + tifName);
      String[] names = new String[namesDic.size()];
      names = namesDic.keySet().toArray(names);
      String[] pos = new String[names.length];
      for (int i =0; i< names.length; i++){
         String[] splitName = names[i].split("_");
         pos[i] = splitName[splitName.length-1];
      }
      return pos;
   }

   private static ImagePlus loadImgOfPosition(String pathToFluoImgsDir, String pos) {
      File[] listOfFiles = new File(pathToFluoImgsDir).listFiles();
      String fluoTiffName = null;
      for (File f:listOfFiles){
         if (Pattern.matches(".*MMStack_" + pos+"\\..*", f.getName())){
            fluoTiffName = f.getName();
         }
      }
      assert fluoTiffName!= null;
      IJ.log(fluoTiffName);
      HashMap<String, String> map = populateSeriesImgNames(pathToFluoImgsDir + File.separator + fluoTiffName);
      String serie_number;
      if (map.size() !=1){
         serie_number = map.get(fluoTiffName.split("\\.")[0]);
         IJ.log(serie_number + " selected");
      }else{
         serie_number = "";
      }
      ImagePlus im2 = ImgUtils.lociImport(pathToFluoImgsDir + File.separator + fluoTiffName, serie_number);
      return im2;
   }

   private static HashMap<String, String> populateSeriesImgNames(String pathToTiffFile) {
      HashMap<String, String> seriesImgNames = new HashMap<>();
      ImageReader reader = new ImageReader();
      IMetadata omexmlMetadata = MetadataTools.createOMEXMLMetadata();
      reader.setMetadataStore(omexmlMetadata);
      try {
         reader.setId(pathToTiffFile);
      } catch (FormatException | IOException e) {
         e.printStackTrace();
      }
      int seriesCount = reader.getSeriesCount();
      for (int i = 0; i < seriesCount; i++) {
         reader.setSeries(i);
         String name = omexmlMetadata.getImageName(i); // this is the image name stored in the file
         String label = "series_" + (i + 1);  // this is the label that you see in ImageJ
         seriesImgNames.put(name, label);
      }
      IJ.log(seriesCount + " series registered");
      return seriesImgNames;
   }

   private static void findAbnormalCells(String mitoDir,
                                         DefaultSetOfCells soc,
                                         HashMap map) {
      if (FileUtils.exists(mitoDir)) {
         PrintWriter out = null;
         try {
            out = new PrintWriter(mitoDir + File.separator + "abnormalCells.txt");
         } catch (FileNotFoundException e) {
            IOUtils.printErrorToIJLog(e);
         }

         for (Object cellNb : map.keySet()) {
            int cellNbInt = Integer.parseInt(String.valueOf(cellNb));
            int anaBOnsetFrame = Integer.valueOf(((String[]) map.get(cellNb))[2]);
            int lastAnaphaseFrame = Integer.valueOf(((String[]) map.get(cellNb))[3]);
            Cell cell = soc.getCell(cellNbInt);
            cell.setAnaBOnsetFrame(anaBOnsetFrame);
            ArrayList<Integer> spotInBtwnFrames = cell.getSpotInBtwnFrames();
            assert out != null;
            if (spotInBtwnFrames.size() > 0) {
               Collections.sort(spotInBtwnFrames);
               int laggingTimePoint = spotInBtwnFrames.get(spotInBtwnFrames.size() - 1);
               if (laggingTimePoint > anaBOnsetFrame && laggingTimePoint < lastAnaphaseFrame) {
                  String laggingMessage = "Lagging :" + cellNb + "_lastLaggingTimePoint_" + laggingTimePoint + "_anaBonset_" + anaBOnsetFrame;
                  out.println(laggingMessage);
                  IJ.log(laggingMessage);
                  IJ.openImage(mitoDir + File.separator + "croppedImgs"
                        + File.separator + cellNb + "_GFP.tif").show();
               }
            }
            //TODO to show unaligned cell
            if (cell.unalignedSpotFrames().size() > 0) {
               String unalignKtMessage = "Unaligned : Cell " + cellNb + " detected with unaligned kinetochore(s)";
               IJ.log(unalignKtMessage);
               out.println(unalignKtMessage);
            }
         }
         assert out != null;
         out.close();
         IJ.log("lagging detection finished");
      }
   }

//   static HashMap getMitoticCellNbs(String mitoDir) {
//      return FileUtils.readTable(mitoDir + File.separator + "mitosis_time_board.csv");
//   }

   public static void analyzeMitosisDynamic(DefaultSetOfCells soc, MaarsParameters parameters) {
      IJ.log("Start python analysis");
      String pos = soc.getPosLabel();
      String pathToRoot = parameters.getSavingPath() + File.separator;
      String mitoDir = pathToRoot + MITODIRNAME + File.separator + pos + File.separator;
      FileUtils.createFolder(mitoDir);
      String[] mitosis_cmd = new String[]{PythonPipeline.getPythonDefaultPathInConda(), MaarsParameters.DEPS_DIR +
            PythonPipeline.ANALYSING_SCRIPT_NAME, pathToRoot, parameters.getDetectionChForMitosis(),
            parameters.getCalibration(), String.valueOf((Math.round(Double.parseDouble(parameters.getFluoParameter(MaarsParameters.TIME_INTERVAL)) / 1000))),
            pos, parameters.getSegmentationParameter(MaarsParameters.SEG_PREFIX),
            parameters.getFluoParameter(MaarsParameters.FLUO_PREFIX), "-minimumPeriod", parameters.getMinimumMitosisDuration()};
      PythonPipeline.runPythonScript(mitosis_cmd, mitoDir + "mitosisDetection_log.txt");
//      HashMap map = getMitoticCellNbs(mitoDir);
      ArrayList<String> cmds = new ArrayList<>();
      cmds.add(String.join(" ", mitosis_cmd));
      String bashPath = mitoDir + "pythonAnalysis.sh";
      FileUtils.writeScript(bashPath,cmds);
      IJ.log("Script saved");
//      findAbnormalCells(mitoDir, soc, map);
   }
}
