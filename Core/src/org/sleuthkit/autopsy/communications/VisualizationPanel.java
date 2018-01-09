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
import com.mxgraph.layout.mxOrganicLayout;
import com.mxgraph.layout.orthogonal.mxOrthogonalLayout;
import com.mxgraph.model.mxCell;
import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.util.mxConstants;
import com.mxgraph.view.mxGraph;
import com.mxgraph.view.mxStylesheet;
import java.awt.BorderLayout;
import java.awt.Color;
import java.beans.PropertyVetoException;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import javax.swing.JPanel;
import org.openide.explorer.ExplorerManager;
import org.openide.explorer.ExplorerUtils;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.lookup.ProxyLookup;
import org.sleuthkit.autopsy.casemodule.Case;
import static org.sleuthkit.autopsy.casemodule.Case.Events.CURRENT_CASE;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AccountDeviceInstance;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.CommunicationsFilter;
import org.sleuthkit.datamodel.CommunicationsManager;
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
    private Logger logger = Logger.getLogger(VisualizationPanel.class.getName());

    static final private mxStylesheet mxStylesheet = new mxStylesheet();

    static {
        //initialize defaul cell (Vertex and/or Edge)  properties
        mxStylesheet.getDefaultVertexStyle().put(mxConstants.STYLE_SHAPE, mxConstants.SHAPE_ELLIPSE);
        mxStylesheet.getDefaultVertexStyle().put(mxConstants.STYLE_FONTCOLOR, "#000000");
        mxStylesheet.getDefaultEdgeStyle().put(mxConstants.STYLE_NOLABEL, true);
    }

    private final ExplorerManager vizEM = new ExplorerManager();
    private final ExplorerManager gacEM = new ExplorerManager();
    private final ProxyLookup proxyLookup = new ProxyLookup(
            ExplorerUtils.createLookup(gacEM, getActionMap()),
            ExplorerUtils.createLookup(vizEM, getActionMap()));

    private final mxGraphComponent graphComponent;
    private final mxGraph graph;
    private final Map<String, mxCell> nodeMap = new HashMap<>();
    private final Multimap<BlackboardArtifact, mxCell> edgeMap = MultimapBuilder.hashKeys().hashSetValues().build();

    private CommunicationsManager commsManager;

    void setFilterProvider(FilterProvider filterProvider) {
        this.filterProvider = filterProvider;
    }
    private FilterProvider filterProvider;

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
        graph.setCellsDeletable(false);
        graph.setCellsBendable(true);
        graph.setKeepEdgesInBackground(true);

        graphComponent = new mxGraphComponent(graph);
        graphComponent.setAutoScroll(true);
        graphComponent.setOpaque(true);
        graphComponent.setBackground(Color.WHITE);
        jPanel1.add(graphComponent, BorderLayout.CENTER);

        graphComponent.getGraphControl().addMouseMotionListener(graphComponent.getPanningHandler());

        splitPane.setRightComponent(new MessageBrowser(vizEM, gacEM));
        CVTEvents.getCVTEventBus().register(this);

        graph.setStylesheet(mxStylesheet);

        graph.getSelectionModel().addListener(null, (sender, evt) -> {
            Object[] selectionCells = graph.getSelectionCells();
            if (selectionCells.length == 1) {
                mxCell selectionCell = (mxCell) selectionCells[0];
                try {

                    if (selectionCell.isVertex()) {
                        final AccountDeviceInstanceNode accountDeviceInstanceNode =
                                new AccountDeviceInstanceNode(((AccountDeviceInstanceKey) selectionCell.getValue()),
                                        commsManager);
                        vizEM.setRootContext(SimpleParentNode.createFromChildNodes(accountDeviceInstanceNode));
                        vizEM.setSelectedNodes(new Node[]{accountDeviceInstanceNode});

                    } else if (selectionCell.isEdge()) {
                        @SuppressWarnings("unchecked")
                        AbstractNode abstractNode = new AbstractNode(Children.create(new RelaionshipSetNodeFactory((Set<BlackboardArtifact>) selectionCell.getValue()), true));
                        vizEM.setRootContext(abstractNode);
                        vizEM.setSelectedNodes(new Node[]{abstractNode});
                    }
                } catch (PropertyVetoException ex) {
                    logger.log(Level.SEVERE, "Account selection vetoed.", ex);
                }
            }
        });
    }

    @Override
    public Lookup getLookup() {
        return proxyLookup;
    }

    private mxCell getOrCreateVertex(AccountDeviceInstanceKey accountDeviceInstanceKey) {
        final AccountDeviceInstance accountDeviceInstance = accountDeviceInstanceKey.getAccountDeviceInstance();
        final String name =// accountDeviceInstance.getDeviceId() + ":"                +
                accountDeviceInstance.getAccount().getTypeSpecificID();
        mxCell vertex = nodeMap.get(name);
        if (vertex == null) {
            double size = Math.sqrt(accountDeviceInstanceKey.getMessageCount()) + 10;
            vertex = (mxCell) graph.insertVertex(
                    graph.getDefaultParent(),
                    name, accountDeviceInstanceKey,
                    new Random().nextInt(200),
                    new Random().nextInt(200),
                    size,
                    size);
            graph.getView().getState(vertex, true).setLabel(name);
            nodeMap.put(name, vertex);
        }
        return vertex;
    }

    @SuppressWarnings("unchecked")
    private void addEdge(BlackboardArtifact artifact, mxCell pinnedAccountVertex, mxCell relatedAccountVertex) throws TskCoreException {
        Object[] edgesBetween = graph.getEdgesBetween(pinnedAccountVertex, relatedAccountVertex);
        if (edgesBetween.length == 0) {
            final String edgeName = pinnedAccountVertex.getId() + " <-> " + relatedAccountVertex.getId();
            mxCell edge = (mxCell) graph.insertEdge(graph.getDefaultParent(), edgeName, new HashSet<>(Arrays.asList(artifact)), pinnedAccountVertex, relatedAccountVertex);
            edgeMap.put(artifact, edge);
        } else if (edgesBetween.length == 1) {
            final mxCell edge = (mxCell) edgesBetween[0];
            ((Collection<BlackboardArtifact>) edge.getValue()).add(artifact);
            edge.setStyle("strokeWidth=" + Math.sqrt(((Collection) edge.getValue()).size()));
        }
    }

    @Subscribe
    public void pinAccounts(PinAccountEvent pinEvent) {

        final Set<AccountDeviceInstanceKey> adiKeys = pinEvent.getAccountDeviceInstances();
        final CommunicationsFilter commsFilter = filterProvider.getFilter();

        graph.getModel().beginUpdate();
        try {
            nodeMap.clear();
            edgeMap.clear();
            graph.removeCells(graph.getChildCells(graph.getDefaultParent(), true, true));

            for (AccountDeviceInstanceKey adiKey : adiKeys) {
                mxCell pinnedAccountVertex = getOrCreateVertex(adiKey);

                List<AccountDeviceInstance> relatedAccountDeviceInstances =
                        commsManager.getRelatedAccountDeviceInstances(adiKey.getAccountDeviceInstance(), commsFilter);

                for (AccountDeviceInstance relatedADI : relatedAccountDeviceInstances) {

                    List<BlackboardArtifact> relationships = commsManager.getRelationships(adiKey.getAccountDeviceInstance(), relatedADI, commsFilter);

                    long communicationsCount = relationships.size();
                    AccountDeviceInstanceKey relatedADIKey =
                            new AccountDeviceInstanceKey(relatedADI, commsFilter, communicationsCount);
                    mxCell relatedAccountVertex = getOrCreateVertex(relatedADIKey);
                    for (BlackboardArtifact relationship : relationships) {
                        addEdge(relationship, pinnedAccountVertex, relatedAccountVertex);
                    }
                }
            }
        } catch (TskCoreException ex) {
            Exceptions.printStackTrace(ex);
        } finally {
            // Updates the display
            graph.getModel().endUpdate();

        }

        applyOrganicLayout();
        revalidate();
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
                nodeMap.clear();
                edgeMap.clear();
                graph.removeCells(graph.getChildCells(graph.getDefaultParent(), true, true));
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

        splitPane = new javax.swing.JSplitPane();
        jPanel1 = new javax.swing.JPanel();
        jPanel2 = new javax.swing.JPanel();
        jButton2 = new javax.swing.JButton();
        jButton1 = new javax.swing.JButton();
        jButton3 = new javax.swing.JButton();
        jSeparator1 = new javax.swing.JToolBar.Separator();
        jButton4 = new javax.swing.JButton();
        jButton5 = new javax.swing.JButton();

        setLayout(new java.awt.BorderLayout());

        splitPane.setDividerLocation(400);
        splitPane.setResizeWeight(0.5);

        jPanel1.setLayout(new java.awt.BorderLayout());

        jButton2.setText(org.openide.util.NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.jButton2.text")); // NOI18N
        jButton2.setFocusable(false);
        jButton2.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        jButton2.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        jButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton2ActionPerformed(evt);
            }
        });

        jButton1.setText(org.openide.util.NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.jButton1.text")); // NOI18N
        jButton1.setFocusable(false);
        jButton1.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        jButton1.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton1ActionPerformed(evt);
            }
        });

        jButton3.setText(org.openide.util.NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.jButton3.text")); // NOI18N
        jButton3.setFocusable(false);
        jButton3.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        jButton3.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        jButton3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton3ActionPerformed(evt);
            }
        });

        jSeparator1.setPreferredSize(new java.awt.Dimension(10, 10));

        jButton4.setText(org.openide.util.NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.jButton4.text")); // NOI18N
        jButton4.setFocusable(false);
        jButton4.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        jButton4.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        jButton4.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton4ActionPerformed(evt);
            }
        });

        jButton5.setText(org.openide.util.NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.jButton5.text")); // NOI18N
        jButton5.setFocusable(false);
        jButton5.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        jButton5.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        jButton5.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton5ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jButton2)
                .addGap(5, 5, 5)
                .addComponent(jButton1)
                .addGap(5, 5, 5)
                .addComponent(jButton3)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jSeparator1, javax.swing.GroupLayout.PREFERRED_SIZE, 1, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jButton4)
                .addGap(5, 5, 5)
                .addComponent(jButton5)
                .addContainerGap())
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGap(5, 5, 5)
                .addComponent(jButton2))
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGap(5, 5, 5)
                .addComponent(jButton1))
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGap(5, 5, 5)
                .addComponent(jButton3))
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGap(14, 14, 14)
                .addComponent(jSeparator1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(10, 10, 10))
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGap(5, 5, 5)
                .addComponent(jButton4))
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGap(5, 5, 5)
                .addComponent(jButton5))
        );

        jPanel1.add(jPanel2, java.awt.BorderLayout.NORTH);

        splitPane.setLeftComponent(jPanel1);

        add(splitPane, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents

    private void jButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton2ActionPerformed
//        graphComponent.addMouseListener(new mxPanningHandler(graphComponent));
        graphComponent.getPanningHandler().setEnabled(true);
    }//GEN-LAST:event_jButton2ActionPerformed

    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
        applyOrganicLayout();
    }//GEN-LAST:event_jButton1ActionPerformed

    private void jButton3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton3ActionPerformed
        applyOrthogonalLayout();
    }//GEN-LAST:event_jButton3ActionPerformed

    private void jButton5ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton5ActionPerformed
        graphComponent.zoomIn();
    }//GEN-LAST:event_jButton5ActionPerformed

    private void jButton4ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton4ActionPerformed
        graphComponent.zoomOut();
    }//GEN-LAST:event_jButton4ActionPerformed

    private void applyOrganicLayout() {
        new mxOrganicLayout(graph).execute(graph.getDefaultParent());
        graphComponent.zoomAndCenter();
    }

    private void applyOrthogonalLayout() {
        new mxOrthogonalLayout(graph).execute(graph.getDefaultParent());
        graphComponent.zoomAndCenter();
    }


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton jButton1;
    private javax.swing.JButton jButton2;
    private javax.swing.JButton jButton3;
    private javax.swing.JButton jButton4;
    private javax.swing.JButton jButton5;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JToolBar.Separator jSeparator1;
    private javax.swing.JSplitPane splitPane;
    // End of variables declaration//GEN-END:variables

    private static class SimpleParentNode extends AbstractNode {

        private static SimpleParentNode createFromChildNodes(Node... nodes) {
            Children.Array array = new Children.Array();
            array.add(nodes);
            return new SimpleParentNode(array);
        }

        private SimpleParentNode(Children children) {
            super(children);
        }
    }
}
