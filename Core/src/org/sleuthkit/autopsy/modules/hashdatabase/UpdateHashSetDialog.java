/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.modules.hashdatabase;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.io.File;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingWorker;
import org.openide.util.Lookup;

/**
 *
 * @author root
 */
public class UpdateHashSetDialog extends javax.swing.JDialog {

    List<HashSetUpdateOptions> hashSetOptions = new LinkedList<>();
    private String dataDirectoryPath;

    /**
     * Creates new form UpdateHashSetDialog
     */
    public UpdateHashSetDialog(java.awt.Frame parent) {
        super(parent, false);
        initComponents();
        initHashSetGrid();
        this.dataDirectoryPath = this.dataDirectoryTextField.getText();
    }

    private void initHashSetGrid() {
        Collection<? extends HashSetPreparer> allPreparer = Lookup.getDefault().lookupAll(HashSetPreparer.class);
        this.providerList.setLayout(new GridBagLayout() {
            //@Override
            public Dimension getMinimumSize() {
                return new Dimension(400, 300);
            }

            //@Override
            public Dimension getPreferredSize() {
                return new Dimension(800, 600);
            }

            //@Override
            public Dimension getMaximumSize() {
                return new Dimension(800, 600);
            }
        });

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;

        initGrid(this.providerList, allPreparer.size(), 5);
        for (HashSetPreparer hashSetPreparer : allPreparer) {

            HashSetUpdateOptions option = new HashSetUpdateOptions(hashSetPreparer);
            this.providerList.add(option.getUpdate(), c);
            c.gridx++;
            this.providerList.add(option.getDownloadFullHashSetCheckBox(), c);
            c.gridx++;
            this.providerList.add(option.getNameLabel(), c);
            c.gridx++;
            this.providerList.add(option.getProgressbar(), c);
            c.gridx++;
            this.providerList.add(option.getSatusLabel(), c);
            hashSetOptions.add(option);
            c.gridy++;
        }
    }

    private void initGrid(JPanel panel, int amoutLines, int amountColums) {
        panel.setLayout(new GridLayout(amoutLines + 1, amountColums));

        panel.add(createLabel("update hashset"));
        panel.add(createLabel("full download"));
        panel.add(createLabel("Provider"));
        panel.add(createLabel("Progress"));
        panel.add(createLabel("state"));

    }
    private static final int MAGIC_NUMBER_1 = 9999;
    private static final int MAGIC_NUMBER_2 = 50;

    public JLabel createLabel(String message) {
        JLabel label = new JLabel(message);
        label.setVisible(true);
        label.setSize(MAGIC_NUMBER_1, MAGIC_NUMBER_2);
        return label;
    }

    private boolean isValidDirectory(String dataDirectoryPath) {
        File directory = new File(dataDirectoryPath);
        return directory.exists() && directory.isDirectory();
    }

    class HashSetUpdateOptions {

        private static final int MAGIC_NUMBER_1 = 9999;
        private static final int MAGIC_NUMBER_2 = 50;

        private JLabel nameLabel;
        private JCheckBox downloadDeltaHashSetCheckBox;
        private JCheckBox downloadFullHashSetCheckBox;
        private JProgressBar progressbar;
        private JLabel satusLabel;
        private HashSetPreparer hashSetPreparer;

        public HashSetUpdateOptions(HashSetPreparer hashSetPreparer) {
            this.nameLabel = createLabel(hashSetPreparer.getName());
            this.downloadDeltaHashSetCheckBox = createCheckBox(true);
            this.downloadFullHashSetCheckBox = createCheckBox(false);
            this.satusLabel = createLabel("initialized");
            this.progressbar = new JProgressBar(0, 100);
            this.hashSetPreparer = hashSetPreparer;
        }

        public JLabel createLabel(String message) {
            JLabel label = new JLabel(message);
            label.setVisible(true);
            label.setSize(MAGIC_NUMBER_1, MAGIC_NUMBER_2);
            return label;
        }

        private JCheckBox createCheckBox(boolean marked) {
            JCheckBox checkBox = new JCheckBox();
            checkBox.setSelected(marked);
            return checkBox;
        }

        /**
         * @return the downloadDeltaHashSetCheckBox
         */
        public JCheckBox getUpdate() {
            return downloadDeltaHashSetCheckBox;
        }

        /**
         * @return the isDownloadFullHashSetEnabled
         */
        public JCheckBox getDownloadFullHashSetCheckBox() {
            return downloadFullHashSetCheckBox;
        }

        public JLabel getNameLabel() {
            return nameLabel;
        }

        public boolean isDownloadDeltaHashSetEnabled() {
            return downloadDeltaHashSetCheckBox.isSelected();
        }

        public boolean isDownloadFullHashSetEnabled() {
            return downloadFullHashSetCheckBox.isSelected();
        }

        public JProgressBar getProgressbar() {
            return progressbar;
        }

        public JLabel getSatusLabel() {
            return satusLabel;
        }

        public HashSetPreparer getHashSetPreparer() {
            return hashSetPreparer;
        }

    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        instructionLabel = new javax.swing.JLabel();
        providerList = new javax.swing.JPanel();
        startButton = new javax.swing.JButton();
        dataDirectoryTextField = new javax.swing.JTextField();
        dataDirectoryChooser = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        org.openide.awt.Mnemonics.setLocalizedText(instructionLabel, org.openide.util.NbBundle.getMessage(UpdateHashSetDialog.class, "UpdateHashSetDialog.instructionLabel.text")); // NOI18N

