/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.md5search;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JFrame;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.openide.explorer.ExplorerManager;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.sleuthkit.autopsy.commonfilesearch.CommonAttributeSearchResults;
import org.sleuthkit.autopsy.commonfilesearch.CommonAttributesSearchResultsViewerTable;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataResultViewer;
import org.sleuthkit.autopsy.corecomponents.DataResultTopComponent;
import org.sleuthkit.autopsy.corecomponents.DataResultViewerTable;
import org.sleuthkit.autopsy.directorytree.DataResultFilterNode;

/**
 *
 * @author dgrove
 */
public class Md5SearchDialog extends javax.swing.JDialog {
    private static final String FILES_CORRELATION_TYPE = "Files";
    private final List<CorrelationAttributeInstance.Type> correlationTypes;
    private final Pattern md5Pattern;

    /**
     * Creates new form Md5SearchDialog
     */
    @NbBundle.Messages({"Md5SearchDialog.title=Correlation Property Search"})
    public Md5SearchDialog() {
        super((JFrame) WindowManager.getDefault().getMainWindow(), Bundle.Md5SearchDialog_title(), true);
        this.correlationTypes = new ArrayList<>();
        this.md5Pattern = Pattern.compile("^[a-fA-F0-9]{32}$"); // NON-NLS
        initComponents();
        customizeComponents();
    }
    
