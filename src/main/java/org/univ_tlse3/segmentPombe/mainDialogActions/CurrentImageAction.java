package org.univ_tlse3.segmentPombe.mainDialogActions;

import ij.IJ;
import org.univ_tlse3.segmentPombe.SegPombeMainDialog;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/*
 * Allow to select current image as the image to process
 */
public class CurrentImageAction implements ActionListener {

    private SegPombeMainDialog mainDialog;

    public CurrentImageAction(SegPombeMainDialog mainDialog) {
        this.mainDialog = mainDialog;
    }

    public void actionPerformed(ActionEvent arg0) {
        mainDialog.getAlreadryOpenedImage();
        mainDialog.resetFileNameField();
        mainDialog.setFileNameField(IJ.getImage().getTitle());
    }
}
