/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.contentviewers;

import java.awt.Component;
import org.apache.commons.lang.StringUtils;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.openide.util.NbBundle.Messages;

/**
 * A panel for translation with a subcomponent that allows for translation
 */
final class TranslatablePanel extends JPanel {
    /**
     * an option in drop down of whether or not to translate
     */
    private class TranslateOption {
        private final String text;
        private final boolean translate;

        public TranslateOption(String text, boolean translate) {
            this.text = text;
            this.translate = translate;
        }

        public String getText() {
            return text;
        }
        
        @Override
        public String toString() {
            return text;
        }

        public boolean shouldTranslate() {
            return translate;
        }
    }
    
    
    /**
     * describes a component that will allow for translation that has String-based content
     */
    interface TranslatableComponent {
        /**
         * @return the underlying component to be added to TranslatablePanel as a parent
         */
        Component getComponent();
        
        
        /**
         * set the content for this subcomponent
         * @param content the original content for the component; when this is reset, there is no translation initially
         * @return        if non-null string, this string will appear as error message
         */
        String setContent(String content);
        String getContent();
        
        
        /**
         * sets the state of this component to translated
         * @param translate     whether or not to translate this component
         * @return              if non-null string, this string will appear as error message
         */
        String setTranslated(boolean translate);
        boolean isTranslated();
    }
    
    
    
    private static final long serialVersionUID = 1L;
    
    private final TranslatableComponent subcomponent;
    private final String origOptionText;
    private final String translatedOptionText;


    @Messages({"TranslatablePanel.comboBoxOption.originalText=Original Text",
        "TranslatablePanel.comboBoxOption.translatedText=Translated Text"}) 
    TranslatablePanel(TranslatableComponent subcomponent) {
        this(
            subcomponent, 
            Bundle.TranslatablePanel_comboBoxOption_originalText(), 
            Bundle.TranslatablePanel_comboBoxOption_translatedText(), 
            null);
    }
        
    /**
     * Creates new form TranslatedContentPanel
     */
    TranslatablePanel(TranslatableComponent subcomponent, String origOptionText, String translatedOptionText, String origContent) {
        this.subcomponent = subcomponent;
        this.origOptionText = origOptionText;
        this.translatedOptionText = translatedOptionText;
        
        initComponents();
        additionalInit(origContent);
    }

    
    
    private void setWarningLabelMsg(String msg) {
        warningLabel.setText(msg);
        warningLabel.setVisible(StringUtils.isEmpty(msg));
    }

    void reset() {
        setContent(null);
    }
    
    void setContent(String content) {
        this.translateComboBox.setSelectedIndex(0);
        SwingUtilities.invokeLater(() -> {
            String errMess = this.subcomponent.setContent(content);
            setWarningLabelMsg(errMess);
        });
    }

    

    private void additionalInit(String origContent) {
        add(this.subcomponent.getComponent(), java.awt.BorderLayout.CENTER);
        setWarningLabelMsg(null);
        translateComboBox.removeAllItems();
        translateComboBox.addItem(new TranslateOption(this.origOptionText, false));
        translateComboBox.addItem(new TranslateOption(this.translatedOptionText, true));
        this.subcomponent.setContent(origContent);
    }
    
    private void handleComboBoxChange(TranslateOption translateOption) {
        SwingUtilities.invokeLater(() -> {
           String errMess = this.subcomponent.setTranslated(translateOption.shouldTranslate());
           setWarningLabelMsg(errMess);
        });
    }
    

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        jPanel1 = new javax.swing.JPanel();
        translateComboBox = new javax.swing.JComboBox<>();
        warningLabel = new javax.swing.JLabel();

        setMaximumSize(new java.awt.Dimension(2000, 2000));
        setMinimumSize(new java.awt.Dimension(2, 2));
        setName(""); // NOI18N
        setPreferredSize(new java.awt.Dimension(100, 58));
        setVerifyInputWhenFocusTarget(false);
        setLayout(new java.awt.BorderLayout());

        jPanel1.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        jPanel1.setMaximumSize(new java.awt.Dimension(182, 24));
        jPanel1.setPreferredSize(new java.awt.Dimension(182, 24));
        jPanel1.setLayout(new java.awt.GridBagLayout());

        translateComboBox.setMinimumSize(new java.awt.Dimension(43, 20));
        translateComboBox.setPreferredSize(new java.awt.Dimension(43, 20));
        translateComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                translateComboBoxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.weightx = 0.1;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        jPanel1.add(translateComboBox, gridBagConstraints);

        warningLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/warning16.png"))); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.25;
        jPanel1.add(warningLabel, gridBagConstraints);

        add(jPanel1, java.awt.BorderLayout.NORTH);
    }// </editor-fold>//GEN-END:initComponents

    private void translateComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_translateComboBoxActionPerformed
        handleComboBoxChange((TranslateOption) translateComboBox.getSelectedItem());
    }//GEN-LAST:event_translateComboBoxActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel jPanel1;
    private javax.swing.JComboBox<TranslateOption> translateComboBox;
    private javax.swing.JLabel warningLabel;
    // End of variables declaration//GEN-END:variables
}