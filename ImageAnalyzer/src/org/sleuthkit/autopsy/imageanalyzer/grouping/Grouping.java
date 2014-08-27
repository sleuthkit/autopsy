/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013 Basis Technology Corp.
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
package org.sleuthkit.autopsy.imageanalyzer.grouping;

import org.sleuthkit.autopsy.imageanalyzer.ImageAnalyzerController;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Represents a set of files in a group. The UI listens to changes to the group
 * and updates itself accordingly.
 *
 * This class is named Grouping and not Group to avoid confusion with
 * {@link javafx.scene.Group} and others.
 */
public class Grouping {

    private static final Logger LOGGER = Logger.getLogger(Grouping.class.getName());

    public static final String UNKNOWN = "unknown";

    final private ObservableList<Long> fileIDs = FXCollections.observableArrayList();

    //cache the number of files in this groups with hashset hits
    private int filesWithHashSetHitsCount = -1;

    synchronized public ObservableList<Long> fileIds() {
        return fileIDs;
    }

    final public GroupKey groupKey;

    public Grouping(GroupKey groupKey, List<Long> filesInGroup) {
        this.groupKey = groupKey;
        fileIDs.setAll(filesInGroup);
    }

    synchronized public Integer getSize() {
        return fileIDs.size();
    }

    public double getHashHitDensity() {
        return getFilesWithHashSetHitsCount() / getSize().doubleValue();
    }

    synchronized public Integer getFilesWithHashSetHitsCount() {

        if (filesWithHashSetHitsCount < 0) {
            filesWithHashSetHitsCount = 0;
            for (Long fileID : fileIds()) {
                try {
                    long artcount = ImageAnalyzerController.getDefault().getSleuthKitCase().getBlackboardArtifactsCount(BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT, fileID);
                    if (artcount > 0) {
                        filesWithHashSetHitsCount++;
                    }
                } catch (IllegalStateException | TskCoreException ex) {
                    LOGGER.log(Level.WARNING, "could not access case during getFilesWithHashSetHitsCount()", ex);
                    break;
                }
            }
        }
        return filesWithHashSetHitsCount;
    }

    @Override
    public String toString() {
        return "Grouping{ keyProp=" + groupKey + '}';
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 53 * hash + Objects.hashCode(this.groupKey);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Grouping other = (Grouping) obj;
        if (!Objects.equals(this.groupKey, other.groupKey)) {
            return false;
        }
        return true;
    }

    synchronized public void addFile(Long f) {
        Platform.runLater(() -> {
            if (fileIDs.contains(f) == false) {
                fileIDs.add(f);
            }
        });

    }

    synchronized public void removeFile(Long f) {
        Platform.runLater(() -> {
            fileIDs.removeAll(f);
        });
    }
}
