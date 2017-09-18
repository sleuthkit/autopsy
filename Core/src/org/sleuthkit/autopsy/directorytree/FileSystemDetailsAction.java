/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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
 *
 * @author wschaefer
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
