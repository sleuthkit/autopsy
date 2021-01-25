/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017-2018 Basis Technology Corp.
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
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.ImageIcon;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.windows.Mode;
import org.openide.windows.RetainLocation;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.communications.relationships.RelationshipBrowser;
import org.sleuthkit.autopsy.communications.relationships.SelectionInfo;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.datamodel.CommunicationsFilter;

/**
 * Top component which displays the Communications Visualization Tool.
 */
@TopComponent.Description(preferredID = "CVTTopComponent", persistenceType = TopComponent.PERSISTENCE_NEVER)
@TopComponent.Registration(mode = "cvt", openAtStartup = false)
@RetainLocation("cvt")
@NbBundle.Messages("CVTTopComponent.name= Communications Visualization")
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
public final class CVTTopComponent extends TopComponent {

    private static final long serialVersionUID = 1L;
    private boolean filtersVisible = true;
    private final RelationshipBrowser relationshipBrowser = new RelationshipBrowser();
    private final AccountsBrowser accountsBrowser = new AccountsBrowser(relationshipBrowser);
    private CommunicationsFilter currentFilter;
    private final VisualizationPanel vizPanel = new VisualizationPanel(relationshipBrowser);
    private final FiltersPanel filtersPane = new FiltersPanel();

    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    public CVTTopComponent() {
        initComponents();

        splitPane.setRightComponent(relationshipBrowser);
        splitPane.setDividerLocation(0.25);

        setName(Bundle.CVTTopComponent_name());

        /*
         * Associate a Lookup with the GlobalActionContext (GAC) so that
         * selections in the sub views can be exposed to context-sensitive
         * actions.
         */
        final ModifiableProxyLookup proxyLookup = new ModifiableProxyLookup(accountsBrowser.getLookup());
        associateLookup(proxyLookup);
        // Make sure the Global Actions Context is proxying the selection of the active tab.
        browseVisualizeTabPane.addChangeListener(changeEvent -> {
            Component selectedComponent = browseVisualizeTabPane.getSelectedComponent();
            if (selectedComponent instanceof Lookup.Provider) {
                Lookup lookup = ((Lookup.Provider) selectedComponent).getLookup();
                proxyLookup.setNewLookups(lookup);
            }

            relationshipBrowser.setSelectionInfo(new SelectionInfo(new HashSet<>(), new HashSet<>(), currentFilter));
        });

        filterTabPanel.setLayout(new BorderLayout());
        filterTabPanel.add(filtersPane, BorderLayout.CENTER);
        browseVisualizeTabPane.addTab(NbBundle.getMessage(CVTTopComponent.class, "CVTTopComponent.accountsBrowser.TabConstraints.tabTitle_1"), new ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/communications/images/table.png")), accountsBrowser); // NOI18N
        browseVisualizeTabPane.addTab(NbBundle.getMessage(CVTTopComponent.class, "CVTTopComponent.vizPanel.TabConstraints.tabTitle_1"), new ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/communications/images/emblem-web.png")), vizPanel); // NOI18N
        /*
         * Connect the filtersPane to the accountsBrowser and visualizaionPanel
         * via an Eventbus
         */
        CVTEvents.getCVTEventBus().register(this);
        CVTEvents.getCVTEventBus().register(vizPanel);
        CVTEvents.getCVTEventBus().register(accountsBrowser);
        CVTEvents.getCVTEventBus().register(filtersPane);

        filterTabbedPane.setIconAt(0, new ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/communications/images/arrow-left.png")));
        filterTabbedPane.setTitleAt(0, "");

    }

    @Subscribe
    void pinAccount(CVTEvents.PinAccountsEvent pinEvent) {
        browseVisualizeTabPane.setSelectedIndex(1);
    }

    @Subscribe
    void handle(final CVTEvents.FilterChangeEvent filterChangeEvent) {
        currentFilter = filterChangeEvent.getNewFilter();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        filterTabbedPane = new JTabbedPane();
        filterTabPanel = new JPanel();
        splitPane = new JSplitPane();
        browseVisualizeTabPane = new JTabbedPane();

        setLayout(new BorderLayout());

        filterTabbedPane.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent evt) {
                filterTabbedPaneMouseClicked(evt);
            }
        });
        filterTabbedPane.addTab(NbBundle.getMessage(CVTTopComponent.class, "CVTTopComponent.filterTabPanel.TabConstraints.tabTitle"), filterTabPanel); // NOI18N

        add(filterTabbedPane, BorderLayout.WEST);

        splitPane.setDividerLocation(1);
        splitPane.setResizeWeight(0.25);

        browseVisualizeTabPane.setFont(browseVisualizeTabPane.getFont().deriveFont(browseVisualizeTabPane.getFont().getSize()+7f));
        splitPane.setLeftComponent(browseVisualizeTabPane);
        browseVisualizeTabPane.getAccessibleContext().setAccessibleName(NbBundle.getMessage(CVTTopComponent.class, "CVTTopComponent.browseVisualizeTabPane.AccessibleContext.accessibleName")); // NOI18N

        add(splitPane, BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents

    private void filterTabbedPaneMouseClicked(MouseEvent evt) {//GEN-FIRST:event_filterTabPaneMouseClicked
        int index = filterTabbedPane.indexAtLocation(evt.getX(), evt.getY());
        if (index != -1) {
            if (filtersVisible) {
                filterTabbedPane.setIconAt(0, new ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/communications/images/arrow-right.png")));
                filterTabPanel.removeAll();
                filterTabPanel.revalidate();
                filtersVisible = false;
            } else {
                filterTabbedPane.setIconAt(0, new ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/communications/images/arrow-left.png")));
                filterTabPanel.add(filtersPane, BorderLayout.CENTER);
                filterTabPanel.revalidate();
                filtersVisible = true;
            }
        }
    }//GEN-LAST:event_filterTabPaneMouseClicked


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private JTabbedPane browseVisualizeTabPane;
    private JPanel filterTabPanel;
    private JTabbedPane filterTabbedPane;
    private JSplitPane splitPane;
    // End of variables declaration//GEN-END:variables

    @Override
    public void componentOpened() {
        super.componentOpened();
        WindowManager.getDefault().setTopComponentFloating(this, true);
    }

    @Override
    public void open() {
        super.open();
        /*
         * when the window is (re)opened make sure the filters and accounts are
         * in an up to date and consistent state.
         *
         * Re-applying the filters means we will lose the selection...
         */
        filtersPane.initalizeFilters();
    }

    @Override
    public List<Mode> availableModes(List<Mode> modes) {
        /*
         * This looks like the right thing to do, but online discussions seems
         * to indicate this method is effectively deprecated. A break point
         * placed here was never hit.
         */
        return modes.stream().filter(mode -> mode.getName().equals("cvt"))
                .collect(Collectors.toList());
    }
}
