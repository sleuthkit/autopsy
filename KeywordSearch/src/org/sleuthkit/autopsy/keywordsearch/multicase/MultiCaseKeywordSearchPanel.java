/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.keywordsearch.multicase;

import com.google.common.eventbus.Subscribe;
import com.google.common.eventbus.DeadEvent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javax.swing.AbstractButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.table.TableColumn;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.TableCellRenderer;
import org.netbeans.swing.outline.DefaultOutlineModel;
import org.openide.explorer.ExplorerManager;
import org.netbeans.swing.outline.Outline;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.multiusercases.CaseNodeData;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.EmptyNode;
import org.sleuthkit.autopsy.keywordsearch.multicase.MultiCaseSearcher.MultiCaseSearcherException;
import org.sleuthkit.autopsy.keywordsearch.multicase.SearchQuery.QueryType;

/**
 * Panel to display the controls and results for the multi-case search.
 */
final class MultiCaseKeywordSearchPanel extends javax.swing.JPanel implements ExplorerManager.Provider {

    @Messages({
        "MultiCaseKeywordSearchPanel.emptyNode.waitText=Please Wait..."
    })
    private static final long serialVersionUID = 1L;
    private volatile SearchThread searchThread = null;
    private final Outline outline;
    private final ExplorerManager em;
    private final org.openide.explorer.view.OutlineView outlineView;
    private static final Logger LOGGER = Logger.getLogger(MultiCaseKeywordSearchPanel.class.getName());
    private static final EmptyNode PLEASE_WAIT_NODE = new EmptyNode(Bundle.MultiCaseKeywordSearchPanel_emptyNode_waitText());
    private static final MultiCaseKeywordSearchNode NO_RESULTS_NODE = new MultiCaseKeywordSearchNode(new ArrayList<>());
    private Collection<SearchHit> allSearchHits = new ArrayList<>();
    private Collection<MultiCaseSearcherException> searchExceptions = new ArrayList<>();
    private final SelectMultiUserCasesDialog caseSelectionDialog = SelectMultiUserCasesDialog.getInstance();
    private final Map<String, CaseNodeData> caseNameToCaseDataMap;
    private Node[] currentConfirmedSelections;
    
    /**
     * Creates new form MultiCaseKeywordSearchPanel
     */
    MultiCaseKeywordSearchPanel() {
        em = new ExplorerManager();
        outlineView = new org.openide.explorer.view.OutlineView();
        outline = outlineView.getOutline();
        outlineView.setPropertyColumns(
                Bundle.MultiCaseKeywordSearchNode_properties_caseDirectory(), Bundle.MultiCaseKeywordSearchNode_properties_caseDirectory(),
                Bundle.MultiCaseKeywordSearchNode_properties_dataSource(), Bundle.MultiCaseKeywordSearchNode_properties_dataSource(),
                Bundle.MultiCaseKeywordSearchNode_properties_path(), Bundle.MultiCaseKeywordSearchNode_properties_path(),
                Bundle.MultiCaseKeywordSearchNode_properties_sourceType(), Bundle.MultiCaseKeywordSearchNode_properties_sourceType(),
                Bundle.MultiCaseKeywordSearchNode_properties_source(), Bundle.MultiCaseKeywordSearchNode_properties_source());
        ((DefaultOutlineModel) outline.getOutlineModel()).setNodesColumnLabel(Bundle.MultiCaseKeywordSearchNode_properties_case());
        initComponents();
        outline.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        outline.setRootVisible(false);
        outlineView.setPreferredSize(resultsScrollPane.getPreferredSize());
        resultsScrollPane.setViewportView(outlineView);
        caseSelectionDialog.subscribeToNewCaseSelections(new ChangeListener() {
            @Override
            public void nodeSelectionChanged(Node[] selections, List<CaseNodeData> selectionCaseData) {
                populateCasesList(selectionCaseData);
                currentConfirmedSelections = selections;
                revalidate();
                repaint();
            }
        });
        searchEnabled(true);
        outline.setRowSelectionAllowed(false);
        searchProgressBar.setVisible(false);
        exportButton.setEnabled(false);
        outline.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        caseNameToCaseDataMap = new HashMap<>();
        setColumnWidths();
    }

    /**
     * Listener for new selections
     */
    public interface ChangeListener {
        public void nodeSelectionChanged(Node[] selections, List<CaseNodeData> selectionCaseData);
    }
    
