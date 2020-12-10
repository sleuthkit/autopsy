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
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.event.ListSelectionListener;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 * Abstract class to define methods expected of a list of artifacts in the
 * discovery details section.
 */
abstract class AbstractArtifactListPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    /**
     * Add a listener to the table of artifacts to perform actions when an
     * artifact is selected.
     *
     * @param listener The listener to add to the table of artifacts.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    abstract void addMouseListener(java.awt.event.MouseAdapter mouseListener);

    /**
     * Display the specified JPopupMenu at the specified point.
     *
     * @param popupMenu The JPopupMenu to display.
     * @param point     The point the menu should be displayed at.
     */
    abstract void showPopupMenu(JPopupMenu popupMenu, Point point);

    /**
     * The artifact which is currently selected, null if no artifact is
     * selected.
     *
     * @return The currently selected BlackboardArtifact or null if none is
     *         selected.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    abstract BlackboardArtifact getSelectedArtifact();

    /**
     * Select the row at the specified point.
     *
     * @param point The point which if a row exists it should be selected.
     *
     * @return True if a row was selected, false if no row was selected.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    abstract boolean selectAtPoint(Point point);

    /**
     * Add the specified list of artifacts to the list of artifacts which should
     * be displayed.
     *
     * @param artifactList The list of artifacts to display.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    abstract void addArtifacts(List<BlackboardArtifact> artifactList);

    /**
     * Remove a listener from the table of artifacts.
     *
     * @param listener The listener to remove from the table of artifacts.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    abstract void removeSelectionListener(ListSelectionListener listener);

    /**
     * Add a listener to the table of artifacts to perform actions when an
     * artifact is selected.
     *
     * @param listener The listener to add to the table of artifacts.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    abstract void addSelectionListener(ListSelectionListener listener);

    /**
     * Select the first available artifact in the list if it is not empty to
     * populate the panel to the right.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    abstract void selectFirst();

    /**
     * Remove all artifacts from the list of artifacts displayed.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    abstract void clearList();

    /**
     * Whether the list of artifacts is empty.
     *
     * @return true if the list of artifacts is empty, false if there are
     *         artifacts.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    abstract boolean isEmpty();

}
