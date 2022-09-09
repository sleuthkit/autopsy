/*
 * Autopsy
 *
 * Copyright 2020-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.discovery.ui;

import org.sleuthkit.autopsy.discovery.search.AbstractFilter;
import static java.awt.BorderLayout.CENTER;
import java.awt.Color;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import org.apache.commons.lang.StringUtils;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.discovery.search.DiscoveryAttributes;
import org.sleuthkit.autopsy.discovery.search.DiscoveryEventUtils;
import org.sleuthkit.autopsy.discovery.search.Group;
import org.sleuthkit.autopsy.discovery.search.Group.GroupSortingAlgorithm;
import org.sleuthkit.autopsy.discovery.search.DiscoveryAttributes.GroupingAttributeType;
import org.sleuthkit.autopsy.discovery.search.ResultsSorter;
import org.sleuthkit.autopsy.discovery.search.ResultsSorter.SortingMethod;
import org.sleuthkit.autopsy.discovery.search.SearchData;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Dialog for displaying the controls and filters for configuration of a
 * Discovery search.
 */
final class DiscoveryDialog extends javax.swing.JDialog {

    private static final Set<Case.Events> CASE_EVENTS_OF_INTEREST = EnumSet.of(Case.Events.CURRENT_CASE,
            Case.Events.DATA_SOURCE_ADDED, Case.Events.DATA_SOURCE_DELETED);
    private static final Set<IngestManager.IngestModuleEvent> INGEST_MODULE_EVENTS_OF_INTEREST = EnumSet.of(IngestManager.IngestModuleEvent.DATA_ADDED);
    private static final long serialVersionUID = 1L;
    private final static Logger logger = Logger.getLogger(DiscoveryDialog.class.getName());
    private ImageFilterPanel imageFilterPanel = null;
    private VideoFilterPanel videoFilterPanel = null;
    private DocumentFilterPanel documentFilterPanel = null;
    private DomainFilterPanel domainFilterPanel = null;
    private static final Color SELECTED_COLOR = new Color(216, 230, 242);
    private static final Color UNSELECTED_COLOR = new Color(240, 240, 240);
    private SearchWorker searchWorker = null;
    private static DiscoveryDialog discDialog;
    private static volatile boolean shouldUpdate = false;
    private SearchData.Type type = SearchData.Type.IMAGE;
    private final PropertyChangeListener listener;
    private final Set<BlackboardAttribute> objectsDetected = new HashSet<>();
    private final Set<BlackboardAttribute> interestingItems = new HashSet<>();
    private final Set<BlackboardAttribute> hashSets = new HashSet<>();

    /**
     * Get the Discovery dialog instance.
     *
     * @return The instance of the Discovery Dialog.
     */
    static synchronized DiscoveryDialog getDiscoveryDialogInstance() {
        if (discDialog == null) {
            discDialog = new DiscoveryDialog();
        }
        if (shouldUpdate) {
            discDialog.updateSearchSettings();
            shouldUpdate = false;
        }
        return discDialog;
    }

