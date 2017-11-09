/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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
package org.sleuthkit.autopsy.corecomponentinterfaces;

import java.beans.PropertyChangeListener;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 * Additional functionality of viewers supporting black board results such as
 * the directory tree
 * 
 *@deprecated No longer used.
 */
@Deprecated
public interface BlackboardResultViewer {

    public static final String FINISHED_DISPLAY_EVT = "FINISHED_DISPLAY_EVT"; //NON-NLS

    /**
     * View artifact in a viewer
     *
     * @param art artifact to view
     */
    void viewArtifact(BlackboardArtifact art);

    /**
     * View content associated with the artifact
     *
     * @param art artifact content to view
     */
    void viewArtifactContent(BlackboardArtifact art);

    /**
     * Add listener to fire an action when viewer is done displaying
     *
     * @param l
     */
    void addOnFinishedListener(PropertyChangeListener l);

}
