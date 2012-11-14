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

import java.awt.BorderLayout;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.SimpleTimeZone;
import java.util.TimeZone;
import javax.swing.ComboBoxModel;
import javax.swing.JPanel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.ListDataListener;

/**
 * The "Add Image" wizard panel 1. This class is used to design the "form" of
 * the panel 1 for "Add Image" wizard panel.
 *
 */
final class AddImageVisualPanel1 extends JPanel {
    
    enum EVENT {UPDATE_UI, FOCUS_NEXT};
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
    private ImageTypeModel model;
    
    ImageTypePanel currentPanel;

    /**
     * Creates new form AddImageVisualPanel1
     * @param wizPanel corresponding WizardPanel to handle logic of wizard step
     */
    AddImageVisualPanel1(AddImageWizardPanel1 wizPanel) {
        initComponents();
        this.wizPanel = wizPanel;
        createTimeZoneList();
        customInit();
    }
    
    private void customInit() {
        model = new ImageTypeModel();
        typeComboBox.setModel(model);
        typeComboBox.setSelectedIndex(0);
        typePanel.setLayout(new BorderLayout());
        updateCurrentPanel(ImageFilePanel.getDefault());
    }
    
    /**
     * Changes the current panel to the given panel.
     * @param panel instance of ImageTypePanel to change to
     */
    private void updateCurrentPanel(ImageTypePanel panel) {
        currentPanel = panel;
        typePanel.removeAll();
        typePanel.add((JPanel) currentPanel, BorderLayout.CENTER);
        typePanel.validate();
        typePanel.repaint();
        currentPanel.addPropertyChangeListener(new PropertyChangeListener() {

            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if(evt.getPropertyName().equals(AddImageVisualPanel1.EVENT.UPDATE_UI.toString())) {
                    updateUI(null);
                }
                if(evt.getPropertyName().equals(AddImageVisualPanel1.EVENT.FOCUS_NEXT.toString())) {
                    wizPanel.moveFocusToNext();
                }
            }
            
        });
        currentPanel.setFocus();
        updateUI(null);
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
        return currentPanel.getImagePath();
    }
    
    /**
     * Sets the image path of the current panel.
     * @param s the image path to set
     */
    public void setImagePath(String s) {
        currentPanel.setImagePath(s);
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
        jLabel2 = new javax.swing.JLabel();
        nextLabel = new javax.swing.JLabel();
        timeZoneLabel = new javax.swing.JLabel();
        timeZoneComboBox = new javax.swing.JComboBox();
        noFatOrphansCheckbox = new javax.swing.JCheckBox();
        descLabel = new javax.swing.JLabel();
        inputPanel = new javax.swing.JPanel();
        typeTabel = new javax.swing.JLabel();
        typePanel = new javax.swing.JPanel();
        typeComboBox = new javax.swing.JComboBox();
        imgInfoLabel = new javax.swing.JLabel();

        org.openide.awt.Mnemonics.setLocalizedText(jLabel2, org.openide.util.NbBundle.getMessage(AddImageVisualPanel1.class, "AddImageVisualPanel1.jLabel2.text")); // NOI18N

        setPreferredSize(new java.awt.Dimension(588, 328));

        org.openide.awt.Mnemonics.setLocalizedText(nextLabel, org.openide.util.NbBundle.getMessage(AddImageVisualPanel1.class, "AddImageVisualPanel1.nextLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(timeZoneLabel, org.openide.util.NbBundle.getMessage(AddImageVisualPanel1.class, "AddImageVisualPanel1.timeZoneLabel.text")); // NOI18N

        timeZoneComboBox.setMaximumRowCount(30);

        org.openide.awt.Mnemonics.setLocalizedText(noFatOrphansCheckbox, org.openide.util.NbBundle.getMessage(AddImageVisualPanel1.class, "AddImageVisualPanel1.noFatOrphansCheckbox.text")); // NOI18N
        noFatOrphansCheckbox.setToolTipText(org.openide.util.NbBundle.getMessage(AddImageVisualPanel1.class, "AddImageVisualPanel1.noFatOrphansCheckbox.toolTipText")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(descLabel, org.openide.util.NbBundle.getMessage(AddImageVisualPanel1.class, "AddImageVisualPanel1.descLabel.text")); // NOI18N

        inputPanel.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        org.openide.awt.Mnemonics.setLocalizedText(typeTabel, org.openide.util.NbBundle.getMessage(AddImageVisualPanel1.class, "AddImageVisualPanel1.typeTabel.text")); // NOI18N

        typePanel.setMinimumSize(new java.awt.Dimension(0, 65));
        typePanel.setPreferredSize(new java.awt.Dimension(521, 65));

        javax.swing.GroupLayout typePanelLayout = new javax.swing.GroupLayout(typePanel);
        typePanel.setLayout(typePanelLayout);
        typePanelLayout.setHorizontalGroup(
            typePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );
        typePanelLayout.setVerticalGroup(
            typePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 65, Short.MAX_VALUE)
        );

        javax.swing.GroupLayout inputPanelLayout = new javax.swing.GroupLayout(inputPanel);
        inputPanel.setLayout(inputPanelLayout);
        inputPanelLayout.setHorizontalGroup(
            inputPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(inputPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(inputPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(inputPanelLayout.createSequentialGroup()
                        .addComponent(typeTabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(typeComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 292, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 123, Short.MAX_VALUE))
                    .addComponent(typePanel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 544, Short.MAX_VALUE))
                .addContainerGap())
        );
        inputPanelLayout.setVerticalGroup(
            inputPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(inputPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(inputPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(typeTabel)
                    .addComponent(typeComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(typePanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        imgInfoLabel.setFont(new java.awt.Font("Tahoma", 1, 14)); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(imgInfoLabel, org.openide.util.NbBundle.getMessage(AddImageVisualPanel1.class, "AddImageVisualPanel1.imgInfoLabel.text")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(inputPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(nextLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(timeZoneLabel)
                                .addGap(18, 18, 18)
                                .addComponent(timeZoneComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 252, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addComponent(noFatOrphansCheckbox)
                            .addGroup(layout.createSequentialGroup()
                                .addGap(21, 21, 21)
                                .addComponent(descLabel))
                            .addComponent(imgInfoLabel))
                        .addGap(0, 54, Short.MAX_VALUE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(imgInfoLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(inputPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(timeZoneLabel)
                    .addComponent(timeZoneComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(noFatOrphansCheckbox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(descLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 69, Short.MAX_VALUE)
                .addComponent(nextLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0))
        );
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.JLabel descLabel;
    private javax.swing.JLabel imgInfoLabel;
    private javax.swing.JPanel inputPanel;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel nextLabel;
    private javax.swing.JCheckBox noFatOrphansCheckbox;
    private javax.swing.JComboBox timeZoneComboBox;
    private javax.swing.JLabel timeZoneLabel;
    private javax.swing.JComboBox typeComboBox;
    private javax.swing.JPanel typePanel;
    private javax.swing.JLabel typeTabel;
    // End of variables declaration//GEN-END:variables

    /**
     * The "listener" that updates the UI of this panel based on the changes of
     * fields on this panel. This is also the method to check whether all the
     * fields on this panel are correctly filled and decides whether to enable
     * the "Next" button or not.
     * 
     * @param e  the document event
     */
    public void updateUI(DocumentEvent e) {
        this.wizPanel.enableNextButton(currentPanel.enableNext());
    }
    
    /**
     * ComboBoxModel to control typeComboBox and supply ImageTypePanels.
     */
    private class ImageTypeModel implements ComboBoxModel {
        ImageTypePanel selected;
        ImageTypePanel[] types = ImageTypePanel.getPanels();

        @Override
        public void setSelectedItem(Object anItem) {
            selected = (ImageTypePanel) anItem;
            updateCurrentPanel(selected);
        }

        @Override
        public Object getSelectedItem() {
            return selected;
        }

        @Override
        public int getSize() {
            return types.length;
        }

        @Override
        public Object getElementAt(int index) {
            return types[index];
        }

        @Override
        public void addListDataListener(ListDataListener l) {
        }

        @Override
        public void removeListDataListener(ListDataListener l) {
        }
        
    }
}
