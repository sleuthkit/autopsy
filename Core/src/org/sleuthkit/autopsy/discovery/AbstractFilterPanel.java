/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.discovery;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

/**
 *
 * @author wschaefer
 */
abstract class AbstractFilterPanel extends javax.swing.JPanel implements ActionListener {

    AbstractFilterPanel() {
        constraints.fill = GridBagConstraints.VERTICAL;
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.gridheight = 2;
        constraints.gridwidth = LABEL_WIDTH;
        constraints.weightx = LABEL_WEIGHT;
        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.insets = new Insets(0, 8, 12, 8);
    }
    private static final double LABEL_WEIGHT = 0;
    private static final double PANEL_WEIGHT = 0;
    private static final int LABEL_WIDTH = 1;
    private static final int PANEL_WIDTH = 2;
    private static final int NUMBER_OF_COLUMNS = 6;
    private static final long serialVersionUID = 1L;
    private final GridBagLayout layout = new GridBagLayout();
    private final GridBagConstraints constraints = new GridBagConstraints();
    private final List<AbstractDiscoveryFiltersPanel> filters = new ArrayList<>();

    abstract FileSearchData.FileType getFileType();

    void addFilter(AbstractDiscoveryFiltersPanel filterPanel, boolean isSelected, int[] indicesSelected) {
        filterPanel.configurePanel(isSelected, indicesSelected);
        filterPanel.addListeners(this, new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent evt) {
                if (!evt.getValueIsAdjusting()) {
                    validateFields();
                }
            }
        });
        filters.add(filterPanel);
        addToGridBagLayout(filterPanel.getCheckbox(), null);
        addToGridBagLayout(filterPanel, null);
        constraints.weightx = .9;
        constraints.fill = GridBagConstraints.BOTH;
        addToGridBagLayout(new javax.swing.Box.Filler(new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0), new java.awt.Dimension(32767, 0)), null);
        constraints.fill = GridBagConstraints.VERTICAL;
        constraints.weightx = LABEL_WEIGHT;
        updateLayout();
    }

    void endPanel() {
        //add filler at end
        constraints.gridy++;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.weightx = .9;
        constraints.weighty = .9;
        add(new javax.swing.Box.Filler(new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0), new java.awt.Dimension(32767, 32767)), constraints);
    }

    void clearFilters() {
        filters.clear();
    }

    private void addToGridBagLayout(Component componentToAdd, Component additionalComponentToAdd) {
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

    private void updateLayout() {
        setLayout(layout);
    }

    /**
     * The settings are not valid so disable the search button and display the
     * given error message.
     *
     * @param error
     */
    private void setInvalid(String error) {
        firePropertyChange("FilterError", error, error);
    }

    /**
     * The settings are valid so enable the Search button
     */
    private void setValid() {
        firePropertyChange("FilterError", null, null);
    }

    private void validateFields() {
        String errorString;
        for (AbstractDiscoveryFiltersPanel filterPanel : filters) {
            errorString = filterPanel.checkForError();
            if (errorString != null) {
                setInvalid(errorString);
                return;
            }
        }
        setValid();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        validateFields();
    }

    /**
     * Get a list of all filters selected by the user.
     *
     * @return the list of filters
     */
    /**
     * Get a list of all filters selected by the user.
     *
     * @return the list of filters
     */
    List<FileSearchFiltering.FileFilter> getFilters() {
        List<FileSearchFiltering.FileFilter> filtersToUse = new ArrayList<>();
        filtersToUse.add(new FileSearchFiltering.FileTypeFilter(getFileType()));
        for (AbstractDiscoveryFiltersPanel filterPanel : filters) {
            if (filterPanel.getCheckbox().isSelected()) {
                FileSearchFiltering.FileFilter filter = filterPanel.getFilter();
                if (filter != null) {
                    filtersToUse.add(filter);
                }
            }
        }
        return filtersToUse;
    }

}
