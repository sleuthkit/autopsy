/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.discovery;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionListener;

/**
 *
 * @author wschaefer
 */
abstract class AbstractFilterPanel extends javax.swing.JPanel implements ActionListener {

    private static final double LABEL_WEIGHT = 0;
    private static final double PANEL_WEIGHT = 0;
    private static final int LABEL_WIDTH = 1;
    private static final int PANEL_WIDTH = 2;
    private static final int NUMBER_OF_COLUMNS = 6;
    private static final long serialVersionUID = 1L;
    private final GridBagLayout layout = new GridBagLayout();
    private final GridBagConstraints constraints = new GridBagConstraints();

    final void initConstraints() {
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.gridheight = 2;
        constraints.gridwidth = LABEL_WIDTH;
        constraints.weightx = LABEL_WEIGHT;
        constraints.anchor = GridBagConstraints.NORTHWEST;
    }

    final void addToGridBagLayout(Component componentToAdd, Component additionalComponentToAdd) {
        if (constraints.gridx % 2 == 0) {
            constraints.weightx = LABEL_WEIGHT;
            constraints.gridwidth = LABEL_WIDTH;
        } else {
            constraints.weightx = PANEL_WEIGHT;
            constraints.gridwidth = PANEL_WIDTH;
        }
        if (additionalComponentToAdd != null) {
            constraints.gridheight = 1;
            add(componentToAdd, constraints);
            constraints.gridy++;
            add(additionalComponentToAdd, constraints);
            constraints.gridy--;
            constraints.gridheight = 2;
        } else {
            add(componentToAdd, constraints);
        }
        constraints.gridx = (constraints.gridx + LABEL_WIDTH) % NUMBER_OF_COLUMNS;
        if (constraints.gridx == 0) {
            constraints.gridy += constraints.gridheight;
        }
    }

    final void updateLayout() {
        setLayout(layout);
    }

    /**
     * The settings are not valid so disable the search button and display the
     * given error message.
     *
     * @param error
     */
    void setInvalid(String error) {
        firePropertyChange("FilterError", error, error);
    }

    /**
     * The settings are valid so enable the Search button
     */
    void setValid() {
        firePropertyChange("FilterError", null, null);
    }
}
