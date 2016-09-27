package edu.univ_tlse3.acquisition;
import edu.univ_tlse3.utils.FileUtils;
import org.micromanager.acquisition.SequenceSettings;
import org.micromanager.data.Datastore;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.MMException;

/**
 * Created by Tong LI on 24/06/2016.
 */
public class MAARS_mda{
    MMStudio mm_;
    SequenceSettings acqSettings_;
    String channelGroup_;
    public MAARS_mda(MMStudio mm, SequenceSettings acqSettings, String channelGroup) {
        mm_ = mm;
        acqSettings_ = acqSettings;
        channelGroup_ = channelGroup;
    }

    public Datastore acquire() {
        FileUtils.createFolder(acqSettings_.root);
        mm_.getAcquisitionEngine().setSequenceSettings(acqSettings_);
        mm_.getAcquisitionEngine().setChannelGroup(channelGroup_);
        Datastore ds = null;
        try {
            ds = mm_.getAcquisitionEngine().acquire();
        } catch (MMException e) {
            e.printStackTrace();
        }
        while (mm_.getAcquisitionEngine().isAcquisitionRunning()){
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return ds;
    }
}
