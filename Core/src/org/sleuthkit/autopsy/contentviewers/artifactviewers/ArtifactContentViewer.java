/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.contentviewers.artifactviewers;

import java.awt.Component;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 * Common interface implemented by artifact viewers.
 * 
 * An artifact viewer displays the artifact in a custom
 * layout panel suitable for the artifact type.
 * 
 */
public interface ArtifactContentViewer {
   
    /**
     * Called to display the contents of the given artifact. 
     *
     * @param artifact the artifact to display.
     */
    void setArtifact(BlackboardArtifact artifact);
    
    /**
     * Returns the panel.
     * 
     * @return display panel.
     */
    Component getComponent();

    /**
     * Checks whether the given artifact is supported by the viewer.
     *
     * @param artifact Artifact to check.
     *
     * @return True if the artifact can be displayed by the viewer, false otherwise.
     */
    boolean isSupported(BlackboardArtifact artifact);
   
}
