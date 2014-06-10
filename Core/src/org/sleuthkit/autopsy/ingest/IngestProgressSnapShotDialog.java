/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2014 Basis Technology Corp.
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
package org.sleuthkit.autopsy.ingest;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Toolkit;
import javax.swing.JDialog;
import javax.swing.JFrame;

public class IngestProgressSnapShotDialog extends JDialog {

    // RJCTODO
//    private static final String TITLE = NbBundle.getMessage(RunIngestModulesDialog.class, "IngestDialog.title.text");
    private static final String TITLE = "Ingest Progress Snapshot";
    private static Dimension DIMENSIONS = new Dimension(500, 300);

    public IngestProgressSnapShotDialog(JFrame frame, String title, boolean modal) {
        super(frame, title, modal);
    }

    public IngestProgressSnapShotDialog() {
        this(new JFrame(TITLE), TITLE, false);
    }

    /**
     * Shows the Ingest dialog.
     */
    public void display() {
        setLayout(new BorderLayout());
        Dimension screenDimension = Toolkit.getDefaultToolkit().getScreenSize();
        setSize(DIMENSIONS);
        int w = this.getSize().width;
        int h = this.getSize().height;
        setLocation((screenDimension.width - w) / 2, (screenDimension.height - h) / 2);
        add(new IngestProgressSnapShotPanel());
        pack();
        setResizable(false);
        setVisible(true);        
    }
}
