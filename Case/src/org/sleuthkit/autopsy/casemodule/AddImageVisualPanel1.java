/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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
package org.sleuthkit.autopsy.casemodule;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.SimpleTimeZone;
import java.util.TimeZone;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * The "Add Image" wizard panel 1. This class is used to design the "form" of
 * the panel 1 for "Add Image" wizard panel.
 *
 * @author jantonius
 */
final class AddImageVisualPanel1 extends JPanel implements DocumentListener {

    private JFileChooser fc = new JFileChooser();
    static final List<String> rawExt = Arrays.asList(new String[]{".img", ".dd", ".001", ".aa"});
    static final String rawDesc = "Raw Images (*.img, *.dd, *.001, *.aa)";
    static GeneralFilter rawFilter = new GeneralFilter(rawExt, rawDesc);
    static final List<String> encaseExt = Arrays.asList(new String[]{".e01"});
    static final String encaseDesc = "Encase Images (*.e01)";
    static GeneralFilter encaseFilter = new GeneralFilter(encaseExt, encaseDesc);
    static final List<String> allExt = new ArrayList<String>();
    {
        allExt.addAll(rawExt);
        allExt.addAll(encaseExt);
    }
    static final String allDesc = "All Supported Types";
    static GeneralFilter allFilter = new GeneralFilter(allExt, allDesc);
    private AddImageWizardPanel1 wizPanel;

    /**
     * Creates new form AddImageVisualPanel1
     * @param wizPanel corresponding WizardPanel to handle logic of wizard step
     */
    AddImageVisualPanel1(AddImageWizardPanel1 wizPanel) {
        initComponents();
        this.wizPanel = wizPanel;
        fc.setDragEnabled(false);
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.setMultiSelectionEnabled(false);
        fc.addChoosableFileFilter(allFilter);
        fc.addChoosableFileFilter(rawFilter);
        fc.addChoosableFileFilter(encaseFilter);
        fc.setFileFilter(allFilter);
        imgPathTextField.getDocument().addDocumentListener(this);
        imgPathTextField.setText("");
        createTimeZoneList();
    }

    /**
     * Returns the name of the this panel. This name will be shown on the left
     * panel of the "Add Image" wizard panel.
     *
     * @return name  the name of this panel
     */
    @Override
    public String getName() {
        return "Enter Image Information";
    }

    /**
     * Gets the image path from the Image Path Text Field.
     *
     * @return imagePath  the image path
     */
    public String getImagePath() {
        String imgPath = imgPathTextField.getText();
        if (Case.pathExists(imgPath)) {
            return imgPath;
        } else {
            return "";
        }
    }

    public JTextField getImagePathTextField() {
        return this.imgPathTextField;
    }
    
    /**
     * 
     * @return true if no fat orphans processing is selected
     */
    boolean getNoFatOrphans() {
        return noFatOrphansCheckbox.isSelected();
    }

    /**
     * Gets the time zone that selected on the drop down list.
     *
     * @return timeZone  the time zone that selected
     */
    public String getSelectedTimezone() {
        String tz = timeZoneComboBox.getSelectedItem().toString();
        return tz.substring(tz.indexOf(")") + 2).trim();
    }

    // add the timeZone list to the timeZoneComboBox
    /**
     * Creates the drop down list for the time zones and then makes the local
     * machine time zones to be selected.
     */
    public void createTimeZoneList() {
        // load and add all timezone
        String[] ids = SimpleTimeZone.getAvailableIDs();
        for (String id : ids) {
            TimeZone zone = TimeZone.getTimeZone(id);
            int offset = zone.getRawOffset() / 1000;
            int hour = offset / 3600;
            int minutes = (offset % 3600) / 60;
            String item = String.format("(GMT%+d:%02d) %s", hour, minutes, id);

            /*
            DateFormat dfm = new SimpleDateFormat("z");
            dfm.setTimeZone(zone);
            boolean hasDaylight = zone.useDaylightTime();
            String first = dfm.format(new Date(2010, 1, 1));
            String second = dfm.format(new Date(2011, 6, 6));
            int mid = hour * -1;
            String result = first + Integer.toString(mid);
            if(hasDaylight){
            result = result + second;
            }
            timeZoneComboBox.addItem(item + " (" + result + ")");
             */
            timeZoneComboBox.addItem(item);
        }
        // get the current timezone
        TimeZone thisTimeZone = Calendar.getInstance().getTimeZone();
        int thisOffset = thisTimeZone.getRawOffset() / 1000;
        int thisHour = thisOffset / 3600;
        int thisMinutes = (thisOffset % 3600) / 60;
        String formatted = String.format("(GMT%+d:%02d) %s", thisHour, thisMinutes, thisTimeZone.getID());

        // set the selected timezone
        timeZoneComboBox.setSelectedItem(formatted);
    }

