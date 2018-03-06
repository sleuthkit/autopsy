/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.hashdatabase;

import java.awt.Color;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import javax.swing.JFrame;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Executors;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttribute;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamGlobalFileInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamGlobalSet;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Imports a hash set into the central repository and updates a progress dialog
 */
class ImportCentralRepoDbProgressDialog extends javax.swing.JDialog implements PropertyChangeListener {

    private CentralRepoImportWorker worker;   // Swing worker that will import the file and send updates to the dialog

    @NbBundle.Messages({"ImportCentralRepoDbProgressDialog.title.text=Central Repository Import Progress",})
    ImportCentralRepoDbProgressDialog() {
        super((JFrame) WindowManager.getDefault().getMainWindow(),
                Bundle.ImportCentralRepoDbProgressDialog_title_text(),
                true);

        initComponents();
        customizeComponents();
    }

    private void customizeComponents() {
        // This is preventing the user from closing the dialog using the X
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        bnOk.setEnabled(false);
    }

    /**
     * Import the selected hash set into the central repository. Will bring up a
     * progress dialog while the import is in progress.
     *
     * @param hashSetName
     * @param version
     * @param orgId
     * @param searchDuringIngest
     * @param sendIngestMessages
     * @param knownFilesType
     * @param readOnly
     * @param importFileName
     */
    void importFile(String hashSetName, String version, int orgId,
            boolean searchDuringIngest, boolean sendIngestMessages, HashDbManager.HashDb.KnownFilesType knownFilesType,
            boolean readOnly, String importFileName) {

        worker = new CentralRepoImportWorker(hashSetName, version, orgId, searchDuringIngest, sendIngestMessages,
                knownFilesType, readOnly, importFileName);
        worker.addPropertyChangeListener(this);
        worker.execute();

        setLocationRelativeTo((JFrame) WindowManager.getDefault().getMainWindow());
        this.setVisible(true);
    }

    /**
     * Get the HashDb object for the newly imported data. Should be called after
     * importFile completes.
     *
     * @return The new HashDb object or null if the import failed/was canceled
     */
    HashDbManager.HashDb getDatabase() {
        if (worker != null) {
            return worker.getDatabase();
        }
        return null;
    }

    /**
     * Updates the dialog from events from the worker. The two events we handle
     * are progress updates and the done event.
     *
     * @param evt
     */
    @NbBundle.Messages({"ImportCentralRepoDbProgressDialog.errorParsingFile.message=Error parsing hash set file"})
    @Override
    public void propertyChange(PropertyChangeEvent evt) {

        if ("progress".equals(evt.getPropertyName())) {
            // The progress has been updated. Update the progress bar and text
            progressBar.setValue(worker.getProgress());
            lbProgress.setText(getProgressString());
        } else if ("state".equals(evt.getPropertyName())
                && (SwingWorker.StateValue.DONE.equals(evt.getNewValue()))) {

            // The worker is done processing            
            // Disable cancel button and enable ok
            bnCancel.setEnabled(false);
            bnOk.setEnabled(true);

            if (worker.getImportSuccess()) {
                // If the import succeeded, finish the progress bar and display the
                // total number of imported hashes
                progressBar.setValue(progressBar.getMaximum());
                lbProgress.setText(getProgressString());
            } else {
                // If there was an error, reset the progress bar and display an error message
                progressBar.setValue(0);
                lbProgress.setForeground(Color.red);
                lbProgress.setText(Bundle.ImportCentralRepoDbProgressDialog_errorParsingFile_message());
            }
        }
    }

    @NbBundle.Messages({"ImportCentralRepoDbProgressDialog.linesProcessed.message= hashes processed"})
    private String getProgressString() {
        return worker.getNumHashesProcessed() + Bundle.ImportCentralRepoDbProgressDialog_linesProcessed_message();
    }

    private class CentralRepoImportWorker extends SwingWorker<Void, Void> {