    /**
     * If a collection of SearchHits is received update the results shown on the
     * panel to include them.
     *
     * @param hits the collection of SearchHits which was received.
     */
    @Messages({"MultiCaseKeywordSearchPanel.countOfResults.label=Count: "})
    @Subscribe
    void subscribeToResults(Collection<SearchHit> hits) {
        allSearchHits.addAll(hits);
        if (allSearchHits.size() > 0) {
            MultiCaseKeywordSearchNode resultsNode = new MultiCaseKeywordSearchNode(allSearchHits);
            SwingUtilities.invokeLater(() -> {
                em.setRootContext(resultsNode);
                outline.setRowSelectionAllowed(true);
                resultsCountLabel.setText(Bundle.MultiCaseKeywordSearchPanel_countOfResults_label() + Integer.toString(outline.getRowCount()));
            });
        } else {
            em.setRootContext(NO_RESULTS_NODE);
            resultsCountLabel.setText(Bundle.MultiCaseKeywordSearchPanel_countOfResults_label() + 0);
        }
    }

    /**
     * If a string is received and it matches the
     * MultiCaseSearcher.SEARCH_COMPLETE_STRING reset elements of this panel to
     * reflect that the search is done.
     *
     * @param stringRecived the String which was received
     */
    @Subscribe
    void subscribeToStrings(String stringReceived) {
        if (stringReceived.equals(MultiCaseSearcher.getSearchCompleteMessage())) {
            searchThread.unregisterWithSearcher(MultiCaseKeywordSearchPanel.this);
            searchThread = null;
            searchEnabled(true);
            if (!searchExceptions.isEmpty()) {
                warningLabel.setText(Bundle.MultiCaseKeywordSearchPanel_errorsEncounter_text(searchExceptions.size()));
            }
            if (!em.getRootContext().equals(PLEASE_WAIT_NODE) && !em.getRootContext().equals(NO_RESULTS_NODE)) {
                exportButton.setEnabled(true);
                SwingUtilities.invokeLater(() -> {
                    exportButton.setEnabled(true);
                    setColumnWidths();
                });
            }
        } else {
            //If it is not the SEARCH_COMPLETE_STRING log it.
            LOGGER.log(Level.INFO, "String posted to MultiCaseKeywordSearchPanel EventBus with value of " + stringReceived);
        }
    }

    /**
     * If a InterruptedException is received over the EventBus update the
     * warning label.
     *
     * @param exception the InterruptedException which was received.
     */
    @Subscribe
    void subscribeToInterruptionExceptions(InterruptedException exception) {
        warningLabel.setText(exception.getMessage());
        //if we are still displaying please wait force it to update to no results
        if (em.getRootContext().equals(PLEASE_WAIT_NODE)) {
            em.setRootContext(NO_RESULTS_NODE);
            resultsCountLabel.setText(Bundle.MultiCaseKeywordSearchPanel_countOfResults_label() + 0);
        }
    }

    /**
     * If a MultiCaseSearcherException is received over the EventBus cancel the
     * current search and update the warning label.
     *
     * @param exception the MultiCaseSearcherException which was received.
     */
    @Messages({"# {0} - numberOfErrors",
        "MultiCaseKeywordSearchPanel.errorsEncounter.text={0} Error(s) encountered while performing search"
    })
    @Subscribe
    void subscribeToMultiCaseSearcherExceptions(MultiCaseSearcherException exception) {
        searchExceptions.add(exception);
    }

    /**
     * Log all other events received over the event bus which are not
     * specifically covered by another @Subscribe method
     *
     * @param deadEvent Any object received over the event bus which was not of
     *                  a type otherwise subscribed to
     */
    @Subscribe
    void subscribeToDeadEvents(DeadEvent deadEvent) {
        LOGGER.log(Level.INFO, "Dead Event posted to MultiCaseKeywordSearchPanel EventBus " + deadEvent.toString());
    }

    private void displaySearchErrors() {
        if (!searchExceptions.isEmpty()) {
            StringBuilder strBuilder = new StringBuilder("");
            searchExceptions.forEach((exception) -> {
                strBuilder.append("- ").append(exception.getMessage()).append(System.lineSeparator());
            });
            SwingUtilities.invokeLater(() -> {
                new MultiCaseKeywordSearchErrorDialog(strBuilder.toString());
            });
        }

    }

