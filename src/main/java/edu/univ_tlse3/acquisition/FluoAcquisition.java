package edu.univ_tlse3.acquisition;

import edu.univ_tlse3.maars.MaarsParameters;
import mmcorej.CMMCore;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;

import org.micromanager.acquisition.SequenceSettings;
import org.micromanager.internal.MMStudio;

import ij.ImagePlus;
import org.micromanager.acquisition.ChannelSpec;

/**
 * @author Tong LI, mail:tongli.bioinfo@gmail.com
 * @version Nov 19, 2015
 */
public class FluoAcquisition extends SuperClassAcquisition {
    private String channelGroup;
    private double zRange;
    private double zStep;
    private String savingRoot;

	public FluoAcquisition(MMStudio mm, CMMCore mmc, MaarsParameters parameters) {
		super(mm, mmc);
        this.channelGroup = parameters.getChannelGroup();
        this.zRange = Double.parseDouble(parameters.getFluoParameter(MaarsParameters.RANGE_SIZE_FOR_MOVIE));
        this.zStep = Double.parseDouble(parameters.getFluoParameter(MaarsParameters.STEP));
        this.savingRoot = parameters.getSavingPath();
	}

    public ArrayList<Double> computZSlices(double zFocus){
        return SuperClassAcquisition.computZSlices(zRange,zStep,zFocus);
    }

	public SequenceSettings buildSeqSetting(MaarsParameters parameters, ArrayList<Double> slices){
        ArrayList<String> arrayChannels = new ArrayList<String>();
        Collections.addAll(arrayChannels, parameters.getUsingChannels().split(",", -1));

        ArrayList<ChannelSpec> channelSetting = new ArrayList<ChannelSpec>();
        Color chColor;
        double chExpose;
        for (String ch : arrayChannels){
            chColor = MaarsParameters.getColor(parameters.getChColor(ch));
            chExpose = Double.parseDouble(parameters.getChExposure(ch));
            ChannelSpec channel_spec = new ChannelSpec();
            channel_spec.config = ch;
            channel_spec.color = chColor;
            channel_spec.exposure = chExpose;
            channelSetting.add(channel_spec);
        }

        SequenceSettings fluoAcqSetting = new SequenceSettings();
        fluoAcqSetting.save = Boolean
                .parseBoolean(parameters.getFluoParameter(MaarsParameters.SAVE_FLUORESCENT_MOVIES));
        fluoAcqSetting.prefix = "";
        fluoAcqSetting.root = this.savingRoot;
        fluoAcqSetting.slices = slices;
        fluoAcqSetting.channels = channelSetting;
        fluoAcqSetting.shouldDisplayImages= true;
        fluoAcqSetting.keepShutterOpenSlices = true;
        fluoAcqSetting.keepShutterOpenChannels = false;
        fluoAcqSetting.channelGroup = channelGroup;
        fluoAcqSetting.slicesFirst = true;
        fluoAcqSetting.intervalMs = Double.parseDouble(parameters.getFluoParameter(MaarsParameters.TIME_INTERVAL));
        return fluoAcqSetting;
    }

    public ImagePlus acquireToImp(SequenceSettings acqSettings, MaarsParameters parameters) {
        return super.acquire(acqSettings, parameters);
    }
}