        private final int HASH_IMPORT_THRESHOLD = 10000;
        private final String hashSetName;
        private final String version;
        private final int orgId;
        private final boolean searchDuringIngest;
        private final boolean sendIngestMessages;
        private final HashDbManager.HashDb.KnownFilesType knownFilesType;
        private final boolean readOnly;
        private final String importFileName;
        private HashDbManager.CentralRepoHashSet newHashDb = null;
        private final AtomicInteger referenceSetID = new AtomicInteger();
        private final AtomicLong hashCount = new AtomicLong();
        private final AtomicBoolean importSuccess = new AtomicBoolean();

        CentralRepoImportWorker(String hashSetName, String version, int orgId,
                boolean searchDuringIngest, boolean sendIngestMessages, HashDbManager.HashDb.KnownFilesType knownFilesType,
                boolean readOnly, String importFileName) {

            this.hashSetName = hashSetName;
            this.version = version;
            this.orgId = orgId;
            this.searchDuringIngest = searchDuringIngest;
            this.sendIngestMessages = sendIngestMessages;
            this.knownFilesType = knownFilesType;
            this.readOnly = readOnly;
            this.importFileName = importFileName;
            this.hashCount.set(0);
            this.importSuccess.set(false);
            this.referenceSetID.set(-1);
        }

        /**
         * Get the newly created database
         *
         * @return the imported database. May be null if an error occurred or
         * the user canceled
         */
        synchronized HashDbManager.CentralRepoHashSet getDatabase() {
            return newHashDb;
        }

        /**
         * Get the number of hashes that have been read in so far
         *
         * @return current hash count
         */
        long getNumHashesProcessed() {
            return hashCount.get();
        }

        /**
         * Check if the import was successful or if there was an error.
         *
         * @return true if the import process completed without error, false
         * otherwise
         */
        boolean getImportSuccess() {
            return importSuccess.get();
        }

        @Override
        protected Void doInBackground() throws Exception {

            // Create the hash set parser
            HashSetParser hashSetParser;
            if (importFileName.toLowerCase().endsWith(".idx") || importFileName.toLowerCase().endsWith(".txt")) {
                hashSetParser = new IdxHashSetParser(importFileName);
            } else if (importFileName.toLowerCase().endsWith(".hash")) {
                hashSetParser = new EncaseHashSetParser(importFileName);
            } else if (importFileName.toLowerCase().endsWith(".kdb")) {
                hashSetParser = new KdbHashSetParser(importFileName);
            } else if (importFileName.toLowerCase().endsWith(".hsh")) {
                hashSetParser = new HashkeeperHashSetParser(importFileName);
            } else {
                // We've gotten here with a format that can't be processed
                throw new TskCoreException("Hash set to import is an unknown format : " + importFileName);
            }

            try {
                // Conver to the FileKnown enum used by EamGlobalSet
                TskData.FileKnown knownStatus;
                if (knownFilesType.equals(HashDbManager.HashDb.KnownFilesType.KNOWN)) {
                    knownStatus = TskData.FileKnown.KNOWN;
                } else {
                    knownStatus = TskData.FileKnown.BAD;
                }

                // Create an empty hashset in the central repository
                EamDb dbManager = EamDb.getInstance();
                referenceSetID.set(dbManager.newReferenceSet(new EamGlobalSet(orgId, hashSetName, version, knownStatus, 
                        readOnly, EamDb.getInstance().getCorrelationTypeById(CorrelationAttribute.FILES_TYPE_ID))));

                // Get the "FILES" content type. This is a database lookup so we
                // only want to do it once.
                CorrelationAttribute.Type contentType = dbManager.getCorrelationTypeById(CorrelationAttribute.FILES_TYPE_ID);

                // Holds the current batch of hashes that need to be written to the central repo
                Set<EamGlobalFileInstance> globalInstances = new HashSet<>();

                while (!hashSetParser.doneReading()) {
                    if (isCancelled()) {
                        return null;
                    }

                    String newHash = hashSetParser.getNextHash();

                    if (newHash != null) {
                        EamGlobalFileInstance eamGlobalFileInstance = new EamGlobalFileInstance(
                                referenceSetID.get(),
                                newHash,
                                knownStatus,
                                "");

                        globalInstances.add(eamGlobalFileInstance);

                        // If we've hit the threshold for writing the hashes, write them
                        // all to the central repo
                        if (hashCount.incrementAndGet() % HASH_IMPORT_THRESHOLD == 0) {
                            dbManager.bulkInsertReferenceTypeEntries(globalInstances, contentType);
                            globalInstances.clear();

                            int progress = (int) (hashCount.get() * 100 / hashSetParser.getExpectedHashCount());
                            if (progress < 100) {
                                this.setProgress(progress);
                            } else {
                                this.setProgress(99);
                            }
                        }
                    }
                }

                // Add any remaining hashes to the central repo
                dbManager.bulkInsertReferenceTypeEntries(globalInstances, contentType);
                this.setProgress(100);
                return null;
            } finally {
                hashSetParser.close();
            }
        }

