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
abstract class AbstractFiltersPanel extends javax.swing.JPanel implements ActionListener, ListSelectionListener {

    private static boolean isInitialized = false;
    private static final double LABEL_WEIGHT = 0;
    private static final double PANEL_WEIGHT = .1;
    private static final int LABEL_WIDTH = 1;
    private static final int PANEL_WIDTH = 2;
    private static final int NUMBER_OF_COLUMNS = 6;
    private static final long serialVersionUID = 1L;
    private final GridBagLayout layout = new GridBagLayout();
    private final GridBagConstraints constraints = new GridBagConstraints();
    private final List<AbstractDiscoveryFilterPanel> filters = new ArrayList<>();

    abstract FileSearchData.FileType getFileType();

    final synchronized void addFilter(AbstractDiscoveryFilterPanel filterPanel, boolean isSelected, int[] indicesSelected) {
        if (!isInitialized) {
            constraints.fill = GridBagConstraints.VERTICAL;
            constraints.gridx = 0;
            constraints.gridy = 0;
            constraints.gridheight = 2;
            constraints.gridwidth = LABEL_WIDTH;
            constraints.weightx = LABEL_WEIGHT;
            constraints.anchor = GridBagConstraints.NORTHWEST;
            constraints.insets = new Insets(0, 8, 12, 8);
            isInitialized = true;
        }
        filterPanel.configurePanel(isSelected, indicesSelected);
        filterPanel.addListeners(this, this);
        constraints.fill = GridBagConstraints.VERTICAL;
        filters.add(filterPanel);
        constraints.weightx = LABEL_WEIGHT;
        constraints.gridwidth = LABEL_WIDTH;
        addToGridBagLayout(filterPanel.getCheckbox(), null);
        nextSpot(LABEL_WIDTH);
        constraints.fill = GridBagConstraints.BOTH;
        constraints.weightx = PANEL_WEIGHT;
        constraints.gridwidth = PANEL_WIDTH;
        addToGridBagLayout(filterPanel, null);
        nextSpot(PANEL_WIDTH);
        updateLayout();
    }

    private void nextSpot(int width) {
        constraints.gridx += width;
        if (constraints.gridx >= NUMBER_OF_COLUMNS) {
            constraints.fill = GridBagConstraints.VERTICAL;
            constraints.gridy += constraints.gridheight;
            constraints.gridx = 0;
        }
    }


    final synchronized void clearFilters() {
        for (AbstractDiscoveryFilterPanel filterPanel : filters) {
            filterPanel.removeListeners();
        }
        filters.clear();
    }

    private void addToGridBagLayout(Component componentToAdd, Component additionalComponentToAdd) {
        if (additionalComponentToAdd != null) {
            constraints.gridheight /= 2;
            add(componentToAdd, constraints);
            constraints.gridy += constraints.gridheight;
            add(additionalComponentToAdd, constraints);
            constraints.gridy -= constraints.gridheight;
            constraints.gridheight *= 2;
        } else {
            add(componentToAdd, constraints);
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
        System.out.println("ERROR FIRED " + error);
        firePropertyChange("FilterError", error, error);
    }

    /**
     * The settings are valid so enable the Search button
     */
    private void setValid() {
        System.out.println("VALID FIRED");
        firePropertyChange("FilterError", null, null);
    }

    private void validateFields() {
        String errorString;
        System.out.println("VALIDATE FIELDS");
        for (AbstractDiscoveryFilterPanel filterPanel : filters) {
            errorString = filterPanel.checkForError();
            if (errorString != null) {
                setInvalid(errorString);
                return;
            }
            System.out.println("FILTER VALID");
        }
        setValid();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        System.out.println("ACTION PERFORMED");
        validateFields();
    }

    boolean isObjectsFilterSupported() {
        for (AbstractDiscoveryFilterPanel filter : filters) {
            if (filter instanceof ObjectDetectedFilterPanel) {
                return filter.getList().getModel().getSize() > 0;
            }
        }
        return false;
    }

    boolean isHashSetFilterSupported() {
        for (AbstractDiscoveryFilterPanel filter : filters) {
            if (filter instanceof HashSetFilterPanel) {
                return filter.getList().getModel().getSize() > 0;
            }
        }
        return false;
    }

    boolean isInterestingItemsFilterSupported() {
        for (AbstractDiscoveryFilterPanel filter : filters) {
            if (filter instanceof InterestingItemsFilterPanel) {
                return filter.getList().getModel().getSize() > 0;
            }
        }
        return false;
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
    synchronized List<FileSearchFiltering.FileFilter> getFilters() {
        List<FileSearchFiltering.FileFilter> filtersToUse = new ArrayList<>();
        filtersToUse.add(new FileSearchFiltering.FileTypeFilter(getFileType()));
        for (AbstractDiscoveryFilterPanel filterPanel : filters) {
            if (filterPanel.getCheckbox().isSelected()) {
                FileSearchFiltering.FileFilter filter = filterPanel.getFilter();
                if (filter != null) {
                    filtersToUse.add(filter);
                }
            }
        }
        return filtersToUse;
    }

    @Override
    public void valueChanged(ListSelectionEvent evt) {
        System.out.println("VALUE CHANGED");
        if (!evt.getValueIsAdjusting()) {
            validateFields();
        }
    }

}