    private void search() {
        new SwingWorker<List<CorrelationAttributeInstance>, Void>() {
            
            @Override
            protected List<CorrelationAttributeInstance> doInBackground() {
                List<CorrelationAttributeInstance.Type> correlationTypes;
                List<CorrelationAttributeInstance> correlationInstances = new ArrayList<>();
                
                try {
                    correlationTypes = EamDb.getInstance().getDefinedCorrelationTypes();
                    for (CorrelationAttributeInstance.Type type : correlationTypes) {
                        if (type.getId() == CorrelationAttributeInstance.FILES_TYPE_ID) {
                            correlationInstances = EamDb.getInstance().getArtifactInstancesByTypeValue(type, jTextField1.getText());
                            break;
                        }
                    }
                } catch (Exception ex) {
                    //DLG:
                }

                return correlationInstances;
            }

            @Override
            protected void done() {
                try {
                    super.done();
                    List<CorrelationAttributeInstance> correlationInstances = this.get();
                    //DLG: Node rootNode = new CorrelationAttributeInstanceRootNode(searchResults);
                    //DataResultFilterNode dataResultFilterNode = new DataResultFilterNode(rootNode, ExplorerManager.find(Md5SearchDialog.this));
                    //TableFilterNode tableFilterWithDescendantsNode = new TableFilterNode(dataResultFilterNode, 3);
                    DataResultViewerTable table = new CommonAttributesSearchResultsViewerTable();
                    Collection<DataResultViewer> viewers = new ArrayList<>(1);
                    viewers.add(table);
                    
                    DlgSearchNode searchNode = new DlgSearchNode(correlationInstances);
                    DlgFilterNode tableFilterNode = new DlgFilterNode(searchNode, true, searchNode.getName());
                    
                    //Node rootNode;
                    //Children childNodes = Children.create(new CorrelationAttributeInstanceChildNodeFactory(correlationInstances), true);
                    //rootNode = new AbstractNode(childNodes);
                    DataResultTopComponent results = DataResultTopComponent.createInstance(
                            "Files", "Correlation Property Search", tableFilterNode, HIDE_ON_CLOSE, viewers);
                } catch (InterruptedException ex) {
                    Exceptions.printStackTrace(ex); //DLG:
                } catch (ExecutionException ex) {
                    Exceptions.printStackTrace(ex); //DLG:
                }
            }
        }.execute();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jLabel1 = new javax.swing.JLabel();
        jTextField1 = new javax.swing.JTextField();
        jButton1 = new javax.swing.JButton();
        correlationTypeComboBox = new javax.swing.JComboBox<>();
        jLabel2 = new javax.swing.JLabel();
        jScrollPane1 = new javax.swing.JScrollPane();
        jTextArea1 = new javax.swing.JTextArea();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setResizable(false);

        org.openide.awt.Mnemonics.setLocalizedText(jLabel1, org.openide.util.NbBundle.getMessage(Md5SearchDialog.class, "Md5SearchDialog.jLabel1.text")); // NOI18N

        jTextField1.setText(org.openide.util.NbBundle.getMessage(Md5SearchDialog.class, "Md5SearchDialog.jTextField1.text")); // NOI18N
        jTextField1.addInputMethodListener(new java.awt.event.InputMethodListener() {
            public void caretPositionChanged(java.awt.event.InputMethodEvent evt) {
            }
            public void inputMethodTextChanged(java.awt.event.InputMethodEvent evt) {
                jTextField1InputMethodTextChanged(evt);
            }
        });
        jTextField1.addPropertyChangeListener(new java.beans.PropertyChangeListener() {
            public void propertyChange(java.beans.PropertyChangeEvent evt) {
                jTextField1PropertyChange(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(jButton1, org.openide.util.NbBundle.getMessage(Md5SearchDialog.class, "Md5SearchDialog.jButton1.text")); // NOI18N
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton1ActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(jLabel2, org.openide.util.NbBundle.getMessage(Md5SearchDialog.class, "Md5SearchDialog.jLabel2.text")); // NOI18N

        jTextArea1.setEditable(false);
        jTextArea1.setColumns(20);
        jTextArea1.setFont(new java.awt.Font("Tahoma", 0, 11)); // NOI18N
        jTextArea1.setLineWrap(true);
        jTextArea1.setRows(5);
        jTextArea1.setText(org.openide.util.NbBundle.getMessage(Md5SearchDialog.class, "Md5SearchDialog.jTextArea1.text")); // NOI18N
        jTextArea1.setWrapStyleWord(true);
        jTextArea1.setBorder(null);
        jTextArea1.setOpaque(false);
        jScrollPane1.setViewportView(jTextArea1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(11, 11, 11)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jButton1, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel2)
                            .addComponent(jLabel1))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addComponent(correlationTypeComboBox, javax.swing.GroupLayout.Alignment.LEADING, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jTextField1, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.PREFERRED_SIZE, 250, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.Alignment.TRAILING))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(jTextField1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(correlationTypeComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel2))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jButton1)
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void jTextField1PropertyChange(java.beans.PropertyChangeEvent evt) {//GEN-FIRST:event_jTextField1PropertyChange
        //DLG:
    }//GEN-LAST:event_jTextField1PropertyChange

    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
        search();
    }//GEN-LAST:event_jButton1ActionPerformed

    private void jTextField1InputMethodTextChanged(java.awt.event.InputMethodEvent evt) {//GEN-FIRST:event_jTextField1InputMethodTextChanged
        //DLG:
    }//GEN-LAST:event_jTextField1InputMethodTextChanged

    private void customizeComponents() {
        jButton1.setEnabled(false);
        correlationTypeComboBox.setEnabled(false);
        
        /*
         * Add correlation types to the combo-box.
         */
        try {
            EamDb dbManager = EamDb.getInstance();
            correlationTypes.clear();
            correlationTypes.addAll(dbManager.getDefinedCorrelationTypes());
        } catch (EamDbException ex) {
            Exceptions.printStackTrace(ex);
        }

        for (CorrelationAttributeInstance.Type type : correlationTypes) {
            correlationTypeComboBox.addItem(type.getDisplayName());
        }
        
        /*
         * Create listener for text input.
         */
        jTextField1.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void changedUpdate(DocumentEvent e) {
                validateInput();
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                validateInput();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                validateInput();
            }
            
            private void validateInput() {
                Matcher matcher = md5Pattern.matcher(jTextField1.getText().trim());
                if (matcher.find()) {
                    jButton1.setEnabled(true);
                    correlationTypeComboBox.setEnabled(true);
                    correlationTypeComboBox.setSelectedItem(FILES_CORRELATION_TYPE);
                } else {
                    jButton1.setEnabled(false);
                    correlationTypeComboBox.setEnabled(false);
                }
            }
        });
    }

    public void display() {
        this.setLocationRelativeTo(WindowManager.getDefault().getMainWindow());
        setVisible(true);
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JComboBox<String> correlationTypeComboBox;
    private javax.swing.JButton jButton1;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTextArea jTextArea1;
    private javax.swing.JTextField jTextField1;
    // End of variables declaration//GEN-END:variables
}