        private void deleteIncompleteSet() {
            if (referenceSetID.get() >= 0) {

                // This can be slow on large reference sets
                Executors.newSingleThreadExecutor().execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            EamDb.getInstance().deleteReferenceSet(referenceSetID.get());
                        } catch (EamDbException ex2) {
                            Logger.getLogger(ImportCentralRepoDbProgressDialog.class.getName()).log(Level.SEVERE, "Error deleting incomplete hash set from central repository", ex2);
                        }
                    }
                });
            }
        }

        @Override
        synchronized protected void done() {

            if (isCancelled()) {
                // If the user hit cancel, delete this incomplete hash set from the central repo
                deleteIncompleteSet();
                return;
            }

            try {
                get();
                try {
                    newHashDb = HashDbManager.getInstance().addExistingCentralRepoHashSet(hashSetName, version,
                            referenceSetID.get(),
                            searchDuringIngest, sendIngestMessages, knownFilesType, readOnly);
                    importSuccess.set(true);
                } catch (TskCoreException ex) {
                    Logger.getLogger(ImportCentralRepoDbProgressDialog.class.getName()).log(Level.SEVERE, "Error adding imported hash set", ex);
                }
            } catch (Exception ex) {
                // Delete this incomplete hash set from the central repo
                deleteIncompleteSet();
                Logger.getLogger(ImportCentralRepoDbProgressDialog.class.getName()).log(Level.SEVERE, "Error importing hash set", ex);
            }
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

        progressBar = new javax.swing.JProgressBar();
        lbProgress = new javax.swing.JLabel();
        bnOk = new javax.swing.JButton();
        bnCancel = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        org.openide.awt.Mnemonics.setLocalizedText(lbProgress, org.openide.util.NbBundle.getMessage(ImportCentralRepoDbProgressDialog.class, "ImportCentralRepoDbProgressDialog.lbProgress.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(bnOk, org.openide.util.NbBundle.getMessage(ImportCentralRepoDbProgressDialog.class, "ImportCentralRepoDbProgressDialog.bnOk.text")); // NOI18N
        bnOk.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnOkActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(bnCancel, org.openide.util.NbBundle.getMessage(ImportCentralRepoDbProgressDialog.class, "ImportCentralRepoDbProgressDialog.bnCancel.text")); // NOI18N
        bnCancel.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnCancelActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(jLabel1, org.openide.util.NbBundle.getMessage(ImportCentralRepoDbProgressDialog.class, "ImportCentralRepoDbProgressDialog.jLabel1.text")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(progressBar, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addContainerGap())
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel1)
                            .addComponent(lbProgress))
                        .addGap(0, 0, Short.MAX_VALUE))))
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(bnOk)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(bnCancel)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(lbProgress)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(progressBar, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(bnCancel)
                    .addComponent(bnOk))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void bnCancelActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnCancelActionPerformed
        this.worker.cancel(true);
        this.dispose();
    }//GEN-LAST:event_bnCancelActionPerformed

    private void bnOkActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnOkActionPerformed
        this.dispose();
    }//GEN-LAST:event_bnOkActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton bnCancel;
    private javax.swing.JButton bnOk;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel lbProgress;
    private javax.swing.JProgressBar progressBar;
    // End of variables declaration//GEN-END:variables
}
