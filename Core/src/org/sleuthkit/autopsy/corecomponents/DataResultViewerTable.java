/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.corecomponents;

import com.google.common.eventbus.Subscribe;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.dnd.DnDConstants;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.FeatureDescriptor;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyVetoException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.Preferences;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import static javax.swing.SwingConstants.CENTER;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.event.TreeExpansionListener;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import org.netbeans.swing.etable.ETableColumn;
import org.netbeans.swing.etable.ETableColumnModel;
import org.netbeans.swing.outline.DefaultOutlineCellRenderer;
import org.netbeans.swing.outline.DefaultOutlineModel;
import org.netbeans.swing.outline.Outline;
import org.openide.explorer.ExplorerManager;
import org.openide.explorer.view.OutlineView;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Node.Property;
import org.openide.nodes.NodeEvent;
import org.openide.nodes.NodeListener;
import org.openide.nodes.NodeMemberEvent;
import org.openide.nodes.NodeReorderEvent;
import org.openide.util.ImageUtilities;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataResultViewer;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.datamodel.NodeProperty;
import org.sleuthkit.autopsy.datamodel.NodeSelectionInfo;
import org.sleuthkit.autopsy.datamodel.BaseChildFactory;
import org.sleuthkit.autopsy.datamodel.BaseChildFactory.PageChangeEvent;
import org.sleuthkit.autopsy.datamodel.BaseChildFactory.PageCountChangeEvent;
import org.sleuthkit.autopsy.datamodel.BaseChildFactory.PageSizeChangeEvent;
import org.sleuthkit.datamodel.Score.Significance;

/**
 * A tabular result viewer that displays the children of the given root node
 * using an OutlineView.
 *
 * Instances of this class should use the explorer manager of an ancestor top
 * component to connect the lookups of the nodes displayed in the OutlineView to
 * the actions global context. The explorer manager can be supplied during
 * construction, but the typical use case is for the result viewer to find the
 * ancestor top component's explorer manager at runtime.
 */
@ServiceProvider(service = DataResultViewer.class)
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
public class DataResultViewerTable extends AbstractDataResultViewer {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(DataResultViewerTable.class.getName());

    // How many rows to sample in order to determine column width.
    private static final int SAMPLE_ROW_NUM = 100;

    // The padding to be added in addition to content size when considering column width.
    private static final int COLUMN_PADDING = 15;

    // The minimum column width.
    private static final int MIN_COLUMN_WIDTH = 30;

    // The maximum column width.
    private static final int MAX_COLUMN_WIDTH = 300;

    // The minimum row height to use when calculating whether scroll bar will be used.
    private static final int MIN_ROW_HEIGHT = 10;

    // The width of the scroll bar.
    private static final int SCROLL_BAR_WIDTH = ((Integer) UIManager.get("ScrollBar.width")).intValue();

    // Any additional padding to be used for the first column.
    private static final int FIRST_COL_ADDITIONAL_WIDTH = 0;

    private static final String NOTEPAD_ICON_PATH = "org/sleuthkit/autopsy/images/notepad16.png";
    private static final String RED_CIRCLE_ICON_PATH = "org/sleuthkit/autopsy/images/red-circle-exclamation.png";
    private static final String YELLOW_CIRCLE_ICON_PATH = "org/sleuthkit/autopsy/images/yellow-circle-yield.png";
    private static final ImageIcon COMMENT_ICON = new ImageIcon(ImageUtilities.loadImage(NOTEPAD_ICON_PATH, false));
    private static final ImageIcon INTERESTING_SCORE_ICON = new ImageIcon(ImageUtilities.loadImage(YELLOW_CIRCLE_ICON_PATH, false));
    private static final ImageIcon NOTABLE_ICON_SCORE = new ImageIcon(ImageUtilities.loadImage(RED_CIRCLE_ICON_PATH, false));
    @NbBundle.Messages("DataResultViewerTable.firstColLbl=Name")
    static private final String FIRST_COLUMN_LABEL = Bundle.DataResultViewerTable_firstColLbl();
    private final String title;
    private final Map<String, ETableColumn> columnMap;
    private final Map<Integer, Property<?>> propertiesMap;
    private final Outline outline;
    private final TableListener outlineViewListener;
    private final IconRendererTableListener iconRendererListener;
    private Node rootNode;

    /**
     * Multiple nodes may have been visited in the context of this
     * DataResultViewerTable. We keep track of the page state for these nodes in
     * the following map.
     */
    private final Map<String, PagingSupport> nodeNameToPagingSupportMap = new ConcurrentHashMap<>();

    /**
     * The paging support instance for the current node.
     */
    private PagingSupport pagingSupport = null;

    /**
     * Constructs a tabular result viewer that displays the children of the
     * given root node using an OutlineView. The viewer should have an ancestor
     * top component to connect the lookups of the nodes displayed in the
     * OutlineView to the actions global context. The explorer manager will be
     * discovered at runtime.
     */
    public DataResultViewerTable() {
        this(null, Bundle.DataResultViewerTable_title());
    }

    /**
     * Constructs a tabular result viewer that displays the children of a given
     * root node using an OutlineView. The viewer should have an ancestor top
     * component to connect the lookups of the nodes displayed in the
     * OutlineView to the actions global context.
     *
     * @param explorerManager The explorer manager of the ancestor top
     *                        component.
     */
    public DataResultViewerTable(ExplorerManager explorerManager) {
        this(explorerManager, Bundle.DataResultViewerTable_title());
    }

    /**
     * Constructs a tabular result viewer that displays the children of a given
     * root node using an OutlineView with a given title. The viewer should have
     * an ancestor top component to connect the lookups of the nodes displayed
     * in the OutlineView to the actions global context.
     *
     * @param explorerManager The explorer manager of the ancestor top
     *                        component.
     * @param title           The title.
     */
    public DataResultViewerTable(ExplorerManager explorerManager, String title) {
        super(explorerManager);
        this.title = title;
        this.columnMap = new HashMap<>();
        this.propertiesMap = new TreeMap<>();

        /*
         * Execute the code generated by the GUI builder.
         */
        initComponents();

        initializePagingSupport();

        /*
         * Disable the CSV export button for the common properties results
         */
        if (this instanceof org.sleuthkit.autopsy.commonpropertiessearch.CommonAttributesSearchResultsViewerTable) {
            exportCSVButton.setEnabled(false);
        }

        /*
         * Configure the child OutlineView (explorer view) component.
         */
        outlineView.setAllowedDragActions(DnDConstants.ACTION_NONE);

        outline = outlineView.getOutline();
        outline.setRowSelectionAllowed(true);
        outline.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        outline.setRootVisible(false);
        outline.setDragEnabled(false);

        /*
         * Add a table listener to the child OutlineView (explorer view) to
         * persist the order of the table columns when a column is moved.
         */
        outlineViewListener = new TableListener();
        outline.getColumnModel().addColumnModelListener(outlineViewListener);

        iconRendererListener = new IconRendererTableListener();
        outline.getColumnModel().addColumnModelListener(iconRendererListener);

        /*
         * Add a mouse listener to the child OutlineView (explorer view) to make
         * sure the first column of the table is kept in place.
         */
        outline.getTableHeader().addMouseListener(outlineViewListener);
    }

