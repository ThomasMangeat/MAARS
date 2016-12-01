package edu.univ_tlse3.gui;

import edu.univ_tlse3.display.SOCVisualizer;
import edu.univ_tlse3.maars.MAARS;
import edu.univ_tlse3.maars.MAARSNoAcq;
import edu.univ_tlse3.maars.MaarsParameters;
import edu.univ_tlse3.utils.FileUtils;
import ij.IJ;
import mmcorej.CMMCore;
import org.apache.commons.math3.util.FastMath;
import org.micromanager.internal.MMStudio;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Class to create and display a dialog to get parameters of the whole analysis
 *
 * @author Tong LI
 */

public class MaarsMainDialog implements ActionListener {

   private final Label numFieldLabel;
   private final MMStudio mm;
   private final CMMCore mmc;
   private final double calibration;
   private JFrame mainDialog;
   private MaarsParameters parameters;
   private JButton autofocusButton;
   private JButton okMainDialogButton;
   private JButton showDataVisualizer_;
   private JButton segmButton;
   private JButton fluoAnalysisButton;
   private JFormattedTextField savePathTf;
   private JFormattedTextField widthTf;
   private JFormattedTextField heightTf;
   private JFormattedTextField fluoAcqDurationTf;
   private JCheckBox withOutAcqChk;
   private JRadioButton dynamicOpt;
   private JRadioButton staticOpt;
   private MaarsFluoAnalysisDialog fluoDialog_;
   private MaarsSegmentationDialog segDialog_;
   private JPanel stopAndVisualizerButtonPanel_;
   private SOCVisualizer socVisualizer_;
   private JButton stopButton_;
//   private Thread MAARSMainth_;
    private ExecutorService es_ = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

