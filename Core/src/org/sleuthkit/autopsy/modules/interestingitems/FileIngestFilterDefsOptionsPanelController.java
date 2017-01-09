/*
 * Autopsy Forensic Browser
 *
 * Copyright 2016 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.interestingitems;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import org.netbeans.spi.options.OptionsPanelController;
import org.openide.util.HelpCtx;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestJobSettings;

@OptionsPanelController.TopLevelRegistration(
        categoryName = "#OptionsCategory_Name_FileIngestFilterDefinitions",
        iconBase = "org/sleuthkit/autopsy/images/file_ingest_filter32x32.png",
        keywords = "#OptionsCategory_Keywords_FileIngestFilterDefinitions",
        keywordsCategory = "FileIngestFilterDefinitions",
        position = 7
)

/**
 * Class for creating an FilesSetDefsPanel which will be used for configuring
 * the FileIngestFilter.
 */
public final class FileIngestFilterDefsOptionsPanelController extends OptionsPanelController {

    private FilesSetDefsPanel panel;
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private boolean changed;
    public final static String NEW_FILE_INGEST_FILTER = "Create new file ingest filter...";
    private static final Logger LOGGER = Logger.getLogger(FileIngestFilterDefsOptionsPanelController.class.getName());

    /**
     * Component should load its data here.
     */
    @Override
    public void update() {
        getPanel().load();
        changed = false;
    }

    /**
     * Returns an array which will contain the names of all options which should
     * exist in the "Run Ingest Modules On:" JCombobox
     *
     * Keeping the default File Ingest Filters and the saved one seperate allows
     * the default to always be first elements in
     *
     * @return -filterNames an array of all established filter names as well as
     *         a Create New option
     */
    public String[] getComboBoxContents() {
        ArrayList<String> nameList = new ArrayList<>();
        for (FilesSet fSet : IngestJobSettings.getStandardFileIngestFilters()) {
            nameList.add(fSet.getName());
        }
        nameList.add(NEW_FILE_INGEST_FILTER);
        if (!(panel == null)) {
            nameList.addAll(panel.getKeys());
        }
        return nameList.toArray(new String[nameList.size()]);
    }

    /**
     * This method is called when both the Ok and Apply buttons are pressed. It
     * applies to any of the panels that have been opened in the process of
     * using the options pane.
     */
    @Override
    public void applyChanges() {
        if (changed) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    getPanel().store();
                    changed = false;
                }
            });
        }
    }

    /**
     * This method is called when the Cancel button is pressed. It applies to
     * any of the panels that have been opened in the process of using the
     * options pane.
     */
    @Override
    public void cancel() {
        // need not do anything special, if no changes have been persisted yet
    }

    @Override
    public boolean isValid() {
        return true;
    }

    /**
     * Used to determine whether any changes have been made to this controller's
     * panel.
     *
     * @return Whether or not a change has been made.
     */
    @Override
    public boolean isChanged() {
        return changed;
    }

    @Override
    public HelpCtx getHelpCtx() {
        return null;
    }

    @Override
    public JComponent getComponent(Lookup masterLookup) {
        return getPanel();
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener l) {
        pcs.addPropertyChangeListener(l);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener l) {
        pcs.removePropertyChangeListener(l);
    }

    /**
     * Creates an interestingItemsDefPanel that will be labeled to indicate it
     * is for File Ingest Filter settings
     *
     * @return an FilesSetDefsPanel which has text and fields modified to
     *         indicate it is for File Ingest Filtering.
     */
    private FilesSetDefsPanel getPanel() {
        if (panel == null) {
            panel = new FilesSetDefsPanel(FilesSetDefsPanel.PANEL_TYPE.FILE_INGEST_FILTERS);
            panel.addPropertyChangeListener(new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent evt) {
                    if (evt.getPropertyName().equals(OptionsPanelController.PROP_CHANGED)) {
                        changed();
                    }
                }
            });
        }
        return panel;
    }

    void changed() {
        if (!changed) {
            changed = true;
            pcs.firePropertyChange(OptionsPanelController.PROP_CHANGED, false, true);
        }
        pcs.firePropertyChange(OptionsPanelController.PROP_VALID, null, null);
    }

}
