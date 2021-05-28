/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2021 Basis Technology Corp.
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

import java.awt.Graphics;
import javax.swing.JLabel;

/**
 * Base class for drawing a message overlay. Contains a paint method for
 * painting a JLabel using a java.awt.Graphics object.
 */
public class BaseMessageOverlay {
    private final JLabel label;
    private boolean visible = false;

    /**
     * Main constructor for the Overlay.
     */
    public BaseMessageOverlay() {
        label = new JLabel();
        label.setHorizontalAlignment(JLabel.CENTER);
        label.setVerticalAlignment(JLabel.CENTER);
        label.setOpaque(false);
    }

    /**
     * @return Whether or not this message overlay should be visible.
     */
    public boolean isVisible() {
        return visible;
    }

    /**
     * Sets this layer visible when painted. In order to be shown in UI, this
     * component needs to be repainted.
     *
     * @param visible Whether or not it is visible.
     */
    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    /**
     * Sets the message to be displayed in the child jlabel.
     *
     * @param message The message to be displayed.
     */
    public void setMessage(String message) {
        label.setText(String.format("<html><div style='text-align: center;'>%s</div></html>",
                message == null ? "" : message));
    }

    /**
     * Paints the jlabel at full width and height with the graphics object.
     *
     * @param g      The graphics object.
     * @param width  The width.
     * @param height The height.
     */
    public void paintOverlay(Graphics g, int width, int height) {
        paintOverlay(g, width, height, null);
    }

    /**
     * Paints the jlabel at full width and height with the graphics object.
     *
     * @param g             The graphics object.
     * @param parentWidth   The width of the component.
     * @param parentHeight  The height of the component.
     * @param labelMaxWidth The maximum width of the label drawn for the
     *                      overlay. The label will be vertically and
     *                      horizontally centered.
     */
    public void paintOverlay(Graphics g, int parentWidth, int parentHeight, Integer labelMaxWidth) {
        if (!visible) {
            return;
        }

        int labelWidth = (labelMaxWidth == null) ? parentWidth : Math.min(labelMaxWidth, parentWidth);
        int leftPad = (parentWidth - labelWidth) / 2;

        // paint the jlabel if visible.
        g.translate(leftPad, 0);
        label.setBounds(0, 0, labelWidth, parentHeight);
        g.translate(0, 0);

        label.paint(g);
    }
}