   /**
    * Constructor
    *
    * @param mm         : graphical user interface of Micro-Manager
    * @param parameters :MaarsParameters
    */
   public MaarsMainDialog(MMStudio mm, MaarsParameters parameters) {

      // ------------initialization of parameters---------------//
      Color labelColor = Color.ORANGE;
      Color bgColor = Color.WHITE;

      this.mm = mm;
      this.mmc = mm.core();
      this.parameters = parameters;

      // initialize mainFrame

      IJ.log("create main dialog ...");
      mainDialog = new JFrame("Mitosis Analysing And Recording System - MAARS");
      JFrame.setDefaultLookAndFeelDecorated(true);
      mainDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

      // set minimal dimension of mainDialog

      int maxDialogWidth = 350;
      int maxDialogHeight = 600;
      Dimension minimumSize = new Dimension(maxDialogWidth, maxDialogHeight);
      mainDialog.setMinimumSize(minimumSize);

      // Get number of field to explore

      int defaultXFieldNumber = parameters.getFieldNb(MaarsParameters.X_FIELD_NUMBER);
      int defaultYFieldNumber = parameters.getFieldNb(MaarsParameters.Y_FIELD_NUMBER);

      // Calculate width and height for each field

      calibration = mm.getCMMCore().getPixelSizeUm();
      double fieldWidth = mmc.getImageWidth() * calibration;
      double fieldHeight = mmc.getImageHeight() * calibration;

      // Exploration Label

      Label explorationLabel = new Label("Area to explore", Label.CENTER);
      explorationLabel.setBackground(labelColor);

      // field width Panel (Label + textfield)

      JPanel widthPanel = new JPanel(new GridLayout(1, 0));
      widthPanel.setBackground(bgColor);
      JLabel widthLabel = new JLabel("Width :", SwingConstants.CENTER);
      widthTf = new JFormattedTextField(Integer.class);
      widthTf.setValue((int)FastMath.round(fieldWidth * defaultXFieldNumber));
      widthTf.addKeyListener(new KeyAdapter() {
         @Override
         public void keyReleased(KeyEvent keyEvent) {
            super.keyReleased(keyEvent);
            refreshNumField();
         }
      });
      widthPanel.add(widthLabel);
      widthPanel.add(widthTf);

      // field Height Panel (Label + textfield)

      JPanel heightPanel = new JPanel(new GridLayout(1, 0));
      heightPanel.setBackground(bgColor);
      JLabel heightLabel = new JLabel("Height :", SwingConstants.CENTER);
      heightTf = new JFormattedTextField(Integer.class);
      heightTf.setValue((int)FastMath.round(fieldHeight * defaultYFieldNumber));
      heightTf.addKeyListener(new KeyAdapter() {
         @Override
         public void keyReleased(KeyEvent keyEvent) {
            super.keyReleased(keyEvent);
            refreshNumField();
         }
      });
      heightPanel.add(heightLabel);
      heightPanel.add(heightTf);

      // number of field label

      numFieldLabel = new Label("Number of field : " + defaultXFieldNumber * defaultYFieldNumber,  Label.CENTER);

      // analysis parameters label

      Label analysisParamLabel = new Label("Analysis parameters", Label.CENTER);
      analysisParamLabel.setBackground(labelColor);

      // autofocus button

      JPanel autoFocusPanel = new JPanel(new GridLayout(1, 0));
      autofocusButton = new JButton("Autofocus");
      autofocusButton.addActionListener(this);
      autofocusButton.setEnabled(false);
      autoFocusPanel.add(autofocusButton);

      // segmentation button

      JPanel segPanel = new JPanel(new GridLayout(1, 0));
      segmButton = new JButton("Segmentation");
      segmButton.addActionListener(this);
      segPanel.add(segmButton);

      // fluo analysis button

      JPanel fluoAnalysisPanel = new JPanel(new GridLayout(1, 0));
      fluoAnalysisButton = new JButton("Fluorescent analysis");
      fluoAnalysisButton.addActionListener(this);
      fluoAnalysisPanel.add(fluoAnalysisButton);

      // strategy panel (2 radio button + 1 textfield + 1 label)

      JPanel strategyPanel = new JPanel(new GridLayout(1, 0));
      strategyPanel.setBackground(bgColor);
      dynamicOpt = new JRadioButton("Dynamic");
      dynamicOpt.setSelected(parameters.useDynamic());
      staticOpt = new JRadioButton("Static");
      staticOpt.setSelected(!parameters.useDynamic());

      dynamicOpt.addActionListener(this);
      staticOpt.addActionListener(this);

      ButtonGroup group = new ButtonGroup();
      group.add(dynamicOpt);
      group.add(staticOpt);

      strategyPanel.add(staticOpt);
      strategyPanel.add(dynamicOpt);
      fluoAcqDurationTf = new JFormattedTextField(Double.class);
      fluoAcqDurationTf.setValue(parameters.getFluoParameter(MaarsParameters.TIME_LIMIT));
      strategyPanel.add(fluoAcqDurationTf);
      strategyPanel.add(new JLabel("min", SwingConstants.CENTER));

      // checkbox : update or not MAARS parameters

      JPanel chkPanel = new JPanel(new GridLayout(1, 0));
      chkPanel.setBackground(bgColor);
      JCheckBox saveParametersChk = new JCheckBox("Save parameters", true);
      withOutAcqChk = new JCheckBox("Don't do acquisition", false);
      withOutAcqChk.addActionListener(this);
      chkPanel.add(withOutAcqChk);
      chkPanel.add(saveParametersChk);

      // Saving path Panel

      JPanel savePathLabelPanel = new JPanel(new GridLayout(1, 0));
      savePathLabelPanel.setBackground(bgColor);
      JLabel savePathLabel = new JLabel("Saving Path :");
      savePathLabelPanel.add(savePathLabel);

      // Saving Path textfield

      JPanel savePathTfPanel = new JPanel(new GridLayout(1, 0));
      savePathTf = new JFormattedTextField(parameters.getSavingPath());
      savePathTf.setMaximumSize(new Dimension(maxDialogWidth, 1));
      savePathTfPanel.add(savePathTf);

      // show visualiwer acquisitions button

      stopAndVisualizerButtonPanel_ = new JPanel(new GridLayout(1, 0));
      showDataVisualizer_ = new JButton("Show visualizer");
      showDataVisualizer_.addActionListener(this);
      stopAndVisualizerButtonPanel_.add(showDataVisualizer_);

      //

      stopButton_ = new JButton("Stop");
      stopButton_.addActionListener(this);
      stopAndVisualizerButtonPanel_.add(stopButton_);


      // Ok button to run

      JPanel okPanel = new JPanel(new GridLayout(1, 0));
      okMainDialogButton = new JButton("OK");
      okMainDialogButton.addActionListener(this);
      mainDialog.getRootPane().setDefaultButton(okMainDialogButton);
      okPanel.add(okMainDialogButton);

      // ------------set up and add components to Panel then to
      // Frame---------------//

      JPanel mainPanel = new JPanel();
      mainPanel.setBackground(bgColor);
      mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
      mainPanel.add(explorationLabel);
      mainPanel.add(widthPanel);
      mainPanel.add(heightPanel);
      mainPanel.add(numFieldLabel);
      mainPanel.add(analysisParamLabel);
      mainPanel.add(autoFocusPanel);
      mainPanel.add(segPanel);
      mainPanel.add(fluoAnalysisPanel);
      mainPanel.add(strategyPanel);
      mainPanel.add(chkPanel);
      mainPanel.add(savePathLabelPanel);
      mainPanel.add(savePathTfPanel);
      mainPanel.add(okPanel);
      mainPanel.add(stopAndVisualizerButtonPanel_);
      mainDialog.add(mainPanel);
      IJ.log("Done.");
      mainDialog.pack();
   }

