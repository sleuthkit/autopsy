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
import java.text.DecimalFormat;
import java.util.Arrays;
import static java.util.Collections.singleton;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JToolBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import org.jdesktop.layout.GroupLayout;
import org.jdesktop.layout.LayoutStyle;
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
    private static final String BASE_IMAGE_PATH = "/org/sleuthkit/autopsy/communications/images";
    static final private ImageIcon pinIcon =
            new ImageIcon(VisualizationPanel.class.getResource(BASE_IMAGE_PATH + "/marker--pin.png"));
    static final private ImageIcon addPinIcon =
            new ImageIcon(VisualizationPanel.class.getResource(BASE_IMAGE_PATH + "/marker--plus.png"));
    static final private ImageIcon unpinIcon =
            new ImageIcon(VisualizationPanel.class.getResource(BASE_IMAGE_PATH + "/marker--minus.png"));
    static final private ImageIcon unlockIcon =
            new ImageIcon(VisualizationPanel.class.getResource(BASE_IMAGE_PATH + "/lock_large_unlocked.png"));
    static final private ImageIcon lockIcon =
            new ImageIcon(VisualizationPanel.class.getResource(BASE_IMAGE_PATH + "/lock_large_locked.png"));

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

        final mxEventSource.mxIEventListener scaleListener = (Object sender, mxEventObject evt) ->
                zoomLabel.setText(DecimalFormat.getPercentInstance().format(graph.getScale()));
        graph.getView().addListener(mxEvent.SCALE, scaleListener);
        graph.getView().addListener(mxEvent.SCALE_AND_TRANSLATE, scaleListener);

        //right click handler
        graphComponent.getGraphControl().addMouseWheelListener(new MouseAdapter() {
            @Override
            public void mouseWheelMoved(final MouseWheelEvent e) {
                super.mouseWheelMoved(e);
                if (e.getPreciseWheelRotation() > 0) {
                    graphComponent.zoomTo(graph.getScale() / graphComponent.getZoomFactor(), true);
                } else if (e.getPreciseWheelRotation() < 0) {
                    graphComponent.zoomTo(graph.getScale() * graphComponent.getZoomFactor(), true);
                }
            }
        });

        graphComponent.getGraphControl().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                if (SwingUtilities.isRightMouseButton(e)) {
                    mxICell cellAt = (mxICell) graphComponent.getCellAt(e.getX(), e.getY());
                    if (cellAt != null && cellAt.isVertex()) {
                        JPopupMenu jPopupMenu = new JPopupMenu();
                        AccountDeviceInstanceKey adiKey = (AccountDeviceInstanceKey) cellAt.getValue();

                        if (graph.isAccountLocked(adiKey)) {
                            jPopupMenu.add(new JMenuItem(new AbstractAction("UnLock " + cellAt.getId(), unlockIcon) {
                                @Override
                                public void actionPerformed(ActionEvent e) {
                                    graph.unlockAccount((AccountDeviceInstanceKey) cellAt.getValue());
                                }
                            }));

                        }else {
                            jPopupMenu.add(new JMenuItem(new AbstractAction("Lock " + cellAt.getId(), lockIcon) {
                                @Override
                                public void actionPerformed(ActionEvent e) {
                                    graph.lockAccount((AccountDeviceInstanceKey) cellAt.getValue());
                                }
                            }));

                        }
                        if (graph.isAccountPinned(adiKey))  {
                            jPopupMenu.add(new JMenuItem(new AbstractAction("Unpin " + cellAt.getId(), unpinIcon) {
                                @Override
                                public void actionPerformed(ActionEvent e) {
                                    handleUnPinEvent(new CVTEvents.UnpinAccountsEvent(singleton((AccountDeviceInstanceKey) cellAt.getValue())));
                                }
                            }));
                        } else {

                            jPopupMenu.add(new JMenuItem(new AbstractAction("Pin " + cellAt.getId(), addPinIcon) {
                                @Override
                                public void actionPerformed(ActionEvent e) {
                                    handlePinEvent(new CVTEvents.PinAccountsEvent(singleton((AccountDeviceInstanceKey) cellAt.getValue()), false));
                                }
                            }));
                            jPopupMenu.add(new JMenuItem(new AbstractAction("Pin only " + cellAt.getId(), pinIcon) {
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
        if (graph.hasPinnedAccounts()) {
            borderLayoutPanel.remove(jPanel1);
            borderLayoutPanel.add(graphComponent, BorderLayout.CENTER);
            SwingWorker<?, ?> rebuild = graph.rebuild(new ProgressIndicatorImpl(), commsManager, currentFilter);
            rebuild.execute();
        } else {
            borderLayoutPanel.remove(graphComponent);
            borderLayoutPanel.add(jPanel1, BorderLayout.CENTER);
        }
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
        jToolBar2 = new JToolBar();
        statusLabel = new JLabel();
        progresPanel = new JPanel();
        progressBar = new JProgressBar();
        jPanel1 = new JPanel();
        jTextArea1 = new JTextArea();
        jPanel2 = new JPanel();
        jLabel1 = new JLabel();
        hierarchyLayoutButton = new JButton();
        fastOrganicLayoutButton = new JButton();
        OrganicLayoutButton = new JButton();
        circleLayoutButton = new JButton();
        jSeparator1 = new JToolBar.Separator();
        zoomOutButton = new JButton();
        zoomInButton = new JButton();
        zoomActualButton = new JButton();
        fitZoomButton = new JButton();
        jLabel2 = new JLabel();
        zoomLabel = new JLabel();

        setLayout(new BorderLayout());

        splitPane.setDividerLocation(800);
        splitPane.setResizeWeight(0.5);

        borderLayoutPanel.setLayout(new BorderLayout());

        jToolBar2.setFloatable(false);
        jToolBar2.setRollover(true);

        statusLabel.setText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.statusLabel.text")); // NOI18N
        jToolBar2.add(statusLabel);

        progressBar.setMaximumSize(new Dimension(200, 14));
        progressBar.setStringPainted(true);

        GroupLayout progresPanelLayout = new GroupLayout(progresPanel);
        progresPanel.setLayout(progresPanelLayout);
        progresPanelLayout.setHorizontalGroup(progresPanelLayout.createParallelGroup(GroupLayout.LEADING)
            .add(GroupLayout.TRAILING, progresPanelLayout.createSequentialGroup()
                .add(0, 651, Short.MAX_VALUE)
                .add(progressBar, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
        );
        progresPanelLayout.setVerticalGroup(progresPanelLayout.createParallelGroup(GroupLayout.LEADING)
            .add(GroupLayout.TRAILING, progresPanelLayout.createSequentialGroup()
                .add(0, 0, 0)
                .add(progressBar, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
        );

        jToolBar2.add(progresPanel);

        borderLayoutPanel.add(jToolBar2, BorderLayout.PAGE_END);

        jTextArea1.setBackground(new Color(240, 240, 240));
        jTextArea1.setColumns(20);
        jTextArea1.setLineWrap(true);
        jTextArea1.setRows(5);
        jTextArea1.setText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.jTextArea1.text")); // NOI18N

        GroupLayout jPanel1Layout = new GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(jPanel1Layout.createParallelGroup(GroupLayout.LEADING)
            .add(GroupLayout.TRAILING, jPanel1Layout.createSequentialGroup()
                .addContainerGap(213, Short.MAX_VALUE)
                .add(jTextArea1, GroupLayout.PREFERRED_SIZE, 372, GroupLayout.PREFERRED_SIZE)
                .addContainerGap(214, Short.MAX_VALUE))
        );
        jPanel1Layout.setVerticalGroup(jPanel1Layout.createParallelGroup(GroupLayout.LEADING)
            .add(GroupLayout.TRAILING, jPanel1Layout.createSequentialGroup()
                .addContainerGap(200, Short.MAX_VALUE)
                .add(jTextArea1, GroupLayout.PREFERRED_SIZE, 43, GroupLayout.PREFERRED_SIZE)
                .addContainerGap(200, Short.MAX_VALUE))
        );

        borderLayoutPanel.add(jPanel1, BorderLayout.CENTER);

        jLabel1.setText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.jLabel1.text")); // NOI18N

        hierarchyLayoutButton.setText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.hierarchyLayoutButton.text")); // NOI18N
        hierarchyLayoutButton.setFocusable(false);
        hierarchyLayoutButton.setHorizontalTextPosition(SwingConstants.CENTER);
        hierarchyLayoutButton.setVerticalTextPosition(SwingConstants.BOTTOM);
        hierarchyLayoutButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                hierarchyLayoutButtonActionPerformed(evt);
            }
        });

        fastOrganicLayoutButton.setText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.fastOrganicLayoutButton.text")); // NOI18N
        fastOrganicLayoutButton.setFocusable(false);
        fastOrganicLayoutButton.setHorizontalTextPosition(SwingConstants.CENTER);
        fastOrganicLayoutButton.setVerticalTextPosition(SwingConstants.BOTTOM);
        fastOrganicLayoutButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                fastOrganicLayoutButtonActionPerformed(evt);
            }
        });

        OrganicLayoutButton.setText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.OrganicLayoutButton.text")); // NOI18N
        OrganicLayoutButton.setFocusable(false);
        OrganicLayoutButton.setHorizontalTextPosition(SwingConstants.CENTER);
        OrganicLayoutButton.setVerticalTextPosition(SwingConstants.BOTTOM);
        OrganicLayoutButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                OrganicLayoutButtonActionPerformed(evt);
            }
        });

        circleLayoutButton.setText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.circleLayoutButton.text")); // NOI18N
        circleLayoutButton.setFocusable(false);
        circleLayoutButton.setHorizontalTextPosition(SwingConstants.CENTER);
        circleLayoutButton.setVerticalTextPosition(SwingConstants.BOTTOM);
        circleLayoutButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                circleLayoutButtonActionPerformed(evt);
            }
        });

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

        zoomActualButton.setIcon(new ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/communications/images/magnifier-zoom-actual.png"))); // NOI18N
        zoomActualButton.setText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.zoomActualButton.text")); // NOI18N
        zoomActualButton.setFocusable(false);
        zoomActualButton.setHorizontalTextPosition(SwingConstants.CENTER);
        zoomActualButton.setVerticalTextPosition(SwingConstants.BOTTOM);
        zoomActualButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                zoomActualButtonActionPerformed(evt);
            }
        });

        fitZoomButton.setIcon(new ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/communications/images/magnifier-zoom-fit.png"))); // NOI18N
        fitZoomButton.setText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.fitZoomButton.text")); // NOI18N
        fitZoomButton.setFocusable(false);
        fitZoomButton.setHorizontalTextPosition(SwingConstants.CENTER);
        fitZoomButton.setVerticalTextPosition(SwingConstants.BOTTOM);
        fitZoomButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                fitZoomButtonActionPerformed(evt);
            }
        });

        jLabel2.setText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.jLabel2.text")); // NOI18N

        zoomLabel.setText(NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.zoomLabel.text")); // NOI18N

        GroupLayout jPanel2Layout = new GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(jPanel2Layout.createParallelGroup(GroupLayout.LEADING)
            .add(jPanel2Layout.createSequentialGroup()
                .add(3, 3, 3)
                .add(jLabel1)
                .addPreferredGap(LayoutStyle.RELATED)
                .add(fastOrganicLayoutButton)
                .addPreferredGap(LayoutStyle.RELATED)
                .add(OrganicLayoutButton)
                .addPreferredGap(LayoutStyle.RELATED)
                .add(hierarchyLayoutButton)
                .addPreferredGap(LayoutStyle.RELATED)
                .add(circleLayoutButton)
                .addPreferredGap(LayoutStyle.RELATED)
                .add(jSeparator1, GroupLayout.PREFERRED_SIZE, 10, GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(LayoutStyle.RELATED)
                .add(zoomOutButton, GroupLayout.PREFERRED_SIZE, 32, GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(LayoutStyle.RELATED)
                .add(zoomInButton, GroupLayout.PREFERRED_SIZE, 32, GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(LayoutStyle.RELATED)
                .add(zoomActualButton, GroupLayout.PREFERRED_SIZE, 33, GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(LayoutStyle.RELATED)
                .add(fitZoomButton, GroupLayout.PREFERRED_SIZE, 32, GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(LayoutStyle.RELATED, 110, Short.MAX_VALUE)
                .add(jLabel2)
                .addPreferredGap(LayoutStyle.RELATED)
                .add(zoomLabel)
                .add(27, 27, 27))
        );
        jPanel2Layout.setVerticalGroup(jPanel2Layout.createParallelGroup(GroupLayout.LEADING)
            .add(jPanel2Layout.createSequentialGroup()
                .add(3, 3, 3)
                .add(jPanel2Layout.createParallelGroup(GroupLayout.CENTER)
                    .add(jLabel1, GroupLayout.PREFERRED_SIZE, 25, GroupLayout.PREFERRED_SIZE)
                    .add(hierarchyLayoutButton)
                    .add(fastOrganicLayoutButton)
                    .add(OrganicLayoutButton)
                    .add(circleLayoutButton)
                    .add(jSeparator1, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(zoomOutButton)
                    .add(zoomInButton, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(zoomActualButton, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(fitZoomButton, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(jLabel2)
                    .add(zoomLabel))
                .add(3, 3, 3))
        );

        borderLayoutPanel.add(jPanel2, BorderLayout.PAGE_START);

        splitPane.setLeftComponent(borderLayoutPanel);

        add(splitPane, BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents

    private void fitZoomButtonActionPerformed(ActionEvent evt) {//GEN-FIRST:event_fitZoomButtonActionPerformed
        fitGraph();
    }//GEN-LAST:event_fitZoomButtonActionPerformed

    private void zoomActualButtonActionPerformed(ActionEvent evt) {//GEN-FIRST:event_zoomActualButtonActionPerformed
        graphComponent.zoomActual();
    }//GEN-LAST:event_zoomActualButtonActionPerformed

    private void zoomInButtonActionPerformed(ActionEvent evt) {//GEN-FIRST:event_zoomInButtonActionPerformed
        graphComponent.zoomIn();
    }//GEN-LAST:event_zoomInButtonActionPerformed

    private void zoomOutButtonActionPerformed(ActionEvent evt) {//GEN-FIRST:event_zoomOutButtonActionPerformed
        graphComponent.zoomOut();
    }//GEN-LAST:event_zoomOutButtonActionPerformed

    private void circleLayoutButtonActionPerformed(ActionEvent evt) {//GEN-FIRST:event_circleLayoutButtonActionPerformed
        final mxCircleLayout mxCircleLayout = new mxCircleLayout(graph);
        mxCircleLayout.setResetEdges(true);
        morph(mxCircleLayout);
    }//GEN-LAST:event_circleLayoutButtonActionPerformed

    private void OrganicLayoutButtonActionPerformed(ActionEvent evt) {//GEN-FIRST:event_OrganicLayoutButtonActionPerformed
        applyOrganicLayout(10);
    }//GEN-LAST:event_OrganicLayoutButtonActionPerformed

    private void fastOrganicLayoutButtonActionPerformed(ActionEvent evt) {//GEN-FIRST:event_fastOrganicLayoutButtonActionPerformed
        final mxFastOrganicLayout mxFastOrganicLayout = new mxFastOrganicLayout(graph);

        morph(mxFastOrganicLayout);
    }//GEN-LAST:event_fastOrganicLayoutButtonActionPerformed

    private void hierarchyLayoutButtonActionPerformed(ActionEvent evt) {//GEN-FIRST:event_hierarchyLayoutButtonActionPerformed
        final mxHierarchicalLayout mxHierarchicalLayout = new mxHierarchicalLayout(graph);
        morph(mxHierarchicalLayout);
    }//GEN-LAST:event_hierarchyLayoutButtonActionPerformed

    private void applyOrganicLayout(int iterations) {
        mxOrganicLayout mxOrganicLayout = new mxOrganicLayout(graph) {
            @Override
            public boolean isVertexMovable(Object vertex) {
                return super.isVertexMovable(vertex); //To change body of generated methods, choose Tools | Templates.
            }

        };
        mxOrganicLayout.setResetEdges(true);
        mxOrganicLayout.setMaxIterations(iterations);
        morph(mxOrganicLayout);
    }

    private void fitGraph() {

        mxPoint translate = graph.getView().getTranslate();
        if (translate == null || Double.isNaN(translate.getX()) || Double.isNaN(translate.getY())) {
            translate = new mxPoint();
        }

        mxRectangle boundsForCells = graph.getCellBounds(graph.getDefaultParent(), true, true, true);
        if (boundsForCells == null || Double.isNaN(boundsForCells.getWidth()) || Double.isNaN(boundsForCells.getHeight())) {
            boundsForCells = new mxRectangle(0, 0, 1, 1);
        }
        graph.getView().setTranslate(new mxPoint(translate.getX() - boundsForCells.getX(), translate.getY() - boundsForCells.getY()));

        boundsForCells = graph.getCellBounds(graph.getDefaultParent(), true, true, true);
        if (boundsForCells == null || Double.isNaN(boundsForCells.getWidth()) || Double.isNaN(boundsForCells.getHeight())) {
            boundsForCells = new mxRectangle(0, 0, 1, 1);
        }

        Dimension size = graphComponent.getSize();

        double widthFactor = size.getWidth() / boundsForCells.getWidth();
//        widthFactor = boundsForCells.getWidth() / size.getWidth();

        graphComponent.zoom(widthFactor);

//        bounds = graph.getGraphBounds();
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
    private JButton OrganicLayoutButton;
    private JPanel borderLayoutPanel;
    private JButton circleLayoutButton;
    private JButton fastOrganicLayoutButton;
    private JButton fitZoomButton;
    private JButton hierarchyLayoutButton;
    private JLabel jLabel1;
    private JLabel jLabel2;
    private JPanel jPanel1;
    private JPanel jPanel2;
    private JToolBar.Separator jSeparator1;
    private JTextArea jTextArea1;
    private JToolBar jToolBar2;
    private JPanel progresPanel;
    private JProgressBar progressBar;
    private JSplitPane splitPane;
    private JLabel statusLabel;
    private JButton zoomActualButton;
    private JButton zoomInButton;
    private JLabel zoomLabel;
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
