/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.directorytree;

import java.awt.Cursor;
import java.awt.EventQueue;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyVetoException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import javax.swing.Action;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.TreeSelectionModel;
import org.apache.commons.lang3.StringUtils;
import org.openide.explorer.ExplorerManager;
import org.openide.explorer.ExplorerUtils;
import org.openide.explorer.view.BeanTreeView;
import org.openide.explorer.view.Visualizer;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.NodeNotFoundException;
import org.openide.nodes.NodeOp;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.CasePreferences;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.core.RuntimeProperties;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.corecomponentinterfaces.CoreComponentControl;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataExplorer;
import org.sleuthkit.autopsy.corecomponents.DataResultTopComponent;
import org.sleuthkit.autopsy.corecomponents.TableFilterNode;
import org.sleuthkit.autopsy.corecomponents.ViewPreferencesPanel;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.autopsy.datamodel.ArtifactNodeSelectionInfo;
import org.sleuthkit.autopsy.datamodel.BlackboardArtifactNode;
import org.sleuthkit.autopsy.datamodel.CreditCards;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNode;
import org.sleuthkit.autopsy.datamodel.EmailExtracted;
import org.sleuthkit.autopsy.datamodel.EmptyNode;
import org.sleuthkit.autopsy.datamodel.ExtractedContent;
import org.sleuthkit.autopsy.datamodel.FileTypesByMimeType;
import org.sleuthkit.autopsy.datamodel.InterestingHits;
import org.sleuthkit.autopsy.datamodel.KeywordHits;
import org.sleuthkit.autopsy.datamodel.ResultsNode;
import org.sleuthkit.autopsy.datamodel.AutopsyTreeChildFactory;
import org.sleuthkit.autopsy.datamodel.Tags;
import org.sleuthkit.autopsy.datamodel.ViewsNode;
import org.sleuthkit.autopsy.datamodel.accounts.Accounts;
import org.sleuthkit.autopsy.datamodel.accounts.BINRange;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Top component which displays something.
 */
// Registered as a service provider for DataExplorer in layer.xml
@Messages({
    "DirectoryTreeTopComponent.resultsView.title=Listing"
})
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
public final class DirectoryTreeTopComponent extends TopComponent implements DataExplorer, ExplorerManager.Provider {

    private final transient ExplorerManager em = new ExplorerManager();
    private static DirectoryTreeTopComponent instance;
    private final DataResultTopComponent dataResult = new DataResultTopComponent(Bundle.DirectoryTreeTopComponent_resultsView_title());
    private final ViewPreferencesPanel viewPreferencesPanel = new ViewPreferencesPanel(true);
    private final LinkedList<String[]> backList;
    private final LinkedList<String[]> forwardList;
    private static final String PREFERRED_ID = "DirectoryTreeTopComponent"; //NON-NLS
    private static final Logger LOGGER = Logger.getLogger(DirectoryTreeTopComponent.class.getName());
    private AutopsyTreeChildFactory autopsyTreeChildFactory;
    private Children autopsyTreeChildren;
    private Accounts accounts;
    private boolean showRejectedResults;
    private static final long DEFAULT_DATASOURCE_GROUPING_THRESHOLD = 5; // Threshold for prompting the user about grouping by data source
    private static final String GROUPING_THRESHOLD_NAME = "GroupDataSourceThreshold";
    private static final String SETTINGS_FILE = "CasePreferences.properties"; //NON-NLS