    /** 
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        buttonGroup1 = new javax.swing.ButtonGroup();
        imgPathLabel = new javax.swing.JLabel();
        imgPathTextField = new javax.swing.JTextField();
        imgPathBrowserButton = new javax.swing.JButton();
        this.imgPathBrowserButton.setDefaultCapable(true);
        this.imgPathBrowserButton.requestFocus();
        imgInfoLabel = new javax.swing.JLabel();
        timeZoneComboBox = new javax.swing.JComboBox();
        timeZoneLabel = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        noFatOrphansCheckbox = new javax.swing.JCheckBox();
        optionsLabel1 = new javax.swing.JLabel();

        setPreferredSize(new java.awt.Dimension(588, 328));

        org.openide.awt.Mnemonics.setLocalizedText(imgPathLabel, org.openide.util.NbBundle.getMessage(AddImageVisualPanel1.class, "AddImageVisualPanel1.imgPathLabel.text")); // NOI18N

        imgPathTextField.setText(org.openide.util.NbBundle.getMessage(AddImageVisualPanel1.class, "AddImageVisualPanel1.imgPathTextField.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(imgPathBrowserButton, org.openide.util.NbBundle.getMessage(AddImageVisualPanel1.class, "AddImageVisualPanel1.imgPathBrowserButton.text")); // NOI18N
        imgPathBrowserButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                imgPathBrowserButtonActionPerformed(evt);
            }
        });

        imgInfoLabel.setFont(new java.awt.Font("Tahoma", 1, 14)); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(imgInfoLabel, org.openide.util.NbBundle.getMessage(AddImageVisualPanel1.class, "AddImageVisualPanel1.imgInfoLabel.text")); // NOI18N

        timeZoneComboBox.setMaximumRowCount(30);

        org.openide.awt.Mnemonics.setLocalizedText(timeZoneLabel, org.openide.util.NbBundle.getMessage(AddImageVisualPanel1.class, "AddImageVisualPanel1.timeZoneLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jLabel2, org.openide.util.NbBundle.getMessage(AddImageVisualPanel1.class, "AddImageVisualPanel1.jLabel2.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(noFatOrphansCheckbox, org.openide.util.NbBundle.getMessage(AddImageVisualPanel1.class, "AddImageVisualPanel1.noFatOrphansCheckbox.text")); // NOI18N
        noFatOrphansCheckbox.setToolTipText(org.openide.util.NbBundle.getMessage(AddImageVisualPanel1.class, "AddImageVisualPanel1.noFatOrphansCheckbox.toolTipText")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(optionsLabel1, org.openide.util.NbBundle.getMessage(AddImageVisualPanel1.class, "AddImageVisualPanel1.optionsLabel1.text")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(imgInfoLabel)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                        .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                            .addComponent(timeZoneLabel)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(timeZoneComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 253, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                            .addComponent(imgPathLabel)
                            .addGap(18, 18, 18)
                            .addComponent(imgPathTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 389, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                            .addComponent(imgPathBrowserButton)))
                    .addComponent(optionsLabel1)
                    .addComponent(jLabel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(10, 10, 10)
                        .addComponent(noFatOrphansCheckbox)))
                .addContainerGap(39, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(imgInfoLabel)
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(imgPathLabel)
                    .addComponent(imgPathTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(imgPathBrowserButton))
                .addGap(26, 26, 26)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(timeZoneLabel)
                    .addComponent(timeZoneComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addComponent(optionsLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(noFatOrphansCheckbox)
                .addGap(18, 18, Short.MAX_VALUE)
                .addComponent(jLabel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(20, 20, 20))
        );
    }// </editor-fold>//GEN-END:initComponents

    /**
     * When the "Browse" button is pressed, open the file chooser window to
     * select the images.
     *
     * @param evt  the action event
     */
    private void imgPathBrowserButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_imgPathBrowserButtonActionPerformed
        
        String oldText = imgPathTextField.getText();
        // set the current directory of the FileChooser if the ImagePath Field is valid
        File currentDir = new File(oldText);
        if (currentDir.exists()) {
            fc.setCurrentDirectory(currentDir);
        }

        int retval = fc.showOpenDialog(this);
        if (retval == JFileChooser.APPROVE_OPTION) {
            String path = fc.getSelectedFile().getPath();
            imgPathTextField.setText(path);
        }


        this.wizPanel.moveFocusToNext();
}//GEN-LAST:event_imgPathBrowserButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.JLabel imgInfoLabel;
    private javax.swing.JButton imgPathBrowserButton;
    private javax.swing.JLabel imgPathLabel;
    private static javax.swing.JTextField imgPathTextField;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JCheckBox noFatOrphansCheckbox;
    private javax.swing.JLabel optionsLabel1;
    private javax.swing.JComboBox timeZoneComboBox;
    private javax.swing.JLabel timeZoneLabel;
    // End of variables declaration//GEN-END:variables

    /**
     * Gives notification that there was an insert into the document.  The
     * range given by the DocumentEvent bounds the freshly inserted region.
     *
     * @param e the document event
     */
    @Override
    public void insertUpdate(DocumentEvent e) {
        updateUI(e);
    }

    /**
     * Gives notification that a portion of the document has been
     * removed.  The range is given in terms of what the view last
     * saw (that is, before updating sticky positions).
     *
     * @param e the document event
     */
    @Override
    public void removeUpdate(DocumentEvent e) {
        updateUI(e);
    }

    /**
     * Gives notification that an attribute or set of attributes changed.
     *
     * @param e the document event
     */
    @Override
    public void changedUpdate(DocumentEvent e) {
        updateUI(e);
    }

    /**
     * The "listener" that updates the UI of this panel based on the changes of
     * fields on this panel. This is also the method to check whether all the
     * fields on this panel are correctly filled and decides whether to enable
     * the "Next" button or not.
     * 
     * @param e  the document event
     */
    public void updateUI(DocumentEvent e) {
        String imgPath = imgPathTextField.getText();
        boolean isExist = Case.pathExists(imgPath);
        File imgFile = new File(imgPath);

        // check if the given paths exist and those are paths to image files
        boolean isImagePath = allFilter.accept(imgFile);

        this.wizPanel.enableNextButton(isExist && isImagePath);
    }
}
