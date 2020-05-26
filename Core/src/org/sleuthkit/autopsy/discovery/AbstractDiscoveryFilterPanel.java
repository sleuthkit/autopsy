/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.discovery;

import java.awt.event.ActionListener;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.event.ListSelectionListener;

/**
 *
 * @author wschaefer
 */
abstract class AbstractDiscoveryFilterPanel extends javax.swing.JPanel {

    private static final long serialVersionUID = 1L;

    /**
     * Setup the file filter settings.
     *
     * @param selected        Boolean indicating if the filter should be
     *                        selected.
     * @param indicesSelected Array of integers indicating which list items are
     *                        selected, null to indicate leaving selected items
     *                        unchanged or that there are no items to select.
     */
    abstract void configurePanel(boolean selected, int[] indicesSelected);

    abstract JCheckBox getCheckbox();

    abstract JList<?> getList();

    abstract JLabel getAdditionalLabel();

    abstract String checkForError();

    /**
     * Add listeners to the checkbox/list set if listeners have not already been
     * added.
     *
     * @param listener
     * @param listListener
     */
    void addListeners(ActionListener actionListener, ListSelectionListener listListener) {
        if (getCheckbox() != null) {
            getCheckbox().addActionListener(actionListener);
        }
        if (getList() != null) {
            getList().addListSelectionListener(listListener);
        }
    }

    abstract FileSearchFiltering.FileFilter getFilter();

    void removeListeners() {
        if (getCheckbox() != null) {
            for (ActionListener listener : getCheckbox().getActionListeners()) {
                getCheckbox().removeActionListener(listener);
            }
        }
        if (getList() != null) {
            for (ListSelectionListener listener : getList().getListSelectionListeners()) {
                getList().removeListSelectionListener(listener);
            }
        }
    }

    boolean hasPanel() {
        return true;
    }

}
