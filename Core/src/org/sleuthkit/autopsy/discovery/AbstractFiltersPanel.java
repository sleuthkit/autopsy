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
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

/**
 *
 * @author wschaefer
 */
abstract class AbstractFiltersPanel extends JPanel implements ActionListener, ListSelectionListener {

    private boolean isInitialized = false;
    private static final double LABEL_WEIGHT = 0;
    private static final double PANEL_WEIGHT = .1;
    private static final int LABEL_WIDTH = 1;
    private static final int PANEL_WIDTH = 2;
    private static final int LABEL_HEIGHT = 1;
    private static final int PANEL_HEIGHT = 2;
    private static final long serialVersionUID = 1L;
    private final GridBagConstraints constraints = new GridBagConstraints();
    private final List<AbstractDiscoveryFilterPanel> filters = new ArrayList<>();
    private final JPanel firstColumnPanel = new JPanel();
    private final JPanel secondColumnPanel = new JPanel();
    private int firstColumnY = 0;
    private int secondColumnY = 0;

    AbstractFiltersPanel() {
        firstColumnPanel.setLayout(new GridBagLayout());
        secondColumnPanel.setLayout(new GridBagLayout());
    }

    abstract FileSearchData.FileType getFileType();

    final synchronized void addFilter(AbstractDiscoveryFilterPanel filterPanel, boolean isSelected, int[] indicesSelected, int column) {
        if (!isInitialized) {
            constraints.gridy = 0;
            constraints.anchor = GridBagConstraints.FIRST_LINE_START;
            constraints.insets = new Insets(8, 8, 8, 8);
            isInitialized = true;
        }
        if (column == 0) {
            constraints.gridy = firstColumnY;
        } else {
            constraints.gridy = secondColumnY;
        }
        constraints.gridx = 0;
        filterPanel.configurePanel(isSelected, indicesSelected);
        filterPanel.addListeners(this, this);
        filters.add(filterPanel);
        constraints.fill = GridBagConstraints.VERTICAL;
        constraints.gridheight = LABEL_HEIGHT;
        constraints.gridwidth = LABEL_WIDTH;
        constraints.weightx = LABEL_WEIGHT;
        constraints.weighty = LABEL_WEIGHT;
        constraints.gridwidth = LABEL_WIDTH;
        addToGridBagLayout(filterPanel.getCheckbox(), null, column);
        if (filterPanel.hasPanel()) {
            System.out.println("filterPanel has panel" + filterPanel.getClass().toString());
            constraints.gridx += constraints.gridwidth;
            constraints.fill = GridBagConstraints.BOTH;
            constraints.gridheight = PANEL_HEIGHT;
            constraints.weightx = PANEL_WEIGHT;
            constraints.weighty = PANEL_WEIGHT;
            constraints.gridwidth = PANEL_WIDTH;
            addToGridBagLayout(filterPanel, null, column);
        } else {
            System.out.println("filterPanel missing panel" + filterPanel.getClass().toString());
        }
        if (column == 0) {
            firstColumnY += constraints.gridheight;
        } else {
            secondColumnY += constraints.gridheight;
        }
    }

    final void addPanelsToScrollPane(JSplitPane splitPane) {
        splitPane.setLeftComponent(firstColumnPanel);
        splitPane.setRightComponent(secondColumnPanel);
        validate();
        repaint();
    }

    final synchronized void clearFilters() {
        for (AbstractDiscoveryFilterPanel filterPanel : filters) {
            filterPanel.removeListeners();
        }
        filters.clear();
    }

    private void addToGridBagLayout(Component componentToAdd, Component additionalComponentToAdd, int columnIndex) {
        addToColumn(componentToAdd, constraints, columnIndex);
        if (additionalComponentToAdd != null) {
            constraints.gridy += constraints.gridheight;
            addToColumn(additionalComponentToAdd, constraints, columnIndex);
            constraints.gridy -= constraints.gridheight;
        } 
    }

    private void addToColumn(Component component, Object constraints, int columnNumber) {
        if (columnNumber == 0) {
            firstColumnPanel.add(component, constraints);
        } else {
            secondColumnPanel.add(component, constraints);
        }
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
