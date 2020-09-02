/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.datasourcesummary.uiutils;

import java.awt.Graphics;
import javax.swing.JLabel;

/**
 *
 * @author gregd
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

    public void paintOverlay(Graphics g, int width, int height) {
        if (!visible) {
            return;
        }

        // paint the jlabel if visible.
        label.setBounds(0, 0, width, height);
        label.paint(g);
    }
}