    private void initializePagingSupport() {
        if (pagingSupport == null) {
            pagingSupport = new PagingSupport("");
        }

        // Start out with paging controls invisible
        pagingSupport.togglePageControls(false);

        /**
         * Set up a change listener so we know when the user changes the page
         * size
         */
        UserPreferences.addChangeListener((PreferenceChangeEvent evt) -> {
            if (evt.getKey().equals(UserPreferences.RESULTS_TABLE_PAGE_SIZE)) {
                setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                /**
                 * If multiple nodes have been viewed we have to notify all of
                 * them about the change in page size.
                 */
                nodeNameToPagingSupportMap.values().forEach((ps) -> {
                    ps.postPageSizeChangeEvent();
                });
                
                setCursor(null);
            }
        });
    }

    /**
     * Creates a new instance of a tabular result viewer that displays the
     * children of a given root node using an OutlineView. This method exists to
     * make it possible to use the default service provider instance of this
     * class in the "main" results view of the application, while using distinct
     * instances in other places in the UI.
     *
     * @return A new instance of a tabular result viewer,
     */
    @Override
    public DataResultViewer createInstance() {
        return new DataResultViewerTable();
    }

    /**
     * Gets the title of this tabular result viewer.
     *
     * @return title of tab.
     */
    @Override
    @NbBundle.Messages("DataResultViewerTable.title=Table")
    public String getTitle() {
        return title;
    }

    /**
     * Indicates whether a given node is supported as a root node for this
     * tabular viewer.
     *
     * @param candidateRootNode The candidate root node.
     *
     * @return
     */
    @Override
    public boolean isSupported(Node candidateRootNode) {
        return true;
    }

