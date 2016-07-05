/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2013 Basis Technology Corp.
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
package org.sleuthkit.autopsy.actions;

import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import org.openide.util.ImageUtilities;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.services.TagsManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

public class GetTagNameDialog extends JDialog {

    private static final String TAG_ICON_PATH = "org/sleuthkit/autopsy/images/tag-folder-blue-icon-16.png"; //NON-NLS
    private final HashMap<String, TagName> tagNames = new HashMap<>();
    private TagName tagName = null;
    static final Logger logger = Logger.getLogger(GetTagNameDialog.class.getName());

    /**
     * Show the Tag Name Dialog and return the TagName selected by the user. The
     * dialog will be centered with the main autopsy window as its owner. To set
     * another window as the owner use {@link #doDialog(java.awt.Window) }
     *
     * @return a TagName instance selected by the user, or null if the user
     *         canceled the dialog.
     */
    public static TagName doDialog() {
        return doDialog(WindowManager.getDefault().getMainWindow());
    }

    /**
     * Show the Tag Name Dialog and return the TagName selected by the user.
     *
     * @param owner the window that will be the owner of the dialog. The dialog
     *              will be centered over this window and will block the rest of
     *              the application.
     *
     * @return a TagName instance selected by the user, or null if the user
     *         canceled the dialog.
     */
    public static TagName doDialog(final Window owner) {
        return new GetTagNameDialog(owner).tagName;
    }

