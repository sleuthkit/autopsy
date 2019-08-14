/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.filequery;

import java.awt.Image;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author wschaefer
 */
public class ThumbnailsWrapper {

    private final List<Image> thumbnails;
    private final String fileInfo;

    public ThumbnailsWrapper(List<Image> thumbnails, String fileInfo) {

        System.out.println("WRAPPER CREATED");
        this.thumbnails = thumbnails;
        this.fileInfo = fileInfo;
    }

    String getFileInfo() {
        return fileInfo;
    }

    List<Image> getThumbnails() {
        return Collections.unmodifiableList(thumbnails);
    }

}
