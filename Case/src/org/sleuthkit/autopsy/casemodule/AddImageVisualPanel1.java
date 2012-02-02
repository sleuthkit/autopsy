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
import java.util.Calendar;
import java.util.SimpleTimeZone;
import java.util.TimeZone;
import javax.swing.JCheckBox;
import javax.swing.event.DocumentEvent;
import javax.swing.filechooser.FileFilter;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentListener;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;

/**
 * The "Add Image" wizard panel 1. This class is used to design the "form" of
 * the panel 1 for "Add Image" wizard panel.
 *
 * @author jantonius
 */
final class AddImageVisualPanel1 extends JPanel implements DocumentListener {

    private JFileChooser fc = new JFileChooser();
    private FileFilter filter;
    private final String[] imgExt = {".img", ".dd"};
    private final String imgDesc = "Raw Images (*.img, *.dd)";
    private GeneralFilter imgFilter = new GeneralFilter(imgExt, imgDesc, false);
    private final String[] splitExt = {".*\\.[0-9][0-9][0-9]", ".*\\.[a-z][a-z]"};
    private final String splitDesc = "Split Part (*.001, *.002, etc)";
    private GeneralFilter splitFilter = new GeneralFilter(splitExt, splitDesc, true);
    private final String[] encasExt = {".*\\.e[0-9][0-9]", ".*\\.e[a-z][a-z]"};
    private final String encaseDesc = "Encase Images (*.e01, *.eAA)";
    private GeneralFilter encaseFilter = new GeneralFilter(encasExt, encaseDesc, true);
    private boolean multi = false;
    private AddImageWizardPanel1 wizPanel;

