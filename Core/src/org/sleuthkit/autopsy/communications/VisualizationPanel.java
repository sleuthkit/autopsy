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
import com.mxgraph.model.mxCell;
import com.mxgraph.model.mxICell;
import com.mxgraph.swing.handler.mxRubberband;
import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.swing.util.mxMorphing;
import com.mxgraph.util.mxConstants;
import com.mxgraph.util.mxEvent;
import com.mxgraph.util.mxEventObject;
import com.mxgraph.util.mxEventSource;
import com.mxgraph.util.mxPoint;
import com.mxgraph.view.mxCellState;
import com.mxgraph.view.mxGraph;
import com.mxgraph.view.mxGraphView;
import com.mxgraph.view.mxStylesheet;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.beans.PropertyVetoException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import static java.util.Collections.singleton;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLayeredPane;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JSplitPane;
import javax.swing.JToolBar;
import javax.swing.OverlayLayout;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import org.jdesktop.layout.GroupLayout;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.openide.explorer.ExplorerManager;
import org.openide.explorer.ExplorerUtils;
import org.openide.nodes.Node;
import org.openide.util.Exceptions;
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
    private static final URL MARKER_PIN_URL = VisualizationPanel.class.getResource("/org/sleuthkit/autopsy/communications/images/marker--pin.png");
    static final private ImageIcon pinIcon = new ImageIcon(MARKER_PIN_URL);
    static final private ImageIcon addPinIcon =
            new ImageIcon(VisualizationPanel.class.getResource("/org/sleuthkit/autopsy/communications/images/marker--plus.png"));
    static final private ImageIcon unpinIcon =
            new ImageIcon(VisualizationPanel.class.getResource("/org/sleuthkit/autopsy/communications/images/marker--minus.png"));

    static final private mxStylesheet mxStylesheet = new mxStylesheet();

    static {
        //initialize defaul cell (Vertex and/or Edge)  properties
        mxStylesheet.getDefaultVertexStyle().put(mxConstants.STYLE_SHAPE, mxConstants.SHAPE_ELLIPSE);
        mxStylesheet.getDefaultVertexStyle().put(mxConstants.STYLE_PERIMETER, mxConstants.PERIMETER_ELLIPSE);
        mxStylesheet.getDefaultVertexStyle().put(mxConstants.STYLE_FONTCOLOR, "000000");
//        mxStylesheet.getDefaultVertexStyle().put(mxConstants.STYLE_WHITE_SPACE, "wrap");

        mxStylesheet.getDefaultEdgeStyle().put(mxConstants.STYLE_NOLABEL, true);
//        mxStylesheet.getDefaultEdgeStyle().put(mxConstants.STYLE_OPACITY, 50        );
//        mxStylesheet.getDefaultEdgeStyle().put(mxConstants.STYLE_ROUNDED, true);
        mxStylesheet.getDefaultEdgeStyle().put(mxConstants.STYLE_PERIMETER_SPACING, 0);
        mxStylesheet.getDefaultEdgeStyle().put(mxConstants.STYLE_ENDARROW, mxConstants.NONE);
        mxStylesheet.getDefaultEdgeStyle().put(mxConstants.STYLE_STARTARROW, mxConstants.NONE);
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
        graph = new mxGraph() {
            @Override
            public String convertValueToString(Object cell) {
                Object value = getModel().getValue(cell);
                if (value instanceof AccountDeviceInstanceKey) {
                    final AccountDeviceInstanceKey adiKey = (AccountDeviceInstanceKey) value;
                    final String accountName = adiKey.getAccountDeviceInstance().getAccount().getTypeSpecificID();
                    String iconFileName = Utils.getIconFileName(adiKey.getAccountDeviceInstance().getAccount().getAccountType());
                    String label = "<img src=\""
                            + VisualizationPanel.class.getResource("/org/sleuthkit/autopsy/communications/images/" + iconFileName)
                            + "\">" + accountName;
                    if (pinnedAccountDevices.contains(adiKey)) {
                        label += "<img src=\"" + MARKER_PIN_URL + "\">";
                    }
                    return "<span>" + label + "</span>";
                } else {
                    return "";
                }
            }

            @Override
            public String getToolTipForCell(Object cell) {
                return ((mxCell) cell).getId();
            }

        };
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
        graph.setResetEdgesOnMove(true);
        graph.setHtmlLabels(true);
        graph.setStylesheet(mxStylesheet);

        graphComponent = new mxGraphComponent(graph);
        graphComponent.setAutoExtend(true);
        graphComponent.setAutoScroll(true);
        graphComponent.setAutoscrolls(true);
        graphComponent.setConnectable(false);
        graphComponent.setKeepSelectionVisibleOnZoom(true);
        graphComponent.setOpaque(true);
        graphComponent.setToolTips(true);
        graphComponent.setBackground(Color.WHITE);
        layeredPane.add(graphComponent, new Integer(0));
        layeredPane.remove(progressOverlay);

        //install rubber band selection handler
        rubberband = new mxRubberband(graphComponent);

        //right click handler
        graphComponent.getGraphControl().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                super.mouseWheelMoved(e);
                if (e.getPreciseWheelRotation() > 0) {
                    graphComponent.zoomIn();
                } else if (e.getPreciseWheelRotation() < 0) {
                    graphComponent.zoomOut();
                }

            }

            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                if (SwingUtilities.isRightMouseButton(e)) {
                    mxICell cellAt = (mxICell) graphComponent.getCellAt(e.getX(), e.getY());
                    if (cellAt != null && cellAt.isVertex()) {

                        JPopupMenu jPopupMenu = new JPopupMenu();

                        if (pinnedAccountDevices.contains(cellAt.getValue())) {
                            jPopupMenu.add(new JMenuItem(new AbstractAction("Unpin Account " + cellAt.getId(), unpinIcon) {
                                @Override
                                public void actionPerformed(ActionEvent e) {
                                    handleUnPinEvent(new CVTEvents.UnpinAccountsEvent(singleton((AccountDeviceInstanceKey) cellAt.getValue())));
                                }
                            }));
                        } else {

                            jPopupMenu.add(new JMenuItem(new AbstractAction("Pin Account " + cellAt.getId(), addPinIcon) {
                                @Override
                                public void actionPerformed(ActionEvent e) {
                                    handlePinEvent(new CVTEvents.PinAccountsEvent(singleton((AccountDeviceInstanceKey) cellAt.getValue()), false));
                                }
                            }));
                            jPopupMenu.add(new JMenuItem(new AbstractAction("Reset and Pin Account " + cellAt.getId(), pinIcon) {
                                @Override
                                public void actionPerformed(ActionEvent e) {
                                    handlePinEvent(new CVTEvents.PinAccountsEvent(singleton((AccountDeviceInstanceKey) cellAt.getValue()), true));
                                }
                            }));
                        }

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
        final mxCell vertex = nodeMap.computeIfAbsent(name, vertexName -> {
            double size = Math.sqrt(accountDeviceInstanceKey.getMessageCount()) + 10;

            mxCell newVertex = (mxCell) graph.insertVertex(
                    graph.getDefaultParent(),
                    vertexName, accountDeviceInstanceKey,
                    Math.random() * graphComponent.getWidth(),
                    Math.random() * graphComponent.getHeight(),
                    size,
                    size);
            return newVertex;
        });
        final mxCellState state = graph.getView().getState(vertex, true);

        graph.getView().updateLabel(state);
        graph.getView().updateLabelBounds(state);
        return vertex;
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
    void handleUnPinEvent(CVTEvents.UnpinAccountsEvent pinEvent) {
        graph.getModel().beginUpdate();
        try {

            pinnedAccountDevices.removeAll(pinEvent.getAccountDeviceInstances());
            clearGraph();
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

        ProgressHandle handle = ProgressHandleFactory.createHandle("Rebuiling graph");
        layeredPane.add(progressOverlay, new Integer(1));
        new SwingWorker<Set<RelationshipModel>, Void>() {
            @Override
            protected Set<RelationshipModel> doInBackground() throws Exception {
                handle.start();
                Set<RelationshipModel> relationshipModels = new HashSet<>();
                try {

                    /**
                     * set to keep track of accounts related to pinned accounts
                     */
                    Set<AccountDeviceInstanceKey> relatedAccounts = new HashSet<>();
                    for (AccountDeviceInstanceKey adiKey : pinnedAccountDevices) {
                        List<AccountDeviceInstance> relatedAccountDeviceInstances =
                                commsManager.getRelatedAccountDeviceInstances(adiKey.getAccountDeviceInstance(), currentFilter);

                        relatedAccounts.add(adiKey);
                        //get accounts related to pinned account
                        for (AccountDeviceInstance relatedADI : relatedAccountDeviceInstances) {
//                            handle.progress(1);
                            long adiRelationshipsCount = commsManager.getRelationshipSourcesCount(relatedADI, currentFilter);
                            final AccountDeviceInstanceKey relatedADIKey = new AccountDeviceInstanceKey(relatedADI, currentFilter, adiRelationshipsCount);
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
                                relationshipModels.add(new RelationshipModel(relationships, adiKey1, adiKey2));
                            }
                        }
                    }
                } catch (TskCoreException tskCoreException) {
                    logger.log(Level.SEVERE, "Error", tskCoreException);
                } finally {
                }
                return relationshipModels;
            }

            @Override
            protected void done() {
                super.done();
                try {
                    Set<RelationshipModel> get = get();
                    for (RelationshipModel r : get) {
                        addEdge(r.getSources(), r.getAccount1(), r.getAccount2());
                    }
                } catch (InterruptedException | ExecutionException | TskCoreException ex) {
                    Exceptions.printStackTrace(ex);
                } finally {
                    handle.finish();
                    layeredPane.remove(progressOverlay);
                }
            }
        }.execute();

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
        jButton6 = new JButton();
        jButton1 = new JButton();
        jButton8 = new JButton();
        jButton7 = new JButton();
        jSeparator1 = new JToolBar.Separator();
        zoomOutButton = new JButton();
        fitGraphButton = new JButton();
        zoomInButton = new JButton();
        layeredPane = new JLayeredPane();
        progressOverlay = new JPanel();
        jProgressBar1 = new JProgressBar();

        setLayout(new BorderLayout());

        splitPane.setDividerLocation(800);
        splitPane.setResizeWeight(0.5);

        jPanel1.setLayout(new BorderLayout());

        jToolBar1.setRollover(true);

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
        jToolBar1.add(jSeparator1);

        zoomOutButton.setIcon(new ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/communications/images/magnifier-zoom-out-red.png"))); // NOI18N
        zoomOutButton.setText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.zoomOutButton.text")); // NOI18N
        zoomOutButton.setFocusable(false);
        zoomOutButton.setHorizontalTextPosition(SwingConstants.CENTER);
        zoomOutButton.setVerticalTextPosition(SwingConstants.BOTTOM);
        zoomOutButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                zoomOutButtonActionPerformed(evt);
            }
        });
        jToolBar1.add(zoomOutButton);

        fitGraphButton.setIcon(new ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/communications/images/magnifier-zoom-fit.png"))); // NOI18N
        fitGraphButton.setText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.fitGraphButton.text")); // NOI18N
        fitGraphButton.setFocusable(false);
        fitGraphButton.setHorizontalTextPosition(SwingConstants.CENTER);
        fitGraphButton.setVerticalTextPosition(SwingConstants.BOTTOM);
        fitGraphButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                fitGraphButtonActionPerformed(evt);
            }
        });
        jToolBar1.add(fitGraphButton);

        zoomInButton.setIcon(new ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/communications/images/magnifier-zoom-in-green.png"))); // NOI18N
        zoomInButton.setText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.zoomInButton.text")); // NOI18N
        zoomInButton.setFocusable(false);
        zoomInButton.setHorizontalTextPosition(SwingConstants.CENTER);
        zoomInButton.setVerticalTextPosition(SwingConstants.BOTTOM);
        zoomInButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                zoomInButtonActionPerformed(evt);
            }
        });
        jToolBar1.add(zoomInButton);

        jPanel1.add(jToolBar1, BorderLayout.NORTH);

        layeredPane.setLayout(new OverlayLayout(layeredPane));

        GroupLayout progressOverlayLayout = new GroupLayout(progressOverlay);
        progressOverlay.setLayout(progressOverlayLayout);
        progressOverlayLayout.setHorizontalGroup(progressOverlayLayout.createParallelGroup(GroupLayout.LEADING)
            .add(GroupLayout.TRAILING, progressOverlayLayout.createSequentialGroup()
                .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .add(jProgressBar1, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        progressOverlayLayout.setVerticalGroup(progressOverlayLayout.createParallelGroup(GroupLayout.LEADING)
            .add(GroupLayout.TRAILING, progressOverlayLayout.createSequentialGroup()
                .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .add(jProgressBar1, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        layeredPane.add(progressOverlay);

        jPanel1.add(layeredPane, BorderLayout.CENTER);

        splitPane.setLeftComponent(jPanel1);

        add(splitPane, BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents

    private void jButton1ActionPerformed(ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
        morph(new mxFastOrganicLayout(graph) {
            @Override
            public boolean isVertexMovable(Object vertex) {
                return super.isVertexMovable(vertex) && false == pinnedAccountDevices.contains((AccountDeviceInstanceKey) ((mxICell) vertex).getValue());
            }
        });
    }//GEN-LAST:event_jButton1ActionPerformed

    private void zoomInButtonActionPerformed(ActionEvent evt) {//GEN-FIRST:event_zoomInButtonActionPerformed
        graphComponent.zoomIn();
    }//GEN-LAST:event_zoomInButtonActionPerformed

    private void zoomOutButtonActionPerformed(ActionEvent evt) {//GEN-FIRST:event_zoomOutButtonActionPerformed
        graphComponent.zoomOut();
    }//GEN-LAST:event_zoomOutButtonActionPerformed

    private void jButton6ActionPerformed(ActionEvent evt) {//GEN-FIRST:event_jButton6ActionPerformed
        morph(new mxHierarchicalLayout(graph));
    }//GEN-LAST:event_jButton6ActionPerformed

    private void jButton7ActionPerformed(ActionEvent evt) {//GEN-FIRST:event_jButton7ActionPerformed
        morph(new mxCircleLayout(graph));
    }//GEN-LAST:event_jButton7ActionPerformed

    private void jButton8ActionPerformed(ActionEvent evt) {//GEN-FIRST:event_jButton8ActionPerformed
        mxOrganicLayout mxOrganicLayout = new mxOrganicLayout(graph);
        mxOrganicLayout.setMaxIterations(10);
        morph(mxOrganicLayout);
    }//GEN-LAST:event_jButton8ActionPerformed

    private void fitGraphButtonActionPerformed(ActionEvent evt) {//GEN-FIRST:event_fitGraphButtonActionPerformed
        fitGraph();
    }//GEN-LAST:event_fitGraphButtonActionPerformed

    private void fitGraph() {
        mxGraphView view = graphComponent.getGraph().getView();
        view.setTranslate(new mxPoint(-view.getGraphBounds().getX(), -view.getGraphBounds().getY()));

//        final double widthFactor = (double) graphComponent.getWidth() / (int) view.getGraphBounds().getWidth();
//        final double heightFactor = (double) graphComponent.getHeight() / (int) view.getGraphBounds().getHeight();
//
//        view.setScale(Math.min(widthFactor, heightFactor) * view.getScale());
    }

    private void morph(mxIGraphLayout layout) {
        // layout using morphing
        graph.getModel().beginUpdate();
        try {
            layout.execute(graph.getDefaultParent());
        } finally {
            mxMorphing morph = new mxMorphing(graphComponent, 20, 1.2, 20);
            morph.addListener(mxEvent.DONE, (Object sender, mxEventObject event) -> {
                graph.getModel().endUpdate();
//                fitGraph();
            });

            morph.startAnimation();
        }
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private JButton fitGraphButton;
    private JButton jButton1;
    private JButton jButton6;
    private JButton jButton7;
    private JButton jButton8;
    private JPanel jPanel1;
    private JProgressBar jProgressBar1;
    private JToolBar.Separator jSeparator1;
    private JToolBar jToolBar1;
    private JLayeredPane layeredPane;
    private JPanel progressOverlay;
    private JSplitPane splitPane;
    private JButton zoomInButton;
    private JButton zoomOutButton;
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

    private static class RelationshipModel {

        private final List<Content> relationshipSources;
        private final AccountDeviceInstanceKey adiKey1;
        private final AccountDeviceInstanceKey adiKey2;

        private RelationshipModel(List<Content> relationships, AccountDeviceInstanceKey adiKey1, AccountDeviceInstanceKey adiKey2) {
            this.relationshipSources = relationships;
            this.adiKey1 = adiKey1;
            this.adiKey2 = adiKey2;
        }

        public List<Content> getSources() {
            return Collections.unmodifiableList(relationshipSources);
        }

        public AccountDeviceInstanceKey getAccount1() {
            return adiKey1;
        }

        public AccountDeviceInstanceKey getAccount2() {
            return adiKey2;
        }

    }
}
