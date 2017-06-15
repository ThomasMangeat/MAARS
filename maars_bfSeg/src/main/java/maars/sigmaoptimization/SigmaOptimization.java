package maars.sigmaoptimization;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.gui.Roi;
import ij.plugin.filter.PlugInFilter;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import maars.io.IOUtils;
import maars.segmentPombe.ComputeCorrelation;

import java.awt.*;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

/**
 * ImajeJ plugin used to optimise sigma parameter used in segmentation process
 * of SegmentPombe
 *
 * @author marie
 */
public class SigmaOptimization implements PlugInFilter {

   private GenericDialog dialog;
   private double lowerSigma = 1;
   private double upperSigma = 9;
   private double step = 0.2;
   private int zf;
   private int direction = 1;
   private ImagePlus image;
   private String pathToSaveResult;

   /**
    * Create Dialog window to set parameters of algorithm
    */
   private void createDialog() {

      pathToSaveResult = image.getOriginalFileInfo().directory;

      zf = (image.getNSlices() / 2) + 1;

      dialog = new GenericDialog("Choose sigma range");
      dialog.setMinimumSize(new Dimension(300, 200));
      dialog.setSize(750, 500);
      dialog.setModalityType(Dialog.ModalityType.MODELESS);
      dialog.setBounds(0, 0, 300, 400);
      dialog.addNumericField("Lower value", lowerSigma,3);
      dialog.addNumericField("Upper value", upperSigma, 3);
      dialog.addNumericField("step", step, 5);
      dialog.addStringField("path to save results", pathToSaveResult, 20);
      dialog.addNumericField("focus slice", zf, 3);
      dialog.addNumericField("direction", direction, 3);
      Button okButton = new Button("ok");
      okButton.addActionListener(o->{
         lowerSigma = dialog.getNextNumber();
         upperSigma = dialog.getNextNumber();
         step = dialog.getNextNumber();
         pathToSaveResult = dialog.getNextString();
         zf = (int) dialog.getNextNumber();
         direction = (int) dialog.getNextNumber();
         if (execute()) {
            dialog.setVisible(false);
            image.hide();
         }
      });
      dialog.add(okButton);
      dialog.pack();
      dialog.setVisible(true);
   }

   /**
    * Runs the plugin : compute mean correlation value for each sigma in the
    * range tested
    */
   @Override
   public void run(ImageProcessor imageProcessor) {
      createDialog();
   }

   public void setImage(ImagePlus img){
      image = img;
   }

   @Override
   public int setup(String s, ImagePlus imagePlus) {
      image = imagePlus;
      return DOES_16;
   }
   public static void main(String[] args) {
      new ImageJ();
      IJ.open("/media/tong/MAARSData/MAARSData/102/15-06-1/SegImgStacks/_1_MMStack_Pos0.ome.tif");
      SigmaOptimization md = new SigmaOptimization();
      md.setImage(IJ.getImage());
      md.createDialog();
   }

   public boolean execute(){
      RoiManager manager = RoiManager.getInstance();

      if (manager == null) {
         IJ.error("You need to load ROIs.");
         return false;
      }

      System.out.println("get roi as array");
      Roi[] rois = manager.getRoisAsArray();
      System.out.println("nb of roi :" + rois.length);

      FileWriter fw = null;
      try {
         fw = new FileWriter(pathToSaveResult + "opti_sigma.csv");
      } catch (IOException e) {
         System.out.println("could not create writer");
         IOUtils.printErrorToIJLog(e);
      }
      assert fw != null;
      BufferedWriter bw = new BufferedWriter(fw);

      try {
         bw.write("sigma,mean_correlation");
         bw.newLine();
      } catch (IOException e) {
         System.out.println("could not write in file");
         IOUtils.printErrorToIJLog(e);
      }

      for (float sigma = (float) lowerSigma; sigma <= upperSigma; sigma = sigma
            + (float) step) {
         System.out.println("for sigma = " + sigma);

         double total = 0;
         for (Roi roi1 : rois) {
            double x = roi1.getXBase();
            double y = roi1.getYBase();
            float[] iz = new float[image.getNSlices()];

            System.out.println("for pixel x : " + x + " y : " + y);

            for (int z = 0; z < image.getNSlices(); z++) {
               image.setZ(z);
               iz[z] = image.getPixel((int) x, (int) y)[0];
            }
            ComputeCorrelation computeCorrelationImage = new ComputeCorrelation(zf, sigma
                  / (float) image.getCalibration().pixelDepth,
                  direction);
            computeCorrelationImage.preCalculateParameters(0, image.getNSlices() - 1);
            total = total + computeCorrelationImage.integrate(iz);
         }
         double mean = total / rois.length;
         System.out.println("mean = " + mean);
         try {
            bw.write(sigma + "," + mean);
            bw.newLine();
         } catch (IOException e) {
            System.out.println("could not write in file");
            IOUtils.printErrorToIJLog(e);
         }

      }
      try {
         bw.close();
      } catch (IOException e) {
         System.out.println("could not close writer");
         IOUtils.printErrorToIJLog(e);
      }
      return true;
   }
}