    /**
     * Private constructor to construct a new DiscoveryDialog
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    @Messages("DiscoveryDialog.name.text=Discovery")
    private DiscoveryDialog() {
        super(WindowManager.getDefault().getMainWindow(), Bundle.DiscoveryDialog_name_text(), true);
        initComponents();
        listener = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (evt.getPropertyName().equals("FilterError") && evt.getNewValue() != null) {
                    setValid(evt.getNewValue().toString());
                }
            }
        };
        updateSearchSettings();
        groupByCombobox.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent event) {
                if (event.getStateChange() == ItemEvent.SELECTED) {
                    getSelectedFilterPanel().setLastGroupingAttributeType(groupByCombobox.getItemAt(groupByCombobox.getSelectedIndex()));
                }
            }
        });
        orderByCombobox.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent event) {
                if (event.getStateChange() == ItemEvent.SELECTED) {
                    getSelectedFilterPanel().setLastSortingMethod(orderByCombobox.getItemAt(orderByCombobox.getSelectedIndex()));
                }
            }
        });
        groupSortingComboBox.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent event) {
                if (event.getStateChange() == ItemEvent.SELECTED) {
                    getSelectedFilterPanel().setLastGroupSortingAlg(groupSortingComboBox.getItemAt(groupSortingComboBox.getSelectedIndex()));
                }
            }
        });
        Case.addEventTypeSubscriber(CASE_EVENTS_OF_INTEREST, this.new CasePropertyChangeListener());
        IngestManager.getInstance().addIngestModuleEventListener(INGEST_MODULE_EVENTS_OF_INTEREST, this.new ModuleChangeListener());
        setPreferredSize(new java.awt.Dimension(1000, 800));
    }

    /**
     * Update the search settings to a default state.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    void updateSearchSettings() {
        removeAllPanels();
        imageFilterPanel = null;
        videoFilterPanel = null;
        documentFilterPanel = null;
        imageFilterPanel = new ImageFilterPanel();
        videoFilterPanel = new VideoFilterPanel();
        documentFilterPanel = new DocumentFilterPanel();
        domainFilterPanel = new DomainFilterPanel();
        unselectAllButtons();
        imagesButton.setSelected(true);
        imagesButton.setEnabled(false);
        imagesButton.setBackground(SELECTED_COLOR);
        imagesButton.setForeground(Color.BLACK);
        type = SearchData.Type.IMAGE;
        add(imageFilterPanel, CENTER);
        imageFilterPanel.addPropertyChangeListener(listener);
        updateComboBoxes();
        pack();
        repaint();
    }

    /**
     * Set the type buttons to a default state where none are selected.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private void unselectAllButtons() {
        imagesButton.setSelected(false);
        imagesButton.setEnabled(true);
        imagesButton.setBackground(UNSELECTED_COLOR);
        videosButton.setSelected(false);
        videosButton.setEnabled(true);
        videosButton.setBackground(UNSELECTED_COLOR);
        documentsButton.setSelected(false);
        documentsButton.setEnabled(true);
        documentsButton.setBackground(UNSELECTED_COLOR);
        domainsButton.setSelected(false);
        domainsButton.setEnabled(true);
        domainsButton.setBackground(UNSELECTED_COLOR);
    }

    /**
     * Private helper method to perform update of comboboxes update.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private void updateComboBoxes() {
        // Set up the grouping attributes
        List<GroupingAttributeType> groupingAttrs = new ArrayList<>();
        List<SortingMethod> sortingMethods = new ArrayList<>();
        groupByCombobox.removeAllItems();
        if (type == SearchData.Type.DOMAIN) {
            groupingAttrs.addAll(GroupingAttributeType.getOptionsForGroupingForDomains());
            sortingMethods.addAll(SortingMethod.getOptionsForOrderingDomains());
        } else {
            groupingAttrs.addAll(GroupingAttributeType.getOptionsForGroupingForFiles());
            sortingMethods.addAll(SortingMethod.getOptionsForOrderingFiles());
        }
        for (GroupingAttributeType groupingType : groupingAttrs) {
            addTypeToGroupByComboBox(groupingType);
        }
        groupByCombobox.setSelectedItem(getSelectedFilterPanel().getLastGroupingAttributeType());
        orderByCombobox.removeAllItems();
        // Set up the file order list
        for (SortingMethod method : sortingMethods) {
            if (method != SortingMethod.BY_FREQUENCY || CentralRepository.isEnabled()) {
                orderByCombobox.addItem(method);
            }
        }
        orderByCombobox.setSelectedItem(getSelectedFilterPanel().getLastSortingMethod());
        groupSortingComboBox.removeAllItems();
        for (GroupSortingAlgorithm groupSortAlgorithm : GroupSortingAlgorithm.values()) {
            groupSortingComboBox.addItem(groupSortAlgorithm);
        }
        groupSortingComboBox.setSelectedItem(getSelectedFilterPanel().getLastGroupSortingAlg());
    }

    /**
     * Private helper method to get the correct panel for the selected type.
     *
     * @return The panel that corresponds to the currently selected type.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private AbstractFiltersPanel getSelectedFilterPanel() {
        switch (type) {
            case IMAGE:
                return imageFilterPanel;
            case VIDEO:
                return videoFilterPanel;
            case DOCUMENT:
                return documentFilterPanel;
            case DOMAIN:
                return domainFilterPanel;
            default:
                return imageFilterPanel;
        }
    }

    /**
     * Private helper method to add GroupingAttributeType to the
     * groupByCombobox.
     *
     * @param type The Type of GroupingAttribute to add.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private void addTypeToGroupByComboBox(GroupingAttributeType type) {
        switch (type) {
            case FREQUENCY:
                if (!CentralRepository.isEnabled()) {
                    return;
                }
                break;
            case OBJECT_DETECTED:
                if (!imageFilterPanel.isObjectsFilterSupported()) {
                    return;
                }
                break;
            case INTERESTING_ITEM_SET:
                if (!imageFilterPanel.isInterestingItemsFilterSupported()) {
                    return;
                }
                break;
            case HASH_LIST_NAME:
                if (!imageFilterPanel.isHashSetFilterSupported()) {
                    return;
                }
                break;
            default:
                break;
        }
        groupByCombobox.addItem(type);
    }

    /**
     * Validate the filter settings for File type filters.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    void validateDialog() {
        AbstractFiltersPanel panel = getSelectedFilterPanel();
        if (panel != null) {
            panel.validateFields();
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

        javax.swing.JPanel toolBarPanel = new javax.swing.JPanel();
        imagesButton = new javax.swing.JButton();
        videosButton = new javax.swing.JButton();
        documentsButton = new javax.swing.JButton();
        javax.swing.JLabel step1Label = new javax.swing.JLabel();
        javax.swing.Box.Filler filler1 = new javax.swing.Box.Filler(new java.awt.Dimension(104, 0), new java.awt.Dimension(104, 0), new java.awt.Dimension(104, 32767));
        domainsButton = new javax.swing.JButton();
        javax.swing.JPanel displaySettingsPanel = new javax.swing.JPanel();
        searchButton = new javax.swing.JButton();
        errorLabel = new javax.swing.JLabel();
        javax.swing.JPanel sortingPanel = new javax.swing.JPanel();
        groupByCombobox = new javax.swing.JComboBox<>();
        orderByCombobox = new javax.swing.JComboBox<>();
        javax.swing.JLabel orderGroupsByLabel = new javax.swing.JLabel();
        javax.swing.JLabel orderByLabel = new javax.swing.JLabel();
        javax.swing.JLabel groupByLabel = new javax.swing.JLabel();
        groupSortingComboBox = new javax.swing.JComboBox<>();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setMinimumSize(new java.awt.Dimension(600, 300));
        setPreferredSize(new java.awt.Dimension(1000, 800));

        imagesButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/pictures-icon.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(imagesButton, org.openide.util.NbBundle.getMessage(DiscoveryDialog.class, "DiscoveryDialog.imagesButton.text")); // NOI18N
        imagesButton.setDisabledIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/pictures-icon.png"))); // NOI18N
        imagesButton.setFocusable(false);
        imagesButton.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        imagesButton.setMaximumSize(new java.awt.Dimension(90, 43));
        imagesButton.setMinimumSize(new java.awt.Dimension(90, 43));
        imagesButton.setPreferredSize(new java.awt.Dimension(90, 43));
        imagesButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                imagesButtonActionPerformed(evt);
            }
        });

        videosButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/video-icon.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(videosButton, org.openide.util.NbBundle.getMessage(DiscoveryDialog.class, "DiscoveryDialog.videosButton.text")); // NOI18N
        videosButton.setDisabledIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/video-icon.png"))); // NOI18N
        videosButton.setDisabledSelectedIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/video-icon.png"))); // NOI18N
        videosButton.setFocusable(false);
        videosButton.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        videosButton.setMaximumSize(new java.awt.Dimension(90, 43));
        videosButton.setMinimumSize(new java.awt.Dimension(90, 43));
        videosButton.setPreferredSize(new java.awt.Dimension(90, 43));
        videosButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                videosButtonActionPerformed(evt);
            }
        });

        documentsButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/documents-icon.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(documentsButton, org.openide.util.NbBundle.getMessage(DiscoveryDialog.class, "DiscoveryDialog.documentsButton.text")); // NOI18N
        documentsButton.setDisabledIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/documents-icon.png"))); // NOI18N
        documentsButton.setDisabledSelectedIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/documents-icon.png"))); // NOI18N
        documentsButton.setFocusable(false);
        documentsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                documentsButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(step1Label, org.openide.util.NbBundle.getMessage(DiscoveryDialog.class, "DiscoveryDialog.step1Label.text")); // NOI18N

        domainsButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/domain-32.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(domainsButton, org.openide.util.NbBundle.getMessage(DiscoveryDialog.class, "DiscoveryDialog.domainsButton.text")); // NOI18N
        domainsButton.setDisabledIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/domain-32.png"))); // NOI18N
        domainsButton.setDisabledSelectedIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/domain-32.png"))); // NOI18N
        domainsButton.setFocusable(false);
        domainsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                domainsButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout toolBarPanelLayout = new javax.swing.GroupLayout(toolBarPanel);
        toolBarPanel.setLayout(toolBarPanelLayout);
        toolBarPanelLayout.setHorizontalGroup(
            toolBarPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(toolBarPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(toolBarPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(toolBarPanelLayout.createSequentialGroup()
                        .addGap(10, 10, 10)
                        .addComponent(imagesButton, javax.swing.GroupLayout.PREFERRED_SIZE, 110, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(videosButton, javax.swing.GroupLayout.PREFERRED_SIZE, 110, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(documentsButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(domainsButton)
                        .addContainerGap(190, Short.MAX_VALUE))
                    .addGroup(toolBarPanelLayout.createSequentialGroup()
                        .addComponent(step1Label, javax.swing.GroupLayout.PREFERRED_SIZE, 243, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(filler1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addContainerGap(391, Short.MAX_VALUE))))
        );

        toolBarPanelLayout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {documentsButton, domainsButton, imagesButton, videosButton});

        toolBarPanelLayout.setVerticalGroup(
            toolBarPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(toolBarPanelLayout.createSequentialGroup()
                .addGap(6, 6, 6)
                .addGroup(toolBarPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(filler1, javax.swing.GroupLayout.PREFERRED_SIZE, 10, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(step1Label))
                .addGap(6, 6, 6)
                .addGroup(toolBarPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(videosButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(imagesButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(documentsButton, javax.swing.GroupLayout.PREFERRED_SIZE, 43, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(domainsButton, javax.swing.GroupLayout.PREFERRED_SIZE, 43, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(6, 6, 6))
        );

        getContentPane().add(toolBarPanel, java.awt.BorderLayout.PAGE_START);

        org.openide.awt.Mnemonics.setLocalizedText(searchButton, org.openide.util.NbBundle.getMessage(DiscoveryDialog.class, "DiscoveryDialog.searchButton.text")); // NOI18N
        searchButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                searchButtonActionPerformed(evt);
            }
        });

        errorLabel.setForeground(new java.awt.Color(255, 0, 0));

        sortingPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(org.openide.util.NbBundle.getMessage(DiscoveryDialog.class, "DiscoveryDialog.sortingPanel.border.title"))); // NOI18N
        sortingPanel.setPreferredSize(new java.awt.Dimension(345, 112));

        org.openide.awt.Mnemonics.setLocalizedText(orderGroupsByLabel, org.openide.util.NbBundle.getMessage(DiscoveryDialog.class, "DiscoveryDialog.orderGroupsByLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(orderByLabel, org.openide.util.NbBundle.getMessage(DiscoveryDialog.class, "DiscoveryDialog.orderByLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(groupByLabel, org.openide.util.NbBundle.getMessage(DiscoveryDialog.class, "DiscoveryDialog.groupByLabel.text")); // NOI18N

        javax.swing.GroupLayout sortingPanelLayout = new javax.swing.GroupLayout(sortingPanel);
        sortingPanel.setLayout(sortingPanelLayout);
        sortingPanelLayout.setHorizontalGroup(
            sortingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(sortingPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(sortingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(orderGroupsByLabel)
                    .addComponent(groupByLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(sortingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(groupSortingComboBox, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(groupByCombobox, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(orderByLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(orderByCombobox, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );
        sortingPanelLayout.setVerticalGroup(
            sortingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(sortingPanelLayout.createSequentialGroup()
                .addGap(6, 6, 6)
                .addGroup(sortingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(groupByCombobox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(groupByLabel)
                    .addComponent(orderByCombobox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(orderByLabel))
                .addGap(6, 6, 6)
                .addGroup(sortingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(groupSortingComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(orderGroupsByLabel))
                .addGap(6, 6, 6))
        );

        javax.swing.GroupLayout displaySettingsPanelLayout = new javax.swing.GroupLayout(displaySettingsPanel);
        displaySettingsPanel.setLayout(displaySettingsPanelLayout);
        displaySettingsPanelLayout.setHorizontalGroup(
            displaySettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, displaySettingsPanelLayout.createSequentialGroup()
                .addGap(8, 8, 8)
                .addGroup(displaySettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(sortingPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 741, Short.MAX_VALUE)
                    .addGroup(displaySettingsPanelLayout.createSequentialGroup()
                        .addComponent(errorLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 575, Short.MAX_VALUE)
                        .addGap(65, 65, 65)
                        .addComponent(searchButton, javax.swing.GroupLayout.PREFERRED_SIZE, 101, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addGap(8, 8, 8))
        );
        displaySettingsPanelLayout.setVerticalGroup(
            displaySettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, displaySettingsPanelLayout.createSequentialGroup()
                .addGap(6, 6, 6)
                .addComponent(sortingPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 82, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(6, 6, 6)
                .addGroup(displaySettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(errorLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(searchButton))
                .addGap(6, 6, 6))
        );

        getContentPane().add(displaySettingsPanel, java.awt.BorderLayout.PAGE_END);

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void imagesButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_imagesButtonActionPerformed
        removeAllPanels();
        add(imageFilterPanel, CENTER);
        unselectAllButtons();
        imagesButton.setSelected(true);
        imagesButton.setEnabled(false);
        imagesButton.setBackground(SELECTED_COLOR);
        imagesButton.setForeground(Color.BLACK);
        type = SearchData.Type.IMAGE;
        imageFilterPanel.addPropertyChangeListener(listener);
        updateComboBoxes();
        validateDialog();
        pack();
        repaint();
    }//GEN-LAST:event_imagesButtonActionPerformed

    private void videosButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_videosButtonActionPerformed
        removeAllPanels();
        add(videoFilterPanel, CENTER);
        unselectAllButtons();
        videosButton.setSelected(true);
        videosButton.setEnabled(false);
        videosButton.setBackground(SELECTED_COLOR);
        videosButton.setForeground(Color.BLACK);
        videoFilterPanel.addPropertyChangeListener(listener);
        type = SearchData.Type.VIDEO;
        updateComboBoxes();
        validateDialog();
        pack();
        repaint();
    }//GEN-LAST:event_videosButtonActionPerformed

    private void documentsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_documentsButtonActionPerformed
        removeAllPanels();
        add(documentFilterPanel, CENTER);
        unselectAllButtons();
        documentsButton.setSelected(true);
        documentsButton.setEnabled(false);
        documentsButton.setBackground(SELECTED_COLOR);
        documentsButton.setForeground(Color.BLACK);
        type = SearchData.Type.DOCUMENT;
        documentFilterPanel.addPropertyChangeListener(listener);
        updateComboBoxes();
        validateDialog();
        pack();
        repaint();
    }//GEN-LAST:event_documentsButtonActionPerformed

    /**
     * Helper method to remove all filter panels and their listeners
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private void removeAllPanels() {
        if (imageFilterPanel != null) {
            remove(imageFilterPanel);
            imageFilterPanel.removePropertyChangeListener(listener);
        }
        if (domainFilterPanel != null) {
            remove(domainFilterPanel);
            domainFilterPanel.removePropertyChangeListener(listener);
        }
        if (documentFilterPanel != null) {
            remove(documentFilterPanel);
            documentFilterPanel.removePropertyChangeListener(listener);
        }
        if (videoFilterPanel != null) {
            remove(videoFilterPanel);
            videoFilterPanel.removePropertyChangeListener(listener);
        }
    }

    private void searchButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_searchButtonActionPerformed
        setVisible(false); //set visible used here instead of dispose incase dispose code changes
        final DiscoveryTopComponent tc = DiscoveryTopComponent.getTopComponent();
        if (tc == null) {
            setValid("No Top Component Found");
            return;
        }
        if (tc.isOpened() == false) {
            tc.open();
        }
        tc.resetTopComponent();
        // Get the selected filters
        List<AbstractFilter> filters;
        if (videosButton.isSelected()) {
            filters = videoFilterPanel.getFilters();
        } else if (documentsButton.isSelected()) {
            filters = documentFilterPanel.getFilters();
        } else if (imagesButton.isSelected()) {
            filters = imageFilterPanel.getFilters();
        } else if (domainsButton.isSelected()) {
            filters = domainFilterPanel.getFilters();
        } else {
            logger.log(Level.SEVERE, "No filter type selected");
            filters = new ArrayList<>();
        }

        DiscoveryEventUtils.getDiscoveryEventBus().post(new DiscoveryEventUtils.SearchStartedEvent(type));

        // Get the grouping attribute and group sorting method
        DiscoveryAttributes.AttributeType groupingAttr = groupByCombobox.getItemAt(groupByCombobox.getSelectedIndex()).getAttributeType();
        Group.GroupSortingAlgorithm groupSortAlgorithm = groupSortingComboBox.getItemAt(groupSortingComboBox.getSelectedIndex());

        // Get the file sorting method
        ResultsSorter.SortingMethod fileSort = (ResultsSorter.SortingMethod) orderByCombobox.getSelectedItem();
        CentralRepository centralRepoDb = null;
        if (CentralRepository.isEnabled()) {
            try {
                centralRepoDb = CentralRepository.getInstance();
            } catch (CentralRepoException ex) {
                centralRepoDb = null;
                logger.log(Level.SEVERE, "Error loading central repository database, no central repository options will be available for Discovery", ex);
            }
        }
        searchWorker = new SearchWorker(centralRepoDb, type, filters, groupingAttr, groupSortAlgorithm, fileSort);
        searchWorker.execute();
        tc.toFront();
        tc.requestActive();
    }//GEN-LAST:event_searchButtonActionPerformed

    private void domainsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_domainsButtonActionPerformed
        removeAllPanels();
        add(domainFilterPanel, CENTER);
        unselectAllButtons();
        domainsButton.setSelected(true);
        domainsButton.setEnabled(false);
        domainsButton.setBackground(SELECTED_COLOR);
        domainsButton.setForeground(Color.BLACK);
        type = SearchData.Type.DOMAIN;
        domainFilterPanel.addPropertyChangeListener(listener);
        updateComboBoxes();
        validateDialog();
        pack();
        repaint();
    }//GEN-LAST:event_domainsButtonActionPerformed

    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    @Override
    public void dispose() {
        setVisible(false);
    }

    /**
     * Cancel the searchWorker if it exists.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    void cancelSearch() {
        if (searchWorker != null) {
            searchWorker.cancel(true);
            searchWorker = null;
        }
    }

    /**
     * The adjust the controls to reflect whether the settings are valid based
     * on the error.
     *
     * @param error The error message to display, empty string if there is no
     *              error.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private void setValid(String error) {
        if (StringUtils.isBlank(error)) {
            errorLabel.setText("");
            searchButton.setEnabled(true);
        } else {
            errorLabel.setText(error);
            searchButton.setEnabled(false);
        }
    }


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton documentsButton;
    private javax.swing.JButton domainsButton;
    private javax.swing.JLabel errorLabel;
    private javax.swing.JComboBox<GroupingAttributeType> groupByCombobox;
    private javax.swing.JComboBox<GroupSortingAlgorithm> groupSortingComboBox;
    private javax.swing.JButton imagesButton;
    private javax.swing.JComboBox<SortingMethod> orderByCombobox;
    private javax.swing.JButton searchButton;
    private javax.swing.JButton videosButton;
    // End of variables declaration//GEN-END:variables

    /**
     * PropertyChangeListener to listen to case level events that may modify the
     * filters available.
     */
    private class CasePropertyChangeListener implements PropertyChangeListener {