    /**
     * Creates new form AddImageVisualPanel1
     * @param wizPanel corresponding WizardPanel to handle logic of wizard step
     */
    AddImageVisualPanel1(AddImageWizardPanel1 wizPanel) {
        initComponents();
        this.wizPanel = wizPanel;
        fc.setDragEnabled(multi);
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.setMultiSelectionEnabled(multi);
        fc.addChoosableFileFilter(imgFilter);
        filter = imgFilter;
        buttonGroup1.add(encase);
        buttonGroup1.add(rawSingle);
        buttonGroup1.add(rawSplit);
        imgPathTextField.getDocument().addDocumentListener(this);
        imgPathTextField.setText("");
        jLabel1.setText("");
        rawSingle.setSelected(true);
        rawSplit.setSelected(false);
        encase.setSelected(false);
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
     * Gets the array of image paths from the Image Path Text Field.
     *
     * @return imagePaths  the array of image paths
     */
    public String[] getImagePaths() {
        String[] imgPath = Case.convertImgPath(imgPathTextField.getText());
        if (Case.checkMultiplePathExist(imgPath)) {
            return imgPath;
        } else {
            return new String[0];
        }
    }
    
    public JTextField getImagePathTextField() {
        return this.imgPathTextField;
    }
   

    /**
     * Gets the type of the image that's selected.
     *
     * @return imgType  the type of the image that selected
     */
    public String getImgType() {
        if (rawSingle.isSelected()) {
            return "Raw Single";
        }
        if (rawSplit.isSelected()) {
            return "Raw Split";
        }
        if (encase.isSelected()) {
            return "EnCase";
        } else {
            return "Nothing Selected";
        }
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
        rawSingle = new javax.swing.JRadioButton();
        rawSplit = new javax.swing.JRadioButton();
        imgTypeLabel = new javax.swing.JLabel();
        encase = new javax.swing.JRadioButton();
        imgPathLabel = new javax.swing.JLabel();
        multipleSelectLabel = new javax.swing.JLabel();
        imgPathTextField = new javax.swing.JTextField();
        imgPathBrowserButton = new javax.swing.JButton();
        this.imgPathBrowserButton.setDefaultCapable(true);
        this.imgPathBrowserButton.requestFocus();
        imgInfoLabel = new javax.swing.JLabel();
        jLabel1 = new javax.swing.JLabel();
        timeZoneComboBox = new javax.swing.JComboBox();
        timeZoneLabel = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();

        org.openide.awt.Mnemonics.setLocalizedText(rawSingle, org.openide.util.NbBundle.getMessage(AddImageVisualPanel1.class, "AddImageVisualPanel1.rawSingle.text")); // NOI18N
        rawSingle.setRequestFocusEnabled(false);
        rawSingle.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rawSingleActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(rawSplit, org.openide.util.NbBundle.getMessage(AddImageVisualPanel1.class, "AddImageVisualPanel1.rawSplit.text")); // NOI18N
        rawSplit.setRequestFocusEnabled(false);
        rawSplit.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rawSplitActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(imgTypeLabel, org.openide.util.NbBundle.getMessage(AddImageVisualPanel1.class, "AddImageVisualPanel1.imgTypeLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(encase, org.openide.util.NbBundle.getMessage(AddImageVisualPanel1.class, "AddImageVisualPanel1.encase.text")); // NOI18N
        encase.setRequestFocusEnabled(false);
        encase.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                encaseActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(imgPathLabel, org.openide.util.NbBundle.getMessage(AddImageVisualPanel1.class, "AddImageVisualPanel1.imgPathLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(multipleSelectLabel, org.openide.util.NbBundle.getMessage(AddImageVisualPanel1.class, "AddImageVisualPanel1.multipleSelectLabel.text")); // NOI18N

        imgPathTextField.setText(org.openide.util.NbBundle.getMessage(AddImageVisualPanel1.class, "AddImageVisualPanel1.imgPathTextField.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(imgPathBrowserButton, org.openide.util.NbBundle.getMessage(AddImageVisualPanel1.class, "AddImageVisualPanel1.imgPathBrowserButton.text")); // NOI18N
        imgPathBrowserButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                imgPathBrowserButtonActionPerformed(evt);
            }
        });

        imgInfoLabel.setFont(new java.awt.Font("Tahoma", 1, 14));
        org.openide.awt.Mnemonics.setLocalizedText(imgInfoLabel, org.openide.util.NbBundle.getMessage(AddImageVisualPanel1.class, "AddImageVisualPanel1.imgInfoLabel.text")); // NOI18N

        jLabel1.setForeground(new java.awt.Color(255, 0, 51));
        org.openide.awt.Mnemonics.setLocalizedText(jLabel1, org.openide.util.NbBundle.getMessage(AddImageVisualPanel1.class, "AddImageVisualPanel1.jLabel1.text")); // NOI18N

        timeZoneComboBox.setMaximumRowCount(30);

        org.openide.awt.Mnemonics.setLocalizedText(timeZoneLabel, org.openide.util.NbBundle.getMessage(AddImageVisualPanel1.class, "AddImageVisualPanel1.timeZoneLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jLabel2, org.openide.util.NbBundle.getMessage(AddImageVisualPanel1.class, "AddImageVisualPanel1.jLabel2.text")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(imgPathLabel)
                        .addGap(18, 18, 18)
                        .addComponent(imgPathTextField, javax.swing.GroupLayout.DEFAULT_SIZE, 414, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(imgPathBrowserButton))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(imgTypeLabel)
                            .addGroup(layout.createSequentialGroup()
                                .addGap(10, 10, 10)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(rawSplit)
                                    .addComponent(rawSingle)
                                    .addComponent(encase)))
                            .addComponent(multipleSelectLabel)
                            .addComponent(imgInfoLabel)
                            .addComponent(jLabel1)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(timeZoneLabel)
                                .addGap(10, 10, 10)
                                .addComponent(timeZoneComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 315, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addGap(0, 102, Short.MAX_VALUE))
                    .addComponent(jLabel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(imgInfoLabel)
                .addGap(19, 19, 19)
                .addComponent(imgTypeLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(rawSingle)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(rawSplit)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(encase)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(imgPathLabel)
                    .addComponent(imgPathTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(imgPathBrowserButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(multipleSelectLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(timeZoneComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(timeZoneLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 43, Short.MAX_VALUE)
                .addComponent(jLabel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(21, 21, 21)
                .addComponent(jLabel1)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    /**
     * When the "rawSingle" radio button is selected.
     *
     * @param evt  the action event
     */
    private void rawSingleActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_rawSingleActionPerformed
        rawSingle.setSelected(true);
        rawSplit.setSelected(false);
        encase.setSelected(false);
        multipleSelectLabel.setText("Single Image: Multiple Select Disabled");
        filter = imgFilter;
        multi = false;
        this.updateUI(null);
}//GEN-LAST:event_rawSingleActionPerformed

    /**
     * When the "rawSplit" radio button is selected.
     *
     * @param evt  the action event
     */
    private void rawSplitActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_rawSplitActionPerformed
        rawSingle.setSelected(false);
        rawSplit.setSelected(true);
        encase.setSelected(false);
        multipleSelectLabel.setText("Split Image: Multiple Select Enabled. Use Ctrl, Shift, "
                + "or Drag to select multiple image parts");
        filter = splitFilter;
        multi = true;
        updateUI(null);
}//GEN-LAST:event_rawSplitActionPerformed

    /**
     * When the "encase" radio button is selected.
     *
     * @param evt  the action event
     */
    private void encaseActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_encaseActionPerformed
        rawSingle.setSelected(false);
        rawSplit.setSelected(false);
        encase.setSelected(true);
        multipleSelectLabel.setText("EnCase Image: Multiple Select Enabled. Use Ctrl, Shift, "
                + "or Drag to select multiple image parts");
        filter = encaseFilter;
        multi = true;
        updateUI(null);
}//GEN-LAST:event_encaseActionPerformed

    /**
     * When the "Browse" button is pressed, open the file chooser window to
     * select the images.
     *
     * @param evt  the action event
     */
    private void imgPathBrowserButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_imgPathBrowserButtonActionPerformed
        fc.resetChoosableFileFilters();
        fc.addChoosableFileFilter(filter);
        fc.setFileFilter(filter);
        fc.setMultiSelectionEnabled(multi);
        fc.setDragEnabled(multi);

        // set the current directory of the FileChooser if the ImagePath Field is valid
        File currentDir = new File(imgPathTextField.getText());
        if (currentDir.exists()) {
            fc.setCurrentDirectory(currentDir);
        }

        int retval = fc.showOpenDialog(this);
        if (retval == JFileChooser.APPROVE_OPTION) {
            File[] files = fc.getSelectedFiles();
            String path = "";
            if (multi) {
                for (File file : files) {
                    path = path + "\"" + file.getPath() + "\" ";
                }
                imgPathTextField.setText(path);
            } else {
                path = fc.getSelectedFile().getPath();
                imgPathTextField.setText(path);
            }
        }
}//GEN-LAST:event_imgPathBrowserButtonActionPerformed
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.JRadioButton encase;
    private javax.swing.JLabel imgInfoLabel;
    private javax.swing.JButton imgPathBrowserButton;
    private javax.swing.JLabel imgPathLabel;
    private static javax.swing.JTextField imgPathTextField;
    private javax.swing.JLabel imgTypeLabel;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel multipleSelectLabel;
    private static javax.swing.JRadioButton rawSingle;
    private javax.swing.JRadioButton rawSplit;
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
        String[] imgPath = Case.convertImgPath(imgPathTextField.getText());
        boolean isExist = Case.checkMultiplePathExist(imgPath);
        File imgFile = new File(imgPath[0]);

        // check if the given paths exist and those are paths to image files
        boolean isImagePath = true;
        for (int i = 0; i < imgPath.length; i++) {
            File tempImgFile = new File(imgPath[i]);
            isImagePath = isImagePath && tempImgFile.exists() && !tempImgFile.isDirectory()
                    && (imgFilter.accept(tempImgFile) || splitFilter.accept(tempImgFile)
                    || encaseFilter.accept(tempImgFile));
        }


        if (isImagePath) {
            Case currentCase = Case.getCurrentCase();
            File dbFile = new File(currentCase.getCaseDirectory() + File.separator + imgFile.getName() + ".db");

            if (dbFile.exists()) {
                String dbExist = "This database already exists. Do you want to overwrite the database?";
                NotifyDescriptor d = new NotifyDescriptor.Confirmation(dbExist, "Warning: Overwrite Database", NotifyDescriptor.YES_NO_OPTION, NotifyDescriptor.WARNING_MESSAGE);
                d.setValue(NotifyDescriptor.NO_OPTION);

                isExist = false;

                Object res = DialogDisplayer.getDefault().notify(d);
                if (res != null && res == DialogDescriptor.YES_OPTION) {
                    isExist = dbFile.delete();
                    if (!isExist) {
                        jLabel1.setText("*Database for this image is already created and it can't be deleted because it's being used.");
                    }
                }
                if (res != null && res == DialogDescriptor.NO_OPTION) {
                    jLabel1.setText("*Database for this image exist. Either delete it or select another image.");
                }
            }
        } else {
            isExist = false;
        }

        if (isExist) {
            jLabel1.setText("");
        }

        this.wizPanel.enableNextButton(isExist);
    }
}
