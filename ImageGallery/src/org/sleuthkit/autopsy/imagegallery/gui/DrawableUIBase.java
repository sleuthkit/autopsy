/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.imagegallery.gui;

import java.util.Objects;
import static java.util.Objects.nonNull;
import java.util.Optional;
import java.util.logging.Level;
import javafx.scene.layout.AnchorPane;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableFile;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 */
abstract public class DrawableUIBase extends AnchorPane implements DrawableView {

    private final ImageGalleryController controller;

    volatile private Optional<DrawableFile<?>> fileOpt = Optional.empty();

    volatile private Optional<Long> fileIDOpt = Optional.empty();

    public DrawableUIBase(ImageGalleryController controller) {
        this.controller = controller;
    }

    @Override
    public ImageGalleryController getController() {
        return controller;
    }

    @Override
    public Optional<Long> getFileID() {
        return fileIDOpt;
    }

    void setFileIDOpt(Optional<Long> fileIDOpt) {
        this.fileIDOpt = fileIDOpt;
    }

    void setFileOpt(Optional<DrawableFile<?>> fileOpt) {
        this.fileOpt = fileOpt;
    }

    @Override
    public Optional<DrawableFile<?>> getFile() {
        if (fileIDOpt.isPresent()) {
            if (fileOpt.isPresent() && fileOpt.get().getId() == fileIDOpt.get()) {
                return fileOpt;
            } else {
                try {
                    fileOpt = Optional.of(getController().getFileFromId(fileIDOpt.get()));
                } catch (TskCoreException ex) {
                    Logger.getAnonymousLogger().log(Level.WARNING, "failed to get DrawableFile for obj_id" + fileIDOpt.get(), ex);
                    fileOpt = Optional.empty();
                }
                return fileOpt;
            }
        } else {
            return Optional.empty();
        }
    }

    protected abstract void setFileHelper(Long newFileID);

    @Override
    public void setFile(Long newFileID) {
        if (getFileID().isPresent()) {
            if (Objects.equals(newFileID, getFileID().get()) == false) {
                setFileHelper(newFileID);
            }
        } else if (nonNull(newFileID)) {
            setFileHelper(newFileID);
        }
    }


}
