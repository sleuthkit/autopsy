/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017-18 Basis Technology Corp.
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
package org.sleuthkit.autopsy.communications;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.eventbus.Subscribe;
import com.mxgraph.layout.hierarchical.mxHierarchicalLayout;
import com.mxgraph.layout.mxCircleLayout;
import com.mxgraph.layout.mxFastOrganicLayout;
import com.mxgraph.layout.mxIGraphLayout;
import com.mxgraph.layout.mxOrganicLayout;
import com.mxgraph.layout.orthogonal.mxOrthogonalLayout;
import com.mxgraph.model.mxCell;
import com.mxgraph.model.mxICell;
import com.mxgraph.swing.handler.mxRubberband;
import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.swing.util.mxMorphing;
import com.mxgraph.util.mxConstants;
import com.mxgraph.util.mxEvent;
import com.mxgraph.util.mxEventObject;
import com.mxgraph.util.mxEventSource;
import com.mxgraph.util.mxEventSource.mxIEventListener;
import com.mxgraph.view.mxGraph;
import com.mxgraph.view.mxStylesheet;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyVetoException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import static java.util.Collections.singleton;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSplitPane;
import javax.swing.JToolBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import org.openide.explorer.ExplorerManager;
import org.openide.explorer.ExplorerUtils;
import org.openide.nodes.Node;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ProxyLookup;
import org.sleuthkit.autopsy.casemodule.Case;
import static org.sleuthkit.autopsy.casemodule.Case.Events.CURRENT_CASE;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AccountDeviceInstance;
import org.sleuthkit.datamodel.CommunicationsFilter;
import org.sleuthkit.datamodel.CommunicationsManager;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A panel that goes in the Visualize tab of the Communications Visualization
 * Tool. Hosts an JGraphX mxGraphComponent that host the communications network
 * visualization and a MessageBrowser for viewing details of communications.
 *
 * The Lookup provided by getLookup will be proxied by the lookup of the
 * CVTTopComponent when this tab is active allowing for context sensitive
 * actions to work correctly.
 */
final public class VisualizationPanel extends JPanel implements Lookup.Provider {
    
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(VisualizationPanel.class.getName());
    
    static final private ImageIcon imageIcon =
            new ImageIcon("images/icons8-neural-network.png");
    
    static final private mxStylesheet mxStylesheet = new mxStylesheet();
    
    static {
        //initialize defaul cell (Vertex and/or Edge)  properties
        mxStylesheet.getDefaultVertexStyle().put(mxConstants.STYLE_SHAPE, mxConstants.SHAPE_ELLIPSE);
        mxStylesheet.getDefaultVertexStyle().put(mxConstants.STYLE_FONTCOLOR, "#000000");
        mxStylesheet.getDefaultEdgeStyle().put(mxConstants.STYLE_NOLABEL, true);
        mxStylesheet.getDefaultEdgeStyle().put(mxConstants.STYLE_ENDARROW, mxConstants.NONE);
    }
    
    private final ExplorerManager vizEM = new ExplorerManager();
    private final ExplorerManager gacEM = new ExplorerManager();
    private final ProxyLookup proxyLookup = new ProxyLookup(
            ExplorerUtils.createLookup(gacEM, getActionMap()),
            ExplorerUtils.createLookup(vizEM, getActionMap()));
    
    private final mxGraphComponent graphComponent;
    private final mxGraph graph;
    private final Map<String, mxCell> nodeMap = new HashMap<>();
    private final Multimap<Content, mxCell> edgeMap = MultimapBuilder.hashKeys().hashSetValues().build();
    
    private CommunicationsManager commsManager;
    private final HashSet<AccountDeviceInstanceKey> pinnedAccountDevices = new HashSet<>();
    private CommunicationsFilter currentFilter;
    private final mxRubberband rubberband;
    