    /**
     * Sets the current root node of this tabular result viewer.
     *
     * @param rootNode The node to set as the current root node, possibly null.
     */
    @Override
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    public void setNode(Node rootNode) {
        if (!SwingUtilities.isEventDispatchThread()) {
            LOGGER.log(Level.SEVERE, "Attempting to run setNode() from non-EDT thread");
            return;
        }

        /*
         * The quick filter must be reset because when determining column width,
         * ETable.getRowCount is called, and the documentation states that quick
         * filters must be unset for the method to work "If the quick-filter is
         * applied the number of rows do not match the number of rows in the
         * model."
         */
        outline.unsetQuickFilter();

        this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            if (rootNode != null) {
                this.rootNode = rootNode;

                /**
                 * Check to see if we have previously created a paging support
                 * class for this node.
                 */
                if (!Node.EMPTY.equals(rootNode)) {
                    String nodeName = rootNode.getName();
                    pagingSupport = nodeNameToPagingSupportMap.get(nodeName);
                    if (pagingSupport == null) {
                        pagingSupport = new PagingSupport(nodeName);
                        nodeNameToPagingSupportMap.put(nodeName, pagingSupport);
                    }
                    pagingSupport.updateControls();

                    rootNode.addNodeListener(new NodeListener() {
                        @Override
                        public void childrenAdded(NodeMemberEvent nme) {
                            /**
                             * This is the only somewhat reliable way I could
                             * find to reset the cursor after a page change.
                             * When you change page the old children nodes will
                             * be removed and new ones added.
                             */
                            SwingUtilities.invokeLater(() -> {
                                setCursor(null);
                            });
                        }

                        @Override
                        public void childrenRemoved(NodeMemberEvent nme) {
                            SwingUtilities.invokeLater(() -> {
                                setCursor(null);
                            });
                        }

                        @Override
                        public void childrenReordered(NodeReorderEvent nre) {
                            // No-op
                        }

                        @Override
                        public void nodeDestroyed(NodeEvent ne) {
                            // No-op
                        }

                        @Override
                        public void propertyChange(PropertyChangeEvent evt) {
                            // No-op
                        }
                    });
                }
            }

            /*
             * If the given node is not null and has children, set it as the
             * root context of the child OutlineView, otherwise make an
             * "empty"node the root context.
             */
            if (rootNode != null && rootNode.getChildren().getNodesCount() > 0) {
                this.getExplorerManager().setRootContext(this.rootNode);
                outline.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
                setupTable();
            } else {
                Node emptyNode = new AbstractNode(Children.LEAF);
                this.getExplorerManager().setRootContext(emptyNode);
                outline.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
                outlineViewListener.listenToVisibilityChanges(false);
                outlineView.setPropertyColumns();
            }
        } finally {
            this.setCursor(null);
        }
    }

    /**
     * Adds a tree expansion listener to the OutlineView of this tabular results
     * viewer.
     *
     * @param listener The listener
     */
    protected void addTreeExpansionListener(TreeExpansionListener listener) {
        outlineView.addTreeExpansionListener(listener);
    }

    /**
     * Sets up the Outline view of this tabular result viewer by creating column
     * headers based on the children of the current root node. The persisted
     * column order, sorting and visibility is used.
     */
    private void setupTable() {
        /*
         * Since we are modifying the columns, we don't want to listen to
         * added/removed events as un-hide/hide, until the table setup is done.
         */
        outlineViewListener.listenToVisibilityChanges(false);
        /*
         * OutlineView makes the first column be the result of
         * node.getDisplayName with the icon. This duplicates our first column,
         * which is the file name, etc. So, pop that property off the list, but
         * use its display name as the header for the column so that the header
         * can change depending on the type of data being displayed.
         *
         * NOTE: This assumes that the first property is always the one that
         * duplicates getDisplayName(). The current implementation does not
         * allow the first property column to be moved.
         */
        List<Node.Property<?>> props = loadColumnOrder();
        boolean propsExist = props.isEmpty() == false;
        Node.Property<?> firstProp = null;
        if (propsExist) {
            firstProp = props.remove(0);
        }

        assignColumns(props); // assign columns to match the properties
        if (firstProp != null) {
            ((DefaultOutlineModel) outline.getOutlineModel()).setNodesColumnLabel(firstProp.getDisplayName());
        }

        /*
         * Load column sorting information from preferences file and apply it to
         * columns.
         */
        loadColumnSorting();

        /*
         * Save references to columns before we deal with their visibility. This
         * has to happen after the sorting is applied, because that actually
         * causes the columns to be recreated. It has to happen before
         * loadColumnVisibility so we have referenecs to the columns to pass to
         * setColumnHidden.
         */
        populateColumnMap();

        /*
         * Load column visibility information from preferences file and apply it
         * to columns.
         */
        loadColumnVisibility();

        /*
         * Set the column widths.
         *
         * IMPORTANT: This needs to come after the preceding calls to determine
         * the columns that will be displayed and their layout, which includes a
         * call to ResultViewerPersistence.getAllChildProperties(). That method
         * calls Children.getNodes(true) on the root node to ensure ALL of the
         * nodes have been created in the NetBeans asynch child creation thread,
         * and then uses the first one hundred nodes to determine which columns
         * to display, including their header text.
         */
        setColumnWidths();

        /*
         * If one of the child nodes of the root node is to be selected, select
         * it.
         */
        if (rootNode instanceof TableFilterNode) {
            NodeSelectionInfo selectedChildInfo = ((TableFilterNode) rootNode).getChildNodeSelectionInfo();
            if (null != selectedChildInfo) {
                Node[] childNodes = rootNode.getChildren().getNodes(true);
                for (int i = 0; i < childNodes.length; ++i) {
                    Node childNode = childNodes[i];
                    if (selectedChildInfo.matches(childNode)) {
                        SwingUtilities.invokeLater(() -> {
                            try {
                                this.getExplorerManager().setExploredContextAndSelection(this.rootNode, new Node[]{childNode});
                            } catch (PropertyVetoException ex) {
                                LOGGER.log(Level.SEVERE, "Failed to select node specified by selected child info", ex);
                            }
                        });

                        break;
                    }
                }
                ((TableFilterNode) rootNode).setChildNodeSelectionInfo(null);
            }
        }

        /*
         * The table setup is done, so any added/removed events can now be
         * treated as un-hide/hide.
         */
        outlineViewListener.listenToVisibilityChanges(true);

    }

    /*
     * Populates the column map for the child OutlineView of this tabular result
     * viewer with references to the column objects for use when loading/storing
     * the visibility info.
     */
    private void populateColumnMap() {
        columnMap.clear();
        TableColumnModel columnModel = outline.getColumnModel();
        int columnCount = columnModel.getColumnCount();
        //for each property get a reference to the column object from the column model.
        for (Map.Entry<Integer, Property<?>> entry : propertiesMap.entrySet()) {
            final String propName = entry.getValue().getName();
            if (entry.getKey() < columnCount) {
                final ETableColumn column = (ETableColumn) columnModel.getColumn(entry.getKey());
                columnMap.put(propName, column);

            }
        }
    }

    /*
     * Sets the column widths for the child OutlineView of this tabular results
     * viewer providing any additional width to last column.
     */
    protected void setColumnWidths() {
        // based on https://stackoverflow.com/questions/17627431/auto-resizing-the-jtable-column-widths
        final TableColumnModel columnModel = outline.getColumnModel();

        // the remaining table width that can be used in last row
        double availableTableWidth = outlineView.getSize().getWidth();

        for (int columnIdx = 0; columnIdx < outline.getColumnCount(); columnIdx++) {
            int columnPadding = (columnIdx == 0) ? FIRST_COL_ADDITIONAL_WIDTH + COLUMN_PADDING : COLUMN_PADDING;
            TableColumn tableColumn = columnModel.getColumn(columnIdx);

            // The width of this column
            int width = MIN_COLUMN_WIDTH;

            // get header cell width
            // taken in part from https://stackoverflow.com/a/18381924
            TableCellRenderer headerRenderer = tableColumn.getHeaderRenderer();
            if (headerRenderer == null) {
                headerRenderer = outline.getTableHeader().getDefaultRenderer();
            }
            Object headerValue = tableColumn.getHeaderValue();
            Component headerComp = headerRenderer.getTableCellRendererComponent(outline, headerValue, false, false, 0, columnIdx);
            width = Math.max(headerComp.getPreferredSize().width + columnPadding, width);

            // get the max of row widths from the first SAMPLE_ROW_NUM rows
            Component comp = null;
            int rowCount = outline.getRowCount();
            for (int row = 0; row < Math.min(rowCount, SAMPLE_ROW_NUM); row++) {
                TableCellRenderer renderer = outline.getCellRenderer(row, columnIdx);
                comp = outline.prepareRenderer(renderer, row, columnIdx);
                width = Math.max(comp.getPreferredSize().width + columnPadding, width);
            }

            // no higher than maximum column width
            if (width > MAX_COLUMN_WIDTH) {
                width = MAX_COLUMN_WIDTH;
            }

            // if last column, calculate remaining width factoring in the possibility of a scroll bar.
            if (columnIdx == outline.getColumnCount() - 1) {
                int rowHeight = comp == null ? MIN_ROW_HEIGHT : comp.getPreferredSize().height;
                if (headerComp.getPreferredSize().height + rowCount * rowHeight > outlineView.getSize().getHeight()) {
                    availableTableWidth -= SCROLL_BAR_WIDTH;
                }

                columnModel.getColumn(columnIdx).setPreferredWidth(Math.max(width, (int) availableTableWidth));
            } else {
                // otherwise set preferred width to width and decrement availableTableWidth accordingly
                columnModel.getColumn(columnIdx).setPreferredWidth(width);
                availableTableWidth -= width;
            }
        }
    }

    protected TableColumnModel getColumnModel() {
        return outline.getColumnModel();
    }

    /*
     * Sets up the columns for the child OutlineView of this tabular results
     * viewer with respect to column names and visisbility.
     */
    synchronized private void assignColumns(List<Property<?>> props) {
        String[] propStrings = new String[props.size() * 2];
        for (int i = 0; i < props.size(); i++) {
            final Property<?> prop = props.get(i);
            prop.setValue("ComparableColumnTTV", Boolean.TRUE); //NON-NLS
            //First property column is sorted initially
            if (i == 0) {
                prop.setValue("TreeColumnTTV", Boolean.TRUE); // Identifies special property representing first (tree) column. NON-NLS
                prop.setValue("SortingColumnTTV", Boolean.TRUE); // TreeTableView should be initially sorted by this property column. NON-NLS
            }
            propStrings[2 * i] = prop.getName();
            propStrings[2 * i + 1] = prop.getDisplayName();
        }
        outlineView.setPropertyColumns(propStrings);
    }

    /**
     * Persists the current column visibility information for the child
     * OutlineView of this tabular result viewer using a preferences file.
     */
    private synchronized void storeColumnVisibility() {
        if (rootNode == null || propertiesMap.isEmpty()) {
            return;
        }
        if (rootNode instanceof TableFilterNode) {
            TableFilterNode tfn = (TableFilterNode) rootNode;
            final Preferences preferences = NbPreferences.forModule(DataResultViewerTable.class);
            final ETableColumnModel columnModel = (ETableColumnModel) outline.getColumnModel();
            for (Map.Entry<String, ETableColumn> entry : columnMap.entrySet()) {
                String columnName = entry.getKey();
                final String columnHiddenKey = ResultViewerPersistence.getColumnHiddenKey(tfn, columnName);
                final TableColumn column = entry.getValue();
                boolean columnHidden = columnModel.isColumnHidden(column);
                if (columnHidden) {
                    preferences.putBoolean(columnHiddenKey, true);
                } else {
                    preferences.remove(columnHiddenKey);
                }
            }
        }
    }

    /**
     * Persists the current column ordering for the child OutlineView of this
     * tabular result viewer using a preferences file.
     */
    private synchronized void storeColumnOrder() {
        if (rootNode == null || propertiesMap.isEmpty()) {
            return;
        }
        if (rootNode instanceof TableFilterNode) {
            TableFilterNode tfn = (TableFilterNode) rootNode;
            final Preferences preferences = NbPreferences.forModule(DataResultViewerTable.class);
            // Store the current order of the columns into settings
            for (Map.Entry<Integer, Property<?>> entry : propertiesMap.entrySet()) {
                preferences.putInt(ResultViewerPersistence.getColumnPositionKey(tfn, entry.getValue().getName()), entry.getKey());
            }
        }
    }

    /**
     * Persists the current column sorting information using a preferences file.
     */
    private synchronized void storeColumnSorting() {
        if (rootNode == null || propertiesMap.isEmpty()) {
            return;
        }
        if (rootNode instanceof TableFilterNode) {
            final TableFilterNode tfn = ((TableFilterNode) rootNode);
            final Preferences preferences = NbPreferences.forModule(DataResultViewerTable.class);
            ETableColumnModel columnModel = (ETableColumnModel) outline.getColumnModel();
            for (Map.Entry<String, ETableColumn> entry : columnMap.entrySet()) {
                ETableColumn etc = entry.getValue();
                String columnName = entry.getKey();
                //store sort rank and order
                final String columnSortOrderKey = ResultViewerPersistence.getColumnSortOrderKey(tfn, columnName);
                final String columnSortRankKey = ResultViewerPersistence.getColumnSortRankKey(tfn, columnName);
                if (etc.isSorted() && (columnModel.isColumnHidden(etc) == false)) {
                    preferences.putBoolean(columnSortOrderKey, etc.isAscending());
                    preferences.putInt(columnSortRankKey, etc.getSortRank());
                } else {
                    columnModel.setColumnSorted(etc, true, 0);
                    preferences.remove(columnSortOrderKey);
                    preferences.remove(columnSortRankKey);
                }
            }
        }
    }

    /**
     * Reads and applies the column sorting information persisted to the
     * preferences file. Must be called after loadColumnOrder, since it depends
     * on the properties map being initialized, and after assignColumns, since
     * it cannot set the sort on columns that have not been added to the table.
     */
    private synchronized void loadColumnSorting() {
        if (rootNode == null || propertiesMap.isEmpty()) {
            return;
        }
        if (rootNode instanceof TableFilterNode) {
            final TableFilterNode tfn = (TableFilterNode) rootNode;
            final Preferences preferences = NbPreferences.forModule(DataResultViewerTable.class);
            //organize property sorting information, sorted by rank
            TreeSet<ColumnSortInfo> sortInfos = new TreeSet<>(Comparator.comparing(ColumnSortInfo::getRank));
            propertiesMap.entrySet().stream().forEach(entry -> {
                final String propName = entry.getValue().getName();
                //if the sort rank is undefined, it will be defaulted to 0 => unsorted.
                Integer sortRank = preferences.getInt(ResultViewerPersistence.getColumnSortRankKey(tfn, propName), 0);
                //default to true => ascending
                Boolean sortOrder = preferences.getBoolean(ResultViewerPersistence.getColumnSortOrderKey(tfn, propName), true);
                sortInfos.add(new ColumnSortInfo(entry.getKey(), sortRank, sortOrder));
            });
            //apply sort information in rank order.
            sortInfos.forEach(sortInfo -> outline.setColumnSorted(sortInfo.modelIndex, sortInfo.order, sortInfo.rank));
        }
    }

    /**
     * Reads and applies the column visibility information persisted to the
     * preferences file.
     */
    private synchronized void loadColumnVisibility() {
        if (rootNode == null || propertiesMap.isEmpty()) {
            return;
        }
        if (rootNode instanceof TableFilterNode) {
            final Preferences preferences = NbPreferences.forModule(DataResultViewerTable.class);
            final TableFilterNode tfn = ((TableFilterNode) rootNode);
            ETableColumnModel columnModel = (ETableColumnModel) outline.getColumnModel();
            for (Map.Entry<Integer, Property<?>> entry : propertiesMap.entrySet()) {
                final String propName = entry.getValue().getName();
                boolean hidden = preferences.getBoolean(ResultViewerPersistence.getColumnHiddenKey(tfn, propName), false);
                final TableColumn column = columnMap.get(propName);
                columnModel.setColumnHidden(column, hidden);
            }
        }
    }

    /**
     * Gets a list of child properties (columns) in the order persisted in the
     * preference file. Also initialized the properties map with the column
     * order.
     *
     * @return a List<Node.Property<?>> of the properties in the persisted
     *         order.
     */
    private synchronized List<Node.Property<?>> loadColumnOrder() {

        List<Property<?>> props = ResultViewerPersistence.getAllChildProperties(rootNode, 100);

        // If node is not table filter node, use default order for columns
        if (!(rootNode instanceof TableFilterNode)) {
            return props;
        }

        final TableFilterNode tfn = ((TableFilterNode) rootNode);
        propertiesMap.clear();

        /*
         * We load column index values into the properties map. If a property's
         * index is outside the range of the number of properties or the index
         * has already appeared as the position of another property, we put that
         * property at the end.
         */
        int offset = props.size();

        final Preferences preferences = NbPreferences.forModule(DataResultViewerTable.class);

        for (Property<?> prop : props) {
            Integer value = preferences.getInt(ResultViewerPersistence.getColumnPositionKey(tfn, prop.getName()), -1);
            if (value >= 0 && value < offset && !propertiesMap.containsKey(value)) {
                propertiesMap.put(value, prop);
            } else {
                propertiesMap.put(offset, prop);
                offset++;
            }
        }

        /*
         * NOTE: it is possible to have "discontinuities" in the keys (i.e.
         * column numbers) of the map. This happens when some of the columns had
         * a previous setting, and other columns did not. We need to make the
         * keys 0-indexed and continuous.
         */
        compactPropertiesMap();

        return new ArrayList<>(propertiesMap.values());
    }

    /**
     * Makes properties map 0-indexed and re-arranges elements to make sure the
     * indexes are continuous.
     */
    private void compactPropertiesMap() {

        // check if there are discontinuities in the map keys. 
        int size = propertiesMap.size();
        Queue<Integer> availablePositions = new LinkedList<>();
        for (int i = 0; i < size; i++) {
            if (!propertiesMap.containsKey(i)) {
                availablePositions.add(i);
            }
        }

        // if there are no discontinuities, we are done
        if (availablePositions.isEmpty()) {
            return;
        }

        // otherwise, move map elements into the available positions. 
        // we don't want to just move down all elements, as we want to preserve the order
        // of the ones that had previous setting (i.e. ones that have key < size)
        ArrayList<Integer> keys = new ArrayList<>(propertiesMap.keySet());
        for (int key : keys) {
            if (key >= size) {
                propertiesMap.put(availablePositions.remove(), propertiesMap.remove(key));
            }
        }
    }

    /**
     * Frees the resources that have been allocated by this tabular results
     * viewer, in preparation for permanently disposing of it.
     */
    @Override
    public void clearComponent() {
        this.outlineView.removeAll();
        this.outlineView = null;
        super.clearComponent();
    }

    /**
     * Encapsulates sorting information for a column to make loadSort simpler.
     */
    static private final class ColumnSortInfo {

        private final int modelIndex;
        private final int rank;
        private final boolean order;

        private ColumnSortInfo(int modelIndex, int rank, boolean order) {
            this.modelIndex = modelIndex;
            this.rank = rank;
            this.order = order;
        }

        private int getRank() {
            return rank;
        }
    }

    /**
     * Maintains the current page state for a node and provides support for
     * paging through results. Uses an EventBus to communicate with child
     * factory implementations.
     */
    private class PagingSupport {

        private int currentPage;
        private int totalPages;
        private final String nodeName;

        PagingSupport(String nodeName) {
            currentPage = 1;
            totalPages = 0;
            this.nodeName = nodeName;
            initialize();
        }

        private void initialize() {
            if (!nodeName.isEmpty()) {
                BaseChildFactory.register(nodeName, this);
            }
            updateControls();
        }

        void nextPage() {
            currentPage++;
            postPageChangeEvent();
        }

        void previousPage() {
            currentPage--;
            postPageChangeEvent();
        }

        @NbBundle.Messages({"# {0} - totalPages",
            "DataResultViewerTable.goToPageTextField.msgDlg=Please enter a valid page number between 1 and {0}",
            "DataResultViewerTable.goToPageTextField.err=Invalid page number"})
        void gotoPage() {
            int originalPage = currentPage;

            try {
                currentPage = Integer.decode(gotoPageTextField.getText());
            } catch (NumberFormatException e) {
                //ignore input
                return;
            }

            if (currentPage > totalPages || currentPage < 1) {
                currentPage = originalPage;
                JOptionPane.showMessageDialog(DataResultViewerTable.this,
                        Bundle.DataResultViewerTable_goToPageTextField_msgDlg(totalPages),
                        Bundle.DataResultViewerTable_goToPageTextField_err(),
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            postPageChangeEvent();
        }

        /**
         * Notify subscribers (i.e. child factories) that a page change has
         * occurred.
         */
        void postPageChangeEvent() {
            try {
                BaseChildFactory.post(nodeName, new PageChangeEvent(currentPage));
            } catch (BaseChildFactory.NoSuchEventBusException ex) {
                LOGGER.log(Level.WARNING, "Failed to post page change event.", ex); //NON-NLS
            }
            DataResultViewerTable.this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            updateControls();
        }

        /**
         * Notify subscribers (i.e. child factories) that a page size change has
         * occurred.
         */
        void postPageSizeChangeEvent() {
            // Reset page variables when page size changes
            currentPage = 1;

            if (this == pagingSupport) {
                updateControls();
            }
            try {
                BaseChildFactory.post(nodeName, new PageSizeChangeEvent(UserPreferences.getResultsTablePageSize()));
            } catch (BaseChildFactory.NoSuchEventBusException ex) {
                LOGGER.log(Level.WARNING, "Failed to post page size change event.", ex); //NON-NLS
            }
        }

        /**
         * Subscribe to notification that the number of pages has changed.
         *
         * @param event
         */
        @Subscribe
        public void subscribeToPageCountChange(PageCountChangeEvent event) {
            if (event != null) {
                totalPages = event.getPageCount();
                if (totalPages > 1) {
                    // Make paging controls visible if there is more than one page.
                    togglePageControls(true);
                }

                // Only update UI controls if this event is for the node currently being viewed.
                if (nodeName.equals(rootNode.getName())) {
                    updateControls();
                }
            }
        }

        /**
         * Make paging controls visible or invisible based on flag.
         *
         * @param onOff
         */
        private void togglePageControls(boolean onOff) {
            pageLabel.setVisible(onOff);
            pagesLabel.setVisible(onOff);
            pagePrevButton.setVisible(onOff);
            pageNextButton.setVisible(onOff);
            pageNumLabel.setVisible(onOff);
            gotoPageLabel.setVisible(onOff);
            gotoPageTextField.setVisible(onOff);
            gotoPageTextField.setVisible(onOff);
            validate();
            repaint();
        }

        @NbBundle.Messages({"# {0} - currentPage", "# {1} - totalPages",
            "DataResultViewerTable.pageNumbers.curOfTotal={0} of {1}"})
        private void updateControls() {
            if (totalPages == 0) {
                pagePrevButton.setEnabled(false);
                pageNextButton.setEnabled(false);
                pageNumLabel.setText("");
                gotoPageTextField.setText("");
                gotoPageTextField.setEnabled(false);
            } else {
                pageNumLabel.setText(Bundle.DataResultViewerTable_pageNumbers_curOfTotal(Integer.toString(currentPage), Integer.toString(totalPages)));

                pageNextButton.setEnabled(currentPage != totalPages);
                pagePrevButton.setEnabled(currentPage != 1);
                gotoPageTextField.setEnabled(totalPages > 1);
                gotoPageTextField.setText("");
            }
        }
    }

    /**
     * Listener which sets the custom icon renderer on columns which contain
     * icons instead of text when a column is added.
     */
    private class IconRendererTableListener implements TableColumnModelListener {

        @NbBundle.Messages({"DataResultViewerTable.commentRender.name=C",
            "DataResultViewerTable.commentRender.toolTip=C(omments) indicates whether the item has a comment",
            "DataResultViewerTable.scoreRender.name=S",
            "DataResultViewerTable.scoreRender.toolTip=S(core) indicates whether the item is interesting or notable",
            "DataResultViewerTable.countRender.name=O",
            "DataResultViewerTable.countRender.toolTip=O(ccurrences) indicates the number of data sources containing the item in the Central Repository"})
        @Override
        public void columnAdded(TableColumnModelEvent e) {
            if (e.getSource() instanceof ETableColumnModel) {
                TableColumn column = ((TableColumnModel) e.getSource()).getColumn(e.getToIndex());
                if (column.getHeaderValue().toString().equals(Bundle.DataResultViewerTable_commentRender_name())) {
                    //if the current column is a comment column set the cell renderer to be the HasCommentCellRenderer
                    outlineView.setPropertyColumnDescription(column.getHeaderValue().toString(), Bundle.DataResultViewerTable_commentRender_toolTip());
                    column.setCellRenderer(new HasCommentCellRenderer());
                } else if (column.getHeaderValue().toString().equals(Bundle.DataResultViewerTable_scoreRender_name())) {
                    //if the current column is a score column set the cell renderer to be the ScoreCellRenderer
                    outlineView.setPropertyColumnDescription(column.getHeaderValue().toString(), Bundle.DataResultViewerTable_scoreRender_toolTip());
                    column.setCellRenderer(new ScoreCellRenderer());
                } else if (column.getHeaderValue().toString().equals(Bundle.DataResultViewerTable_countRender_name())) {
                    outlineView.setPropertyColumnDescription(column.getHeaderValue().toString(), Bundle.DataResultViewerTable_countRender_toolTip());
                    column.setCellRenderer(new CountCellRenderer());
                }
            }
        }

        @Override
        public void columnRemoved(TableColumnModelEvent e
        ) {
            //Don't do anything when column removed
        }

        @Override
        public void columnMoved(TableColumnModelEvent e
        ) {
            //Don't do anything when column moved
        }

        @Override
        public void columnMarginChanged(ChangeEvent e
        ) {
            //Don't do anything when column margin changed
        }

        @Override
        public void columnSelectionChanged(ListSelectionEvent e
        ) {
            //Don't do anything when column selection changed
        }

    }

    /**
     * Listens to mouse events and table column events and persists column order
     * sorting, and visibility changes.
     */
    private class TableListener extends MouseAdapter implements TableColumnModelListener {

        // When a column in the table is moved, these two variables keep track of where
        // the column started and where it ended up.
        private int startColumnIndex = -1;
        private int endColumnIndex = -1;
        private boolean listenToVisibilitEvents;

        @Override
        public void columnMoved(TableColumnModelEvent e) {
            int fromIndex = e.getFromIndex();
            int toIndex = e.getToIndex();
            if (fromIndex == toIndex) {
                return;
            }

            /*
             * Because a column may be dragged to several different positions
             * before the mouse is released (thus causing multiple
             * TableColumnModelEvents to be fired), we want to keep track of the
             * starting column index in this potential series of movements.
             * Therefore we only keep track of the original fromIndex in
             * startColumnIndex, but we always update endColumnIndex to know the
             * final position of the moved column. See the MouseListener
             * mouseReleased method.
             */
            if (startColumnIndex == -1) {
                startColumnIndex = fromIndex;
            }
            endColumnIndex = toIndex;

            // This list contains the keys of propertiesMap in order
            ArrayList<Integer> indicesList = new ArrayList<>(propertiesMap.keySet());
            int leftIndex = Math.min(fromIndex, toIndex);
            int rightIndex = Math.max(fromIndex, toIndex);
            // Now we can copy the range of keys that have been affected by
            // the column movement
            List<Integer> range = indicesList.subList(leftIndex, rightIndex + 1);
            int rangeSize = range.size();

            if (fromIndex < toIndex) {
                // column moved right, shift all properties left, put in moved
                // property at the rightmost index
                Property<?> movedProp = propertiesMap.get(range.get(0));
                for (int i = 0; i < rangeSize - 1; i++) {
                    propertiesMap.put(range.get(i), propertiesMap.get(range.get(i + 1)));
                }
                propertiesMap.put(range.get(rangeSize - 1), movedProp);
            } else {
                // column moved left, shift all properties right, put in moved
                // property at the leftmost index
                Property<?> movedProp = propertiesMap.get(range.get(rangeSize - 1));
                for (int i = rangeSize - 1; i > 0; i--) {
                    propertiesMap.put(range.get(i), propertiesMap.get(range.get(i - 1)));
                }
                propertiesMap.put(range.get(0), movedProp);
            }

            storeColumnOrder();
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            /*
             * If the startColumnIndex is not -1 (which is the reset value),
             * that means columns have been moved around. We then check to see
             * if either the starting or end position is 0 (the first column),
             * and then swap them back if that is the case because we don't want
             * to allow movement of the first column. We then reset
             * startColumnIndex to -1, the reset value. We check if
             * startColumnIndex is at reset or not because it is possible for
             * the mouse to be released and a MouseEvent to be fired without
             * having moved any columns.
             */
            if (startColumnIndex != -1 && (startColumnIndex == 0 || endColumnIndex == 0)) {
                outline.moveColumn(endColumnIndex, startColumnIndex);
            }
            startColumnIndex = -1;
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            //the user clicked a column header
            storeColumnSorting();
        }

        @Override
        public void columnAdded(TableColumnModelEvent e) {
            columnAddedOrRemoved();
        }

        @Override
        public void columnRemoved(TableColumnModelEvent e) {
            columnAddedOrRemoved();
        }

        /**
         * Process a columnAdded or columnRemoved event. If we are listening to
         * visibilty events the assumption is that added/removed are really
         * unhide/hide. If we are not listening do nothing.
         */
        private void columnAddedOrRemoved() {
            if (listenToVisibilitEvents) {
                SwingUtilities.invokeLater(DataResultViewerTable.this::storeColumnVisibility);

            }
        }

        @Override
        public void columnMarginChanged(ChangeEvent e) {
        }

        @Override
        public void columnSelectionChanged(ListSelectionEvent e) {
        }

        /**
         * Set the listener to listen or not to visibility changes. When this is
         * true, the listener treats all column added/removed events as
         * un-hide/hide, and persists the hidden/visible state to the
         * preferences file. When false, the listener treats added/removed as
         * added/removed (which it ignores).
         *
         * @param b
         */
        private void listenToVisibilityChanges(boolean b) {
            this.listenToVisibilitEvents = b;
        }
    }

    /*
     * A renderer which based on the contents of the cell will display an icon
     * to indicate the presence of a comment related to the content.
     */
    private final class HasCommentCellRenderer extends DefaultOutlineCellRenderer {

        private static final long serialVersionUID = 1L;

        @NbBundle.Messages({"DataResultViewerTable.commentRenderer.crComment.toolTip=Comment exists in Central Repository",
            "DataResultViewerTable.commentRenderer.tagComment.toolTip=Comment exists on associated tag(s)",
            "DataResultViewerTable.commentRenderer.crAndTagComment.toolTip=Comments exist both in Central Repository and on associated tag(s)",
            "DataResultViewerTable.commentRenderer.noComment.toolTip=No comments found"})
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setBackground(component.getBackground());  //inherit highlighting for selection
            setHorizontalAlignment(CENTER);
            Object switchValue = null;
            if ((value instanceof NodeProperty)) {
                //The Outline view has properties in the cell, the value contained in the property is what we want
                try {
                    switchValue = ((Node.Property) value).getValue();
                } catch (IllegalAccessException | InvocationTargetException ex) {
                    //Unable to get the value from the NodeProperty no Icon will be displayed
                }
            } else {
                //JTables contain the value we want directly in the cell
                switchValue = value;
            }
            setText("");
            if ((switchValue instanceof HasCommentStatus)) {

                switch ((HasCommentStatus) switchValue) {
                    case CR_COMMENT:
                        setIcon(COMMENT_ICON);
                        setToolTipText(Bundle.DataResultViewerTable_commentRenderer_crComment_toolTip());
                        break;
                    case TAG_COMMENT:
                        setIcon(COMMENT_ICON);
                        setToolTipText(Bundle.DataResultViewerTable_commentRenderer_tagComment_toolTip());
                        break;
                    case CR_AND_TAG_COMMENTS:
                        setIcon(COMMENT_ICON);
                        setToolTipText(Bundle.DataResultViewerTable_commentRenderer_crAndTagComment_toolTip());
                        break;
                    case TAG_NO_COMMENT:
                    case NO_COMMENT:
                    default:
                        setIcon(null);
                        setToolTipText(Bundle.DataResultViewerTable_commentRenderer_noComment_toolTip());
                }
            } else {
                setIcon(null);
            }

            return this;
        }

    }

    /*
     * A renderer which based on the contents of the cell will display an icon
     * to indicate the score associated with the item.
     */
    private final class ScoreCellRenderer extends DefaultOutlineCellRenderer {

        private static final long serialVersionUID = 1L;

        /**
         * Returns the icon denoted by the Score's Significance.
         *
         * @param significance The Score's Significance.
         *
         * @return The icon (or null) related to that significance.
         */
        private ImageIcon getIcon(Significance significance) {
            if (significance == null) {
                return null;
            }

            switch (significance) {
                case NOTABLE:
                    return NOTABLE_ICON_SCORE;
                case LIKELY_NOTABLE:
                    return INTERESTING_SCORE_ICON;
                case LIKELY_NONE:
                case NONE:
                case UNKNOWN:
                default:
                    return null;
            }
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setBackground(component.getBackground());  //inherit highlighting for selection
            setHorizontalAlignment(CENTER);
            Object switchValue = null;
            if ((value instanceof NodeProperty)) {
                //The Outline view has properties in the cell, the value contained in the property is what we want
                try {
                    switchValue = ((Node.Property) value).getValue();
                    setToolTipText(((FeatureDescriptor) value).getShortDescription());
                } catch (IllegalAccessException | InvocationTargetException ex) {
                    //Unable to get the value from the NodeProperty no Icon will be displayed
                }

            } else {
                //JTables contain the value we want directly in the cell
                switchValue = value;
            }
            setText("");
            if ((switchValue instanceof org.sleuthkit.datamodel.Score)) {
                setIcon(getIcon(((org.sleuthkit.datamodel.Score) switchValue).getSignificance()));
            } else {
                setIcon(null);
            }
            return this;
        }

    }

    /*
     * A renderer which based on the contents of the cell will display an empty
     * cell if no count was available.
     */
    private final class CountCellRenderer extends DefaultOutlineCellRenderer {

        private static final long serialVersionUID = 1L;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setBackground(component.getBackground());  //inherit highlighting for selection
            setHorizontalAlignment(LEFT);
            Object countValue = null;
            if ((value instanceof NodeProperty)) {
                //The Outline view has properties in the cell, the value contained in the property is what we want
                try {
                    countValue = ((Node.Property) value).getValue();
                    setToolTipText(((FeatureDescriptor) value).getShortDescription());
                } catch (IllegalAccessException | InvocationTargetException ex) {
                    //Unable to get the value from the NodeProperty no Icon will be displayed
                }
            } else {
                //JTables contain the value we want directly in the cell
                countValue = value;
            }
            setText("");
            if ((countValue instanceof Long)) {
                //Don't display value if value is negative used so that sorting will behave as desired
                if ((Long) countValue >= 0) {
                    setText(countValue.toString());
                }
            }
            return this;
        }

    }

    /**
     * Enum to denote the presence of a comment associated with the content or
     * artifacts generated from it.
     */
    public enum HasCommentStatus {
        NO_COMMENT,
        TAG_NO_COMMENT,
        CR_COMMENT,
        TAG_COMMENT,
        CR_AND_TAG_COMMENTS
    }

    /**
     * Enum to denote the score given to an item to draw the users attention
     */
    public enum Score {
        NO_SCORE,
        INTERESTING_SCORE,
        NOTABLE_SCORE
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        pageLabel = new javax.swing.JLabel();
        pageNumLabel = new javax.swing.JLabel();
        pagesLabel = new javax.swing.JLabel();
        pagePrevButton = new javax.swing.JButton();
        pageNextButton = new javax.swing.JButton();
        outlineView = new OutlineView(DataResultViewerTable.FIRST_COLUMN_LABEL);
        gotoPageLabel = new javax.swing.JLabel();
        gotoPageTextField = new javax.swing.JTextField();
        exportCSVButton = new javax.swing.JButton();

        pageLabel.setText(org.openide.util.NbBundle.getMessage(DataResultViewerTable.class, "DataResultViewerTable.pageLabel.text")); // NOI18N

        pageNumLabel.setText(org.openide.util.NbBundle.getMessage(DataResultViewerTable.class, "DataResultViewerTable.pageNumLabel.text")); // NOI18N

        pagesLabel.setText(org.openide.util.NbBundle.getMessage(DataResultViewerTable.class, "DataResultViewerTable.pagesLabel.text")); // NOI18N

        pagePrevButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/corecomponents/btn_step_back.png"))); // NOI18N
        pagePrevButton.setText(org.openide.util.NbBundle.getMessage(DataResultViewerTable.class, "DataResultViewerTable.pagePrevButton.text")); // NOI18N
        pagePrevButton.setDisabledIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/corecomponents/btn_step_back_disabled.png"))); // NOI18N
        pagePrevButton.setFocusable(false);
        pagePrevButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        pagePrevButton.setMargin(new java.awt.Insets(2, 0, 2, 0));
        pagePrevButton.setPreferredSize(new java.awt.Dimension(55, 23));
        pagePrevButton.setRolloverIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/corecomponents/btn_step_back_hover.png"))); // NOI18N
        pagePrevButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        pagePrevButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                pagePrevButtonActionPerformed(evt);
            }
        });

        pageNextButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/corecomponents/btn_step_forward.png"))); // NOI18N
        pageNextButton.setText(org.openide.util.NbBundle.getMessage(DataResultViewerTable.class, "DataResultViewerTable.pageNextButton.text")); // NOI18N
        pageNextButton.setDisabledIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/corecomponents/btn_step_forward_disabled.png"))); // NOI18N
        pageNextButton.setFocusable(false);
        pageNextButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        pageNextButton.setMargin(new java.awt.Insets(2, 0, 2, 0));
        pageNextButton.setMaximumSize(new java.awt.Dimension(27, 23));
        pageNextButton.setMinimumSize(new java.awt.Dimension(27, 23));
        pageNextButton.setRolloverIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/corecomponents/btn_step_forward_hover.png"))); // NOI18N
        pageNextButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        pageNextButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                pageNextButtonActionPerformed(evt);
            }
        });

        gotoPageLabel.setText(org.openide.util.NbBundle.getMessage(DataResultViewerTable.class, "DataResultViewerTable.gotoPageLabel.text")); // NOI18N

        gotoPageTextField.setText(org.openide.util.NbBundle.getMessage(DataResultViewerTable.class, "DataResultViewerTable.gotoPageTextField.text")); // NOI18N
        gotoPageTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                gotoPageTextFieldActionPerformed(evt);
            }
        });

        exportCSVButton.setText(org.openide.util.NbBundle.getMessage(DataResultViewerTable.class, "DataResultViewerTable.exportCSVButton.text")); // NOI18N
        exportCSVButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exportCSVButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(outlineView, javax.swing.GroupLayout.DEFAULT_SIZE, 904, Short.MAX_VALUE)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(pageLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(pageNumLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 53, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(14, 14, 14)
                .addComponent(pagesLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(pagePrevButton, javax.swing.GroupLayout.PREFERRED_SIZE, 16, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(pageNextButton, javax.swing.GroupLayout.PREFERRED_SIZE, 16, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(gotoPageLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(gotoPageTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 33, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(exportCSVButton))
        );

        layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {pageNextButton, pagePrevButton});

        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addGap(3, 3, 3)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(pageLabel)
                    .addComponent(pageNumLabel)
                    .addComponent(pagesLabel)
                    .addComponent(pagePrevButton, javax.swing.GroupLayout.PREFERRED_SIZE, 14, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(pageNextButton, javax.swing.GroupLayout.PREFERRED_SIZE, 15, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(gotoPageLabel)
                    .addComponent(gotoPageTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(exportCSVButton))
                .addGap(3, 3, 3)
                .addComponent(outlineView, javax.swing.GroupLayout.DEFAULT_SIZE, 321, Short.MAX_VALUE)
                .addContainerGap())
        );

        layout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {pageNextButton, pagePrevButton});

        gotoPageLabel.getAccessibleContext().setAccessibleName("");
    }// </editor-fold>//GEN-END:initComponents

    private void pagePrevButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pagePrevButtonActionPerformed
        pagingSupport.previousPage();
    }//GEN-LAST:event_pagePrevButtonActionPerformed

    private void pageNextButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pageNextButtonActionPerformed
        pagingSupport.nextPage();
    }//GEN-LAST:event_pageNextButtonActionPerformed

    private void gotoPageTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_gotoPageTextFieldActionPerformed
        pagingSupport.gotoPage();
    }//GEN-LAST:event_gotoPageTextFieldActionPerformed

    @NbBundle.Messages({"DataResultViewerTable.exportCSVButtonActionPerformed.empty=No data to export"
    })
    private void exportCSVButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportCSVButtonActionPerformed
        Node currentRoot = this.getExplorerManager().getRootContext();
        if (currentRoot != null && currentRoot.getChildren().getNodesCount() > 0) {
            org.sleuthkit.autopsy.directorytree.ExportCSVAction.saveNodesToCSV(java.util.Arrays.asList(currentRoot.getChildren().getNodes()), this);
        } else {
            MessageNotifyUtil.Message.info(Bundle.DataResultViewerTable_exportCSVButtonActionPerformed_empty());
        }
    }//GEN-LAST:event_exportCSVButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton exportCSVButton;
    private javax.swing.JLabel gotoPageLabel;
    private javax.swing.JTextField gotoPageTextField;
    private org.openide.explorer.view.OutlineView outlineView;
    private javax.swing.JLabel pageLabel;
    private javax.swing.JButton pageNextButton;
    private javax.swing.JLabel pageNumLabel;
    private javax.swing.JButton pagePrevButton;
    private javax.swing.JLabel pagesLabel;
    // End of variables declaration//GEN-END:variables

}
