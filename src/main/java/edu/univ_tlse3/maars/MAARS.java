package edu.univ_tlse3.maars;

import edu.univ_tlse3.acquisition.FluoAcquisition;
import edu.univ_tlse3.acquisition.SegAcquisition;
import edu.univ_tlse3.cellstateanalysis.Cell;
import edu.univ_tlse3.cellstateanalysis.FluoAnalyzer;
import edu.univ_tlse3.cellstateanalysis.PythonPipeline;
import edu.univ_tlse3.cellstateanalysis.SetOfCells;
import edu.univ_tlse3.resultSaver.MAARSGeometrySaver;
import edu.univ_tlse3.resultSaver.MAARSImgSaver;
import edu.univ_tlse3.resultSaver.MAARSSpotsSaver;
import edu.univ_tlse3.utils.ImgUtils;
import ij.IJ;
import ij.ImagePlus;
import ij.plugin.frame.RoiManager;
import mmcorej.CMMCore;
import org.micromanager.AutofocusPlugin;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.MMException;
import org.micromanager.internal.utils.ReportingUtils;
import edu.univ_tlse3.utils.FileUtils;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Main MAARS program
 *
 * @author Tong LI, mail: tongli.bioinfo@gmail.com
 * @version Nov 21, 2015
 */
public class MAARS implements Runnable {
    private PrintStream curr_err;
    private PrintStream curr_out;
    private MMStudio mm;
    private CMMCore mmc;
    private MaarsParameters parameters;
    private SetOfCells soc;

    /**
     * Constructor
     *
     * @param mm         MMStudio object (gui)
     * @param mmc        CMMCore object (core)
     * @param parameters MAARS parameters object
     */
    public MAARS(MMStudio mm, CMMCore mmc, MaarsParameters parameters) {
        this.mmc = mmc;
        this.parameters = parameters;
        this.soc = new SetOfCells();
        this.mm = mm;
    }

    private static void mailNotify() {
        String to = "tongli.bioinfo@gmail.com";

        // Sender's email ID needs to be mentioned
        String from = "MAARS@univ-tlse3.fr";

        // Get system properties
        Properties properties = System.getProperties();

        // Setup mail server
        properties.setProperty("mail.smtp.host", "smtps.univ-tlse3.fr");

        // Get the default Session object.
        Session session = Session.getDefaultInstance(properties);

        try {
            // Create a default MimeMessage object.
            MimeMessage message = new MimeMessage(session);

            // Set From: header field of the header.
            message.setFrom(new InternetAddress(from));

            // Set To: header field of the header.
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));

            // Set Subject: header field
            message.setSubject("Analysis done!");

            // Now set the actual message
            message.setText("");