    /**
     * Get the list of cases from the Multi user case browser
     */
    private void populateCasesList(List<CaseNodeData> selectedNodes) {
        Collection<String> disabledCases = getCases(false);
        casesPanel.removeAll();
        casesPanel.revalidate();
        casesPanel.repaint();
        caseNameToCaseDataMap.clear();
        int casePanelWidth = casesPanel.getPreferredSize().width;
        int heightOfAllRows = 0;
        for (CaseNodeData data : selectedNodes) {
            //select all new cases and cases which were previously selected
            String multiUserCaseName = data.getName();
            caseNameToCaseDataMap.put(multiUserCaseName, data);
            boolean isSelected = true;
            if (disabledCases.contains(multiUserCaseName)) {
                isSelected = false;
            }
            JCheckBox caseCheckBox = new JCheckBox(multiUserCaseName, isSelected);
            caseCheckBox.setBackground(Color.white);
            if (casePanelWidth < caseCheckBox.getPreferredSize().width) {
                casePanelWidth = caseCheckBox.getPreferredSize().width;
            }
            heightOfAllRows += caseCheckBox.getPreferredSize().height;
            casesPanel.add(caseCheckBox);
        }
        casesPanel.setPreferredSize(new Dimension(casePanelWidth, heightOfAllRows));
    }

