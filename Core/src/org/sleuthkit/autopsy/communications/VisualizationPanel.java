/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017 Basis Technology Corp.
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

import com.google.common.eventbus.Subscribe;
import com.mxgraph.layout.mxFastOrganicLayout;
import com.mxgraph.layout.mxOrganicLayout;
import com.mxgraph.model.mxCell;
import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.util.mxConstants;
import com.mxgraph.view.mxGraph;
import com.mxgraph.view.mxStylesheet;
import java.awt.Color;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.beans.PropertyVetoException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;
import javax.swing.JPanel;
import org.apache.commons.lang3.StringUtils;
import org.openide.explorer.ExplorerManager;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.communications.AccountsRootChildren.AccountDeviceInstanceNode;
import static org.sleuthkit.autopsy.communications.RelationshipNode.getAttributeDisplayString;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AccountDeviceInstance;
import org.sleuthkit.datamodel.BlackboardArtifact;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL_FROM;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL_TO;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_FROM;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_TO;
import org.sleuthkit.datamodel.CommunicationsFilter;
import org.sleuthkit.datamodel.CommunicationsManager;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 */
public class VisualizationPanel extends JPanel {

    static final private mxStylesheet mxStylesheet = new mxStylesheet();
    private ExplorerManager explorerManager;
    private final mxGraph graph;

    private final Map<String, mxCell> nodeMap = new HashMap<>();
    private final mxGraphComponent graphComponent;

    static {
        mxStylesheet.getDefaultVertexStyle().put(mxConstants.STYLE_SHAPE, mxConstants.SHAPE_ELLIPSE);
        mxStylesheet.getDefaultEdgeStyle().put(mxConstants.STYLE_NOLABEL, true);
    }

    /**
     * Creates new form VizPanel
     */
    public VisualizationPanel() {
        initComponents();
        graph = new mxGraph();
        graph.setCellsEditable(false);
        graph.setCellsResizable(false);
        graph.setCellsMovable(true);
        graph.setCellsDisconnectable(false);
        graph.setConnectableEdges(false);
        graph.setDisconnectOnMove(false);
        graph.setEdgeLabelsMovable(false);
        graph.setVertexLabelsMovable(false);
        graphComponent = new mxGraphComponent(graph);
        graphComponent.setAutoScroll(true);
        graphComponent.setOpaque(true);
        graphComponent.setBackground(Color.WHITE);
        this.add(graphComponent);
        graphComponent.addMouseWheelListener(new MouseWheelListener() {
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                graphComponent.zoomTo(graphComponent.getZoomFactor() + e.getPreciseWheelRotation(), true);
            }
        });

        CVTEvents.getCVTEventBus().register(this);