            // Send message
            Transport.send(message);
            System.out.println("Sent message successfully....");
        } catch (MessagingException mex) {
            mex.printStackTrace();
        }
    }

    static void saveAll(SetOfCells soc, ImagePlus mergedImg, String pathToFluoDir,
                        ArrayList<String> arrayChannels, Boolean splitChannel) {
        MAARSSpotsSaver spotSaver = new MAARSSpotsSaver(pathToFluoDir);
        MAARSGeometrySaver geoSaver = new MAARSGeometrySaver(pathToFluoDir);
        MAARSImgSaver imgSaver = new MAARSImgSaver(pathToFluoDir, mergedImg);
        for (Cell cell : soc) {
            geoSaver.save(cell);
            spotSaver.save(cell);
            HashMap<String, ImagePlus> croppedImgSet = ImgUtils.cropMergedImpWithRois(cell, mergedImg, splitChannel);
            imgSaver.saveCroppedImgs(croppedImgSet, cell.getCellNumber());
        }
        imgSaver.exportChannelBtf(splitChannel, arrayChannels);
    }

    private static void showChromLaggingCells(String pathToSegDir, double timeInterval, SetOfCells soc,
                                              Boolean splitChannel) {
        if (FileUtils.exists(pathToSegDir + "_MITOSIS")) {
            String[] listAcqNames = new File(pathToSegDir + "_MITOSIS" + File.separator + "figs" + File.separator)
                    .list();
            String pattern = "(\\d+)(_slopChangePoints_)(\\d+)(.png)";
            ImagePlus merotelyImp;
            PrintWriter out = null;
            try {
                out = new PrintWriter(pathToSegDir + "_MITOSIS" + File.separator + "laggingCells.txt");
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            for (String acqName : listAcqNames) {
                if (Pattern.matches(pattern, acqName)) {
                    String[] splitName = acqName.split("_", -1);
                    int cellNb = Integer.parseInt(splitName[0]);
                    int anaBOnsetFrame = Integer.parseInt(
                            splitName[splitName.length - 1].substring(0, splitName[splitName.length - 1].length() - 4));
                    Cell cell = soc.getCell(cellNb);
                    ArrayList<Integer> spotInBtwnFrames = cell.getSpotInBtwnFrames();
                    Collections.sort(spotInBtwnFrames);
                    if (spotInBtwnFrames.size() > 0) {
                        if (spotInBtwnFrames.get(spotInBtwnFrames.size() - 1) > anaBOnsetFrame) {
                            out.println(cellNb + "_last_" + spotInBtwnFrames.get(spotInBtwnFrames.size() - 1) + "_onset_" + anaBOnsetFrame);
                            String timeStamp = new SimpleDateFormat("yyyyMMdd_HH:mm:ss")
                                    .format(Calendar.getInstance().getTime());
                            // IJ.log(timeStamp + " : cell " + cellNb + "_" +
                            // abnormalStateTimes * timeInterval + " s.");
                            if (splitChannel) {
                                merotelyImp = IJ.openImage(pathToSegDir + "_MITOSIS" + File.separator + "cropImgs"
                                        + File.separator + cellNb + "_GFP.tif");
                                merotelyImp.show();
                            } else {
                                merotelyImp = IJ.openImage(pathToSegDir + "_MITOSIS" + File.separator + "cropImgs"
                                        + File.separator + cellNb + "_merged.tif");
                                merotelyImp.show();
                            }
                        }
                    }
                }
            }
            assert out != null;
            out.close();
        }
    }

    static void analyzeMitosisDynamic(SetOfCells soc, MaarsParameters parameters, Boolean splitChannel,
                                      String pathToSegDir, Boolean showChromLagging) {
        double timeInterval = Double.parseDouble(parameters.getFluoParameter(MaarsParameters.TIME_INTERVAL)) / 1000;
        // TODO
        PythonPipeline.getMitosisFiles(pathToSegDir, "CFP", "0.1075", "200",
                String.valueOf(Math.round((timeInterval))));
        if (showChromLagging) {
            MAARS.showChromLaggingCells(pathToSegDir, timeInterval, soc, splitChannel);
        }
    }

    /**
     * A MAARS need specific autofocus process based on JAF(H&P) sharpness
     * autofocus
     *
     * @param mm  MMStudio object (gui)
     * @param mmc CMMCore object (core)
     */
    public void autofocus(MMStudio mm, CMMCore mmc) {
        try {
            mmc.setShutterDevice(parameters.getChShutter(parameters.getSegmentationParameter(MaarsParameters.CHANNEL)));
        } catch (Exception e2) {
            IJ.error("Can't set BF channel for autofocusing");
            e2.printStackTrace();
        }
        double initialPosition = 0;
        String focusDevice = mmc.getFocusDevice();
        try {
            initialPosition = mmc.getPosition();
        } catch (Exception e) {
            IJ.error("Can't get current z level");
            e.printStackTrace();
        }

        // Get autofocus manager
        IJ.log("First autofocus");
        AutofocusPlugin autofocus = mm.getAutofocus();
        double firstPosition = 0;
        try {
            mmc.setShutterOpen(true);
            autofocus.fullFocus();
            mmc.waitForDevice(focusDevice);
            firstPosition = mmc.getPosition(mmc.getFocusDevice());
        } catch (Exception e2) {
            e2.printStackTrace();
        }

        try {
            mmc.waitForDevice(focusDevice);
            mmc.setPosition(focusDevice, 2 * initialPosition - firstPosition);
        } catch (Exception e) {
            IJ.error("Can't set z position");
            e.printStackTrace();
        }

        IJ.log("Seconde autofocus");
        double secondPosition = 0;
        try {
            autofocus.fullFocus();
            mmc.waitForDevice(focusDevice);
            secondPosition = mmc.getPosition(mmc.getFocusDevice());
        } catch (MMException e1) {
            e1.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            mmc.waitForDevice(focusDevice);
            mmc.setPosition(focusDevice, (secondPosition + firstPosition) / 2);
        } catch (Exception e) {
            IJ.error("Can't set z position");
            e.printStackTrace();
        }
        try {
            mmc.setShutterOpen(false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        // Start time
        long start = System.currentTimeMillis();
        mmc.setAutoShutter(false);

        // Set XY stage device
        try {
            mmc.setOriginXY(mmc.getXYStageDevice());
        } catch (Exception e1) {
            e1.printStackTrace();
        }

        String channels = parameters.getUsingChannels();
        String[] arrayCh = channels.split(",", -1);
        ArrayList<String> arrayChannels = new ArrayList<String>();
        Collections.addAll(arrayChannels, arrayCh);

        // Acquisition path arrangement
        ExplorationXYPositions explo = new ExplorationXYPositions(mmc, parameters);
        double timeInterval = Double.parseDouble(parameters.getFluoParameter(MaarsParameters.TIME_INTERVAL));
        int nThread = Runtime.getRuntime().availableProcessors();
        ExecutorService es = Executors.newFixedThreadPool(nThread);
        for (int i = 0; i < explo.length(); i++) {
            try {
                mm.core().setXYPosition(explo.getX(i), explo.getY(i));
                mmc.waitForDevice(mmc.getXYStageDevice());
            } catch (Exception e) {
                IJ.error("Can't set XY stage devie");
                e.printStackTrace();
            }
            String xPos = String.valueOf(Math.round(explo.getX(i)));
            String yPos = String.valueOf(Math.round(explo.getY(i)));
            IJ.log("Current position : x_" + xPos + " y_" + yPos);
            String pathToSegDir = FileUtils
                    .convertPath(parameters.getSavingPath() + File.separator + "X" + xPos + "_Y" + yPos);
            String pathToFluoDir = pathToSegDir + "_FLUO" + File.separator;
            // autofocus(mm, mmc);
            double zFocus = 0;
            String focusDevice = mmc.getFocusDevice();
            try {
                zFocus = mmc.getPosition(focusDevice);
                mmc.waitForDevice(focusDevice);
            } catch (Exception e) {
                ReportingUtils.logMessage("could not get z current position");
                e.printStackTrace();
            }
            SegAcquisition segAcq = new SegAcquisition(mm, mmc, parameters);
            segAcq.setBaseSaveDir(pathToSegDir);
            IJ.log("Acquire bright field image...");
            ImagePlus segImg = segAcq.acquire(parameters.getSegmentationParameter(MaarsParameters.CHANNEL), zFocus,
                    true);
            // --------------------------segmentation-----------------------------//
            MaarsSegmentation ms = new MaarsSegmentation(parameters);
            ms.segmentation(segImg, pathToSegDir);
            if (ms.roiDetected()) {
                // from Roi initialize a set of cell
                soc.reset();
                soc.loadCells(pathToSegDir);
                soc.setRoiMeasurementIntoCells(ms.getRoiMeasurements());
                // ----------------start acquisition and analysis --------//
                FluoAcquisition fluoAcq = new FluoAcquisition(mm, mmc, parameters);
                fluoAcq.setBaseSaveDir(pathToFluoDir);
                try {
                    PrintStream ps = new PrintStream(pathToSegDir + File.separator + "CellStateAnalysis.LOG");
                    curr_err = System.err;
                    curr_out = System.err;
                    System.setOut(ps);
                    System.setErr(ps);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
                int frame = 0;
                Boolean do_analysis = Boolean.parseBoolean(parameters.getFluoParameter(MaarsParameters.DO_ANALYSIS));
                Boolean saveFilm = Boolean
                        .parseBoolean(parameters.getFluoParameter(MaarsParameters.SAVE_FLUORESCENT_MOVIES));
                if (parameters.useDynamic()) {
                    // being dynamic acquisition
                    double startTime = System.currentTimeMillis();
                    double timeLimit = Double.parseDouble(parameters.getFluoParameter(MaarsParameters.TIME_LIMIT)) * 60
                            * 1000;
                    while (System.currentTimeMillis() - startTime <= timeLimit) {
                        double beginAcq = System.currentTimeMillis();
                        for (String channel : arrayChannels) {
                            ImagePlus fluoImage = fluoAcq.acquire(frame, channel, zFocus, saveFilm);
                            if (do_analysis) {
                                es.submit(new FluoAnalyzer(fluoImage, segImg.getCalibration(), soc, channel,
                                        Integer.parseInt(parameters.getChMaxNbSpot(channel)),
                                        Double.parseDouble(parameters.getChSpotRaius(channel)),
                                        Double.parseDouble(parameters.getChQuality(channel)), frame));
                            }
                        }
                        frame++;
                        double acqTook = System.currentTimeMillis() - beginAcq;
                        System.out.println(String.valueOf(acqTook));
                        if (timeInterval > acqTook) {
                            try {
                                Thread.sleep((long) (timeInterval - acqTook));
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        } else {
                            IJ.log("Attention : acquisition before took longer than " + timeInterval / 1000 + " s.");
                        }
                    }
                    // dynamic acquisition finished, find whether there is
                    // merotely cells.
                } else {
                    // being static acquisition
                    for (String channel : arrayChannels) {
                        ImagePlus fluoImage = fluoAcq.acquire(frame, channel, zFocus, saveFilm);
                        if (do_analysis) {
                            es.submit(new FluoAnalyzer(fluoImage, segImg.getCalibration(), soc, channel,
                                    Integer.parseInt(parameters.getChMaxNbSpot(channel)),
                                    Double.parseDouble(parameters.getChSpotRaius(channel)),
                                    Double.parseDouble(parameters.getChQuality(channel)), frame));
                        }
                    }
                }
                RoiManager.getInstance().reset();
                RoiManager.getInstance().close();
                if (soc.size() != 0 && do_analysis) {
                    long startWriting = System.currentTimeMillis();
                    Boolean splitChannel = true;
                    ImagePlus mergedImg = ImgUtils.loadFullFluoImgs(pathToFluoDir);
                    mergedImg.getCalibration().frameInterval = timeInterval / 1000;
                    MAARS.saveAll(soc, mergedImg, pathToFluoDir, arrayChannels, splitChannel);
                    MAARS.analyzeMitosisDynamic(soc, parameters, splitChannel, pathToSegDir, true);
                    ReportingUtils.logMessage("it took " + (double) (System.currentTimeMillis() - startWriting) / 1000
                            + " sec for writing results");
                }
                mailNotify();
            }
        }
        mmc.setAutoShutter(true);
        es.shutdown();
        try {
            // TODO no acq should be more than 3 hours
            es.awaitTermination(180, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.setErr(curr_err);
        System.setOut(curr_out);
        IJ.log("it took " + (double) (System.currentTimeMillis() - start) / 1000 + " sec for analysing");
    }
}