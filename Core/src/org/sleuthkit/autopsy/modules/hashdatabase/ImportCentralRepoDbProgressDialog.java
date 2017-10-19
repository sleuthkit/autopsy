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

import java.awt.Cursor;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javax.swing.JFrame;
import javax.swing.JProgressBar;
import javax.swing.SwingWorker;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttribute;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamGlobalFileInstance;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 *
 */
class ImportCentralRepoDbProgressDialog extends javax.swing.JDialog implements PropertyChangeListener{


    long totalHashes;
    private SwingWorker<Void, Void> worker;
   
    
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
    }
    
    void importFile(String hashSetName, String version, int orgId,
            boolean searchDuringIngest, boolean sendIngestMessages, HashDbManager.HashDb.KnownFilesType knownFilesType,
            String importFileName){          
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));       
        
        File importFile = new File(importFileName);
        worker = new ImportIDXWorker(hashSetName, version, orgId, searchDuringIngest, sendIngestMessages, 
                knownFilesType, importFile, totalHashes);
        totalHashes = ((ImportIDXWorker)worker).getEstimatedTotalHashes();
        worker.addPropertyChangeListener(this);
        worker.execute();
        
        setLocationRelativeTo((JFrame) WindowManager.getDefault().getMainWindow()); 
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        this.setVisible(true);
    }
    
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        System.out.println("### Evt type: " + evt.getPropertyName());
        System.out.println("       newValue: " + evt.getNewValue().toString());
        System.out.println("### Setting progress to " + worker.getProgress());
        
        if("progress".equals(evt.getPropertyName())){
            progressBar.setValue(worker.getProgress());
            String mes = "Count: " + worker.getProgress();
            lbProgress.setText(mes);
        } else if ("state".equals(evt.getPropertyName())
                && (SwingWorker.StateValue.DONE.equals(evt.getNewValue()))) {
            // Disable cancel and enable ok
            bnCancel.setEnabled(false);
            bnOk.setEnabled(true);
            
            progressBar.setValue(progressBar.getMaximum());
            String mes = "Count: " + worker.getProgress();
            lbProgress.setText(mes);
        }
    }
    
    class ImportIDXWorker extends SwingWorker<Void, Void>{
        
        private final int HASH_IMPORT_THRESHOLD = 10000;
        private final String hashSetName;
        private final String version;
        private final int orgId;
        private final boolean searchDuringIngest;
        private final boolean sendIngestMessages;
        private final HashDbManager.HashDb.KnownFilesType knownFilesType;
        private final File importFile;
        private final long totalLines;
        private int crIndex = -1;
        
        ImportIDXWorker(String hashSetName, String version, int orgId,
            boolean searchDuringIngest, boolean sendIngestMessages, HashDbManager.HashDb.KnownFilesType knownFilesType,
            File importFile, long totalLines){
            
            this.hashSetName = hashSetName;
            this.version = version;
            this.orgId = orgId;
            this.searchDuringIngest = searchDuringIngest;
            this.sendIngestMessages = sendIngestMessages;
            this.knownFilesType = knownFilesType;
            this.importFile = importFile;
            this.totalLines = totalLines;
        }
        
        /**
         * Doing an actual count of the number of lines in a large idx file (such
         * as the nsrl) is slow, so just get something in the general area for the
         * progress bar.
         * @return Approximate number of hashes in the file
         */
        long getEstimatedTotalHashes(){
            long fileSize = importFile.length();
            return (fileSize / 0x33); // IDX file lines are generally 0x33 bytes long
        }
        
        @Override
        protected Void doInBackground() throws Exception {

            try{
                // Create an empty hashset in the central repository
                crIndex = EamDb.getInstance().newReferenceSet(orgId, hashSetName, version);
            } catch (EamDbException ex){
                throw new TskCoreException(ex.getLocalizedMessage());
            }

            try{

                TskData.FileKnown knownStatus;
                if (knownFilesType.equals(HashDbManager.HashDb.KnownFilesType.KNOWN)) {
                    knownStatus = TskData.FileKnown.KNOWN;
                } else {
                    knownStatus = TskData.FileKnown.BAD;
                }

                EamDb dbManager = EamDb.getInstance();
                CorrelationAttribute.Type contentType = dbManager.getCorrelationTypeById(CorrelationAttribute.FILES_TYPE_ID); // get "FILES" type
                BufferedReader reader = new BufferedReader(new FileReader(importFile));
                String line;
                Set<EamGlobalFileInstance> globalInstances = new HashSet<>();

                long numLines = 0;
                while ((line = reader.readLine()) != null) {
                    
                    String[] parts = line.split("\\|");

                    // Header lines start with a 41 character dummy hash, 1 character longer than a SHA-1 hash
                    if (parts.length != 2 || parts[0].length() == 41) {
                        continue;
                    }

                    EamGlobalFileInstance eamGlobalFileInstance = new EamGlobalFileInstance(
                            crIndex, 
                            parts[0].toLowerCase(), 
                            knownStatus, 
                            "");

                    globalInstances.add(eamGlobalFileInstance);
                    numLines++;

                    if(numLines % HASH_IMPORT_THRESHOLD == 0){
                        dbManager.bulkInsertReferenceTypeEntries(globalInstances, contentType);
                        globalInstances.clear();
                        
                        int progress = (int)(numLines * 100 / totalLines);
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
            catch (Exception ex){
                // TODO
                ex.printStackTrace();
                throw new TskCoreException(ex.getLocalizedMessage());
            }


            // Let any external listeners know that there's a new set   
            //try {
            //    changeSupport.firePropertyChange(HashDbManager.SetEvt.DB_ADDED.toString(), null, hashSetName);
            //} catch (Exception e) {
            //    logger.log(Level.SEVERE, "HashDbManager listener threw exception", e); //NON-NLS
            //    MessageNotifyUtil.Notify.show(
            //            NbBundle.getMessage(this.getClass(), "HashDbManager.moduleErr"),
            //            NbBundle.getMessage(this.getClass(), "HashDbManager.moduleErrorListeningToUpdatesMsg"),
             //           MessageNotifyUtil.MessageType.ERROR);
            //}
            //return hashDb;
        }
        
        @Override
        protected void done() {
            try {
                get();
                try{
                    System.out.println("### Finished - adding hashDb object");
                    HashDbManager.CentralRepoHashDb hashDb = HashDbManager.getInstance().addExistingCentralRepoHashSet(hashSetName, version, crIndex, 
                            searchDuringIngest, sendIngestMessages, knownFilesType);
                } catch (TskCoreException ex){
                    System.out.println("\n### Error!");
                }
            } catch (InterruptedException | ExecutionException ex) {
                
                System.out.println("\n### Interrupted!");
                
                // If the user hit cancel, delete this incomplete hash set from the central repo
                if(crIndex >= 0){
                    try{
                        EamDb.getInstance().deleteReferenceSet(crIndex);
                    } catch (EamDbException ex2){
                        
                    }
                }
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
        jButton1 = new javax.swing.JButton();
        bnOk = new javax.swing.JButton();
        bnCancel = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        org.openide.awt.Mnemonics.setLocalizedText(lbProgress, org.openide.util.NbBundle.getMessage(ImportCentralRepoDbProgressDialog.class, "ImportCentralRepoDbProgressDialog.lbProgress.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jButton1, org.openide.util.NbBundle.getMessage(ImportCentralRepoDbProgressDialog.class, "ImportCentralRepoDbProgressDialog.jButton1.text")); // NOI18N
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton1ActionPerformed(evt);
            }
        });

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

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(27, 27, 27)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jButton1)
                            .addComponent(progressBar, javax.swing.GroupLayout.PREFERRED_SIZE, 346, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addContainerGap(27, Short.MAX_VALUE))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(lbProgress)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(bnOk)
                        .addGap(18, 18, 18)
                        .addComponent(bnCancel)
                        .addGap(24, 24, 24))))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(112, 112, 112)
                        .addComponent(progressBar, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(lbProgress)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 89, Short.MAX_VALUE))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(bnOk)
                            .addComponent(bnCancel))
                        .addGap(72, 72, 72)))
                .addComponent(jButton1)
                .addGap(42, 42, 42))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
        this.dispose();
    }//GEN-LAST:event_jButton1ActionPerformed

    private void bnCancelActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnCancelActionPerformed
        this.worker.cancel(true);
    }//GEN-LAST:event_bnCancelActionPerformed

    private void bnOkActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnOkActionPerformed
        this.dispose();
    }//GEN-LAST:event_bnOkActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton bnCancel;
    private javax.swing.JButton bnOk;
    private javax.swing.JButton jButton1;
    private javax.swing.JLabel lbProgress;
    private javax.swing.JProgressBar progressBar;
    // End of variables declaration//GEN-END:variables
}
