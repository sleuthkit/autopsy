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
package org.sleuthkit.autopsy.directorytree;

import java.awt.Cursor;
import java.awt.EventQueue;
import java.beans.PropertyVetoException;
import java.io.IOException;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataExplorer;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.Action;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.tree.TreeSelectionModel;
import org.openide.util.NbBundle;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;
import org.openide.explorer.ExplorerManager;
import org.openide.explorer.ExplorerUtils;
import org.openide.explorer.view.BeanTreeView;
import org.openide.explorer.view.TreeView;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.NodeNotFoundException;
import org.openide.nodes.NodeOp;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.corecomponentinterfaces.BlackboardResultViewer;
import org.sleuthkit.autopsy.corecomponents.DataResultTopComponent;
import org.sleuthkit.autopsy.corecomponents.TableFilterNode;
import org.sleuthkit.autopsy.datamodel.BlackboardArtifactNode;
import org.sleuthkit.autopsy.datamodel.ExtractedContentNode;
import org.sleuthkit.autopsy.datamodel.Images;
import org.sleuthkit.autopsy.datamodel.KeywordHits;
import org.sleuthkit.autopsy.datamodel.Results;
import org.sleuthkit.autopsy.datamodel.ResultsNode;
import org.sleuthkit.autopsy.datamodel.RootContentChildren;
import org.sleuthkit.autopsy.datamodel.Views;
import org.sleuthkit.autopsy.datamodel.ViewsNode;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.IngestManager.IngestModuleEvent;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskException;

/**
 * Top component which displays something.
 */
// Registered as a service provider for DataExplorer in layer.xml
public final class DirectoryTreeTopComponent extends TopComponent implements DataExplorer, ExplorerManager.Provider, BlackboardResultViewer {

    private transient ExplorerManager em = new ExplorerManager();
    private static DirectoryTreeTopComponent instance;
    private DataResultTopComponent dataResult = new DataResultTopComponent(true, "Directory Listing");
    private boolean backFwdFlag; // flag whether the back or forward button is pressed
    private ArrayList<Node> backList;
    private ArrayList<Node> forwardList;
    /**
     * path to the icon used by the component and its open action
     */
//    static final String ICON_PATH = "SET/PATH/TO/ICON/HERE";
    private static final String PREFERRED_ID = "DirectoryTreeTopComponent";
    private PropertyChangeSupport pcs;
    // for error handling
    private JPanel caller;
    private String className = this.getClass().toString();
    private static final Logger logger = Logger.getLogger(DirectoryTreeTopComponent.class.getName());
    private RootContentChildren contentChildren;

    /**
     * the constructor
     */
    private DirectoryTreeTopComponent() {
        initComponents();

        // only allow one item to be selected at a time
        ((BeanTreeView) jScrollPane1).setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        // remove the close button
        putClientProperty(TopComponent.PROP_CLOSING_DISABLED, Boolean.TRUE);
        setName(NbBundle.getMessage(DirectoryTreeTopComponent.class, "CTL_DirectoryTreeTopComponent"));
        setToolTipText(NbBundle.getMessage(DirectoryTreeTopComponent.class, "HINT_DirectoryTreeTopComponent"));

        setListener();
        associateLookup(ExplorerUtils.createLookup(em, getActionMap()));


        this.pcs = new PropertyChangeSupport(this);
        this.backFwdFlag = false;

        // set the back & forward list and also disable the back & forward button
        this.backList = new ArrayList<Node>();
        this.forwardList = new ArrayList<Node>();
        backButton.setEnabled(false);
        forwardButton.setEnabled(false);
    }