    /**
     * the constructor
     */
    private DirectoryTreeTopComponent() {
        initComponents();

        // only allow one item to be selected at a time
        getTree().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        //Hook into the JTree and pre-expand the Views Node and Results node when a user
        //expands an item in the tree that makes these nodes visible.
        ((ExpansionBeanTreeView) getTree()).addTreeExpansionListener(new TreeExpansionListener() {
            @Override
            public void treeExpanded(TreeExpansionEvent event) {
                //Bail immediately if we are not in the Group By view.
                //Assumption here is that the views are already expanded.
                if (!CasePreferences.getGroupItemsInTreeByDataSource()) {
                    return;
                }

                Node expandedNode = Visualizer.findNode(event.getPath().getLastPathComponent());
                for (Node child : em.getRootContext().getChildren().getNodes()) {
                    if (child.equals(expandedNode)) {
                        preExpandNodes(child.getChildren());
                    }
                }
            }

            @Override
            public void treeCollapsed(TreeExpansionEvent event) {
                //Do nothing
            }

        });
        // remove the close button
        putClientProperty(TopComponent.PROP_CLOSING_DISABLED, Boolean.TRUE);
        setName(NbBundle.getMessage(DirectoryTreeTopComponent.class, "CTL_DirectoryTreeTopComponent"));
        setToolTipText(NbBundle.getMessage(DirectoryTreeTopComponent.class, "HINT_DirectoryTreeTopComponent"));

        subscribeToChangeEvents();
        associateLookup(ExplorerUtils.createLookup(em, getActionMap()));

        // set the back & forward list and also disable the back & forward button
        this.backList = new LinkedList<>();
        this.forwardList = new LinkedList<>();
        backButton.setEnabled(false);
        forwardButton.setEnabled(false);

        viewPreferencesPopupMenu.add(viewPreferencesPanel);
        viewPreferencesPopupMenu.setSize(viewPreferencesPanel.getPreferredSize().width + 6, viewPreferencesPanel.getPreferredSize().height + 6);
        viewPreferencesPopupMenu.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                openViewPreferencesButton.setSelected(true);
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                openViewPreferencesButton.setSelected(false);
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
                openViewPreferencesButton.setSelected(false);
            }
        });
    }

    /**
     * Pre-expands the Views node, the Results node, and all of the children of
     * the Results node.
     *
     * @param rootChildren Children node containing Results node and Views node.
     */
    private void preExpandNodes(Children rootChildren) {
        BeanTreeView tree = getTree();

        Node results = rootChildren.findChild(ResultsNode.NAME);
        if (!Objects.isNull(results)) {
            tree.expandNode(results);
            Children resultsChildren = results.getChildren();
            Arrays.stream(resultsChildren.getNodes()).forEach(tree::expandNode);
        }

        Node views = rootChildren.findChild(ViewsNode.NAME);
        if (!Objects.isNull(views)) {
            tree.expandNode(views);
        }
    }

    /**
     * Make this TopComponent a listener to various change events.
     */
    private void subscribeToChangeEvents() {
        UserPreferences.addChangeListener(new PreferenceChangeListener() {
            @Override
            public void preferenceChange(PreferenceChangeEvent evt) {
                switch (evt.getKey()) {
                    case UserPreferences.DISPLAY_TIMES_IN_LOCAL_TIME:
                    case UserPreferences.TIME_ZONE_FOR_DISPLAYS:
                    case UserPreferences.HIDE_KNOWN_FILES_IN_DATA_SRCS_TREE:
                    case UserPreferences.HIDE_SLACK_FILES_IN_DATA_SRCS_TREE:
                    case UserPreferences.HIDE_SCO_COLUMNS:
                    case UserPreferences.DISPLAY_TRANSLATED_NAMES:
                    case UserPreferences.KEEP_PREFERRED_VIEWER:
                    case UserPreferences.TEXT_TRANSLATOR_NAME:
                        refreshContentTreeSafe();
                        break;
                    case UserPreferences.SHOW_ONLY_CURRENT_USER_TAGS:
                        refreshTagsTree();
                        break;
                    case UserPreferences.HIDE_KNOWN_FILES_IN_VIEWS_TREE:
                    case UserPreferences.HIDE_SLACK_FILES_IN_VIEWS_TREE:
                        // TODO: Need a way to refresh the Views subtree alone.
                        refreshContentTreeSafe();
                        break;
                }
            }
        });

        Case.addEventTypeSubscriber(EnumSet.of(Case.Events.CURRENT_CASE, Case.Events.DATA_SOURCE_ADDED), this);
        this.em.addPropertyChangeListener(this);
    }

    public void setDirectoryListingActive() {
        this.dataResult.requestActive();
    }

    public void openDirectoryListing() {
        this.dataResult.open();
    }

    public DataResultTopComponent getDirectoryListing() {
        return this.dataResult;
    }

    /**
     * Show rejected results?
     *
     * @return True if showing rejected results; otherwise false.
     */
    public boolean getShowRejectedResults() {
        return showRejectedResults;
    }

    /**
     * Setter to determine if rejected results should be shown or not.
     *
     * @param showRejectedResults True if showing rejected results; otherwise
     *                            false.
     */
    public void setShowRejectedResults(boolean showRejectedResults) {
        this.showRejectedResults = showRejectedResults;
        if (accounts != null) {
            accounts.setShowRejected(showRejectedResults);
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        viewPreferencesPopupMenu = new javax.swing.JPopupMenu();
        treeView = new ExpansionBeanTreeView();
        backButton = new javax.swing.JButton();
        forwardButton = new javax.swing.JButton();
        openViewPreferencesButton = new javax.swing.JButton();

        treeView.setBorder(null);

        backButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/directorytree/btn_step_back.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(backButton, org.openide.util.NbBundle.getMessage(DirectoryTreeTopComponent.class, "DirectoryTreeTopComponent.backButton.text")); // NOI18N
        backButton.setBorderPainted(false);
        backButton.setContentAreaFilled(false);
        backButton.setDisabledIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/directorytree/btn_step_back_disabled.png"))); // NOI18N
        backButton.setMargin(new java.awt.Insets(2, 0, 2, 0));
        backButton.setMaximumSize(new java.awt.Dimension(55, 100));
        backButton.setMinimumSize(new java.awt.Dimension(5, 5));
        backButton.setPreferredSize(new java.awt.Dimension(24, 24));
        backButton.setRolloverIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/directorytree/btn_step_back_hover.png"))); // NOI18N
        backButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                backButtonActionPerformed(evt);
            }
        });

        forwardButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/directorytree/btn_step_forward.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(forwardButton, org.openide.util.NbBundle.getMessage(DirectoryTreeTopComponent.class, "DirectoryTreeTopComponent.forwardButton.text")); // NOI18N
        forwardButton.setBorderPainted(false);
        forwardButton.setContentAreaFilled(false);
        forwardButton.setDisabledIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/directorytree/btn_step_forward_disabled.png"))); // NOI18N
        forwardButton.setMargin(new java.awt.Insets(2, 0, 2, 0));
        forwardButton.setMaximumSize(new java.awt.Dimension(55, 100));
        forwardButton.setMinimumSize(new java.awt.Dimension(5, 5));
        forwardButton.setPreferredSize(new java.awt.Dimension(24, 24));
        forwardButton.setRolloverIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/directorytree/btn_step_forward_hover.png"))); // NOI18N
        forwardButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                forwardButtonActionPerformed(evt);
            }
        });

        openViewPreferencesButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/directorytree/view-preferences-23.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(openViewPreferencesButton, org.openide.util.NbBundle.getMessage(DirectoryTreeTopComponent.class, "DirectoryTreeTopComponent.openViewPreferencesButton.text")); // NOI18N
        openViewPreferencesButton.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));
        openViewPreferencesButton.setBorderPainted(false);
        openViewPreferencesButton.setContentAreaFilled(false);
        openViewPreferencesButton.setMaximumSize(new java.awt.Dimension(24, 24));
        openViewPreferencesButton.setMinimumSize(new java.awt.Dimension(24, 24));
        openViewPreferencesButton.setPreferredSize(new java.awt.Dimension(24, 24));
        openViewPreferencesButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                openViewPreferencesButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(treeView)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(backButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(forwardButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 140, Short.MAX_VALUE)
                .addComponent(openViewPreferencesButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(openViewPreferencesButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(backButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(forwardButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(treeView, javax.swing.GroupLayout.DEFAULT_SIZE, 919, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void backButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_backButtonActionPerformed
        // change the cursor to "waiting cursor" for this operation
        this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        // the end is the current place,
        String[] currentNodePath = backList.pollLast();
        forwardList.addLast(currentNodePath);
        forwardButton.setEnabled(true);

        /*
         * We peek instead of poll because we use its existence in the list
         * later on so that we do not reset the forward list after the selection
         * occurs.
         */
        String[] newCurrentNodePath = backList.peekLast();

        // enable / disable the back and forward button
        if (backList.size() > 1) {
            backButton.setEnabled(true);
        } else {
            backButton.setEnabled(false);
        }

        // update the selection on directory tree
        setSelectedNode(newCurrentNodePath, null);

        this.setCursor(null);
    }//GEN-LAST:event_backButtonActionPerformed

    private void forwardButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_forwardButtonActionPerformed
        // change the cursor to "waiting cursor" for this operation
        this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        String[] newCurrentNodePath = forwardList.pollLast();
        if (!forwardList.isEmpty()) {
            forwardButton.setEnabled(true);
        } else {
            forwardButton.setEnabled(false);
        }

        backList.addLast(newCurrentNodePath);
        backButton.setEnabled(true);

        // update the selection on directory tree
        setSelectedNode(newCurrentNodePath, null);

        this.setCursor(null);
    }//GEN-LAST:event_forwardButtonActionPerformed

    private void openViewPreferencesButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_openViewPreferencesButtonActionPerformed
        viewPreferencesPanel.load();
        viewPreferencesPopupMenu.show(openViewPreferencesButton, 0, openViewPreferencesButton.getHeight() - 1);
    }//GEN-LAST:event_openViewPreferencesButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton backButton;
    private javax.swing.JButton forwardButton;
    private javax.swing.JButton openViewPreferencesButton;
    private javax.swing.JScrollPane treeView;
    private javax.swing.JPopupMenu viewPreferencesPopupMenu;
    // End of variables declaration//GEN-END:variables

    /**
     * Gets default instance. Do not use directly: reserved for *.settings files
     * only, i.e. deserialization routines; otherwise you could get a
     * non-deserialized instance. To obtain the singleton instance, use
     * {@link #findInstance}.
     *
     * @return instance - the default instance
     */
    public static synchronized DirectoryTreeTopComponent getDefault() {
        if (instance == null) {
            instance = new DirectoryTreeTopComponent();
        }
        return instance;
    }

    /**
     * Obtain the DirectoryTreeTopComponent instance. Never call
     * {@link #getDefault} directly!
     *
     * @return getDefault() - the default instance
     */
    public static synchronized DirectoryTreeTopComponent findInstance() {
        WindowManager winManager = WindowManager.getDefault();
        TopComponent win = winManager.findTopComponent(PREFERRED_ID);
        if (win == null) {
            LOGGER.warning(
                    "Cannot find " + PREFERRED_ID + " component. It will not be located properly in the window system."); //NON-NLS
            return getDefault();
        }
        if (win instanceof DirectoryTreeTopComponent) {
            return (DirectoryTreeTopComponent) win;
        }
        LOGGER.warning(
                "There seem to be multiple components with the '" + PREFERRED_ID //NON-NLS
                + "' ID. That is a potential source of errors and unexpected behavior."); //NON-NLS
        return getDefault();
    }

    /**
     * Overwrite when you want to change default persistence type. Default
     * persistence type is PERSISTENCE_ALWAYS
     *
     * @return TopComponent.PERSISTENCE_ALWAYS
     */
    @Override
    public int getPersistenceType() {
        return TopComponent.PERSISTENCE_NEVER;
    }

    /**
     * Ask the user if they want to group by data source when opening a large
     * case.
     *
     * @param dataSourceCount The number of data sources in the case.
     */
    private void promptForDataSourceGrouping(int dataSourceCount) {
        if (CasePreferences.getGroupItemsInTreeByDataSource() == null) {
            GroupDataSourcesDialog dialog = new GroupDataSourcesDialog(dataSourceCount);
            dialog.display();
            if (dialog.groupByDataSourceSelected()) {
                CasePreferences.setGroupItemsInTreeByDataSource(true);
                refreshContentTreeSafe();
            } else {
                CasePreferences.setGroupItemsInTreeByDataSource(false);
            }
        }
    }

    /**
     * Called only when top component was closed on all workspaces before and
     * now is opened for the first time on some workspace. The intent is to
     * provide subclasses information about TopComponent's life cycle across all
     * existing workspaces. Subclasses will usually perform initializing tasks
     * here.
     */
    @NbBundle.Messages({"# {0} - dataSourceCount",
        "DirectoryTreeTopComponent.componentOpened.groupDataSources.text=This case contains {0} data sources. Would you like to group by data source for faster loading?",
        "DirectoryTreeTopComponent.componentOpened.groupDataSources.title=Group by data source?"})
    @Override
    public void componentOpened() {
        // change the cursor to "waiting cursor" for this operation
        this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        Case currentCase = null;
        try {
            currentCase = Case.getCurrentCaseThrows();
        } catch (NoCurrentCaseException ex) {
            // No open case.
        }

        // close the top component if there's no image in this case
        if (null == currentCase || currentCase.hasData() == false) {
            getTree().setRootVisible(false); // hide the root
        } else {
            // If the case contains a lot of data sources, and they aren't already grouping
            // by data source, give the user the option to do so before loading the tree.
            if (RuntimeProperties.runningWithGUI()) {
                long threshold = DEFAULT_DATASOURCE_GROUPING_THRESHOLD;
                if (ModuleSettings.settingExists(ModuleSettings.MAIN_SETTINGS, GROUPING_THRESHOLD_NAME)) {
                    try {
                        threshold = Long.parseLong(ModuleSettings.getConfigSetting(ModuleSettings.MAIN_SETTINGS, GROUPING_THRESHOLD_NAME));
                    } catch (NumberFormatException ex) {
                        LOGGER.log(Level.SEVERE, "Group data sources threshold is not a number", ex);
                    }
                } else {
                    ModuleSettings.setConfigSetting(ModuleSettings.MAIN_SETTINGS, GROUPING_THRESHOLD_NAME, String.valueOf(threshold));
                }

                try {
                    int dataSourceCount = currentCase.getDataSources().size();
                    if (!Objects.equals(CasePreferences.getGroupItemsInTreeByDataSource(), true)
                            && dataSourceCount > threshold) {
                        promptForDataSourceGrouping(dataSourceCount);
                    }
                } catch (TskCoreException ex) {
                    LOGGER.log(Level.SEVERE, "Error loading data sources", ex);
                }
            }

            // if there's at least one image, load the image and open the top componen
            autopsyTreeChildFactory = new AutopsyTreeChildFactory();
            autopsyTreeChildren = Children.create(autopsyTreeChildFactory, true);
            Node root = new AbstractNode(autopsyTreeChildren) {
                //JIRA-2807: What is the point of these overrides?
                /**
                 * to override the right click action in the white blank space
                 * area on the directory tree window
                 */
                @Override
                public Action[] getActions(boolean popup) {
                    return new Action[]{};
                }

                // Overide the AbstractNode use of DefaultHandle to return
                // a handle which can be serialized without a parent
                @Override
                public Node.Handle getHandle() {
                    return new Node.Handle() {
                        @Override
                        public Node getNode() throws IOException {
                            return em.getRootContext();
                        }
                    };
                }
            };

            root = new DirectoryTreeFilterNode(root, true);

            em.setRootContext(root);
            em.getRootContext().setName(currentCase.getName());
            em.getRootContext().setDisplayName(currentCase.getName());
            getTree().setRootVisible(false); // hide the root

            // Reset the forward and back lists because we're resetting the root context
            resetHistory();
            new SwingWorker<Node[], Void>() {
                @Override
                protected Node[] doInBackground() throws Exception {
                    Children rootChildren = em.getRootContext().getChildren();
                    preExpandNodes(rootChildren);
                    /*
                     * JIRA-2806: What is this supposed to do? Right now it
                     * selects the data sources node, but the comment seems to
                     * indicate it is supposed to select the first datasource.
                     */
                    // select the first image node, if there is one
                    // (this has to happen after dataResult is opened, because the event
                    // of changing the selected node fires a handler that tries to make
                    // dataResult active)
                    if (rootChildren.getNodesCount() > 0) {
                        return new Node[]{rootChildren.getNodeAt(0)};
                    }
                    return new Node[]{};
                }

                @Override
                protected void done() {
                    super.done();

                    // if the dataResult is not opened
                    if (!dataResult.isOpened()) {
                        dataResult.open(); // open the data result top component as well when the directory tree is opened
                    }
                    /*
                     * JIRA-2806: What is this supposed to do?
                     */
                    // select the first image node, if there is one
                    // (this has to happen after dataResult is opened, because the event
                    // of changing the selected node fires a handler that tries to make
                    // dataResult active)
                    try {
                        Node[] selections = get();
                        if (selections != null && selections.length > 0) {
                            em.setSelectedNodes(selections);
                        }
                    } catch (PropertyVetoException ex) {
                        LOGGER.log(Level.SEVERE, "Error setting default selected node.", ex); //NON-NLS
                    } catch (InterruptedException | ExecutionException ex) {
                        LOGGER.log(Level.SEVERE, "Error expanding tree to initial state.", ex); //NON-NLS
                    } finally {
                        setCursor(null);
                    }
                }
            }.execute();
        }
    }

    /**
     * Called only when top component was closed so that now it is closed on all
     * workspaces in the system. The intent is to provide subclasses information
     * about TopComponent's life cycle across workspaces. Subclasses will
     * usually perform cleaning tasks here.
     */
    @Override
    public void componentClosed() {
        //@@@ push the selection node to null?
        autopsyTreeChildren = null;
    }

    void writeProperties(java.util.Properties p) {
        // better to version settings since initial version as advocated at
        // http://wiki.apidesign.org/wiki/PropertyFiles
        p.setProperty("version", "1.0");
        // TODO store your settings
    }

    Object readProperties(java.util.Properties p) {
        if (instance == null) {
            instance = this;
        }
        instance.readPropertiesImpl(p);
        return instance;
    }

    private void readPropertiesImpl(java.util.Properties p) {
        String version = p.getProperty("version");
        // TODO read your settings according to their version
    }

    /**
     * Returns the unique ID of this TopComponent
     *
     * @return PREFERRED_ID the unique ID of this TopComponent
     */
    @Override
    protected String preferredID() {
        return PREFERRED_ID;
    }

    @Override
    public boolean canClose() {
        /*
         * Only allow the main tree view in the left side of the main window to
         * be closed if there is no opne case or the open case has no data
         * sources.
         */
        try {
            Case openCase = Case.getCurrentCaseThrows();
            return openCase.hasData() == false;
        } catch (NoCurrentCaseException ex) {
            return true;
        }
    }

    /**
     * Gets the explorer manager.
     *
     * @return the explorer manager
     */
    @Override
    public ExplorerManager getExplorerManager() {
        return this.em;
    }

    /**
     * Right click action for this top component window
     *
     * @return actions the list of actions
     */
    @Override
    public Action[] getActions() {
        return new Action[]{};
    }

    /**
     * Gets the original selected node on the explorer manager
     *
     * @return node the original selected Node
     */
    public Node getSelectedNode() {
        Node result = null;

        Node[] selectedNodes = this.getExplorerManager().getSelectedNodes();
        if (selectedNodes.length > 0) {
            result = selectedNodes[0];
        }
        return result;
    }

    /**
     * The "listener" that monitors changes made in the Case class. This serves
     * the purpose of keeping the UI in sync with the data as it changes.
     *
     * @param event The property change event.
     */
    @Override
    public void propertyChange(PropertyChangeEvent event) {
        if (RuntimeProperties.runningWithGUI()) {
            String changed = event.getPropertyName();
            if (changed.equals(Case.Events.CURRENT_CASE.toString())) { // changed current case
                // When a case is closed, the old value of this property is the 
                // closed Case object and the new value is null. When a case is 
                // opened, the old value is null and the new value is the new Case
                // object.
                // @@@ This needs to be revisited. Perhaps case closed and case
                // opened events instead of property change events would be a better
                // solution. Either way, more probably needs to be done to clean up
                // data model objects when a case is closed.
                if (event.getOldValue() != null && event.getNewValue() == null) {
                    // The current case has been closed. Reset the ExplorerManager.
                    SwingUtilities.invokeLater(() -> {
                        Node emptyNode = new AbstractNode(Children.LEAF);
                        em.setRootContext(emptyNode);
                    });
                } else if (event.getNewValue() != null) {
                    // A new case has been opened. Reset the ExplorerManager. 
                    Case newCase = (Case) event.getNewValue();
                    final String newCaseName = newCase.getName();
                    SwingUtilities.invokeLater(() -> {
                        em.getRootContext().setName(newCaseName);
                        em.getRootContext().setDisplayName(newCaseName);

                        // Reset the forward and back
                        // buttons. Note that a call to CoreComponentControl.openCoreWindows()
                        // by the new Case object will lead to a componentOpened() call
                        // that will repopulate the tree.
                        // @@@ The repopulation of the tree in this fashion also merits
                        // reconsideration.
                        resetHistory();
                    });
                }
            } // if the image is added to the case
            else if (changed.equals(Case.Events.DATA_SOURCE_ADDED.toString())) {
                /**
                 * Checking for a current case is a stop gap measure until a
                 * different way of handling the closing of cases is worked out.
                 * Currently, remote events may be received for a case that is
                 * already closed.
                 */
                try {
                    Case.getCurrentCaseThrows();
                    /*
                     * In case the Case 'updateGUIForCaseOpened()' method hasn't
                     * already done so, open the tree and all other core
                     * windows.
                     *
                     * TODO: (JIRA-4053) DirectoryTreeTopComponent should not be
                     * responsible for opening core windows. Consider moving
                     * this elsewhere.
                     */
                    SwingUtilities.invokeLater(() -> {
                        if (!DirectoryTreeTopComponent.this.isOpened()) {
                            CoreComponentControl.openCoreWindows();
                        }
                    });
                } catch (NoCurrentCaseException notUsed) {
                    /**
                     * Case is closed, do nothing.
                     */
                }
            } // change in node selection
            else if (changed.equals(ExplorerManager.PROP_SELECTED_NODES)) {
                respondSelection((Node[]) event.getOldValue(), (Node[]) event.getNewValue());
            } 
        }
    }

    /**
     * Event handler to run when selection changed
     *
     * TODO this needs to be revised
     *
     * @param oldNodes
     * @param newNodes
     */
    @NbBundle.Messages("DirectoryTreeTopComponent.emptyMimeNode.text=Data not available. Run file type identification module.")
    void respondSelection(final Node[] oldNodes, final Node[] newNodes) {
        if (!Case.isCaseOpen()) {
            return;
        }

        // Some lock that prevents certain Node operations is set during the
        // ExplorerManager selection-change, so we must handle changes after the
        // selection-change event is processed.
        //TODO find a different way to refresh data result viewer, scheduling this
        //to EDT breaks loading of nodes in the background
        EventQueue.invokeLater(() -> {
            // change the cursor to "waiting cursor" for this operation
            DirectoryTreeTopComponent.this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            try {
                Node treeNode = DirectoryTreeTopComponent.this.getSelectedNode();
                if (treeNode != null) {
                    Node originNode = ((DirectoryTreeFilterNode) treeNode).getOriginal();
                    //set node, wrap in filter node first to filter out children
                    Node drfn = new DataResultFilterNode(originNode, DirectoryTreeTopComponent.this.em);
                    // Create a TableFilterNode with knowledge of the node's type to allow for column order settings
                    if (FileTypesByMimeType.isEmptyMimeTypeNode(originNode)) {
                        //Special case for when File Type Identification has not yet been run and
                        //there are no mime types to populate Files by Mime Type Tree
                        EmptyNode emptyNode = new EmptyNode(Bundle.DirectoryTreeTopComponent_emptyMimeNode_text());
                        dataResult.setNode(new TableFilterNode(emptyNode, true, "This Node Is Empty")); //NON-NLS
                    } else if (originNode instanceof DisplayableItemNode) {
                        dataResult.setNode(new TableFilterNode(drfn, true, ((DisplayableItemNode) originNode).getItemType()));
                    } else {
                        dataResult.setNode(new TableFilterNode(drfn, true));
                    }
                    String displayName = "";
                    Content content = originNode.getLookup().lookup(Content.class);
                    if (content != null) {
                        try {
                            displayName = content.getUniquePath();
                        } catch (TskCoreException ex) {
                            LOGGER.log(Level.SEVERE, "Exception while calling Content.getUniquePath() for node: {0}", originNode); //NON-NLS
                        }
                    } else if (originNode.getLookup().lookup(String.class) != null) {
                        displayName = originNode.getLookup().lookup(String.class);
                    }
                    dataResult.setPath(displayName);
                }
                // set the directory listing to be active
                if (oldNodes != null && newNodes != null
                        && (oldNodes.length == newNodes.length)) {
                    boolean sameNodes = true;
                    for (int i = 0; i < oldNodes.length; i++) {
                        sameNodes = sameNodes && oldNodes[i].getName().equals(newNodes[i].getName());
                    }
                    if (!sameNodes) {
                        dataResult.requestActive();
                    }
                }
            } finally {
                setCursor(null);
            }
        });

        // update the back and forward list
        updateHistory(em.getSelectedNodes());
    }

    private void updateHistory(Node[] selectedNodes) {
        if (selectedNodes.length == 0) {
            return;
        }

        Node selectedNode = selectedNodes[0];
        String selectedNodeName = selectedNode.getName();

        /*
         * get the previous entry to make sure we don't duplicate it. Motivation
         * for this is also that if we used the back button, then we already
         * added the 'current' node to 'back' and we will detect that and not
         * reset the forward list.
         */
        String[] currentLast = backList.peekLast();
        String lastNodeName = null;
        if (currentLast != null && currentLast.length > 0) {
            lastNodeName = currentLast[currentLast.length - 1];
        }

        if (currentLast == null || !selectedNodeName.equals(lastNodeName)) {
            //add to the list if the last if not the same as current
            final String[] selectedPath = NodeOp.createPath(selectedNode, em.getRootContext());
            backList.addLast(selectedPath); // add the node to the "backList"
            if (backList.size() > 1) {
                backButton.setEnabled(true);
            } else {
                backButton.setEnabled(false);
            }

            forwardList.clear(); // clear the "forwardList"
            forwardButton.setEnabled(false); // disable the forward Button
        }
    }

    /**
     * Resets the back and forward list, and also disable the back and forward
     * buttons.
     */
    private void resetHistory() {
        // clear the back and forward list
        backList.clear();
        forwardList.clear();
        backButton.setEnabled(false);
        forwardButton.setEnabled(false);
    }

    /**
     * Gets the tree on this DirectoryTreeTopComponent.
     *
     * @return tree the BeanTreeView
     */
    BeanTreeView getTree() {
        return (BeanTreeView) this.treeView;
    }

    /**
     * Refresh the content node part of the dir tree safely in the EDT thread
     */
    public void refreshContentTreeSafe() {
        SwingUtilities.invokeLater(this::rebuildTree);
    }

    /**
     * Refresh only the tags subtree(s) of the tree view.
     */
    private void refreshTagsTree() {
        SwingUtilities.invokeLater(() -> {
            // Ensure the component children have been created first.
            if (autopsyTreeChildren == null) {
                return;
            }

            if (Objects.equals(CasePreferences.getGroupItemsInTreeByDataSource(), true)) {
                for (Node dataSource : autopsyTreeChildren.getNodes()) {
                    Node tagsNode = dataSource.getChildren().findChild(Tags.getTagsDisplayName());
                    if (tagsNode != null) {
                        //Reports is at the same level as the data sources so we want to ignore it
                        ((Tags.RootNode) tagsNode).refresh();
                    }
                }
            } else {
                Node tagsNode = autopsyTreeChildren.findChild(Tags.getTagsDisplayName());
                if (tagsNode != null) {
                    ((Tags.RootNode) tagsNode).refresh();
                }
            }
        });
    }

    /**
     * Rebuilds the autopsy tree.
     *
     * Does nothing if there is no open case.
     */
    private void rebuildTree() {

        // if no open case or has no data then there is no tree to rebuild
        Case currentCase;
        try {
            currentCase = Case.getCurrentCaseThrows();
        } catch (NoCurrentCaseException ex) {
            return;
        }
        if (null == currentCase || currentCase.hasData() == false) {
            return;
        }

        // refresh all children of the root.
        autopsyTreeChildFactory.refreshChildren();

        // Select the first node and reset the selection history
        // This should happen on the EDT once the tree has been rebuilt.
        // hence the SwingWorker that does this in the done() method
        new SwingWorker<Void, Void>() {

            @Override
            protected Void doInBackground() throws Exception {
                return null;
            }

            @Override
            protected void done() {
                super.done();
                try {
                    get();
                    resetHistory();
                    preExpandNodes(em.getRootContext().getChildren());
                } catch (InterruptedException | ExecutionException ex) {
                    LOGGER.log(Level.SEVERE, "Error selecting tree node.", ex); //NON-NLS
                } //NON-NLS
            }
        }.execute();
    }

    /**
     * Set the selected node using a path to a previously selected node.
     *
     * @param previouslySelectedNodePath Path to a previously selected node.
     * @param rootNodeName               Name of the root node to match, may be
     *                                   null.
     */
    private void setSelectedNode(final String[] previouslySelectedNodePath, final String rootNodeName) {
        if (previouslySelectedNodePath == null) {
            return;
        }
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (previouslySelectedNodePath.length > 0 && (rootNodeName == null || previouslySelectedNodePath[0].equals(rootNodeName))) {
                    Node selectedNode = null;
                    ArrayList<String> selectedNodePath = new ArrayList<>(Arrays.asList(previouslySelectedNodePath));
                    while (null == selectedNode && !selectedNodePath.isEmpty()) {
                        try {
                            selectedNode = NodeOp.findPath(em.getRootContext(), selectedNodePath.toArray(new String[selectedNodePath.size()]));
                        } catch (NodeNotFoundException ex) {
                            // The selected node may have been deleted (e.g., a deleted tag), so truncate the path and try again. 
                            if (selectedNodePath.size() > 1) {
                                selectedNodePath.remove(selectedNodePath.size() - 1);
                            } else {
                                StringBuilder nodePath = new StringBuilder();
                                for (int i = 0; i < previouslySelectedNodePath.length; ++i) {
                                    nodePath.append(previouslySelectedNodePath[i]).append("/");
                                }
                                LOGGER.log(Level.WARNING, "Failed to find any nodes to select on path " + nodePath.toString(), ex); //NON-NLS
                                break;
                            }
                        }
                    }

                    if (null != selectedNode) {
                        if (rootNodeName != null) {
                            //called from tree auto refresh context
                            //remove last from backlist, because auto select will result in duplication
                            backList.pollLast();
                        }
                        try {
                            em.setExploredContextAndSelection(selectedNode, new Node[]{selectedNode});
                        } catch (PropertyVetoException ex) {
                            LOGGER.log(Level.WARNING, "Property veto from ExplorerManager setting selection to " + selectedNode.getName(), ex); //NON-NLS
                        }
                    }
                }
            }
        });
    }

    @Override
    public TopComponent getTopComponent() {
        return this;
    }

    @Override
    public boolean hasMenuOpenAction() {
        return false;
    }

    public void viewArtifact(final BlackboardArtifact art) {
        int typeID = art.getArtifactTypeID();
        String typeName = art.getArtifactTypeName();
        Children rootChilds = em.getRootContext().getChildren();
        Node treeNode = null;
        Node resultsNode = rootChilds.findChild(ResultsNode.NAME);
        Children resultsChilds = resultsNode.getChildren();
        if (typeID == BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID()) {
            Node hashsetRootNode = resultsChilds.findChild(typeName);
            Children hashsetRootChilds = hashsetRootNode.getChildren();
            try {
                String setName = null;
                List<BlackboardAttribute> attributes = art.getAttributes();
                for (BlackboardAttribute att : attributes) {
                    int typeId = att.getAttributeType().getTypeID();
                    if (typeId == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID()) {
                        setName = att.getValueString();
                    }
                }
                treeNode = hashsetRootChilds.findChild(setName);
            } catch (TskCoreException ex) {
                LOGGER.log(Level.WARNING, "Error retrieving attributes", ex); //NON-NLS
            }
        } else if (typeID == BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID()) {
            Node keywordRootNode = resultsChilds.findChild(typeName);
            Children keywordRootChilds = keywordRootNode.getChildren();
            try {
                String listName = null;
                String keywordName = null;
                String regex = null;
                List<BlackboardAttribute> attributes = art.getAttributes();
                for (BlackboardAttribute att : attributes) {
                    int typeId = att.getAttributeType().getTypeID();
                    if (typeId == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID()) {
                        listName = att.getValueString();
                    } else if (typeId == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD.getTypeID()) {
                        keywordName = att.getValueString();
                    } else if (typeId == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_REGEXP.getTypeID()) {
                        regex = att.getValueString();
                    }
                }
                if (listName == null) {
                    if (regex == null) {  //using same labels used for creation 
                        listName = NbBundle.getMessage(KeywordHits.class, "KeywordHits.simpleLiteralSearch.text");
                    } else {
                        listName = NbBundle.getMessage(KeywordHits.class, "KeywordHits.singleRegexSearch.text");
                    }
                }
                Node listNode = keywordRootChilds.findChild(listName);
                if (listNode == null) {
                    return;
                }
                Children listChildren = listNode.getChildren();
                if (listChildren == null) {
                    return;
                }
                if (regex != null) {  //For support of regex nodes such as URLs, IPs, Phone Numbers, and Email Addrs as they are down another level
                    Node regexNode = listChildren.findChild(regex);
                    if (regexNode == null) {
                        return;
                    }
                    listChildren = regexNode.getChildren();
                    if (listChildren == null) {
                        return;
                    }
                }

                treeNode = listChildren.findChild(keywordName);

            } catch (TskCoreException ex) {
                LOGGER.log(Level.WARNING, "Error retrieving attributes", ex); //NON-NLS
            }
        } else if (typeID == BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT.getTypeID()
                || typeID == BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_ARTIFACT_HIT.getTypeID()) {
            Node interestingItemsRootNode = resultsChilds.findChild(NbBundle
                    .getMessage(InterestingHits.class, "InterestingHits.interestingItems.text"));
            Children interestingItemsRootChildren = interestingItemsRootNode.getChildren();
            try {
                String setName = null;
                List<BlackboardAttribute> attributes = art.getAttributes();
                for (BlackboardAttribute att : attributes) {
                    int typeId = att.getAttributeType().getTypeID();
                    if (typeId == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID()) {
                        setName = att.getValueString();
                    }
                }
                Node setNode = interestingItemsRootChildren.findChild(setName);
                if (setNode == null) {
                    return;
                }
                Children interestingChildren = setNode.getChildren();
                if (interestingChildren == null) {
                    return;
                }
                treeNode = interestingChildren.findChild(art.getDisplayName());
            } catch (TskCoreException ex) {
                LOGGER.log(Level.WARNING, "Error retrieving attributes", ex); //NON-NLS
            }
        } else if (typeID == BlackboardArtifact.ARTIFACT_TYPE.TSK_EMAIL_MSG.getTypeID()) {
            Node emailMsgRootNode = resultsChilds.findChild(typeName);
            Children emailMsgRootChilds = emailMsgRootNode.getChildren();
            Map<String, String> parsedPath = null;
            try {
                List<BlackboardAttribute> attributes = art.getAttributes();
                for (BlackboardAttribute att : attributes) {
                    int typeId = att.getAttributeType().getTypeID();
                    if (typeId == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH.getTypeID()) {
                        parsedPath = EmailExtracted.parsePath(att.getValueString());
                        break;
                    }
                }
                if (parsedPath == null) {
                    return;
                }
                Node defaultNode = emailMsgRootChilds.findChild(parsedPath.get(NbBundle.getMessage(EmailExtracted.class, "EmailExtracted.defaultAcct.text")));
                Children defaultChildren = defaultNode.getChildren();
                treeNode = defaultChildren.findChild(parsedPath.get(NbBundle.getMessage(EmailExtracted.class, "EmailExtracted.defaultFolder.text")));
            } catch (TskCoreException ex) {
                LOGGER.log(Level.WARNING, "Error retrieving attributes", ex); //NON-NLS
            }

        } else if (typeID == BlackboardArtifact.ARTIFACT_TYPE.TSK_ACCOUNT.getTypeID()) {
            Node accountRootNode = resultsChilds.findChild(art.getDisplayName());
            Children accountRootChilds = accountRootNode.getChildren();
            List<BlackboardAttribute> attributes;
            String accountType = null;
            String ccNumberName = null;
            try {
                attributes = art.getAttributes();
                for (BlackboardAttribute att : attributes) {
                    int typeId = att.getAttributeType().getTypeID();
                    if (typeId == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ACCOUNT_TYPE.getTypeID()) {
                        accountType = att.getValueString();
                    }
                    if (typeId == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CARD_NUMBER.getTypeID()) {
                        ccNumberName = att.getValueString();
                    }
                }
                if (accountType == null) {
                    return;
                }

                if (accountType.equals(Account.Type.CREDIT_CARD.getTypeName())) {
                    Node accountNode = accountRootChilds.findChild(Account.Type.CREDIT_CARD.getDisplayName());
                    if (accountNode == null) {
                        return;
                    }
                    Children accountChildren = accountNode.getChildren();
                    if (accountChildren == null) {
                        return;
                    }
                    Node binNode = accountChildren.findChild(NbBundle.getMessage(Accounts.class, "Accounts.ByBINNode.name"));
                    if (binNode == null) {
                        return;
                    }
                    Children binChildren = binNode.getChildren();
                    if (ccNumberName == null) {
                        return;
                    }
                    //right padded with 0s to 8 digits when single number
                    //when a range of numbers, the first 6 digits are rightpadded with 0s to 8 digits then a dash then 3 digits, the 6,7,8, digits of the end number right padded with 9s
                    String binName = StringUtils.rightPad(ccNumberName, 8, "0");
                    binName = binName.substring(0, 8);
                    int bin;
                    try {
                        bin = Integer.parseInt(binName);
                    } catch (NumberFormatException ex) {
                        LOGGER.log(Level.WARNING, "Unable to parseInt a BIN for node selection from string binName=" + binName, ex); //NON-NLS
                        return;
                    }
                    CreditCards.BankIdentificationNumber binInfo = CreditCards.getBINInfo(bin);
                    if (binInfo != null) {
                        int startBin = ((BINRange) binInfo).getBINstart();
                        int endBin = ((BINRange) binInfo).getBINend();
                        if (startBin != endBin) {
                            binName = Integer.toString(startBin) + "-" + Integer.toString(endBin).substring(5); //if there is a range re-construct the name it appears as 
                        }
                    }
                    if (binName == null) {
                        return;
                    }
                    treeNode = binChildren.findChild(binName);
                } else { //default account type
                    treeNode = accountRootChilds.findChild(accountType);
                }
            } catch (TskCoreException ex) {
                LOGGER.log(Level.WARNING, "Error retrieving attributes", ex); //NON-NLS
            }
        } else {
            Node extractedContent = resultsChilds.findChild(ExtractedContent.NAME);
            Children extractedChilds = extractedContent.getChildren();
            if (extractedChilds == null) {
                return;
            }
            treeNode = extractedChilds.findChild(typeName);
        }

        if (treeNode == null) {
            return;
        }

        DisplayableItemNode undecoratedParentNode = (DisplayableItemNode) ((DirectoryTreeFilterNode) treeNode).getOriginal();
        undecoratedParentNode.setChildNodeSelectionInfo(new ArtifactNodeSelectionInfo(art));
        getTree().expandNode(treeNode);
        if (this.getSelectedNode().equals(treeNode)) {
            this.setDirectoryListingActive();
            this.respondSelection(em.getSelectedNodes(), new Node[]{treeNode});
        } else {
            try {
                em.setExploredContextAndSelection(treeNode, new Node[]{treeNode});
            } catch (PropertyVetoException ex) {
                LOGGER.log(Level.WARNING, "Property Veto: ", ex); //NON-NLS
            }
        }
        // Another thread is needed because we have to wait for dataResult to populate
    }

    public void viewArtifactContent(BlackboardArtifact art) {
        new ViewContextAction(
                NbBundle.getMessage(this.getClass(), "DirectoryTreeTopComponent.action.viewArtContent.text"),
                new BlackboardArtifactNode(art)).actionPerformed(null);
    }

    public void addOnFinishedListener(PropertyChangeListener l) {
        DirectoryTreeTopComponent.this.addPropertyChangeListener(l);
    }

}