        @Override
        @SuppressWarnings("fallthrough")
        public void propertyChange(PropertyChangeEvent evt) {
            switch (Case.Events.valueOf(evt.getPropertyName())) {
                case CURRENT_CASE: {
                    if (evt.getNewValue() == null) {
                        //do not refresh when a case is closed only when it is opened.
                        break;
                    }
                    //else fallthrough
                }
                case DATA_SOURCE_ADDED:
                //fallthrough
                case DATA_SOURCE_DELETED:
                    shouldUpdate = true;
                    break;
                default:
                    //do nothing if the event is not one of the above events.
                    break;
            }
        }
    }

    /**
     * PropertyChangeListener to listen to ingest module events that may modify
     * the filters available.
     */
    private class ModuleChangeListener implements PropertyChangeListener {

        /**
         * @SuppressWarnings("deprecation") - we need to support already
         * existing interesting file and artifact hits.
         */
        @SuppressWarnings("deprecation")
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if (!shouldUpdate) {
                String eventType = evt.getPropertyName();
                if (eventType.equals(IngestManager.IngestModuleEvent.DATA_ADDED.toString())) {
                    /**
                     * Checking for a current case is a stop gap measure until a
                     * different way of handling the closing of cases is worked
                     * out. Currently, remote events may be received for a case
                     * that is already closed.
                     */
                    try {
                        Case.getCurrentCaseThrows();
                        /**
                         * Even with the check above, it is still possible that
                         * the case will be closed in a different thread before
                         * this code executes. If that happens, it is possible
                         * for the event to have a null oldValue.
                         */
                        ModuleDataEvent eventData = (ModuleDataEvent) evt.getOldValue();
                        if (null != eventData) {
                            if (eventData.getBlackboardArtifactType().getTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_OBJECT_DETECTED.getTypeID() && eventData.getArtifacts() != null) {
                                shouldUpdate = shouldUpdateFilters(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DESCRIPTION.getTypeID(), eventData, objectsDetected);
                            } else if (eventData.getBlackboardArtifactType().getTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID()) {
                                shouldUpdate = shouldUpdateFilters(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID(), eventData, hashSets);
                            } else if (eventData.getBlackboardArtifactType().getTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_ITEM.getTypeID()
                                    || eventData.getBlackboardArtifactType().getTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT.getTypeID()
                                    || eventData.getBlackboardArtifactType().getTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_ARTIFACT_HIT.getTypeID()) {
                                shouldUpdate = shouldUpdateFilters(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID(), eventData, interestingItems);
                            }
                        }
                    } catch (NoCurrentCaseException notUsed) {
                        // Case is closed, do nothing.
                    } catch (TskCoreException ex) {
                        logger.log(Level.WARNING, "Unable to determine if discovery UI should be updated", ex);
                    }
                }
            }
        }

        /**
         * Helper method to determine if the artifact in the eventData
         * represents a new value for the filter.
         *
         * @param attributeTypeId  The attribute id of the attribute which
         *                         contains the value for the filter.
         * @param eventData        The event which contains the artifacts.
         * @param filterSetToCheck The set of current values for the relevant
         *                         filter.
         *
         * @return True if the value is a new value for the filter, false
         *         otherwise.
         *
         * @throws TskCoreException Thrown because the attributes were unable to
         *                          be retrieved for one of the artifacts in the
         *                          eventData.
         */
        private boolean shouldUpdateFilters(int attributeTypeId, ModuleDataEvent eventData, Set<BlackboardAttribute> filterSetToCheck) throws TskCoreException {
            for (BlackboardArtifact artifact : eventData.getArtifacts()) {
                if (artifact.getAttributes() != null) {
                    for (BlackboardAttribute attr : artifact.getAttributes()) {
                        if (attr.getAttributeType().getTypeID() == attributeTypeId && !filterSetToCheck.contains(attr)) {
                            filterSetToCheck.add(attr);
                            return true;
                        }
                    }
                }
            }
            return false;
        }
    }
}