    /**
     * Set the FileBrowserTopComponent as the listener to any property changes
     * in the Case.java class
     */
    private void setListener() {
        Case.addPropertyChangeListener(this);// add this class to listen to any changes in the Case.java class
        this.em.addPropertyChangeListener(this);
        IngestManager.addPropertyChangeListener(this);
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
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jScrollPane1 = new BeanTreeView();
        backButton = new javax.swing.JButton();
        forwardButton = new javax.swing.JButton();
        jSeparator1 = new javax.swing.JSeparator();

        jScrollPane1.setBorder(null);

        backButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/directorytree/btn_step_back.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(backButton, org.openide.util.NbBundle.getMessage(DirectoryTreeTopComponent.class, "DirectoryTreeTopComponent.backButton.text")); // NOI18N
        backButton.setBorderPainted(false);
        backButton.setContentAreaFilled(false);
        backButton.setDisabledIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/directorytree/btn_step_back_disabled.png"))); // NOI18N
        backButton.setMargin(new java.awt.Insets(2, 0, 2, 0));
        backButton.setMaximumSize(new java.awt.Dimension(55, 100));
        backButton.setMinimumSize(new java.awt.Dimension(5, 5));
        backButton.setPreferredSize(new java.awt.Dimension(23, 23));
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
        forwardButton.setPreferredSize(new java.awt.Dimension(23, 23));
        forwardButton.setRolloverIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/directorytree/btn_step_forward_hover.png"))); // NOI18N
        forwardButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                forwardButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(backButton, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0)
                .addComponent(forwardButton, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(206, Short.MAX_VALUE))
            .addComponent(jSeparator1, javax.swing.GroupLayout.DEFAULT_SIZE, 262, Short.MAX_VALUE)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 262, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(backButton, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(forwardButton, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(0, 0, 0)
                .addComponent(jSeparator1, javax.swing.GroupLayout.PREFERRED_SIZE, 1, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 860, Short.MAX_VALUE)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void backButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_backButtonActionPerformed
        // change the cursor to "waiting cursor" for this operation
        this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
//        try {
        // update the back and forward List
        int currentIndex = backList.size() - 1;
        Node currentNode = backList.get(currentIndex);
        Node newCurrentNode = backList.get(currentIndex - 1);
        backList.remove(currentIndex);
        forwardList.add(currentNode);

        // enable / disable the back and forward button
        if (backList.size() > 1) {
            backButton.setEnabled(true);
        } else {
            backButton.setEnabled(false);
        }
        this.forwardButton.setEnabled(true);

        this.backFwdFlag = true; // set the flag

        // update the selection on directory tree
        try {
            em.setExploredContextAndSelection(newCurrentNode.getParentNode(), new Node[]{newCurrentNode});
        } catch (PropertyVetoException ex) {
            Logger.getLogger(this.className).log(Level.WARNING, "Error: can't go back to the previous selected node.", ex);
        }

        this.backFwdFlag = false; // reset the flag
//        }
//        finally {
        this.setCursor(null);
//        }
    }//GEN-LAST:event_backButtonActionPerformed

    private void forwardButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_forwardButtonActionPerformed
        // change the cursor to "waiting cursor" for this operation
        this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
//        try {
        // update the back and forward List
        int newCurrentIndex = forwardList.size() - 1;
        Node newCurrentNode = forwardList.get(newCurrentIndex);
        forwardList.remove(newCurrentIndex);
        backList.add(newCurrentNode);

        // enable / disable the back and forward button
        if (forwardList.size() > 0) {
            forwardButton.setEnabled(true);
        } else {
            forwardButton.setEnabled(false);
        }
        this.backButton.setEnabled(true);

        this.backFwdFlag = true; // set the flag

        // update the selection on directory tree
        try {
            em.setExploredContextAndSelection(newCurrentNode.getParentNode(), new Node[]{newCurrentNode});
        } catch (PropertyVetoException ex) {
            Logger.getLogger(this.className).log(Level.WARNING, "Error: can't go forward to the previous selected node.", ex);
        }

        this.backFwdFlag = false; // reset the flag
//        }
//        finally {
        this.setCursor(null);
//    }
    }//GEN-LAST:event_forwardButtonActionPerformed
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton backButton;
    private javax.swing.JButton forwardButton;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JSeparator jSeparator1;
    // End of variables declaration//GEN-END:variables

    /**
     * Gets default instance. Do not use directly: reserved for *.settings files
     * only, i.e. deserialization routines; otherwise you could get a
     * non-deserialized instance. To obtain the singleton instance, use
     * {@link #findInstance}.
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
     */
    public static synchronized DirectoryTreeTopComponent findInstance() {
        WindowManager winManager = WindowManager.getDefault();
        TopComponent win = winManager.findTopComponent(PREFERRED_ID);
        if (win == null) {
            Logger.getLogger(DirectoryTreeTopComponent.class.getName()).warning(
                    "Cannot find " + PREFERRED_ID + " component. It will not be located properly in the window system.");
            return getDefault();
        }
        if (win instanceof DirectoryTreeTopComponent) {
            return (DirectoryTreeTopComponent) win;
        }
        Logger.getLogger(DirectoryTreeTopComponent.class.getName()).warning(
                "There seem to be multiple components with the '" + PREFERRED_ID
                + "' ID. That is a potential source of errors and unexpected behavior.");
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
     * Called only when top component was closed on all workspaces before and
     * now is opened for the first time on some workspace. The intent is to
     * provide subclasses information about TopComponent's life cycle across all
     * existing workspaces. Subclasses will usually perform initializing tasks
     * here.
     */
    @Override
    public void componentOpened() {
        // change the cursor to "waiting cursor" for this operation
        this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            if (Case.existsCurrentCase()) {
                Case currentCase = Case.getCurrentCase();

                // close the top component if there's no image in this case
                if (currentCase.getImageIDs().length == 0) {
                    //this.close();
                    ((BeanTreeView) this.jScrollPane1).setRootVisible(false); // hide the root
                } else {
                    // if there's at least one image, load the image and open the top component
                    List<Object> items = new ArrayList<Object>();
                    items.add(new Images(currentCase.getSleuthkitCase()));
                    items.add(new Views(currentCase.getSleuthkitCase()));
                    items.add(new Results(currentCase.getSleuthkitCase()));
                    contentChildren = new RootContentChildren(items);
                    Node root = new AbstractNode(contentChildren) {
                        /**
                         * to override the right click action in the white blank
                         * space area on the directory tree window
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
                    ((BeanTreeView) this.jScrollPane1).setRootVisible(false); // hide the root

                    // Reset the forward and back lists because we're resetting the root context
                    backButton.setEnabled(false);
                    forwardButton.setEnabled(false);
                    forwardList.clear();
                    backList.clear();

                    Children childNodes = em.getRootContext().getChildren();
                    TreeView tree = getTree();

                    Node results = childNodes.findChild(ResultsNode.NAME);
                    tree.expandNode(results);

                    Children resultsChilds = results.getChildren();
                    tree.expandNode(resultsChilds.findChild(KeywordHits.NAME));
                    tree.expandNode(resultsChilds.findChild(ExtractedContentNode.NAME));


                    Node views = childNodes.findChild(ViewsNode.NAME);
                    Children viewsChilds = views.getChildren();
                    for (Node n : viewsChilds.getNodes()) {
                        tree.expandNode(n);
                    }

                    tree.collapseNode(views);

                    // if the dataResult is not opened
                    if (!dataResult.isOpened()) {
                        dataResult.open(); // open the data result top component as well when the directory tree is opened
                    }


                    // select the first image node, if there is one
                    // (this has to happen after dataResult is opened, because the event
                    // of changing the selected node fires a handler that tries to make
                    // dataResult active)
                    if (childNodes.getNodesCount() > 0) {
                        try {
                            em.setSelectedNodes(new Node[]{childNodes.getNodeAt(0)});
                        } catch (Exception ex) {
                            Logger logger = Logger.getLogger(DirectoryTreeTopComponent.class.getName());
                            logger.log(Level.SEVERE, "Error setting default selected node.", ex);
                        }
                    }

                }
            }
        } finally {
            this.setCursor(null);
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
        return !Case.existsCurrentCase() || Case.getCurrentCase().getRootObjectsCount() == 0; // only allow this window to be closed when there's no case opened or no image in this case
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
     * The "listener" that listens to any changes made in the Case.java class.
     * It will do something based on the changes in the Case.java class.
     *
     * @param evt the property change event
     */
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        String changed = evt.getPropertyName();
        Object oldValue = evt.getOldValue();
        Object newValue = evt.getNewValue();

        // change in the case name
        if (changed.equals(Case.CASE_NAME)) {
            // set the main title of the window
            String oldCaseName = oldValue.toString();
            String newCaseName = newValue.toString();


            // update the case name
            if ((!oldCaseName.equals("")) && (!newCaseName.equals(""))) {
                // change the root name and display name
                em.getRootContext().setName(newCaseName);
                em.getRootContext().setDisplayName(newCaseName);
            }
        } // changed current case
        else if (changed.equals(Case.CASE_CURRENT_CASE)) {

            // case opened
            if (newValue != null) {
                resetHistoryListAndButtons();
            }
        } // if the image is added to the case
        else if (changed.equals(Case.CASE_ADD_IMAGE)) {
            componentOpened();
//            Image img = (Image)newValue;
//
//            int[] imageIDs = Case.getCurrentCase().getImageIDs();
//
//            // add the first image
//            if(imageIDs.length == 1){
//                
//            }
//            else{
//                // add the additional images
//                ImageNode newNode = new ImageNode(img);
//                ((ImageChildren)getOriginalRootContent().getChildren()).addNode(newNode);
//
//                // expand the new added node
//                int count = em.getRootContext().getChildren().getNodesCount();
//                em.setExploredContext(em.getRootContext().getChildren().getNodeAt(count - 1));
//            }
        } // not supporting deleting images for now
        //        // if the image is removed from the case
        //        if(changed.equals(Case.CASE_DEL_IMAGE)){
        //            if(Case.getCurrentCase().getImageIDs().length > 0){
        //                // just remove the given image from the directory tree
        //                Image img = (Image)newValue;
        //                int ID = Integer.parseInt(oldValue.toString());
        //                ImageNode tempNode = new ImageNode(img);
        //                ((ImageChildren)getOriginalRootContent().getChildren()).removeNode(tempNode);
        //            }
        //        }
        // change in node selection
        else if (changed.equals(ExplorerManager.PROP_SELECTED_NODES)) {
            if (getSelectedNode() == null && oldValue != null) {
                try {
                    em.setSelectedNodes((Node[]) oldValue);
                } catch (PropertyVetoException ex) {
                    logger.log(Level.WARNING, "Error resetting node", ex);
                }
            }
            final Node[] oldNodes = (Node[]) oldValue;
            final Node[] newNodes = (Node[]) newValue;
            // Some lock that prevents certain Node operations is set during the
            // ExplorerManager selection-change, so we must handle changes after the
            // selection-change event is processed.
            EventQueue.invokeLater(new Runnable() {
                @Override
                public void run() {
                    // change the cursor to "waiting cursor" for this operation
                    DirectoryTreeTopComponent.this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                    try {

                        // make sure dataResult is open
                        dataResult.open();

                        Node treeNode = DirectoryTreeTopComponent.this.getSelectedNode();
                        if (treeNode != null) {
                            OriginalNode origin = treeNode.getLookup().lookup(OriginalNode.class);
                            if (origin == null) {
                                return;
                            }
                            Node originNode = origin.getNode();

                            DirectoryTreeTopComponent.this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                            //set node, wrap in filter node first to filter out children
                            Node drfn = new DataResultFilterNode(originNode, DirectoryTreeTopComponent.this.em);
                            DirectoryTreeTopComponent.this.dataResult.setNode(new TableFilterNode(drfn, true));

                            String displayName = "";
                            if (originNode.getLookup().lookup(Content.class) != null) {
                                Content content = originNode.getLookup().lookup(Content.class);
                                if (content != null) {
                                    try {
                                        displayName = content.getUniquePath();
                                    } catch (TskCoreException ex) {
                                        logger.log(Level.SEVERE, "Exception while calling Content.getUniquePath() for node: " + originNode);
                                    }
                                }
                            } else if (originNode.getLookup().lookup(String.class) != null) {
                                displayName = originNode.getLookup().lookup(String.class);
                            }
                            DirectoryTreeTopComponent.this.dataResult.setPath(displayName);
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
                        DirectoryTreeTopComponent.this.setCursor(null);
                    }
                }
            });

            // update the back and forward list
            Node[] selectedNode = em.getSelectedNodes();
            if (selectedNode.length > 0 && !backFwdFlag) {
                Node selectedContext = selectedNode[0];

                backList.add(selectedContext); // add the node to the "backList"
                if (backList.size() > 1) {
                    backButton.setEnabled(true);
                } else {
                    backButton.setEnabled(false);
                }

                forwardList.clear(); // clear the "forwardList"
                forwardButton.setEnabled(false); // disable the forward Button
            }
        } else if (changed.equals(IngestModuleEvent.DATA.toString())) {
            final ModuleDataEvent event = (ModuleDataEvent) oldValue;
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    refreshTree(event.getArtifactType());
                }
            });
        } else if (changed.equals(IngestModuleEvent.COMPLETED.toString())) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    refreshTree();
                }
            });
        }
    }

    @Override
    public synchronized void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    @Override
    public synchronized void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    /**
     * Resets the back and forward list, and also disable the back and forward
     * buttons.
     */
    private void resetHistoryListAndButtons() {
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
    public BeanTreeView getTree() {
        return (BeanTreeView) this.jScrollPane1;
    }

    /**
     * Refreshes the nodes in the tree to reflect updates in the database should
     * be called in the gui thread
     */
    void refreshTree(final BlackboardArtifact.ARTIFACT_TYPE... types) {

        Node selected = getSelectedNode();
        final String[] path = NodeOp.createPath(selected, em.getRootContext());

        //TODO: instead, we should choose a specific key to refresh? Maybe?
        //contentChildren.refreshKeys();

        Children dirChilds = em.getRootContext().getChildren();

        Node results = dirChilds.findChild(ResultsNode.NAME);

        OriginalNode original = results.getLookup().lookup(OriginalNode.class);
        ResultsNode resultsNode = (ResultsNode) original.getNode();
        RootContentChildren resultsNodeChilds = (RootContentChildren) resultsNode.getChildren();
        resultsNodeChilds.refreshKeys(types);

        final TreeView tree = getTree();

        tree.expandNode(results);

        Children resultsChilds = results.getChildren();

        if (resultsChilds == null) //intermediate state check
        {
            return;
        }

        Node childNode = resultsChilds.findChild(KeywordHits.NAME);
        if (childNode == null) //intermediate state check
        {
            return;
        }
        tree.expandNode(childNode);

        childNode = resultsChilds.findChild(ExtractedContentNode.NAME);
        if (childNode == null) //intermediate state check
        {
            return;
        }
        tree.expandNode(childNode);

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {

                if (path.length > 0 && path[0].equals(ResultsNode.NAME)) {
                    try {
                        Node newSelection = NodeOp.findPath(em.getRootContext(), path);
                        resetHistoryListAndButtons();
                        if (newSelection != null) {
                            tree.expandNode(newSelection);
                            em.setExploredContextAndSelection(newSelection, new Node[]{newSelection});
                        }
                        // We need to set the selection, which will refresh dataresult and get rid of the oob exception
                    } catch (NodeNotFoundException ex) {
                        logger.log(Level.WARNING, "Node not found", ex);
                    } catch (PropertyVetoException ex) {
                        logger.log(Level.WARNING, "Property Veto", ex);
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

    @Override
    public void viewArtifact(final BlackboardArtifact art) {
        BlackboardArtifact.ARTIFACT_TYPE type = BlackboardArtifact.ARTIFACT_TYPE.fromID(art.getArtifactTypeID());
        Children rootChilds = em.getRootContext().getChildren();
        Node treeNode = null;
        Node resultsNode = rootChilds.findChild(ResultsNode.NAME);
        Children resultsChilds = resultsNode.getChildren();
        if (type.equals(BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT)) {
            Node hashsetRootNode = resultsChilds.findChild(type.getLabel());
            Children hashsetRootChilds = hashsetRootNode.getChildren();
            try {
                String setName = null;
                List<BlackboardAttribute> attributes = art.getAttributes();
                for (BlackboardAttribute att : attributes) {
                    int typeId = att.getAttributeTypeID();
                    if (typeId == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID()) {
                        setName = att.getValueString();
                    }
                }
                treeNode = hashsetRootChilds.findChild(setName);
            } catch (TskException ex) {
                logger.log(Level.WARNING, "Error retrieving attributes", ex);
            }
        } else if (type.equals(BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT)) {
            Node keywordRootNode = resultsChilds.findChild(type.getLabel());
            Children keywordRootChilds = keywordRootNode.getChildren();
            try {
                String listName = null;
                String keywordName = null;
                List<BlackboardAttribute> attributes = art.getAttributes();
                for (BlackboardAttribute att : attributes) {
                    int typeId = att.getAttributeTypeID();
                    if (typeId == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID()) {
                        listName = att.getValueString();
                    } else if (typeId == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD.getTypeID()) {
                        keywordName = att.getValueString();
                    }
                }
                Node listNode = keywordRootChilds.findChild(listName);
                Children listChildren = listNode.getChildren();
                treeNode = listChildren.findChild(keywordName);
            } catch (TskException ex) {
                logger.log(Level.WARNING, "Error retrieving attributes", ex);
            }
        } else {
            Node extractedContent = resultsChilds.findChild(ExtractedContentNode.NAME);
            Children extractedChilds = extractedContent.getChildren();
            treeNode = extractedChilds.findChild(type.getLabel());
        }
        try {
            em.setExploredContextAndSelection(treeNode, new Node[]{treeNode});
        } catch (PropertyVetoException ex) {
            logger.log(Level.WARNING, "Property Veto: ", ex);
        }

        // Another thread is needed because we have to wait for dataResult to populate
        EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                Children resultChilds = dataResult.getRootNode().getChildren();
                Node select = resultChilds.findChild(Long.toString(art.getArtifactID()));
                if (select != null) {
                    dataResult.requestActive();
                    dataResult.setSelectedNodes(new Node[]{select});
                    fireViewerComplete();
                }
            }
        });
    }

    @Override
    public void viewArtifactContent(BlackboardArtifact art) {
        new ViewContextAction("View Artifact Content", new BlackboardArtifactNode(art)).actionPerformed(null);
    }

//    private class HistoryManager<T> {
//        private Stack<T> past, future;
//
//    }
    @Override
    public void addOnFinishedListener(PropertyChangeListener l) {
        DirectoryTreeTopComponent.this.addPropertyChangeListener(l);
    }

    void fireViewerComplete() {
        firePropertyChange(BlackboardResultViewer.FINISHED_DISPLAY_EVT, 0, 1);
    }
}
