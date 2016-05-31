package edu.univ_tlse3.cellstateanalysis;

import com.google.common.collect.Lists;
import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.SpotCollection;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.process.FloatProcessor;
import edu.univ_tlse3.cellstateanalysis.singleCellAnalysisFactory.FindMerotely;
import edu.univ_tlse3.utils.ImgUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.*;

/**
 * @author Tong LI, mail:tongli.bioinfo@gmail.com
 * @version Nov 19, 2015
 */
public class FluoAnalyzer implements Callable<FloatProcessor> {

    private ImagePlus fluoImage;
    private SetOfCells soc;
    private Calibration bfImgCal;
    private Calibration fluoImgCal;
    private String channel;
    private int maxNbSpot;
    private double radius;
    private double quality;
    private int frame;
    private SpotCollection collection;
    private Model model;

    /**
     * @param fluoImage image to analyze zProjectedFluoImg
     * @param bfImgCal  bright field image calibration, need it to decide whether or
     *                  not rescale ROI
     * @param soc       the set of cell to analyze
     * @param channel   fluo image channel
     * @param maxNbSpot max number of spot in corresponding channel
     * @param radius    radius of spot in corresponding channel
     * @param frame     time point
     */

    public FluoAnalyzer(ImagePlus fluoImage, Calibration bfImgCal, SetOfCells soc, String channel, int maxNbSpot,
                        double radius, double quality, int frame) {
        this.fluoImage = fluoImage;
        this.fluoImgCal = fluoImage.getCalibration();
        this.soc = soc;
        this.bfImgCal = bfImgCal;
        this.channel = channel;
        this.maxNbSpot = maxNbSpot;
        this.radius = radius;
        this.quality = quality;
        this.frame = frame;
    }

