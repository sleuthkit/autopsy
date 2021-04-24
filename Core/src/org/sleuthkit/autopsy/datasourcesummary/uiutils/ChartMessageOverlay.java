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
package org.sleuthkit.autopsy.datasourcesummary.uiutils;

import java.awt.Graphics2D;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.panel.AbstractOverlay;
import org.jfree.chart.panel.Overlay;

/**
 * A JFreeChart message overlay that can show a message for the purposes of the
 * LoadableComponent.
 */
class ChartMessageOverlay extends AbstractOverlay implements Overlay {

    private static final long serialVersionUID = 1L;
    private final BaseMessageOverlay overlay = new BaseMessageOverlay();

    // multiply this value by the smaller dimension (height or width) of the component
    // to determine width of text to be displayed.
    private static final double MESSAGE_WIDTH_FACTOR = .6;

    /**
     * Sets this layer visible when painted. In order to be shown in UI, this
     * component needs to be repainted.
     *
     * @param visible Whether or not it is visible.
     */
    void setVisible(boolean visible) {
        overlay.setVisible(visible);
    }

    /**
     * Sets the message to be displayed in the child jlabel.
     *
     * @param message The message to be displayed.
     */
    void setMessage(String message) {
        overlay.setMessage(message);
    }

    @Override
    public void paintOverlay(Graphics2D gd, ChartPanel cp) {
        int labelWidth = (int) (Math.min(cp.getWidth(), cp.getHeight()) * MESSAGE_WIDTH_FACTOR);
        overlay.paintOverlay(gd, cp.getWidth(), cp.getHeight(), labelWidth);
    }
}
