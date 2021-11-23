/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.mainui.nodes;

import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.LocalFilesDataSource;
import org.sleuthkit.datamodel.Pool;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.Volume;

/**
 * Consolidates node paths shared between the result view table and the tree.
 */
class NodeIconUtil {
    
    final static NodeIconUtil FOLDER = new NodeIconUtil("org/sleuthkit/autopsy/images/Folder-icon.png");
    final static NodeIconUtil DELETED_FOLDER = new NodeIconUtil("org/sleuthkit/autopsy/images/folder-icon-deleted.png");
    final static NodeIconUtil VIRTUAL_DIRECTORY = new NodeIconUtil("org/sleuthkit/autopsy/images/folder-icon-virtual.png");
    final static NodeIconUtil CARVED_FILE = new NodeIconUtil("org/sleuthkit/autopsy/images/carved-file-x-icon-16.png");
    final static NodeIconUtil DELETED_FILE = new NodeIconUtil("org/sleuthkit/autopsy/images/file-icon-deleted.png");
    final static NodeIconUtil IMAGE = new NodeIconUtil("org/sleuthkit/autopsy/images/hard-drive-icon.jpg");
    final static NodeIconUtil VOLUME = new NodeIconUtil("org/sleuthkit/autopsy/images/vol-icon.png");
    final static NodeIconUtil POOL = new NodeIconUtil("org/sleuthkit/autopsy/images/pool-icon.png");
    final static NodeIconUtil FILE = new NodeIconUtil("org/sleuthkit/autopsy/images/file-icon.png");
    final static NodeIconUtil LOCAL_FILES_DATA_SOURCE = new NodeIconUtil("org/sleuthkit/autopsy/images/fileset-icon-16.png");
    //final static NodeIconUtil  = new NodeIconUtil("");
    
    private final String iconPath;
    
    private NodeIconUtil(String path) {
        this.iconPath = path;
    }
    
    String getPath() {
        return iconPath;
    }
    
    public static String getPathForContent(Content c) {
        if (c instanceof Image) {
            return IMAGE.getPath();
        } else if (c instanceof LocalFilesDataSource) {
            return LOCAL_FILES_DATA_SOURCE.getPath();
        } else if (c instanceof Volume) {
            return VOLUME.getPath();
        } else if (c instanceof Pool) {
            return POOL.getPath();
        } else if (c instanceof AbstractFile) {
            AbstractFile file = (AbstractFile) c;
            if (((AbstractFile) c).isDir()) {
                if (file.isDirNameFlagSet(TskData.TSK_FS_NAME_FLAG_ENUM.UNALLOC)) {
                    return DELETED_FOLDER.getPath();
                } else {
                    return FOLDER.getPath();
                }
            } else {
                if (file.isDirNameFlagSet(TskData.TSK_FS_NAME_FLAG_ENUM.UNALLOC)) {
                    if (file.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.CARVED)) {
                        return CARVED_FILE.getPath();
                    } else {
                        return DELETED_FILE.getPath();
                    }
                } else {
                    return FILE.getPath();
                }
            }
        }
        return FILE.getPath();
    }
}