    /**
     * the main, use one new thread just in order to free acquisition thread to
     * acquire images as soon as possible
     */
    @Override
    public FloatProcessor call() throws Exception {
        if (fluoImage.getCalibration().getUnit().equals("cm")) {
            fluoImage = ImgUtils.unitCmToMicron(fluoImage);
        }
        // TODO project or not. Do not project if do 3D detection
        ImagePlus zProjectedFluoImg = ImgUtils.zProject(fluoImage);
        zProjectedFluoImg.setTitle(fluoImage.getTitle() + "_" + channel + "_projected");
        zProjectedFluoImg.setCalibration(fluoImage.getCalibration());
        // if (frame == 0) {
        // ResultsTable resultTable = new ResultsTable();
        // Analyzer analyzer = new Analyzer(zProjectedFluoImg,
        // Measurements.MEAN + Measurements.STD_DEV + Measurements.MIN_MAX,
        // resultTable);
        // Iterator<Cell> it = soc.iterator();
        // while (it.hasNext()) {
        // zProjectedFluoImg.setRoi(it.next().getCellShapeRoi());
        // analyzer.measure();
        // zProjectedFluoImg.deleteRoi();
        // }
        // resultTable.show(channel + " 0 frame fluo measure");
        // }
        // Call trackmate to detect spots
        MaarsTrackmate trackmate = new MaarsTrackmate(zProjectedFluoImg, radius, quality);

        this.model = trackmate.doDetection(true);
//		if (frame == 0) {
//			SelectionModel selectionModel = new SelectionModel(model);
//			HyperStackDisplayer displayer = new HyperStackDisplayer(model, selectionModel, zProjectedFluoImg);
//			displayer.render();
//			displayer.refresh();
//		}
        int nbCell = soc.size();
        collection = SpotsContainer.getNBestqualitySpots(model.getSpots(), nbCell, maxNbSpot);
        double[] factors = ImgUtils.getRescaleFactor(bfImgCal, fluoImgCal);

        int nThread = Runtime.getRuntime().availableProcessors();
        ExecutorService es = Executors.newFixedThreadPool(nThread);
        final int[] nbOfCellEachThread = new int[2];
        nbOfCellEachThread[0] = nbCell / nThread;
        nbOfCellEachThread[1] = nbOfCellEachThread[0] + nbCell % nThread;
        Future<?> future = null;
        for (int i = 0; i < nThread; i++) {
            // analyze every subset of cell
            future = es.submit(new AnalyseBlockCells(i, nbOfCellEachThread, factors));
        }
        es.shutdown();
        if (future != null) {
            future.get();
        }
        try {
            es.awaitTermination(10, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return zProjectedFluoImg.getProcessor().convertToFloatProcessor();
    }

    /**
     * analyzer of subset
     */
    private class AnalyseBlockCells implements Runnable {
        private final int index;
        private final int[] deltas;
        private double[] factors;

        AnalyseBlockCells(int index, final int[] deltas, double[] factors) {
            this.index = index;
            this.deltas = deltas;
            this.factors = factors;
        }

        @Override
        public void run() {
            // distribute number of cells for each thread
            int begin = 0;
            int end;
            if (index == 0) {
                if (deltas[0] != deltas[1]) {
                    end = deltas[1];
                } else {
                    end = deltas[0];
                }
            } else {
                begin = index * deltas[0] + (deltas[1] - deltas[0]);
                end = begin + deltas[0];
            }
            // need to be false because all spots are not visible
            ArrayList<Spot> currentThreadSpots = Lists.newArrayList(collection.iterable(false));
            for (int j = begin + 1; j <= end; j++) {
                Cell cell = soc.getCell(j);
                cell.addChannel(channel);
                cell.setTrackmateModel(model);
                Roi tmpRoi;
                if (factors[0] != 1 || factors[1] != 1) {
                    tmpRoi = cell.rescaleCellShapeRoi(factors);
                } else {
                    tmpRoi = cell.getCellShapeRoi();
                }
                double calibratedXBase = tmpRoi.getXBase() * fluoImgCal.pixelWidth;
                double calibratedYBase = tmpRoi.getYBase() * fluoImgCal.pixelHeight;
                ArrayList<Spot> spotsToDel = new ArrayList<Spot>();
                for (Spot s : currentThreadSpots) {
                    if (tmpRoi.contains((int) Math.round(s.getFeature(Spot.POSITION_X) / fluoImgCal.pixelWidth),
                            (int) Math.round(s.getFeature(Spot.POSITION_Y) / fluoImgCal.pixelHeight))) {
                        cell.putSpot(channel, frame, s);
                        spotsToDel.add(s);
                        if (cell.getNbOfSpots(channel, frame) > maxNbSpot) {
                            Spot lowesetQulitySpot = SpotsContainer
                                    .findLowestQualitySpot(cell.getSpotsInFrame(channel, frame));
                            cell.removeSpot(channel, frame, lowesetQulitySpot);
                        }
                    }
                }
                // remove spots found in current cell in order to accelerate
                // iteration
                for (Spot s2del : spotsToDel) {
                    currentThreadSpots.remove(s2del);
                }
                SpotSetAnalyzor spotSetAnalyzor = new SpotSetAnalyzor(cell.get(Cell.X_CENTROID) * fluoImgCal.pixelWidth,
                        cell.get(Cell.Y_CENTROID) * fluoImgCal.pixelHeight,
                        cell.get(Cell.MAJOR) * fluoImgCal.pixelWidth, cell.get(Cell.ANGLE), calibratedXBase,
                        calibratedYBase);

                Iterable<Spot> spotSet = cell.getSpotsInFrame(channel, frame);
                if (spotSet != null) {
                    HashMap<String, Object> geometry = new HashMap<String, Object>();
                    spotSetAnalyzor.compute(geometry, spotSet);
                    ArrayList<Spot> poles = spotSetAnalyzor.getPoles();
                    new FindMerotely(cell, spotSet, fluoImgCal, poles, radius, frame);
                    cell.putGeometry(channel, frame, geometry);
                }
            }
        }
    }
}