    private GetTagNameDialog(final Window owner) {
        super(owner,
                NbBundle.getMessage(GetTagNameDialog.class, "GetTagNameDialog.createTag"),
                ModalityType.APPLICATION_MODAL);
        setIconImage(ImageUtilities.loadImage(TAG_ICON_PATH));
        initComponents();

        // Set up the dialog to close when Esc is pressed.
        String cancelName = NbBundle.getMessage(this.getClass(), "GetTagNameDialog.cancelName");
        InputMap inputMap = getRootPane().getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), cancelName);

        // Get the current set of tag names and hash them for a speedy lookup in
        // case the user chooses an existing tag name from the tag names table.
        TagsManager tagsManager = Case.getCurrentCase().getServices().getTagsManager();
        List<TagName> currentTagNames = null;
        try {
            currentTagNames = tagsManager.getAllTagNames();
        } catch (TskCoreException ex) {
            Logger.getLogger(GetTagNameDialog.class.getName()).log(Level.SEVERE, "Failed to get tag names", ex); //NON-NLS
        }
        if (null != currentTagNames) {
            for (TagName name : currentTagNames) {
                this.tagNames.put(name.getDisplayName(), name);
            }
        } else {
            currentTagNames = new ArrayList<>();
        }

        currentTagNames.sort(new Comparator<TagName>() {
            @Override
            public int compare(TagName o1, TagName o2) {
                return o1.getDisplayName().compareTo(o2.getDisplayName());
            }
        });
        // Populate the tag names table.
        tagsTable.setModel(new TagsTableModel(currentTagNames));
        tagsTable.setTableHeader(null);
        tagsTable.setCellSelectionEnabled(true);
        tagsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tagsTable.setFocusable(false);
        tagsTable.setRowHeight(tagsTable.getRowHeight() + 5);

        // Center and show the dialog box. 
        this.setLocationRelativeTo(owner);
        setVisible(true);
    }

    private boolean containsIllegalCharacters(String content) {
        return (content.contains("\\")
                || content.contains(":")
                || content.contains("*")
                || content.contains("?")
                || content.contains("\"")
                || content.contains("<")
                || content.contains(">")
                || content.contains("|"));
    }

    private class TagsTableModel extends AbstractTableModel {

        private final ArrayList<TagName> tagNames = new ArrayList<>();

        TagsTableModel(List<TagName> tagNames) {
            for (TagName tagName : tagNames) {
                this.tagNames.add(tagName);
            }
        }

        @Override
        public int getRowCount() {
            return tagNames.size();
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }

        @Override
        public int getColumnCount() {
            return 1;
        }

        @Override
        public String getValueAt(int rowIndex, int columnIndex) {
            return tagNames.get(rowIndex).getDisplayName();
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

        okButton = new javax.swing.JButton();
        jScrollPane1 = new javax.swing.JScrollPane();
        tagsTable = new javax.swing.JTable();
        preexistingLabel = new javax.swing.JLabel();
        newTagPanel = new javax.swing.JPanel();
        tagNameLabel = new javax.swing.JLabel();
        tagNameField = new javax.swing.JTextField();
        addTagButton = new javax.swing.JButton();
        deleteTagButton = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                formKeyReleased(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(okButton, org.openide.util.NbBundle.getMessage(GetTagNameDialog.class, "GetTagNameDialog.okButton.text")); // NOI18N
        okButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                okButtonActionPerformed(evt);
            }
        });

        jScrollPane1.setBackground(new java.awt.Color(255, 255, 255));

        tagsTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {

            }
        ));
        tagsTable.setGridColor(java.awt.Color.black);
        tagsTable.setShowHorizontalLines(false);
        tagsTable.setShowVerticalLines(false);
        tagsTable.setTableHeader(null);
        jScrollPane1.setViewportView(tagsTable);

        org.openide.awt.Mnemonics.setLocalizedText(preexistingLabel, org.openide.util.NbBundle.getMessage(GetTagNameDialog.class, "GetTagNameDialog.preexistingLabel.text")); // NOI18N

        newTagPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(org.openide.util.NbBundle.getMessage(GetTagNameDialog.class, "GetTagNameDialog.newTagPanel.border.title"))); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(tagNameLabel, org.openide.util.NbBundle.getMessage(GetTagNameDialog.class, "GetTagNameDialog.tagNameLabel.text")); // NOI18N

        tagNameField.setText(org.openide.util.NbBundle.getMessage(GetTagNameDialog.class, "GetTagNameDialog.tagNameField.text")); // NOI18N
        tagNameField.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                tagNameFieldKeyReleased(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(addTagButton, org.openide.util.NbBundle.getMessage(GetTagNameDialog.class, "GetTagNameDialog.addTagButton.text")); // NOI18N
        addTagButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addTagButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout newTagPanelLayout = new javax.swing.GroupLayout(newTagPanel);
        newTagPanel.setLayout(newTagPanelLayout);
        newTagPanelLayout.setHorizontalGroup(
            newTagPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(newTagPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(tagNameLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(tagNameField, javax.swing.GroupLayout.DEFAULT_SIZE, 172, Short.MAX_VALUE)
                .addGap(2, 2, 2)
                .addComponent(addTagButton)
                .addContainerGap())
        );
        newTagPanelLayout.setVerticalGroup(
            newTagPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(newTagPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(newTagPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(tagNameLabel)
                    .addComponent(tagNameField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(addTagButton))
                .addContainerGap(238, Short.MAX_VALUE))
        );

        org.openide.awt.Mnemonics.setLocalizedText(deleteTagButton, org.openide.util.NbBundle.getMessage(GetTagNameDialog.class, "GetTagNameDialog.deleteTagButton.text")); // NOI18N
        deleteTagButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteTagButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(jLabel1, org.openide.util.NbBundle.getMessage(GetTagNameDialog.class, "GetTagNameDialog.jLabel1.text")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 198, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(preexistingLabel)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(deleteTagButton)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 99, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addGap(0, 0, 0)
                        .addComponent(newTagPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(okButton)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(preexistingLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 177, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(deleteTagButton)
                            .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 92, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(0, 0, 0))
                    .addComponent(newTagPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(okButton)
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void okButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_okButtonActionPerformed
        dispose();
    }//GEN-LAST:event_okButtonActionPerformed

    private void formKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_formKeyReleased
        if (evt.getKeyCode() == KeyEvent.VK_ENTER) {
            okButtonActionPerformed(null);
        }
    }//GEN-LAST:event_formKeyReleased

    private void tagNameFieldKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_tagNameFieldKeyReleased
        if (evt.getKeyCode() == KeyEvent.VK_ENTER) {
            okButtonActionPerformed(null);
        }
    }//GEN-LAST:event_tagNameFieldKeyReleased

    @Messages({"GetTagNameDialog.deleteTag.success.text=Successfully deleted tag.",
        "GetTagNameDialog.deleteTag.success.header=Success",
        "GetTagNameDialog.deleteTag.failure.text=Failed to delete tag.",
        "GetTagNameDialog.deleteTag.failure.header=Failure"})
    private void deleteTagButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteTagButtonActionPerformed
        if (this.tagsTable.getSelectedRow() != -1) {
            String tagDisplayName = (String) this.tagsTable.getModel().getValueAt(this.tagsTable.getSelectedRow(), 1);
            try {
                Case.getCurrentCase().getServices().getTagsManager().deleteTagName(tagNames.get(tagDisplayName));
                JOptionPane.showMessageDialog(null, Bundle.GetTagNameDialog_deleteTag_success_text(), Bundle.GetTagNameDialog_deleteTag_success_header(), JOptionPane.INFORMATION_MESSAGE);
            } catch (TskCoreException ex) {
                JOptionPane.showMessageDialog(null, Bundle.GetTagNameDialog_deleteTag_failure_text(), Bundle.GetTagNameDialog_deleteTag_failure_header(), JOptionPane.ERROR_MESSAGE);
                logger.log(Level.SEVERE, "Failed to delete tag: " + tagDisplayName, ex);
            }
        }
    }//GEN-LAST:event_deleteTagButtonActionPerformed

    private void addTagButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addTagButtonActionPerformed
        String tagDisplayName = tagNameField.getText();
        if (tagDisplayName.isEmpty()) {
            JOptionPane.showMessageDialog(null,
                    NbBundle.getMessage(this.getClass(),
                            "GetTagNameDialog.mustSupplyTtagName.msg"),
                    NbBundle.getMessage(this.getClass(), "GetTagNameDialog.tagNameErr"),
                    JOptionPane.ERROR_MESSAGE);
        } else if (containsIllegalCharacters(tagDisplayName)) {
            JOptionPane.showMessageDialog(null,
                    NbBundle.getMessage(this.getClass(), "GetTagNameDialog.illegalChars.msg"),
                    NbBundle.getMessage(this.getClass(), "GetTagNameDialog.illegalCharsErr"),
                    JOptionPane.ERROR_MESSAGE);
        } else {
            tagName = tagNames.get(tagDisplayName);
            if (tagName == null) {
                try {
                    tagName = Case.getCurrentCase().getServices().getTagsManager().addTagName(tagDisplayName);
                    tagNames.put(tagDisplayName, tagName);
                    List<TagName> tagNameList = new ArrayList<>(this.tagNames.values());
                    tagNameList.sort(new Comparator<TagName>() {
                        @Override
                        public int compare(TagName o1, TagName o2) {
                            return o1.getDisplayName().compareTo(o2.getDisplayName());
                        }
                    });
                    tagsTable.setModel(new TagsTableModel(tagNameList));
                    this.tagNameField.setText("");
                } catch (TskCoreException ex) {
                    Logger.getLogger(AddTagAction.class.getName()).log(Level.SEVERE, "Error adding " + tagDisplayName + " tag name", ex); //NON-NLS
                    JOptionPane.showMessageDialog(null,
                            NbBundle.getMessage(this.getClass(),
                                    "GetTagNameDialog.unableToAddTagNameToCase.msg",
                                    tagDisplayName),
                            NbBundle.getMessage(this.getClass(), "GetTagNameDialog.taggingErr"),
                            JOptionPane.ERROR_MESSAGE);
                    tagName = null;
                } catch (TagsManager.TagNameAlreadyExistsException ex) {
                    Logger.getLogger(AddTagAction.class.getName()).log(Level.SEVERE, "Error adding " + tagDisplayName + " tag name", ex); //NON-NLS
                    JOptionPane.showMessageDialog(null,
                            NbBundle.getMessage(this.getClass(),
                                    "GetTagNameDialog.tagNameAlreadyDef.msg",
                                    tagDisplayName),
                            NbBundle.getMessage(this.getClass(), "GetTagNameDialog.dupTagErr"),
                            JOptionPane.ERROR_MESSAGE);
                    tagName = null;
                }
            }
        }
    }//GEN-LAST:event_addTagButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton addTagButton;
    private javax.swing.JButton deleteTagButton;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JPanel newTagPanel;
    private javax.swing.JButton okButton;
    private javax.swing.JLabel preexistingLabel;
    private javax.swing.JTextField tagNameField;
    private javax.swing.JLabel tagNameLabel;
    private javax.swing.JTable tagsTable;
    // End of variables declaration//GEN-END:variables

}