    public VisualizationPanel() {
        initComponents();
        graph = new mxGraph();
        graph.setCellsCloneable(false);
        graph.setDropEnabled(false);
        graph.setCellsCloneable(false);
        graph.setCellsEditable(false);
        graph.setCellsResizable(false);
        graph.setCellsMovable(true);
        graph.setCellsDisconnectable(false);
        graph.setConnectableEdges(false);
        graph.setDisconnectOnMove(false);
        graph.setEdgeLabelsMovable(false);
        graph.setVertexLabelsMovable(false);
        graph.setAllowDanglingEdges(false);
        graph.setCellsBendable(true);
        graph.setKeepEdgesInBackground(true);
        graph.setStylesheet(mxStylesheet);
        graphComponent = new mxGraphComponent(graph);
        graphComponent.setAutoExtend(true);
        graphComponent.setAutoScroll(true);
        graphComponent.setAutoscrolls(true);
        graphComponent.setConnectable(false);
        graphComponent.setKeepSelectionVisibleOnZoom(true);
        graphComponent.setOpaque(true);
        graphComponent.setBackground(Color.WHITE);
        jPanel1.add(graphComponent, BorderLayout.CENTER);

        //install rubber band selection handler
        rubberband = new mxRubberband(graphComponent);

        //right click handler
        graphComponent.getGraphControl().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                if (SwingUtilities.isRightMouseButton(e)) {
                    mxICell cellAt = (mxICell) graphComponent.getCellAt(e.getX(), e.getY());
                    if (cellAt != null && cellAt.isVertex()) {
                        JPopupMenu jPopupMenu = new JPopupMenu();
                        jPopupMenu.add(new JMenuItem(imageIcon) {
                            {
                                setAction(new AbstractAction("Pin Account " + graph.getLabel(cellAt)) {
                                    @Override
                                    public void actionPerformed(ActionEvent e) {
                                        handlePinEvent(new CVTEvents.PinAccountsEvent(singleton((AccountDeviceInstanceKey) cellAt.getValue()), false));
                                    }
                                });
                            }
                        });
                        
                        jPopupMenu.show(graphComponent.getGraphControl(), e.getX(), e.getY());
                    }
                }
            }
        });
        
        splitPane.setRightComponent(new MessageBrowser(vizEM, gacEM));

        //feed selection to explorermanager
        graph.getSelectionModel().addListener(null, new SelectionListener());
    }
    
    @Override
    public Lookup getLookup() {
        return proxyLookup;
    }
    
    private mxCell getOrCreateVertex(AccountDeviceInstanceKey accountDeviceInstanceKey) {
        final AccountDeviceInstance accountDeviceInstance = accountDeviceInstanceKey.getAccountDeviceInstance();
        final String name =// accountDeviceInstance.getDeviceId() + ":"                +
                accountDeviceInstance.getAccount().getTypeSpecificID();
        final mxCell computeIfAbsent = nodeMap.computeIfAbsent(name, vertexName -> {
            double size = Math.sqrt(accountDeviceInstanceKey.getMessageCount()) + 10;
            
            mxCell vertex = (mxCell) graph.insertVertex(
                    graph.getDefaultParent(),
                    vertexName, accountDeviceInstanceKey,
                    Math.random() * graphComponent.getWidth(),
                    Math.random() * graphComponent.getHeight(),
                    size,
                    size);
            graph.getView().getState(vertex, true).setLabel(vertexName);
            return vertex;
        });
        return computeIfAbsent;
    }
    
    @SuppressWarnings("unchecked")
    private void addEdge(Collection<Content> relSources, AccountDeviceInstanceKey account1, AccountDeviceInstanceKey account2) throws TskCoreException {
        mxCell vertex1 = getOrCreateVertex(account1);
        mxCell vertex2 = getOrCreateVertex(account2);
        Object[] edgesBetween = graph.getEdgesBetween(vertex1, vertex2);
        if (edgesBetween.length == 0) {
            final String edgeName = vertex1.getId() + " <-> " + vertex2.getId();
            final HashSet<Content> hashSet = new HashSet<>(relSources);
            mxCell edge = (mxCell) graph.insertEdge(graph.getDefaultParent(), edgeName, hashSet, vertex1, vertex2,
                    "strokeWidth=" + Math.sqrt(hashSet.size()));
//            edgeMap.put(relSource, edge);
        } else if (edgesBetween.length == 1) {
            final mxCell edge = (mxCell) edgesBetween[0];
            ((Collection<Content>) edge.getValue()).addAll(relSources);
            edge.setStyle("strokeWidth=" + Math.sqrt(((Collection) edge.getValue()).size()));
        }
    }
    
    @Subscribe
    void handlePinEvent(CVTEvents.PinAccountsEvent pinEvent) {
        graph.getModel().beginUpdate();
        try {
            if (pinEvent.isReplace()) {
                pinnedAccountDevices.clear();
                clearGraph();
            }
            pinnedAccountDevices.addAll(pinEvent.getAccountDeviceInstances());
            rebuildGraph();
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error pinning accounts", ex);
        } finally {
            // Updates the display
            graph.getModel().endUpdate();
        }

//        applyOrganicLayout();
    }
    
    @Subscribe
    void handleFilterEvent(CVTEvents.FilterChangeEvent filterChangeEvent) {
        
        graph.getModel().beginUpdate();
        try {
            clearGraph();
            currentFilter = filterChangeEvent.getNewFilter();
            rebuildGraph();
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error filtering accounts", ex);
        } finally {
            // Updates the display
            graph.getModel().endUpdate();
        }

//        applyOrganicLayout();
    }
    
    private void rebuildGraph() throws TskCoreException {

        /**
         * set to keep track of accounts related to pinned accounts
         */
        Set<AccountDeviceInstanceKey> relatedAccounts = new HashSet<>();
        for (AccountDeviceInstanceKey adiKey : pinnedAccountDevices) {
            List<AccountDeviceInstance> relatedAccountDeviceInstances =
                    commsManager.getRelatedAccountDeviceInstances(adiKey.getAccountDeviceInstance(), currentFilter);

            //get accounts related to pinned account
            for (AccountDeviceInstance relatedADI : relatedAccountDeviceInstances) {
                long adiRelationshipsCount = commsManager.getRelationshipSourcesCount(relatedADI, currentFilter);
                final AccountDeviceInstanceKey relatedADIKey = new AccountDeviceInstanceKey(relatedADI, currentFilter, adiRelationshipsCount);

                //add and edge between pinned and related accounts
                List<Content> relationships = commsManager.getRelationshipSources(
                        adiKey.getAccountDeviceInstance(),
                        relatedADIKey.getAccountDeviceInstance(),
                        currentFilter);
                addEdge(relationships, adiKey, relatedADIKey);
                relatedAccounts.add(relatedADIKey); //store related accounts
            }
        }

        //for each pair of related accounts add edges if they are related o each other.
        // this is O(n^2) in the number of related accounts!!!
        List<AccountDeviceInstanceKey> relatedAccountsList = new ArrayList<>(relatedAccounts);
        for (int i = 0; i < relatedAccountsList.size(); i++) {
            for (int j = i; j < relatedAccountsList.size(); j++) {
                AccountDeviceInstanceKey adiKey1 = relatedAccountsList.get(i);
                AccountDeviceInstanceKey adiKey2 = relatedAccountsList.get(j);
                List<Content> relationships = commsManager.getRelationshipSources(
                        adiKey1.getAccountDeviceInstance(),
                        adiKey2.getAccountDeviceInstance(),
                        currentFilter);
                if (relationships.size() > 0) {
                    addEdge(relationships, adiKey1, adiKey2);
                }
            }
        }
    }
    
    private void clearGraph() {
        nodeMap.clear();
        edgeMap.clear();
        graph.removeCells(graph.getChildVertices(graph.getDefaultParent()));
    }
    
    @Override
    public void addNotify() {
        super.addNotify();
//        IngestManager.getInstance().addIngestModuleEventListener(ingestListener);
        try {
            commsManager = Case.getCurrentCase().getSleuthkitCase().getCommunicationsManager();
            
        } catch (IllegalStateException ex) {
            logger.log(Level.SEVERE, "Can't get CommunicationsManager when there is no case open.", ex);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error getting CommunicationsManager for the current case.", ex);
            
        }
        
        Case.addEventTypeSubscriber(EnumSet.of(CURRENT_CASE), evt -> {
            graph.getModel().beginUpdate();
            try {
                clearGraph();
            } finally {
                graph.getModel().endUpdate();
            }
            if (evt.getNewValue() != null) {
                Case currentCase = (Case) evt.getNewValue();
                try {
                    commsManager = currentCase.getSleuthkitCase().getCommunicationsManager();
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "Error getting CommunicationsManager for the current case.", ex);
                }
            } else {
                commsManager = null;
            }
            
        });
    }
    
    @Override
    public void removeNotify() {
        super.removeNotify();
//        IngestManager.getInstance().removeIngestModuleEventListener(ingestListener);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        splitPane = new JSplitPane();
        jPanel1 = new JPanel();
        jToolBar1 = new JToolBar();
        jButton2 = new JButton();
        jButton6 = new JButton();
        jButton1 = new JButton();
        jButton8 = new JButton();
        jButton3 = new JButton();
        jButton7 = new JButton();
        jButton4 = new JButton();
        jButton5 = new JButton();

        setLayout(new BorderLayout());

        splitPane.setDividerLocation(400);
        splitPane.setResizeWeight(0.5);

        jPanel1.setLayout(new BorderLayout());

        jToolBar1.setRollover(true);

        jButton2.setText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.jButton2.text")); // NOI18N
        jButton2.setFocusable(false);
        jButton2.setHorizontalTextPosition(SwingConstants.CENTER);
        jButton2.setVerticalTextPosition(SwingConstants.BOTTOM);
        jButton2.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                jButton2ActionPerformed(evt);
            }
        });
        jToolBar1.add(jButton2);

        jButton6.setText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.jButton6.text")); // NOI18N
        jButton6.setFocusable(false);
        jButton6.setHorizontalTextPosition(SwingConstants.CENTER);
        jButton6.setVerticalTextPosition(SwingConstants.BOTTOM);
        jButton6.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                jButton6ActionPerformed(evt);
            }
        });
        jToolBar1.add(jButton6);

        jButton1.setText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.jButton1.text")); // NOI18N
        jButton1.setFocusable(false);
        jButton1.setHorizontalTextPosition(SwingConstants.CENTER);
        jButton1.setVerticalTextPosition(SwingConstants.BOTTOM);
        jButton1.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                jButton1ActionPerformed(evt);
            }
        });
        jToolBar1.add(jButton1);

        jButton8.setText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.jButton8.text")); // NOI18N
        jButton8.setFocusable(false);
        jButton8.setHorizontalTextPosition(SwingConstants.CENTER);
        jButton8.setVerticalTextPosition(SwingConstants.BOTTOM);
        jButton8.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                jButton8ActionPerformed(evt);
            }
        });
        jToolBar1.add(jButton8);

        jButton3.setText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.jButton3.text")); // NOI18N
        jButton3.setFocusable(false);
        jButton3.setHorizontalTextPosition(SwingConstants.CENTER);
        jButton3.setVerticalTextPosition(SwingConstants.BOTTOM);
        jButton3.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                jButton3ActionPerformed(evt);
            }
        });
        jToolBar1.add(jButton3);

        jButton7.setText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.jButton7.text")); // NOI18N
        jButton7.setFocusable(false);
        jButton7.setHorizontalTextPosition(SwingConstants.CENTER);
        jButton7.setVerticalTextPosition(SwingConstants.BOTTOM);
        jButton7.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                jButton7ActionPerformed(evt);
            }
        });
        jToolBar1.add(jButton7);

        jButton4.setText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.jButton4.text")); // NOI18N
        jButton4.setFocusable(false);
        jButton4.setHorizontalTextPosition(SwingConstants.CENTER);
        jButton4.setVerticalTextPosition(SwingConstants.BOTTOM);
        jButton4.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                jButton4ActionPerformed(evt);
            }
        });
        jToolBar1.add(jButton4);

        jButton5.setText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.jButton5.text")); // NOI18N
        jButton5.setFocusable(false);
        jButton5.setHorizontalTextPosition(SwingConstants.CENTER);
        jButton5.setVerticalTextPosition(SwingConstants.BOTTOM);
        jButton5.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                jButton5ActionPerformed(evt);
            }
        });
        jToolBar1.add(jButton5);

        jPanel1.add(jToolBar1, BorderLayout.NORTH);

        splitPane.setLeftComponent(jPanel1);

        add(splitPane, BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents

    private void jButton2ActionPerformed(ActionEvent evt) {//GEN-FIRST:event_jButton2ActionPerformed
//        graphComponent.addMouseListener(new mxPanningHandler(graphComponent));
        graphComponent.setPanning(true);
    }//GEN-LAST:event_jButton2ActionPerformed

    private void jButton1ActionPerformed(ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
        applyFastOrganicLayout();
    }//GEN-LAST:event_jButton1ActionPerformed

    private void jButton3ActionPerformed(ActionEvent evt) {//GEN-FIRST:event_jButton3ActionPerformed
        
        applyOrthogonalLayout();
    }//GEN-LAST:event_jButton3ActionPerformed

    private void jButton5ActionPerformed(ActionEvent evt) {//GEN-FIRST:event_jButton5ActionPerformed
        graphComponent.zoomIn();
    }//GEN-LAST:event_jButton5ActionPerformed

    private void jButton4ActionPerformed(ActionEvent evt) {//GEN-FIRST:event_jButton4ActionPerformed
        graphComponent.zoomOut();
    }//GEN-LAST:event_jButton4ActionPerformed

    private void jButton6ActionPerformed(ActionEvent evt) {//GEN-FIRST:event_jButton6ActionPerformed
        applyHierarchicalLayout();
    }//GEN-LAST:event_jButton6ActionPerformed

    private void jButton7ActionPerformed(ActionEvent evt) {//GEN-FIRST:event_jButton7ActionPerformed
        morph(new mxCircleLayout(graph));
    }//GEN-LAST:event_jButton7ActionPerformed

    private void jButton8ActionPerformed(ActionEvent evt) {//GEN-FIRST:event_jButton8ActionPerformed
        mxOrganicLayout mxOrganicLayout = new mxOrganicLayout(graph);
        mxOrganicLayout.setMaxIterations(10);
        morph(mxOrganicLayout);
    }//GEN-LAST:event_jButton8ActionPerformed
    
    private void applyFastOrganicLayout() {
        morph(new mxFastOrganicLayout(graph));
    }
    
    private void applyOrthogonalLayout() {
        
        morph(new mxOrthogonalLayout(graph));
    }
    
    private void applyHierarchicalLayout() {
        
        morph(new mxHierarchicalLayout(graph));
    }
    
    private void morph(mxIGraphLayout layout) {
        // layout using morphing
        graph.getModel().beginUpdate();
        try {
            layout.execute(graph.getDefaultParent());
        } finally {
            mxMorphing morph = new mxMorphing(graphComponent, 20, 1.2, 20);
            morph.addListener(mxEvent.DONE, new mxIEventListener() {
                
                @Override
                public void invoke(Object sender, mxEventObject event) {
                    graph.getModel().endUpdate();
                    // fitViewport();
                }
            });
            
            morph.startAnimation();
        }
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private JButton jButton1;
    private JButton jButton2;
    private JButton jButton3;
    private JButton jButton4;
    private JButton jButton5;
    private JButton jButton6;
    private JButton jButton7;
    private JButton jButton8;
    private JPanel jPanel1;
    private JToolBar jToolBar1;
    private JSplitPane splitPane;
    // End of variables declaration//GEN-END:variables

    private class SelectionListener implements mxEventSource.mxIEventListener {
        
        @Override
        
        @SuppressWarnings("unchecked")
        public void invoke(Object sender, mxEventObject evt) {
            Object[] selectionCells = graph.getSelectionCells();
            Node rootNode = Node.EMPTY;
            Node[] selectedNodes = new Node[0];
            if (selectionCells.length > 0) {
                mxICell[] selectedCells = Arrays.asList(selectionCells).toArray(new mxCell[selectionCells.length]);
                HashSet<Content> relationshipSources = new HashSet<>();
                HashSet<AccountDeviceInstance> adis = new HashSet<>();
                for (mxICell cell : selectedCells) {
                    if (cell.isEdge()) {
                        relationshipSources.addAll((Set<Content>) cell.getValue());
                    } else if (cell.isVertex()) {
                        adis.add(((AccountDeviceInstanceKey) cell.getValue()).getAccountDeviceInstance());
                    }
                }
                rootNode = SelectionNode.createFromAccountsAndRelationships(relationshipSources, adis, currentFilter, commsManager);
                selectedNodes = new Node[]{rootNode};
            }
            vizEM.setRootContext(rootNode);
            try {
                vizEM.setSelectedNodes(selectedNodes);
            } catch (PropertyVetoException ex) {
                logger.log(Level.SEVERE, "Selection vetoed.", ex);
            }
        }
    }
}