    @Override
    public ExplorerManager getExplorerManager() {
        return em;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        searchTypeGroup = new javax.swing.ButtonGroup();
        searchButton = new javax.swing.JButton();
        substringRadioButton = new javax.swing.JRadioButton();
        keywordTextField = new javax.swing.JTextField();
        exactRadioButton = new javax.swing.JRadioButton();
        regexRadioButton = new javax.swing.JRadioButton();
        casesScrollPane = new javax.swing.JScrollPane();
        casesPanel = new javax.swing.JPanel();
        casesLabel = new javax.swing.JLabel();
        resultsLabel = new javax.swing.JLabel();
        uncheckButton = new javax.swing.JButton();
        checkButton = new javax.swing.JButton();
        toolDescriptionScrollPane = new javax.swing.JScrollPane();
        toolDescriptionTextArea = new javax.swing.JTextArea();
        resultsScrollPane = new javax.swing.JScrollPane();
        cancelButton = new javax.swing.JButton();
        searchProgressBar = new javax.swing.JProgressBar();
        warningLabel = new javax.swing.JLabel();
        exportButton = new javax.swing.JButton();
        resultsCountLabel = new javax.swing.JLabel();
        viewErrorsButton = new javax.swing.JButton();
        pickCasesButton = new javax.swing.JButton();

        setName(""); // NOI18N
        setOpaque(false);
        setPreferredSize(new java.awt.Dimension(1000, 442));

        org.openide.awt.Mnemonics.setLocalizedText(searchButton, org.openide.util.NbBundle.getMessage(MultiCaseKeywordSearchPanel.class, "MultiCaseKeywordSearchPanel.searchButton.text")); // NOI18N
        searchButton.setMaximumSize(new java.awt.Dimension(84, 23));
        searchButton.setMinimumSize(new java.awt.Dimension(84, 23));
        searchButton.setPreferredSize(new java.awt.Dimension(84, 23));
        searchButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                searchButtonActionPerformed(evt);
            }
        });

        searchTypeGroup.add(substringRadioButton);
        org.openide.awt.Mnemonics.setLocalizedText(substringRadioButton, org.openide.util.NbBundle.getMessage(MultiCaseKeywordSearchPanel.class, "MultiCaseKeywordSearchPanel.substringRadioButton.text_1")); // NOI18N
        substringRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                substringRadioButtonActionPerformed(evt);
            }
        });

        keywordTextField.setFont(new java.awt.Font("Monospaced", 0, 14)); // NOI18N
        keywordTextField.setText(org.openide.util.NbBundle.getMessage(MultiCaseKeywordSearchPanel.class, "MultiCaseKeywordSearchPanel.keywordTextField.text_1")); // NOI18N
        keywordTextField.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(192, 192, 192), 1, true));
        keywordTextField.setMinimumSize(new java.awt.Dimension(2, 25));
        keywordTextField.setPreferredSize(new java.awt.Dimension(2, 25));

        searchTypeGroup.add(exactRadioButton);
        exactRadioButton.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(exactRadioButton, org.openide.util.NbBundle.getMessage(MultiCaseKeywordSearchPanel.class, "MultiCaseKeywordSearchPanel.exactRadioButton.text_1")); // NOI18N

        searchTypeGroup.add(regexRadioButton);
        org.openide.awt.Mnemonics.setLocalizedText(regexRadioButton, org.openide.util.NbBundle.getMessage(MultiCaseKeywordSearchPanel.class, "MultiCaseKeywordSearchPanel.regexRadioButton.text_1")); // NOI18N

        casesScrollPane.setPreferredSize(new java.awt.Dimension(174, 281));

        casesPanel.setBackground(new java.awt.Color(255, 255, 255));
        casesPanel.setPreferredSize(new java.awt.Dimension(152, 197));
        casesPanel.setLayout(new javax.swing.BoxLayout(casesPanel, javax.swing.BoxLayout.Y_AXIS));
        casesScrollPane.setViewportView(casesPanel);

        org.openide.awt.Mnemonics.setLocalizedText(casesLabel, org.openide.util.NbBundle.getMessage(MultiCaseKeywordSearchPanel.class, "MultiCaseKeywordSearchPanel.casesLabel.text_1")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(resultsLabel, org.openide.util.NbBundle.getMessage(MultiCaseKeywordSearchPanel.class, "MultiCaseKeywordSearchPanel.resultsLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(uncheckButton, org.openide.util.NbBundle.getMessage(MultiCaseKeywordSearchPanel.class, "MultiCaseKeywordSearchPanel.uncheckButton.text")); // NOI18N
        uncheckButton.setMargin(new java.awt.Insets(2, 6, 2, 6));
        uncheckButton.setMaximumSize(new java.awt.Dimension(84, 23));
        uncheckButton.setMinimumSize(new java.awt.Dimension(84, 23));
        uncheckButton.setPreferredSize(new java.awt.Dimension(84, 23));
        uncheckButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                uncheckButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(checkButton, org.openide.util.NbBundle.getMessage(MultiCaseKeywordSearchPanel.class, "MultiCaseKeywordSearchPanel.checkButton.text")); // NOI18N
        checkButton.setMaximumSize(new java.awt.Dimension(84, 23));
        checkButton.setMinimumSize(new java.awt.Dimension(84, 23));
        checkButton.setPreferredSize(new java.awt.Dimension(84, 23));
        checkButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                checkButtonActionPerformed(evt);
            }
        });

        toolDescriptionTextArea.setEditable(false);
        toolDescriptionTextArea.setBackground(new java.awt.Color(240, 240, 240));
        toolDescriptionTextArea.setColumns(20);
        toolDescriptionTextArea.setFont(new java.awt.Font("Tahoma", 0, 11)); // NOI18N
        toolDescriptionTextArea.setLineWrap(true);
        toolDescriptionTextArea.setRows(3);
        toolDescriptionTextArea.setText(org.openide.util.NbBundle.getMessage(MultiCaseKeywordSearchPanel.class, "MultiCaseKeywordSearchPanel.toolDescriptionTextArea.text")); // NOI18N
        toolDescriptionTextArea.setWrapStyleWord(true);
        toolDescriptionTextArea.setFocusable(false);
        toolDescriptionScrollPane.setViewportView(toolDescriptionTextArea);

        resultsScrollPane.setMinimumSize(new java.awt.Dimension(100, 40));
        resultsScrollPane.setPreferredSize(new java.awt.Dimension(200, 100));
        resultsScrollPane.setRequestFocusEnabled(false);

        org.openide.awt.Mnemonics.setLocalizedText(cancelButton, org.openide.util.NbBundle.getMessage(MultiCaseKeywordSearchPanel.class, "MultiCaseKeywordSearchPanel.cancelButton.text")); // NOI18N
        cancelButton.setEnabled(false);
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });

        warningLabel.setForeground(new java.awt.Color(200, 0, 0));
        org.openide.awt.Mnemonics.setLocalizedText(warningLabel, org.openide.util.NbBundle.getMessage(MultiCaseKeywordSearchPanel.class, "MultiCaseKeywordSearchPanel.warningLabel.text")); // NOI18N
        warningLabel.setFocusable(false);

        org.openide.awt.Mnemonics.setLocalizedText(exportButton, org.openide.util.NbBundle.getMessage(MultiCaseKeywordSearchPanel.class, "MultiCaseKeywordSearchPanel.exportButton.text")); // NOI18N
        exportButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        exportButton.setMaximumSize(new java.awt.Dimension(84, 23));
        exportButton.setMinimumSize(new java.awt.Dimension(84, 23));
        exportButton.setPreferredSize(new java.awt.Dimension(84, 23));
        exportButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exportButtonActionPerformed(evt);
            }
        });

        resultsCountLabel.setHorizontalAlignment(javax.swing.SwingConstants.TRAILING);
        org.openide.awt.Mnemonics.setLocalizedText(resultsCountLabel, org.openide.util.NbBundle.getMessage(MultiCaseKeywordSearchPanel.class, "MultiCaseKeywordSearchPanel.resultsCountLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(viewErrorsButton, org.openide.util.NbBundle.getMessage(MultiCaseKeywordSearchPanel.class, "MultiCaseKeywordSearchPanel.viewErrorsButton.text")); // NOI18N
        viewErrorsButton.setEnabled(false);
        viewErrorsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                viewErrorsButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(pickCasesButton, org.openide.util.NbBundle.getMessage(MultiCaseKeywordSearchPanel.class, "MultiCaseKeywordSearchPanel.pickCasesButton.text_1")); // NOI18N
        pickCasesButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                pickCasesButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(exactRadioButton)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(substringRadioButton)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(regexRadioButton))
                            .addComponent(keywordTextField, javax.swing.GroupLayout.DEFAULT_SIZE, 679, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(toolDescriptionScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 295, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(casesLabel)
                            .addComponent(casesScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                                .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                                    .addComponent(searchButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                    .addComponent(pickCasesButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                                .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                                    .addComponent(uncheckButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                    .addComponent(checkButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(resultsLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 154, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(resultsCountLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 98, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addComponent(resultsScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(viewErrorsButton)
                                    .addComponent(warningLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 695, Short.MAX_VALUE))
                                .addGap(14, 14, 14)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addComponent(exportButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(cancelButton, javax.swing.GroupLayout.DEFAULT_SIZE, 87, Short.MAX_VALUE))))))
                .addContainerGap())
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(layout.createSequentialGroup()
                    .addGap(196, 196, 196)
                    .addComponent(searchProgressBar, javax.swing.GroupLayout.DEFAULT_SIZE, 769, Short.MAX_VALUE)
                    .addGap(108, 108, 108)))
        );

        layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {checkButton, uncheckButton});

        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(keywordTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(regexRadioButton, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                .addComponent(exactRadioButton)
                                .addComponent(substringRadioButton))))
                    .addComponent(toolDescriptionScrollPane))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(casesLabel)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(resultsLabel)
                        .addComponent(resultsCountLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 14, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(resultsScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(casesScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(uncheckButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(checkButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(warningLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 15, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(exportButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(searchButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(viewErrorsButton)
                        .addComponent(pickCasesButton))
                    .addComponent(cancelButton))
                .addContainerGap())
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                    .addContainerGap(433, Short.MAX_VALUE)
                    .addComponent(searchProgressBar, javax.swing.GroupLayout.PREFERRED_SIZE, 22, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addContainerGap()))
        );
    }// </editor-fold>//GEN-END:initComponents

    @Messages({
        "MultiCaseKeywordSearchPanel.warningText.noCases=At least one case must be selected to perform a search.",
        "MultiCaseKeywordSearchPanel.warningText.emptySearch=You must enter something to search for in the text field."
    })
    /**
     * perform a search if the previous search is done or no previous search has
     * occured
     */
    private void searchButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_searchButtonActionPerformed
        if (null == searchThread) {
            Collection<String> cases = getCases(true);
            String searchString = keywordTextField.getText();
            if (cases.isEmpty()) {
                warningLabel.setText(Bundle.MultiCaseKeywordSearchPanel_warningText_noCases());
            } else if (searchString.isEmpty()) {
                warningLabel.setText(Bundle.MultiCaseKeywordSearchPanel_warningText_emptySearch());
            } else {
                //Map case names to CaseNodeData objects
                Collection<CaseNodeData> caseNodeData = cases.stream()
                        .map(c -> caseNameToCaseDataMap.get(c))
                        .collect(Collectors.toList());
                
                //perform the search
                warningLabel.setText("");
                allSearchHits = new ArrayList<>();
                searchExceptions = new ArrayList<>();
                searchEnabled(false);
                exportButton.setEnabled(false);
                outline.setRowSelectionAllowed(false);
                SearchQuery kwsQuery = new SearchQuery(getQueryType(), searchString);
                em.setRootContext(PLEASE_WAIT_NODE);
                resultsCountLabel.setText("");
                searchThread = new SearchThread(caseNodeData, kwsQuery);
                searchThread.registerWithSearcher(MultiCaseKeywordSearchPanel.this);
                searchThread.start();
            }
        }
    }//GEN-LAST:event_searchButtonActionPerformed

    /**
     * Get the cases which match the selected status specified by isSelected.
     *
     * @param isSelected true to get selected cases false to get unselected
     *                   cases
     *
     * @return cases the cases that match the selected status of isSelected
     */
    private Collection<String> getCases(boolean isSelected) {
        Collection<String> cases = new HashSet<>();
        for (Component comp : casesPanel.getComponents()) {
            if (comp instanceof JCheckBox) {
                if (((AbstractButton) comp).isSelected() == isSelected) {
                    cases.add(((AbstractButton) comp).getText());
                }
            }
        }
        return cases;
    }

    /**
     * Get the type of Query which was selected by the user.
     *
     * @return one of the values of the QueryType enum
     */
    private QueryType getQueryType() {
        String queryTypeText = "";
        Enumeration<AbstractButton> buttonGroup = searchTypeGroup.getElements();
        while (buttonGroup.hasMoreElements()) {
            AbstractButton dspButton = buttonGroup.nextElement();
            if (dspButton.isSelected()) {
                queryTypeText = dspButton.getText();
                break;
            }
        }
        if (queryTypeText.equals(substringRadioButton.getText())) {
            return QueryType.SUBSTRING;
        } else if (queryTypeText.equals(regexRadioButton.getText())) {
            return QueryType.REGEX;
        } else {
            //default to Exact match          
            return QueryType.EXACT_MATCH;
        }
    }

    /**
     * Set the column widths to have their width influenced by the width of the
     * content in them for up to the first hundred rows.
     */
    private void setColumnWidths() {
        int widthLimit = 1000;
        int margin = 4;
        int padding = 8;
        for (int col = 0; col < outline.getColumnCount(); col++) {
            int width = 115; //min initial width for columns 
            int rowsToResize = Math.min(outline.getRowCount(), 100);
            for (int row = 0; row < rowsToResize; row++) {
                if (outline.getValueAt(row, col) != null) {
                    TableCellRenderer renderer = outline.getCellRenderer(row, col);
                    Component comp = outline.prepareRenderer(renderer, row, col);
                    width = Math.max(comp.getPreferredSize().width, width);
                }

            }
            width += 2 * margin + padding;
            width = Math.min(width, widthLimit);
            TableColumn column = outline.getColumnModel().getColumn(outline.convertColumnIndexToModel(col));
            column.setPreferredWidth(width);
        }
        resultsScrollPane.setPreferredSize(new Dimension(outline.getPreferredSize().width, resultsScrollPane.getPreferredSize().height));
    }

    /**
     * Un-select all check boxes in the cases list
     *
     * @param evt ignored
     */
    private void uncheckButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_uncheckButtonActionPerformed
        allCheckboxesSetSelected(false);
    }//GEN-LAST:event_uncheckButtonActionPerformed

    /**
     * Select all check boxes in the cases list
     *
     * @param evt ignored
     */
    private void checkButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_checkButtonActionPerformed
        allCheckboxesSetSelected(true);
    }//GEN-LAST:event_checkButtonActionPerformed

    /**
     * Cancel the current multi-case search which is being performed.
     *
     * @param evt ignored
     */
    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
        cancelSearch();
    }//GEN-LAST:event_cancelButtonActionPerformed

    /**
     * Cancel the current multi-case search which is being performed.
     */
    @Messages({
        "MultiCaseKeywordSearchPanel.searchThread.cancellingText=Cancelling search"})
    private void cancelSearch() {
        if (null != searchThread) {
            warningLabel.setText(Bundle.MultiCaseKeywordSearchPanel_searchThread_cancellingText());
            searchThread.interrupt();
        }
    }

    @Messages({"MultiCaseKeywordSearchPanel.searchResultsExport.csvExtensionFilterlbl=Comma Separated Values File (csv)",
        "MultiCaseKeywordSearchPanel.searchResultsExport.featureName=Search Results Export",
        "MultiCaseKeywordSearchPanel.searchResultsExport.failedExportMsg=Export of search results failed"
    })
    /**
     * Export the currently displayed search results to a file specified by the
     * user with data saved in comma seperated format.
     */
    private void exportButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportButtonActionPerformed
        JFileChooser chooser = new JFileChooser();
        final String EXTENSION = "csv"; //NON-NLS
        FileNameExtensionFilter csvFilter = new FileNameExtensionFilter(
                Bundle.MultiCaseKeywordSearchPanel_searchResultsExport_csvExtensionFilterlbl(), EXTENSION);
        chooser.setFileFilter(csvFilter);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setName("Choose file to export results to");
        chooser.setMultiSelectionEnabled(false);
        int returnVal = chooser.showSaveDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File selFile = chooser.getSelectedFile();
            if (selFile == null) {
                JOptionPane.showMessageDialog(this,
                        Bundle.MultiCaseKeywordSearchPanel_searchResultsExport_failedExportMsg(),
                        Bundle.MultiCaseKeywordSearchPanel_searchResultsExport_featureName(),
                        JOptionPane.WARNING_MESSAGE);
                LOGGER.warning("Selected file was null, when trying to export search results");
                return;
            }
            String fileAbs = selFile.getAbsolutePath();
            if (!fileAbs.endsWith("." + EXTENSION)) {
                fileAbs = fileAbs + "." + EXTENSION;
                selFile = new File(fileAbs);
            }
            saveResultsAsTextFile(selFile);
        }
    }//GEN-LAST:event_exportButtonActionPerformed

    private void viewErrorsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_viewErrorsButtonActionPerformed
        displaySearchErrors();
    }//GEN-LAST:event_viewErrorsButtonActionPerformed

    private void pickCasesButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pickCasesButtonActionPerformed
        if (currentConfirmedSelections != null) {
            caseSelectionDialog.setNodeSelections(currentConfirmedSelections);
        }
        caseSelectionDialog.setVisible(true);
        
    }//GEN-LAST:event_pickCasesButtonActionPerformed

    private void substringRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_substringRadioButtonActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_substringRadioButtonActionPerformed

    /**
     * Set the user interface elements to reflect whether the search feature is
     * currently enabled or disabled.
     *
     * @param canSearch True if the search feature should be enabled, false if
     *                  it should be disabled.
     */
    private void searchEnabled(boolean canSearch) {
        searchButton.setEnabled(canSearch);
        cancelButton.setEnabled(!canSearch);
        viewErrorsButton.setEnabled(canSearch);
        viewErrorsButton.setVisible(!searchExceptions.isEmpty());
    }

    @Messages({"# {0} - file name",
        "MultiCaseKeywordSearchPanel.searchResultsExport.fileExistPrompt=File {0} exists, overwrite?",
        "# {0} - file name",
        "MultiCaseKeywordSearchPanel.searchResultsExport.exportMsg=Search results exported to {0}"
    })
    /**
     * Saves the results to the file specified
     */
    private void saveResultsAsTextFile(File resultsFile) {
        if (resultsFile.exists()) {
            //if the file already exists ask the user how to proceed
            boolean shouldWrite = JOptionPane.showConfirmDialog(null,
                    Bundle.MultiCaseKeywordSearchPanel_searchResultsExport_fileExistPrompt(resultsFile.getName()),
                    Bundle.MultiCaseKeywordSearchPanel_searchResultsExport_featureName(),
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)
                    == JOptionPane.YES_OPTION;
            if (!shouldWrite) {
                return;
            }
        }
        try {
            BufferedWriter resultsWriter;
            resultsWriter = new BufferedWriter(new FileWriter(resultsFile));
            int col = 0;
            //write headers
            while (col < outline.getColumnCount()) {

                resultsWriter.write(outline.getColumnName(col));
                col++;
                if (col < outline.getColumnCount()) {
                    resultsWriter.write(",");
                }
            }
            resultsWriter.write(System.lineSeparator());
            //write data
            Children resultsChildren = em.getRootContext().getChildren();
            for (int row = 0; row < resultsChildren.getNodesCount(); row++) {
                col = 0;
                while (col < outline.getColumnCount()) {
                    if (outline.getValueAt(row, col) instanceof Node.Property) {
                        resultsWriter.write(((Node.Property) outline.getValueAt(row, col)).getValue().toString());
                    } else {
                        resultsWriter.write(outline.getValueAt(row, col).toString());
                    }
                    col++;
                    if (col < outline.getColumnCount()) {
                        resultsWriter.write(",");
                    }
                }
                resultsWriter.write(System.lineSeparator());
            }
            resultsWriter.flush();
            resultsWriter.close();
            setColumnWidths();
            JOptionPane.showMessageDialog(
                    WindowManager.getDefault().getMainWindow(),
                    Bundle.MultiCaseKeywordSearchPanel_searchResultsExport_exportMsg(resultsFile.getName()),
                    Bundle.MultiCaseKeywordSearchPanel_searchResultsExport_featureName(),
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (IllegalAccessException | IOException | InvocationTargetException ex) {
            JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(),
                    Bundle.MultiCaseKeywordSearchPanel_searchResultsExport_failedExportMsg(),
                    Bundle.MultiCaseKeywordSearchPanel_searchResultsExport_featureName(),
                    JOptionPane.WARNING_MESSAGE);
            LOGGER.log(Level.WARNING, "Export of search results failed unable to write results csv file", ex);
        }
    }

    /**
     * Set the selected status of all checkboxes.
     *
     * @param selected true if all checkboxes should be selected, false if no
     *                 check boxes should be selected.
     */
    private void allCheckboxesSetSelected(boolean selected) {
        for (Component comp : casesPanel.getComponents()) {
            if (comp instanceof JCheckBox) {
                ((AbstractButton) comp).setSelected(selected);
            }
        }
    }

    /**
     * Ask the user if they want to continue their search while this window is
     * closed. Cancels the current search if they select no.
     */
    @Messages({
        "MultiCaseKeywordSearchPanel.continueSearch.text=A search is currently being performed. "
        + "Would you like the search to continue in the background while the search window is closed?",
        "MultiCaseKeywordSearchPanel.continueSearch.title=Closing multi-case search"
    })
    void closeSearchPanel() {
        if (cancelButton.isEnabled()) {
            boolean shouldContinueSearch = JOptionPane.showConfirmDialog(null,
                    Bundle.MultiCaseKeywordSearchPanel_continueSearch_text(),
                    Bundle.MultiCaseKeywordSearchPanel_continueSearch_title(),
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)
                    == JOptionPane.YES_OPTION;
            if (!shouldContinueSearch) {
                cancelSearch();
            }
        }
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton cancelButton;
    private javax.swing.JLabel casesLabel;
    private javax.swing.JPanel casesPanel;
    private javax.swing.JScrollPane casesScrollPane;
    private javax.swing.JButton checkButton;
    private javax.swing.JRadioButton exactRadioButton;
    private javax.swing.JButton exportButton;
    private javax.swing.JTextField keywordTextField;
    private javax.swing.JButton pickCasesButton;
    private javax.swing.JRadioButton regexRadioButton;
    private javax.swing.JLabel resultsCountLabel;
    private javax.swing.JLabel resultsLabel;
    private javax.swing.JScrollPane resultsScrollPane;
    private javax.swing.JButton searchButton;
    private javax.swing.JProgressBar searchProgressBar;
    private javax.swing.ButtonGroup searchTypeGroup;
    private javax.swing.JRadioButton substringRadioButton;
    private javax.swing.JScrollPane toolDescriptionScrollPane;
    private javax.swing.JTextArea toolDescriptionTextArea;
    private javax.swing.JButton uncheckButton;
    private javax.swing.JButton viewErrorsButton;
    private javax.swing.JLabel warningLabel;
    // End of variables declaration//GEN-END:variables

    /*
     * A thread that performs a keyword search of cases
     */
    private final class SearchThread extends Thread {

        private final Collection<CaseNodeData> caseNodes;
        private final SearchQuery searchQuery;
        private final MultiCaseSearcher multiCaseSearcher = new MultiCaseSearcher();

        /**
         * Constructs a thread that performs a keyword search of cases
         *
         * @param caseNames The names of the cases to search.
         * @param query     The keyword search query to perform.
         */
        private SearchThread(Collection<CaseNodeData> caseNodes, SearchQuery searchQuery) {
            this.caseNodes = caseNodes;
            this.searchQuery = searchQuery;
        }

        /**
         * Register an object with the MultiCaseSearcher eventBus so that the
         * object's subscribe methods can receive results.
         *
         * @param object the object to register with the MultiCaseSearcher
         */
        private void registerWithSearcher(Object object) {
            multiCaseSearcher.registerWithEventBus(object);
        }

        /**
         * Unregister an object with the MultiCaseSearcher so that the object's
         * subscribe methods no longer receive results.
         *
         * @param object the object to unregister with the MultiCaseSearcher
         */
        private void unregisterWithSearcher(Object object) {
            multiCaseSearcher.unregisterWithEventBus(object);
        }

        @Override
        public void interrupt() {
            super.interrupt();
            //in case it is running a method which causes InterruptedExceptions to be ignored
            multiCaseSearcher.stopMultiCaseSearch();
        }

        @Override
        public void run() {
            multiCaseSearcher.performKeywordSearch(caseNodes, searchQuery, new MultiCaseKeywordSearchProgressIndicator(searchProgressBar));
        }

    }

}
