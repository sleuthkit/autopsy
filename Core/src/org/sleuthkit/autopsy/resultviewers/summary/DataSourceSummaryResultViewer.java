/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.resultviewers.summary;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.util.logging.Level;
import javax.swing.SwingUtilities;
import org.openide.explorer.ExplorerManager;
import org.openide.nodes.Node;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.datasourcesummary.ui.DataSourceSummaryTabbedPane;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataResultViewer;
import org.sleuthkit.autopsy.corecomponents.AbstractDataResultViewer;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;

/**
 * A tabular result viewer that displays a summary of the selected Data Source.
 */
@ServiceProvider(service = DataResultViewer.class)
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
public class DataSourceSummaryResultViewer extends AbstractDataResultViewer {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(DataSourceSummaryResultViewer.class.getName());

    private final String title;

    /**
     * Constructs a tabular result viewer that displays a summary of the
     * selected Data Source.
     */
    public DataSourceSummaryResultViewer() {
        this(null);
    }

    /**
     * Constructs a tabular result viewer that displays a summary of the
     * selected Data Source.
     *
     * @param explorerManager The explorer manager of the ancestor top
     *                        component.
     *
     */
    @Messages({
        "DataSourceSummaryResultViewer_title=Summary"
    })
    public DataSourceSummaryResultViewer(ExplorerManager explorerManager) {
        this(explorerManager, Bundle.DataSourceSummaryResultViewer_title());
    }

    /**
     * Constructs a tabular result viewer that displays a summary of the
     * selected Data Source.
     *
     * @param explorerManager The explorer manager of the ancestor top
     *                        component.
     * @param title           The title.
     *
     */
    public DataSourceSummaryResultViewer(ExplorerManager explorerManager, String title) {
        super(explorerManager);
        this.title = title;
        initComponents();
    }

    @Override
    public DataResultViewer createInstance() {
        return new DataSourceSummaryResultViewer();
    }

    @Override
    public boolean isSupported(Node node) {
        return getDataSource(node) != null;
    }

    /**
     * Returns the datasource attached to the node or null if none can be found.
     *
     * @param node The node to search.
     *
     * @return The datasource or null if not found.
     */
    private DataSource getDataSource(Node node) {
        return node == null ? null : node.getLookup().lookup(DataSource.class);
    }

    @Override
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    public void setNode(Node node) {
        if (!SwingUtilities.isEventDispatchThread()) {
            LOGGER.log(Level.SEVERE, "Attempting to run setNode() from non-EDT thread.");
            return;
        }

        DataSource dataSource = getDataSource(node);

        this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            if (summaryPanel != null) {
                summaryPanel.setDataSource(dataSource);
            }
        } finally {
            this.setCursor(null);
        }
    }

    @Override
    public String getTitle() {
        return title;
    }

    private void initComponents() {
        summaryPanel = new DataSourceSummaryTabbedPane();
        setLayout(new BorderLayout());
        add(summaryPanel, BorderLayout.CENTER);
    }

    @Override
    public void clearComponent() {
        summaryPanel.close();
        summaryPanel = null;
    }

    private DataSourceSummaryTabbedPane summaryPanel;

}
