/*
 * Autopsy
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
package org.sleuthkit.autopsy.discovery.ui;

import java.awt.Component;
import javax.swing.JPanel;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 * Class for ensuring all ArtifactDetailsPanels have a setArtifact method.
 *
 */
public abstract class AbstractArtifactDetailsPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    public Component getComponent() {
        return this;
    }

    /**
     * Called to display the contents of the given artifact.
     *
     * @param artifact the artifact to display.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    abstract public void setArtifact(BlackboardArtifact artifact);

}