        graph.setStylesheet(mxStylesheet);

    }

    public void initVisualization(ExplorerManager em, ExplorerManager messageBrowserManager) {
        explorerManager = em;

        graph.getSelectionModel().addListener(null, (sender, evt) -> {
            Object[] selectionCells = graph.getSelectionCells();
            if (selectionCells.length == 1) {
                mxCell selectionCell = (mxCell) selectionCells[0];
                try {
                    CommunicationsManager commsManager = Case.getCurrentCase().getSleuthkitCase().getCommunicationsManager();

                    if (selectionCell.isVertex()) {
                        final AccountDeviceInstanceNode accountDeviceInstanceNode =
                                new AccountDeviceInstanceNode(((AccountDeviceInstanceKey) selectionCell.getValue()),
                                        commsManager);
                        messageBrowserManager.setRootContext(SimpleParentNode.createFromChildNodes(accountDeviceInstanceNode));
                        messageBrowserManager.setSelectedNodes(new Node[]{accountDeviceInstanceNode});

                    } else if (selectionCell.isEdge()) {
                        System.out.println(selectionCell.getId());
//                        explorerManager.setRootContext(new CommunicationsBundleNode(adiKey, commsManager));
                    }
                } catch (TskCoreException tskCoreException) {
                    Logger.getLogger(VisualizationPanel.class.getName()).log(Level.SEVERE,
                            "Could not get communications manager for current case", tskCoreException);
                } catch (PropertyVetoException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        });
    }

    private void addEdge(mxCell pinnedAccountVertex, mxCell relatedAccountVertex) {

        Object[] edgesBetween = graph.getEdgesBetween(pinnedAccountVertex, relatedAccountVertex);

        if (edgesBetween.length == 0) {
            final String edgeName = pinnedAccountVertex.getId() + " <-> " + relatedAccountVertex.getId();
            mxCell edge = (mxCell) graph.insertEdge(graph.getDefaultParent(), edgeName, 1d, pinnedAccountVertex, relatedAccountVertex);
        } else if (edgesBetween.length == 1) {
            final mxCell edge = (mxCell) edgesBetween[0];
            edge.setValue(1d + (double) edge.getValue());
            edge.setStyle("strokeWidth=" + Math.log((double) edge.getValue()));
        }
    }

    private void addEdge(BlackboardArtifact artifact) throws TskCoreException {
        BlackboardArtifact.ARTIFACT_TYPE artfType = BlackboardArtifact.ARTIFACT_TYPE.fromID(artifact.getArtifactTypeID());
        if (null != artfType) {

            String from = null;
            String[] tos = new String[0];

            //Consider refactoring this to reduce boilerplate
            switch (artfType) {
                case TSK_EMAIL_MSG:
                    from = StringUtils.strip(getAttributeDisplayString(artifact, TSK_EMAIL_FROM), " \t\n;");
                    tos = StringUtils.strip(getAttributeDisplayString(artifact, TSK_EMAIL_TO), " \t\n;").split(";");
                    break;
                case TSK_MESSAGE:
                    from = getAttributeDisplayString(artifact, TSK_PHONE_NUMBER_FROM);
                    tos = getAttributeDisplayString(artifact, TSK_PHONE_NUMBER_TO).split(";");
                    break;
                case TSK_CALLLOG:
                    from = getAttributeDisplayString(artifact, TSK_PHONE_NUMBER_FROM);
                    tos = getAttributeDisplayString(artifact, TSK_PHONE_NUMBER_TO).split(";");
                    break;
                default:
                    break;
            }
            for (String to : tos) {
                if (StringUtils.isNotBlank(from) && StringUtils.isNotBlank(to)) {

                    mxCell fromV = getOrCreateNodeDraft(from, 10);
                    mxCell toV = getOrCreateNodeDraft(to, 10);

                    Object[] edgesBetween = graph.getEdgesBetween(fromV, toV);

                    if (edgesBetween.length == 0) {
                        final String edgeName = from + "->" + to;
                        mxCell edge = (mxCell) graph.insertEdge(graph.getDefaultParent(), edgeName, 1d, fromV, toV);
                    } else if (edgesBetween.length == 1) {
                        final mxCell edge = (mxCell) edgesBetween[0];
                        edge.setValue(1d + (double) edge.getValue());
                        edge.setStyle("strokeWidth=" + Math.log((double) edge.getValue()));
                    }
                }
            }
        }
    }

    @Subscribe
    public void pinAccount(PinAccountEvent pinEvent) {

        final AccountDeviceInstanceNode adiNode = pinEvent.getAccountDeviceInstanceNode();
        final AccountDeviceInstanceKey adiKey = adiNode.getAccountDeviceInstanceKey();

        graph.getModel().beginUpdate();
        try {
            nodeMap.clear();
            graph.removeCells(graph.getChildCells(graph.getDefaultParent(), true, true));

            mxCell pinnedAccountVertex = getOrCreateNodeDraft(adiKey);
            CommunicationsManager commsManager = adiNode.getCommsManager();
            final CommunicationsFilter commsFilter = adiNode.getFilter();
            List<AccountDeviceInstance> relatedAccountDeviceInstances =
                    commsManager.getRelatedAccountDeviceInstances(adiNode.getAccountDeviceInstance(), commsFilter);

            for (AccountDeviceInstance relatedADI : relatedAccountDeviceInstances) {
                long communicationsCount = commsManager.getRelationshipSourcesCount(relatedADI, commsFilter);
                String dataSourceName = AccountsRootChildren.getDataSourceName(relatedADI);
                AccountDeviceInstanceKey relatedADIKey = new AccountDeviceInstanceKey(relatedADI, commsFilter, communicationsCount, dataSourceName);
                mxCell relatedAccountVertex = getOrCreateNodeDraft(relatedADIKey);

                addEdge(pinnedAccountVertex, relatedAccountVertex);
            }
        } catch (TskCoreException ex) {
            Exceptions.printStackTrace(ex);
        } finally {
            // Updates the display
            graph.getModel().endUpdate();
            revalidate();
        }

        new mxFastOrganicLayout(graph).execute(graph.getDefaultParent());
    }

    private mxCell getOrCreateNodeDraft(AccountDeviceInstanceKey accountDeviceInstanceKey) {
        final AccountDeviceInstance accountDeviceInstance = accountDeviceInstanceKey.getAccountDeviceInstance();
        final String name =// accountDeviceInstance.getDeviceId() + ":"                +
                accountDeviceInstance.getAccount().getTypeSpecificID();
        mxCell nodeDraft = nodeMap.get(name);
        if (nodeDraft == null) {
            double size = accountDeviceInstanceKey.getMessageCount() / 10;
            nodeDraft = (mxCell) graph.insertVertex(
                    graph.getDefaultParent(),
                    name, accountDeviceInstanceKey,
                    new Random().nextInt(200),
                    new Random().nextInt(200),
                    size,
                    size);
            graph.getView().getState(nodeDraft, true).setLabel(name);
            nodeMap.put(name, nodeDraft);
        }
        return nodeDraft;
    }

    private mxCell getOrCreateNodeDraft(String name, long size) {
        mxCell nodeDraft = nodeMap.get(name);
        if (nodeDraft == null) {
            nodeDraft = (mxCell) graph.insertVertex(
                    graph.getDefaultParent(),
                    name,
                    name,
                    new Random().nextInt(200),
                    new Random().nextInt(200),
                    size,
                    size);
            graph.getView().getState(nodeDraft, true).setLabel(name);
            nodeMap.put(name, nodeDraft);
        }
        return nodeDraft;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jToolBar1 = new javax.swing.JToolBar();
        jButton1 = new javax.swing.JButton();
        jButton2 = new javax.swing.JButton();

        setLayout(new java.awt.BorderLayout());

        jToolBar1.setRollover(true);

        jButton1.setText(org.openide.util.NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.jButton1.text")); // NOI18N
        jButton1.setFocusable(false);
        jButton1.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        jButton1.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton1ActionPerformed(evt);
            }
        });
        jToolBar1.add(jButton1);

        jButton2.setText(org.openide.util.NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.jButton2.text")); // NOI18N
        jButton2.setFocusable(false);
        jButton2.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        jButton2.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        jButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton2ActionPerformed(evt);
            }
        });
        jToolBar1.add(jButton2);

        add(jToolBar1, java.awt.BorderLayout.PAGE_START);
    }// </editor-fold>//GEN-END:initComponents

    private void jButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton2ActionPerformed
//        graphComponent.addMouseListener(new mxPanningHandler(graphComponent));
graphComponent.getPanningHandler().setEnabled(true);
    }//GEN-LAST:event_jButton2ActionPerformed

    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
        new mxOrganicLayout(graph).execute(graph.getDefaultParent());
//        new mxCompactTreeLayout(graph).execute(graph.getDefaultParent());
        graphComponent.zoomAndCenter();
    }//GEN-LAST:event_jButton1ActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton jButton1;
    private javax.swing.JButton jButton2;
    private javax.swing.JToolBar jToolBar1;
    // End of variables declaration//GEN-END:variables

    static class SimpleParentNode extends AbstractNode {

        static SimpleParentNode createFromChildNodes(Node... nodes) {
            Children.Array array = new Children.Array();
            array.add(nodes);
            return new SimpleParentNode(array);
        }

        public SimpleParentNode(Children children) {
            super(children);
        }
    }
}
