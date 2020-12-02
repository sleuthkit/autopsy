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

import java.awt.Point;
import java.util.List;
import javax.swing.JPopupMenu;
import javax.swing.event.ListSelectionListener;
import org.sleuthkit.datamodel.BlackboardArtifact;

interface ArtifactListPanelInterface {

    void addMouseListener(java.awt.event.MouseAdapter mouseListener);

    void showPopupMenu(JPopupMenu popupMenu, Point point);

    BlackboardArtifact getSelectedArtifact();

    void removeSelectionListener(ListSelectionListener listener);

    void addArtifacts(List<BlackboardArtifact> artifactList);

    void addSelectionListener(ListSelectionListener listener);

    void selectFirst();
    
    void clearList();

    boolean isEmpty();

}