        javax.swing.GroupLayout providerListLayout = new javax.swing.GroupLayout(providerList);
        providerList.setLayout(providerListLayout);
        providerListLayout.setHorizontalGroup(
            providerListLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );
        providerListLayout.setVerticalGroup(
            providerListLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 139, Short.MAX_VALUE)
        );

        org.openide.awt.Mnemonics.setLocalizedText(startButton, org.openide.util.NbBundle.getMessage(UpdateHashSetDialog.class, "UpdateHashSetDialog.startButton.text")); // NOI18N
        startButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                startButtonActionPerformed(evt);
            }
        });

        dataDirectoryTextField.setText(org.openide.util.NbBundle.getMessage(UpdateHashSetDialog.class, "UpdateHashSetDialog.dataDirectoryTextField.text")); // NOI18N
        dataDirectoryTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dataDirectoryTextFieldActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(dataDirectoryChooser, org.openide.util.NbBundle.getMessage(UpdateHashSetDialog.class, "UpdateHashSetDialog.dataDirectoryChooser.text")); // NOI18N
        dataDirectoryChooser.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dataDirectoryChooserActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(20, 20, 20)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(dataDirectoryTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 304, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(dataDirectoryChooser))
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                        .addComponent(startButton)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(instructionLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 466, Short.MAX_VALUE)
                            .addComponent(providerList, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
                .addContainerGap(147, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(21, 21, 21)
                .addComponent(instructionLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 39, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(dataDirectoryTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(dataDirectoryChooser))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(providerList, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(startButton)
                .addContainerGap(27, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void startButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_startButtonActionPerformed
        if (isValidDirectory(this.dataDirectoryPath)) {
            hashSetOptions.stream().forEach((hashSetOption) -> {
                new HashSetUpdateWorker(hashSetOption, this.dataDirectoryPath).execute();
            });
        }
    }//GEN-LAST:event_startButtonActionPerformed

    private void dataDirectoryChooserActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dataDirectoryChooserActionPerformed
        final JFileChooser fileChooser = new JFileChooser();
        //int returnVal = fc.showOpenDialog(aComponent);
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.showOpenDialog(this);
        String path = fileChooser.getSelectedFile().getAbsolutePath();
        this.dataDirectoryTextField.setText(path);
        this.dataDirectoryPath = path;
    }//GEN-LAST:event_dataDirectoryChooserActionPerformed

    private void dataDirectoryTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dataDirectoryTextFieldActionPerformed
        this.dataDirectoryPath = this.dataDirectoryTextField.getText();
    }//GEN-LAST:event_dataDirectoryTextFieldActionPerformed

    class HashSetUpdateWorker extends SwingWorker<Object, String> {

        private final HashSetUpdateOptions options;
        private HashSetPreparer hashSetUpdater;

        public HashSetUpdateWorker(HashSetUpdateOptions hashSetUpdateOption, String Location) {
            this.options = hashSetUpdateOption;
            this.hashSetUpdater = hashSetUpdateOption.getHashSetPreparer().createInstance(hashSetUpdateOption.getProgressbar(), Location);
        }

        @Override
        protected Object doInBackground() throws Exception {
            if (!options.isDownloadFullHashSetEnabled()) {
                downloadFullHashSet();
            } else {
                downloadDeltaHashSet();
            }
            return null;
        }

        @Override
        protected void process(List<String> chunks) {
            options.getSatusLabel().setText(chunks.get(chunks.size() - 1));
        }

        private void downloadDeltaHashSet() {
            try {
                publish("downloading");
                hashSetUpdater.downloadDeltaHashSet();
                publish("extracting");
                hashSetUpdater.extract();
                publish("add HashSet to Autopsy DB");
                hashSetUpdater.addHashSetToDatabase();
                publish("indexing");
                options.progressbar.setIndeterminate(true);
                hashSetUpdater.index();
                publish("finished");
                options.progressbar.setVisible(false);
            } catch (HashSetUpdateException ex) {
                publish(ex.toString());
            }

        }

        private void downloadFullHashSet() {
            try {
                publish("downloading");
                hashSetUpdater.downloadFullHashSet();
                publish("extracting");
                hashSetUpdater.extract();
                publish("add HashSet to Autopsy DB");
                hashSetUpdater.addHashSetToDatabase();
                publish("indexing");
                options.progressbar.setIndeterminate(true);
                hashSetUpdater.index();
                publish("finished");
                options.progressbar.setVisible(false);
            } catch (HashSetUpdateException ex) {
                publish(ex.toString());
            }
        }
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException ex) {
            java.util.logging.Logger.getLogger(UpdateHashSetDialog.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            java.util.logging.Logger.getLogger(UpdateHashSetDialog.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            java.util.logging.Logger.getLogger(UpdateHashSetDialog.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(UpdateHashSetDialog.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>

        /* Create and display the dialog */
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                UpdateHashSetDialog dialog = new UpdateHashSetDialog(new javax.swing.JFrame());
                dialog.addWindowListener(new java.awt.event.WindowAdapter() {
                    @Override
                    public void windowClosing(java.awt.event.WindowEvent e) {
                        System.exit(0);
                    }
                });
                dialog.setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton dataDirectoryChooser;
    private javax.swing.JTextField dataDirectoryTextField;
    private javax.swing.JLabel instructionLabel;
    private javax.swing.JPanel providerList;
    private javax.swing.JButton startButton;
    // End of variables declaration//GEN-END:variables

}
