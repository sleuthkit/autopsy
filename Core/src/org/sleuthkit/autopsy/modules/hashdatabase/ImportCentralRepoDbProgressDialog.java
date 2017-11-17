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
import java.awt.Cursor;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.logging.Level;
import javax.swing.JFrame;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.Executors;
import javax.swing.JOptionPane;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttribute;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamGlobalFileInstance;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 *
 */
class ImportCentralRepoDbProgressDialog extends javax.swing.JDialog implements PropertyChangeListener{

    private CentralRepoImportWorker worker;   
    
    /**
     * 
     * @param hashSetName
     * @param version
     * @param orgId
     * @param searchDuringIngest
     * @param sendIngestMessages
     * @param knownFilesType
     * @param importFile 
     */
    @NbBundle.Messages({"ImportCentralRepoDbProgressDialog.title.text=Central Repository Import Progress",
    })
    ImportCentralRepoDbProgressDialog() {
        super((JFrame) WindowManager.getDefault().getMainWindow(),
                Bundle.ImportCentralRepoDbProgressDialog_title_text(),
                true);
               
        initComponents();   
        customizeComponents();
    }
    
    private void customizeComponents(){
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        bnOk.setEnabled(false);
    }
    
    @NbBundle.Messages({"ImportCentralRepoDbProgressDialog.unknownFormat.message=Hash set to import is an unknown format"})
    void importFile(String hashSetName, String version, int orgId,
            boolean searchDuringIngest, boolean sendIngestMessages, HashDbManager.HashDb.KnownFilesType knownFilesType,
            boolean readOnly, String importFileName){          
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));       
        
        File importFile = new File(importFileName);
        if(importFileName.toLowerCase().endsWith(".idx")){
            worker = new ImportIDXWorker(hashSetName, version, orgId, searchDuringIngest, sendIngestMessages, 
                    knownFilesType, readOnly, importFile);
        } else if(importFileName.toLowerCase().endsWith(".hash")){
            worker = new ImportEncaseWorker(hashSetName, version, orgId, searchDuringIngest, sendIngestMessages, 
                    knownFilesType, readOnly, importFile);
        } else {
            // We've gotten here with a format that can't be processed
            JOptionPane.showMessageDialog(null, Bundle.ImportCentralRepoDbProgressDialog_unknownFormat_message());
            return;
        }
        worker.addPropertyChangeListener(this);
        worker.execute();
        
        setLocationRelativeTo((JFrame) WindowManager.getDefault().getMainWindow()); 
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        this.setVisible(true);
    }
    
    HashDbManager.HashDatabase getDatabase(){
        if(worker != null){
            return worker.getDatabase();
        }
        return null;
    }
    
    @NbBundle.Messages({"ImportCentralRepoDbProgressDialog.linesProcessed= hashes processed"})
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        
        if("progress".equals(evt.getPropertyName())){
            progressBar.setValue(worker.getProgressPercentage());
            lbProgress.setText(getProgressString());
        } else if ("state".equals(evt.getPropertyName())
                && (SwingWorker.StateValue.DONE.equals(evt.getNewValue()))) {
            // Disable cancel and enable ok
            bnCancel.setEnabled(false);
            bnOk.setEnabled(true);
            
            if(worker.getError().isEmpty()){
                progressBar.setValue(progressBar.getMaximum());
                lbProgress.setText(getProgressString());
            } else {
                progressBar.setValue(0);
                lbProgress.setForeground(Color.red);
                lbProgress.setText(worker.getError());
            }
        }
    }
    
    private String getProgressString(){
        return worker.getLinesProcessed() + Bundle.ImportCentralRepoDbProgressDialog_linesProcessed();
    }
    
    abstract class CentralRepoImportWorker extends SwingWorker<Void, Void>{
        final int HASH_IMPORT_THRESHOLD = 10000;
        final String hashSetName;
        final String version;
        final int orgId;
        final boolean searchDuringIngest;
        final boolean sendIngestMessages;
        final HashDbManager.HashDb.KnownFilesType knownFilesType;
        final boolean readOnly;
        final File importFile;
        long totalHashes = 1;
        int referenceSetID = -1;
        HashDbManager.CentralRepoHashDb newHashDb = null;
        final AtomicLong numLines = new AtomicLong();
        String errorString = "";
        
        CentralRepoImportWorker(String hashSetName, String version, int orgId,
            boolean searchDuringIngest, boolean sendIngestMessages, HashDbManager.HashDb.KnownFilesType knownFilesType,
            boolean readOnly, File importFile){
            
            this.hashSetName = hashSetName;
            this.version = version;
            this.orgId = orgId;
            this.searchDuringIngest = searchDuringIngest;
            this.sendIngestMessages = sendIngestMessages;
            this.knownFilesType = knownFilesType;
            this.readOnly = readOnly;
            this.importFile = importFile;
            this.numLines.set(0);
        }
        
        HashDbManager.HashDatabase getDatabase(){
            return newHashDb;
        }
        
        long getLinesProcessed(){
            return numLines.get();
        }
        
        int getProgressPercentage(){
            return this.getProgress();
        }
        
        String getError(){
            return errorString;
        }
        
        /**
         * Should be called in the constructor to set the max number of hashes.
         * The value can be updated later after parsing the import file.
         */
        abstract void setEstimatedTotalHashes();
        
        void deleteIncompleteSet(){
            if(referenceSetID >= 0){
                
                // This can be slow on large reference sets
                Executors.newSingleThreadExecutor().execute(new Runnable() {
                    @Override 
                    public void run() {
                        try{
                            EamDb.getInstance().deleteReferenceSet(referenceSetID);
                        } catch (EamDbException ex2){
                            Logger.getLogger(ImportCentralRepoDbProgressDialog.class.getName()).log(Level.SEVERE, "Error deleting incomplete hash set from central repository", ex2);
                        }
                    }
                });
            }
        }
        
        @NbBundle.Messages({"ImportCentralRepoDbProgressDialog.importHashsetError=Error importing hash set",
                            "ImportCentralRepoDbProgressDialog.addDbError.message=Error adding new hash set"})
        @Override
        protected void done() {
            
            if(isCancelled()){
                // If the user hit cancel, delete this incomplete hash set from the central repo
                deleteIncompleteSet();
                return;
            }
            
            try {
                get();
                try{
                    newHashDb = HashDbManager.getInstance().addExistingCentralRepoHashSet(hashSetName, version, 
                            referenceSetID, 
                            searchDuringIngest, sendIngestMessages, knownFilesType, readOnly);
                } catch (TskCoreException ex){
                    JOptionPane.showMessageDialog(null, Bundle.ImportCentralRepoDbProgressDialog_addDbError_message());
                    Logger.getLogger(ImportCentralRepoDbProgressDialog.class.getName()).log(Level.SEVERE, "Error adding imported hash set", ex);
                }
            } catch (Exception ex) {
                // Delete this incomplete hash set from the central repo
                deleteIncompleteSet();
                errorString = Bundle.ImportCentralRepoDbProgressDialog_importHashsetError();
            }
        }       
                
    }
    
    class ImportEncaseWorker extends CentralRepoImportWorker{
        
        ImportEncaseWorker(String hashSetName, String version, int orgId,
                boolean searchDuringIngest, boolean sendIngestMessages, HashDbManager.HashDb.KnownFilesType knownFilesType,
                boolean readOnly, File importFile){
            super(hashSetName, version, orgId, searchDuringIngest, sendIngestMessages, knownFilesType, readOnly, importFile);
            
            setEstimatedTotalHashes();
        }
        
        
        /**
         * Encase files have a 0x480 byte header, then each hash takes 18 bytes
         * @return Approximate number of hashes in the file
         */
        @Override
        final void setEstimatedTotalHashes(){
            long fileSize = importFile.length();
            if(fileSize < 0x492){
                totalHashes =  1; // There's room for at most one hash
            }
            totalHashes = (fileSize - 0x492) / 18;
        }
        
        @Override
        protected Void doInBackground() throws Exception {

            EncaseHashSetParser encaseParser = new EncaseHashSetParser(importFile.getAbsolutePath());
            totalHashes = encaseParser.getExpectedHashes();
            
            TskData.FileKnown knownStatus;
            if (knownFilesType.equals(HashDbManager.HashDb.KnownFilesType.KNOWN)) {
                knownStatus = TskData.FileKnown.KNOWN;
            } else {
                knownStatus = TskData.FileKnown.BAD;
            }
            
            // Create an empty hashset in the central repository
            referenceSetID = EamDb.getInstance().newReferenceSet(orgId, hashSetName, version, knownStatus, readOnly);

            EamDb dbManager = EamDb.getInstance();
            CorrelationAttribute.Type contentType = dbManager.getCorrelationTypeById(CorrelationAttribute.FILES_TYPE_ID); // get "FILES" type

            Set<EamGlobalFileInstance> globalInstances = new HashSet<>();

            while (! encaseParser.doneReading()) {
                if(isCancelled()){
                    return null;
                }

                String newHash = encaseParser.getNextHash();

                if(newHash != null){
                    EamGlobalFileInstance eamGlobalFileInstance = new EamGlobalFileInstance(
                            referenceSetID, 
                            newHash, 
                            knownStatus, 
                            "");

                    globalInstances.add(eamGlobalFileInstance);
                    numLines.incrementAndGet();

                    if(numLines.get() % HASH_IMPORT_THRESHOLD == 0){
                        dbManager.bulkInsertReferenceTypeEntries(globalInstances, contentType);
                        globalInstances.clear();

                        int progress = (int)(numLines.get() * 100 / totalHashes);
                        if(progress < 100){
                            this.setProgress(progress);
                        } else {
                            this.setProgress(99);
                        }
                    }
                }
            }

            dbManager.bulkInsertReferenceTypeEntries(globalInstances, contentType);
            this.setProgress(100);
            return null;
        }
    }
    
    
    class ImportIDXWorker extends CentralRepoImportWorker{
        
        ImportIDXWorker(String hashSetName, String version, int orgId,
                boolean searchDuringIngest, boolean sendIngestMessages, HashDbManager.HashDb.KnownFilesType knownFilesType,
                boolean readOnly, File importFile){
            super(hashSetName, version, orgId, searchDuringIngest, sendIngestMessages, knownFilesType, readOnly, importFile);
            
            setEstimatedTotalHashes();
        }
        
        /**
         * Doing an actual count of the number of lines in a large idx file (such
         * as the nsrl) is slow, so just get something in the general area for the
         * progress bar.
         * @return Approximate number of hashes in the file
         */
        @Override
        final void setEstimatedTotalHashes(){
            long fileSize = importFile.length();
            totalHashes = fileSize / 0x33 + 1; // IDX file lines are generally 0x33 bytes long, and we don't want this to be zero
        }
        
        @Override
        protected Void doInBackground() throws Exception {

            TskData.FileKnown knownStatus;
            if (knownFilesType.equals(HashDbManager.HashDb.KnownFilesType.KNOWN)) {
                knownStatus = TskData.FileKnown.KNOWN;
            } else {
                knownStatus = TskData.FileKnown.BAD;
            }
            
            // Create an empty hashset in the central repository
            referenceSetID = EamDb.getInstance().newReferenceSet(orgId, hashSetName, version, knownStatus, readOnly);

            EamDb dbManager = EamDb.getInstance();
            CorrelationAttribute.Type contentType = dbManager.getCorrelationTypeById(CorrelationAttribute.FILES_TYPE_ID); // get "FILES" type
            BufferedReader reader = new BufferedReader(new FileReader(importFile));
            String line;
            Set<EamGlobalFileInstance> globalInstances = new HashSet<>();

            while ((line = reader.readLine()) != null) {
                if(isCancelled()){
                    return null;
                }

                String[] parts = line.split("\\|");

                // Header lines start with a 41 character dummy hash, 1 character longer than a SHA-1 hash
                if (parts.length != 2 || parts[0].length() == 41) {
                    continue;
                }

                EamGlobalFileInstance eamGlobalFileInstance = new EamGlobalFileInstance(
                        referenceSetID, 
                        parts[0].toLowerCase(), 
                        knownStatus, 
                        "");

                globalInstances.add(eamGlobalFileInstance);
                numLines.incrementAndGet();

                if(numLines.get() % HASH_IMPORT_THRESHOLD == 0){
                    dbManager.bulkInsertReferenceTypeEntries(globalInstances, contentType);
                    globalInstances.clear();

                    int progress = (int)(numLines.get() * 100 / totalHashes);
                    if(progress < 100){
                        this.setProgress(progress);
                    } else {
                        this.setProgress(99);
                    }
                }
            }

            dbManager.bulkInsertReferenceTypeEntries(globalInstances, contentType);
            this.setProgress(100);

            return null;
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
                        .addGap(0, 172, Short.MAX_VALUE))))
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(bnOk, javax.swing.GroupLayout.PREFERRED_SIZE, 65, javax.swing.GroupLayout.PREFERRED_SIZE)
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
                .addComponent(progressBar, javax.swing.GroupLayout.PREFERRED_SIZE, 24, javax.swing.GroupLayout.PREFERRED_SIZE)
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