   /**
    * @return graphical user interface of Micro-Manager
    */
   private MMStudio getMM() {
      return mm;
   }

   /**
    * Show dialog
    */
   public void show() {
      mainDialog.setVisible(true);
   }

   /**
    * Method to display number of field the program has to scan
    */
   private void refreshNumField() {
      int newWidth;
      int newHeigth;

      String widthComp = widthTf.getText();
      String heightComp = heightTf.getText();
      if (!widthComp.equals("") && !heightComp.equals("")) {
         newWidth = Integer.valueOf(widthComp);
         newHeigth = Integer.valueOf(heightComp);
         double tmpNewWidth = (newWidth+1) / (calibration * mmc.getImageWidth());
         double tmpNewHeight = (newHeigth+1) / (calibration * mmc.getImageHeight());
         double fractionalPartNewWidth = tmpNewWidth % 1;
         double fractionalPartNewHeight = tmpNewHeight % 1;

         int newXFieldNumber = (int) FastMath.round(tmpNewWidth - fractionalPartNewWidth);
         int newYFieldNumber = (int) FastMath.round(tmpNewHeight - fractionalPartNewHeight);
         int totoalNbField = newXFieldNumber * newYFieldNumber;
         if (totoalNbField == 0) {
            numFieldLabel.setForeground(Color.red);
            numFieldLabel.setText("Number of field : " + totoalNbField);
         } else {
            numFieldLabel.setForeground(Color.black);
            numFieldLabel.setText("Number of field : " + totoalNbField);
         }

         parameters.setFieldNb(MaarsParameters.X_FIELD_NUMBER, "" + newXFieldNumber);
         parameters.setFieldNb(MaarsParameters.Y_FIELD_NUMBER, "" + newYFieldNumber);
      }
   }

   /**
    * method to save the parameters entered
    */
   private void saveParameters() {
      if (!savePathTf.getText().equals(parameters.getSavingPath())) {
         parameters.setSavingPath(savePathTf.getText());
      }
      if (!fluoAcqDurationTf.getText().equals(parameters.getFluoParameter(MaarsParameters.TIME_LIMIT))) {
         parameters.setFluoParameter(MaarsParameters.TIME_LIMIT, fluoAcqDurationTf.getText());
      }
      try {
         parameters.save();
         if (FileUtils.exists(parameters.getSavingPath())) {
            parameters.save(parameters.getSavingPath());
         }
      } catch (IOException e) {
         IJ.error("Could not save parameters");
      }
   }

