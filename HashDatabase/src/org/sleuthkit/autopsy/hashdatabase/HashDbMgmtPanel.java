/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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
package org.sleuthkit.autopsy.hashdatabase;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.logging.Level;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.border.EmptyBorder;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.sleuthkit.autopsy.coreutils.Log;

/**
 * Panel for displaying and editing the Hash Database settings.
 * @author pmartel
 */
class HashDbMgmtPanel extends javax.swing.JPanel {

    private HashDbSettings settings;
    private static HashDbMgmtPanel instance;
    // text of panel for each database
    private static final String INTRO_TEXT1 = "Hash lookups are conducted when ingest is run.";
    private static final String INTRO_TEXT2 = "Lookup results can be found using the File Search feature.";
    private static final String NSRL_NAME = "NIST NSRL Database";
    private static final String NSRL_DESC = "Hashes that are known good and bad.";
    private static final String KNOWN_BAD_NAME = "Known Bad Database";
    private static final String KNOWN_BAD_DESC = "Hashes that are known bad.";
    private JLabel introText1;
    private JLabel introText2;
    private HashDbPanel NSRLPanel;
    private HashDbPanel knownBadPanel;

    /**
     * 
     * @param settings Settings to initialize the panel state from.
     */
    HashDbMgmtPanel(HashDbSettings settings) {
        this.settings = settings;

        initComponents();
    }
    
    static HashDbMgmtPanel getDefault() {
        if (instance == null)
            try {
                instance = new HashDbMgmtPanel(HashDbSettings.getHashDbSettings());
            } catch (IOException ex) {
                Log.get(HashDbMgmtPanel.class).log(Level.WARNING, "Couldn't get Hash DB settings", ex);
            }
        return instance;
    }

    /**
     * Checks if indexes exist for all defined databases
     * @return true if Sleuth Kit can open the indexes of all databases
     * than have been selected
     */
    boolean indexesExist() {
        HashDb nsrl = this.NSRLPanel.db;
        HashDb knownBad = this.knownBadPanel.db;

        if (nsrl != null && !nsrl.indexExists()) {
            return false;
        }
        if (knownBad != null && !knownBad.indexExists()) {
            return false;
        }
        return true;
    }

    /**
     * Modifies the given settings object to match the current panel state, and
     * then persists it to its backing file.
     * @throws IOException if there is an error saving the settings to a file
     */
    void saveSettings() throws IOException {
        this.settings.setNSRLDatabase(this.NSRLPanel.db);
        this.settings.setKnownBadDatabase(this.knownBadPanel.db);
        this.settings.save();
    }

    /**
     * Initializes all the panel components
     */
    private void initComponents() {

        NSRLPanel = new HashDbPanel(this.settings.getNSRLDatabase(), HashDbMgmtPanel.NSRL_NAME, HashDbMgmtPanel.NSRL_DESC);
        knownBadPanel = new HashDbPanel(this.settings.getKnownBadDatabase(), HashDbMgmtPanel.KNOWN_BAD_NAME, HashDbMgmtPanel.KNOWN_BAD_DESC);

        introText1 = new JLabel();
        introText1.setText(INTRO_TEXT1);
        introText1.setBorder(new EmptyBorder(10, 10, 5, 10));
        introText1.setAlignmentX(Component.CENTER_ALIGNMENT);
        introText1.setMaximumSize(NSRLPanel.getMaximumSize());

        introText2 = new JLabel();
        introText2.setText(INTRO_TEXT2);
        introText2.setBorder(new EmptyBorder(0, 10, 0, 10));
        introText2.setAlignmentX(Component.CENTER_ALIGNMENT);
        introText2.setMaximumSize(NSRLPanel.getMaximumSize());

        BoxLayout layout = new BoxLayout(this, BoxLayout.Y_AXIS);
        this.setLayout(layout);
        this.add(introText1);
        this.add(introText2);
        this.add(NSRLPanel);
        this.add(knownBadPanel);
        this.add(Box.createRigidArea(new Dimension(0, 10)));
    }

    void save(){
        if (indexesExist()) {
            try {
                saveSettings();
            } catch (IOException ex) {
                Log.get(HashDbMgmtPanel.class).log(Level.WARNING, "Couldn't save hash database settings.", ex);
            }
        } else {
            NotifyDescriptor d = new NotifyDescriptor.Message("All selected databases must have indexes.", NotifyDescriptor.INFORMATION_MESSAGE);
            DialogDisplayer.getDefault().notify(d);
        }
    }
}
