/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.directorytree;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.JDialog;
import javax.swing.JFrame;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Volume;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Action which opens a dialog containing the FileSystemDetailsPanel.
 */
final public class FileSystemDetailsAction extends AbstractAction {
    private static final Logger logger = Logger.getLogger(FileSystemDetailsPanel.class.getName());
    final Volume fsContent;

    @NbBundle.Messages({"FileSystemDetailsAction.title.text=File System Details"})
    public FileSystemDetailsAction(Volume content) {
        super(Bundle.FileSystemDetailsAction_title_text());
        enabled = false;
        fsContent = content;
        try {
            if (!fsContent.getFileSystems().isEmpty()) {
                enabled = true;
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Unable to create FileSystemDetailsAction", ex);
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        FileSystemDetailsDialog fsDetailsDialog = new FileSystemDetailsDialog();
        fsDetailsDialog.display(fsContent);

    }

    private final class FileSystemDetailsDialog extends JDialog implements ActionListener {

        private static final long serialVersionUID = 1L;

        private FileSystemDetailsDialog() {
            super((JFrame) WindowManager.getDefault().getMainWindow(),
                    Bundle.FileSystemDetailsAction_title_text(),
                    false);
        }

        private void display(Volume content) {
            FileSystemDetailsPanel fsPanel = new FileSystemDetailsPanel(content);
            fsPanel.setOKButtonActionListener(this);
            setContentPane(fsPanel);
            pack();
            setLocationRelativeTo(WindowManager.getDefault().getMainWindow());
            setVisible(true);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            dispose();
        }

    }

}
