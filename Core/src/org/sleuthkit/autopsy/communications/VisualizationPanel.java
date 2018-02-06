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
import com.mxgraph.util.mxEvent;
import com.mxgraph.util.mxEventObject;
import com.mxgraph.util.mxEventSource;
import com.mxgraph.util.mxPoint;
import com.mxgraph.util.mxRectangle;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.beans.PropertyVetoException;
import java.net.URL;
import java.util.Arrays;
import static java.util.Collections.singleton;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JSplitPane;
import javax.swing.JToolBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import org.jdesktop.layout.GroupLayout;
import org.openide.explorer.ExplorerManager;
import org.openide.explorer.ExplorerUtils;
import org.openide.nodes.Node;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ProxyLookup;
import org.sleuthkit.autopsy.casemodule.Case;
import static org.sleuthkit.autopsy.casemodule.Case.Events.CURRENT_CASE;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.progress.ProgressIndicator;
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

    private final ExplorerManager vizEM = new ExplorerManager();
    private final ExplorerManager gacEM = new ExplorerManager();
    private final ProxyLookup proxyLookup = new ProxyLookup(
            ExplorerUtils.createLookup(gacEM, getActionMap()),
            ExplorerUtils.createLookup(vizEM, getActionMap()));

    private final mxGraphComponent graphComponent;
    private final mxGraphImpl graph;

    private CommunicationsManager commsManager;
    private CommunicationsFilter currentFilter;
    private final mxRubberband rubberband;

    public VisualizationPanel() {
        initComponents();
        graph = new mxGraphImpl();

        graphComponent = new mxGraphComponent(graph);
        graphComponent.setAutoExtend(true);
        graphComponent.setAutoScroll(true);
        graphComponent.setAutoscrolls(true);
        graphComponent.setConnectable(false);
        graphComponent.setDragEnabled(false);
        graphComponent.setKeepSelectionVisibleOnZoom(true);
        graphComponent.setOpaque(true);
        graphComponent.setToolTips(true);
        graphComponent.setBackground(Color.WHITE);
        borderLayoutPanel.add(graphComponent, BorderLayout.CENTER);
        progressBar.setVisible(false);

        //install rubber band selection handler
        rubberband = new mxRubberband(graphComponent);
//        new mxLayoutManager(graph) {
//            final private mxOrganicLayout layout;
//            private int counter;
//            {
//                this.layout = new mxOrganicLayout(graph);
//                this.layout.setMaxIterations(1);
//            }
//
//            @Override
//            protected void executeLayout(mxIGraphLayout layout, Object parent) {
//                if (counter % 10 == 0)
//                {
//                    super.executeLayout(layout, parent);
////                fitGraph();
//                }
//                counter++;
//            }
//
//            @Override
//            public mxIGraphLayout getLayout(Object parent) {
//                if (graph.getModel().getChildCount(parent) > 0) {
//                    return layout;
//                }
//                return null;
//            }
//        };
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
                        if (graph.isAccountPinned((AccountDeviceInstanceKey) cellAt.getValue())) {
                            jPopupMenu.add(new JMenuItem(new AbstractAction("Unpin Account " + cellAt.getId(), unpinIcon) {
                                @Override
                                public void actionPerformed(ActionEvent e) {
                                    handleUnPinEvent(new CVTEvents.UnpinAccountsEvent(singleton((AccountDeviceInstanceKey) cellAt.getValue())));
                                }
                            }));
                        } else {

                            jPopupMenu.add(new JMenuItem(new AbstractAction("Lock Account " + cellAt.getId(), addPinIcon) {
                                @Override
                                public void actionPerformed(ActionEvent e) {
                                    graph.lockAccount((AccountDeviceInstanceKey) cellAt.getValue());
                                }
                            }));

                            jPopupMenu.add(new JMenuItem(new AbstractAction("UnLock Account " + cellAt.getId(), addPinIcon) {
                                @Override
                                public void actionPerformed(ActionEvent e) {
                                    graph.unlockAccount((AccountDeviceInstanceKey) cellAt.getValue());
                                }
                            }));
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

    @Subscribe
    void handleUnPinEvent(CVTEvents.UnpinAccountsEvent pinEvent) {
        graph.getModel().beginUpdate();
        try {
            graph.unpinAccount(pinEvent.getAccountDeviceInstances());
            graph.clear();
            rebuildGraph();
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error pinning accounts", ex);
        } finally {
            // Updates the display
            graph.getModel().endUpdate();
        }

        applyOrganicLayout(10);
    }

    @Subscribe
    void handlePinEvent(CVTEvents.PinAccountsEvent pinEvent) {
        graph.getModel().beginUpdate();
        try {
            if (pinEvent.isReplace()) {
                graph.resetGraph();
            }
            
            graph.pinAccount(pinEvent.getAccountDeviceInstances());
            rebuildGraph();
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error pinning accounts", ex);
        } finally {
            // Updates the display
            graph.getModel().endUpdate();
        }

        applyOrganicLayout(10);
    }

    @Subscribe
    void handleFilterEvent(CVTEvents.FilterChangeEvent filterChangeEvent) {

        graph.getModel().beginUpdate();
        try {
            graph.clear();
            currentFilter = filterChangeEvent.getNewFilter();
            rebuildGraph();
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error filtering accounts", ex);
        } finally {
            // Updates the display
            graph.getModel().endUpdate();
        }

        applyOrganicLayout(10);
    }

    private void rebuildGraph() throws TskCoreException {

        SwingWorker<?,?> rebuild = graph.rebuild(new ProgressIndicatorImpl(), commsManager, currentFilter);

        rebuild.execute();

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
                graph.resetGraph();
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
        borderLayoutPanel = new JPanel();
        jToolBar1 = new JToolBar();
        jButton6 = new JButton();
        jButton1 = new JButton();
        jButton8 = new JButton();
        jButton7 = new JButton();
        jSeparator1 = new JToolBar.Separator();
        zoomOutButton = new JButton();
        fitGraphButton = new JButton();
        zoomInButton = new JButton();
        statusPanel = new JPanel();
        progressBar = new JProgressBar();

        setLayout(new BorderLayout());

        splitPane.setDividerLocation(800);
        splitPane.setResizeWeight(0.5);

        borderLayoutPanel.setLayout(new BorderLayout());

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

        borderLayoutPanel.add(jToolBar1, BorderLayout.NORTH);

        progressBar.setMaximumSize(new Dimension(200, 14));
        progressBar.setStringPainted(true);

        GroupLayout statusPanelLayout = new GroupLayout(statusPanel);
        statusPanel.setLayout(statusPanelLayout);
        statusPanelLayout.setHorizontalGroup(statusPanelLayout.createParallelGroup(GroupLayout.LEADING)
            .add(GroupLayout.TRAILING, statusPanelLayout.createSequentialGroup()
                .addContainerGap(516, Short.MAX_VALUE)
                .add(progressBar, GroupLayout.PREFERRED_SIZE, 200, GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
        statusPanelLayout.setVerticalGroup(statusPanelLayout.createParallelGroup(GroupLayout.LEADING)
            .add(GroupLayout.TRAILING, statusPanelLayout.createSequentialGroup()
                .add(3, 3, 3)
                .add(progressBar, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                .add(3, 3, 3))
        );

        borderLayoutPanel.add(statusPanel, BorderLayout.SOUTH);

        splitPane.setLeftComponent(borderLayoutPanel);

        add(splitPane, BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents

    private void jButton1ActionPerformed(ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
        morph(new mxFastOrganicLayout(graph));
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
        applyOrganicLayout(10);
    }//GEN-LAST:event_jButton8ActionPerformed

    private void applyOrganicLayout(int iterations) {
        mxOrganicLayout mxOrganicLayout = new mxOrganicLayout(graph) {
            @Override
            public boolean isVertexMovable(Object vertex) {
                return super.isVertexMovable(vertex); //To change body of generated methods, choose Tools | Templates.
            }

        };
        mxOrganicLayout.setMaxIterations(iterations);
        morph(mxOrganicLayout);
    }

    private void fitGraphButtonActionPerformed(ActionEvent evt) {//GEN-FIRST:event_fitGraphButtonActionPerformed
        fitGraph();
    }//GEN-LAST:event_fitGraphButtonActionPerformed

    private void fitGraph() {
        final Object[] childVertices = graph.getChildVertices(graph.getDefaultParent());
        mxRectangle boundsForCells = graph.getBoundsForCells(childVertices, true, true, true);
        if (boundsForCells == null) {
            boundsForCells = new mxRectangle();
        }
        mxPoint translate = graph.getView().getTranslate();
        if (translate == null) {
            translate = new mxPoint();
        }

        graph.getView().setTranslate(new mxPoint(translate.getX() - boundsForCells.getX(), translate.getY() - boundsForCells.getY()));


//        graphComponent.zoomActual();
//        graphComponent.zoomAndCenter();
//        graph.getGraphBounds().getWidth()
    }

    private void morph(mxIGraphLayout layout) {
        // layout using morphing
        graph.getModel().beginUpdate();
        try {
            progressBar.setVisible(true);
            progressBar.setIndeterminate(true);
            progressBar.setString("applying layout");
            layout.execute(graph.getDefaultParent());
        } finally {
            mxMorphing morph = new mxMorphing(graphComponent, 20, 1.2, 20);
            morph.addListener(mxEvent.DONE, (Object sender, mxEventObject event) -> {
                graph.getModel().endUpdate();
                fitGraph();
                progressBar.setVisible(false);
                progressBar.setValue(0);
            });
            morph.addListener(mxEvent.EXECUTE, (Object sender, mxEventObject event) -> {
//                fitGraph();
            });

            morph.startAnimation();
        }
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private JPanel borderLayoutPanel;
    private JButton fitGraphButton;
    private JButton jButton1;
    private JButton jButton6;
    private JButton jButton7;
    private JButton jButton8;
    private JToolBar.Separator jSeparator1;
    private JToolBar jToolBar1;
    private JProgressBar progressBar;
    private JSplitPane splitPane;
    private JPanel statusPanel;
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

    private class ProgressIndicatorImpl implements ProgressIndicator {

        @Override
        public void start(String message, int totalWorkUnits) {
            SwingUtilities.invokeLater(() -> {
                progressBar.setVisible(true);
                progressBar.setIndeterminate(false);
                progressBar.setString(message);
                progressBar.setMaximum(totalWorkUnits);
                progressBar.setValue(0);
            });
        }

        @Override
        public void start(String message) {
            SwingUtilities.invokeLater(() -> {
                progressBar.setVisible(true);
                progressBar.setString(message);
                progressBar.setIndeterminate(true);
            });
        }

        @Override
        public void switchToIndeterminate(String message) {
            SwingUtilities.invokeLater(() -> {
                progressBar.setVisible(true);
                progressBar.setIndeterminate(true);
                progressBar.setString(message);
            });
        }

        @Override
        public void switchToDeterminate(String message, int workUnitsCompleted, int totalWorkUnits) {
            SwingUtilities.invokeLater(() -> {
                progressBar.setVisible(true);
                progressBar.setIndeterminate(false);
                progressBar.setString(message);
                progressBar.setMaximum(totalWorkUnits);
                progressBar.setValue(workUnitsCompleted);
            });
        }

        @Override
        public void progress(String message) {
            SwingUtilities.invokeLater(() -> {
                progressBar.setString(message);
            });
        }

        @Override
        public void progress(int workUnitsCompleted) {
            SwingUtilities.invokeLater(() -> {
                progressBar.setValue(workUnitsCompleted);
            });
        }

        @Override
        public void progress(String message, int workUnitsCompleted) {
            SwingUtilities.invokeLater(() -> {
                progressBar.setString(message);
                progressBar.setValue(workUnitsCompleted);
            });
        }

        @Override
        public void finish() {
            SwingUtilities.invokeLater(() -> {
                progressBar.setValue(progressBar.getValue());
                progressBar.setVisible(false);
            });
        }
    }
}