   /**
    * method to set the strategy selected
    */
   private void setAnalysisStrategy() {

      if (dynamicOpt.isSelected()) {
         parameters.setFluoParameter(MaarsParameters.DYNAMIC, "" + true);
      } else if (staticOpt.isSelected()) {
         parameters.setFluoParameter(MaarsParameters.DYNAMIC, "" + false);
      }
   }

   private int overWrite(String path) {
      int overWrite = 0;
      if (FileUtils.exists(path + File.separator + "X0_Y0" + File.separator + "MMStack.ome.tif")) {
         overWrite = JOptionPane.showConfirmDialog(mainDialog, "Overwrite existing acquisitions?");
      }
      return overWrite;
   }

   private SOCVisualizer createVisualizer(){
      final SOCVisualizer socVisualizer = new SOCVisualizer();
      SwingUtilities.invokeLater(new Runnable() {
         public void run() {
            //Turn off metal's use of bold fonts
            UIManager.put("swing.boldMetal", Boolean.FALSE);
            socVisualizer.createAndShowGUI();
         }
      });
      return socVisualizer;
   }

   @Override
   public void actionPerformed(ActionEvent e) {
      if (e.getSource() == autofocusButton) {
         getMM().showAutofocusDialog();
      } else if (e.getSource() == okMainDialogButton) {
         if (socVisualizer_ == null){
            socVisualizer_ = createVisualizer();
         }
         if (Integer.valueOf(widthTf.getText()) * Integer.valueOf(heightTf.getText()) == 0) {
            IJ.error("Session aborted, 0 field to analyse");
         } else {
            saveParameters();
            if (withOutAcqChk.isSelected()) {
                es_.submit(new Thread(new MAARSNoAcq(parameters, socVisualizer_, es_)));
            } else {
               if (overWrite(parameters.getSavingPath()) == JOptionPane.YES_OPTION) {
                   es_.submit(new Thread(new MAARS(mm, mmc, parameters, socVisualizer_, es_)));
               }
            }
         }
      } else if (e.getSource() == segmButton) {
         saveParameters();
         if (segDialog_ != null){
            segDialog_.setVisible(true);
         }else{
            segDialog_ = new MaarsSegmentationDialog(parameters, mm);
         }

      } else if (e.getSource() == fluoAnalysisButton) {
         saveParameters();
         if (fluoDialog_ != null){
            fluoDialog_.setVisible(true);
         }else{
            fluoDialog_ = new MaarsFluoAnalysisDialog(mm, parameters);
         }
      } else if (e.getSource() == dynamicOpt) {
         setAnalysisStrategy();
         fluoAcqDurationTf.setEditable(true);
      } else if (e.getSource() == staticOpt) {
         setAnalysisStrategy();
         fluoAcqDurationTf.setEditable(false);
      } else if (e.getSource() == withOutAcqChk) {
         if (withOutAcqChk.isSelected()) {
             widthTf.setEnabled(false);
             heightTf.setEnabled(false);
            widthTf.setEditable(false);
            heightTf.setEditable(false);
            numFieldLabel.setText("Don't worry about this");
         } else {
             widthTf.setEnabled(true);
             heightTf.setEnabled(true);
            widthTf.setEditable(true);
            heightTf.setEditable(true);
            refreshNumField();
         }
      } else if (e.getSource() == showDataVisualizer_) {
         if (socVisualizer_ == null){
            socVisualizer_ = createVisualizer();
         }else{
            socVisualizer_.showDialog();
         }
      }else if(e.getSource() == stopButton_){
         es_.shutdownNow();
      } else {
         IJ.log("MAARS don't understand what you want, sorry");
      }
   }
}
