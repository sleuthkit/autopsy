/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.filequery;

import java.awt.Image;
import java.util.Collections;
import java.util.List;
import org.sleuthkit.datamodel.AbstractFile;

/**
 *
 * @author wschaefer
 */
public class ThumbnailsWrapper {

    private final List<Image> thumbnails;
    private final AbstractFile abstractFile;
    private final int[] timeStamps;

    public ThumbnailsWrapper(List<Image> thumbnails, int[] timeStamps, AbstractFile file) {
        this.thumbnails = thumbnails;
        this.timeStamps = timeStamps;
        this.abstractFile = file;
    }

    AbstractFile getAbstractFile(){
        return abstractFile;
    }
    
    int[] getTimeStamps(){
        return timeStamps.clone();
    }
    
    String getFileInfo() {
        return abstractFile.getParentPath();
    }

    List<Image> getThumbnails() {
        return Collections.unmodifiableList(thumbnails);
    }

}
