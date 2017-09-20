/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017 Basis Technology Corp.
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

import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.AbstractAction;
import javax.swing.JDialog;
import javax.swing.JFrame;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.FsContent;

/**
 * Action which opens a dialog containing the FileSystemDetailsPanel.
 */
public class FileSystemDetailsAction extends AbstractAction {

    final Content fsContent;

    @NbBundle.Messages({"FileSystemDetailsAction.title.text=File System Details"})
    public FileSystemDetailsAction(Content content) {
        super(Bundle.FileSystemDetailsAction_title_text());
        fsContent = content;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (fsContent instanceof FsContent) {
            FileSystemDetailsDialog fsDetailsDialog = new FileSystemDetailsDialog();
            fsDetailsDialog.display((FsContent) fsContent);
        }
    }

    private final class FileSystemDetailsDialog extends JDialog implements ActionListener {

        private FileSystemDetailsDialog() {
            super((JFrame) WindowManager.getDefault().getMainWindow(),
                    Bundle.FileSystemDetailsAction_title_text(),
                    false);
        }

        private void display(FsContent content) {
            FileSystemDetailsPanel fsPanel = new FileSystemDetailsPanel(content);
            fsPanel.setOKButtonActionListener(this);
            setContentPane(fsPanel);
            pack();
            Dimension screenDimension = Toolkit.getDefaultToolkit().getScreenSize();
            setLocation((screenDimension.width - getSize().width) / 2, (screenDimension.height - getSize().height) / 2);
            setVisible(true);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            dispose();
        }

    }

}
