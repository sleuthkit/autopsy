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

    public ThumbnailsWrapper(List<Image> thumbnails, AbstractFile file) {
        this.thumbnails = thumbnails;
        this.abstractFile = file;
    }

    AbstractFile getAbstractFile(){
        return abstractFile;
    }
    
    String getFileInfo() {
        return abstractFile.getParentPath();
    }

    List<Image> getThumbnails() {
        return Collections.unmodifiableList(thumbnails);
    }

}
