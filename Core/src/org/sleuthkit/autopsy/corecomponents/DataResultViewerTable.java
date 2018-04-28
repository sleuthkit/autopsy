/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2018 Basis Technology Corp.
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

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.dnd.DnDConstants;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyVetoException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.prefs.Preferences;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
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
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataResultViewer;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.datamodel.NodeSelectionInfo;

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
public final class DataResultViewerTable extends AbstractDataResultViewer {

    private static final long serialVersionUID = 1L;
    @NbBundle.Messages("DataResultViewerTable.firstColLbl=Name")
    static private final String FIRST_COLUMN_LABEL = Bundle.DataResultViewerTable_firstColLbl();
    static private final Color TAGGED_ROW_COLOR = new Color(255, 255, 195);
    private static final Logger logger = Logger.getLogger(DataResultViewerTable.class.getName());
    private final String title;
    private final Map<String, ETableColumn> columnMap;
    private final Map<Integer, Property<?>> propertiesMap;
    private final Outline outline;
    private final TableListener outlineViewListener;
    private Node rootNode;

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

        /*
         * Configure the child OutlineView (explorer view) component.
         */
        outlineView.setAllowedDragActions(DnDConstants.ACTION_NONE);
        outline = outlineView.getOutline();
        outline.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        outline.setRootVisible(false);
        outline.setDragEnabled(false);
        outline.setDefaultRenderer(Object.class, new ColorTagCustomRenderer());

        /*
         * Add a table listener to the child OutlineView (explorer view) to
         * persist the order of the table columns when a column is moved.
         */
        outlineViewListener = new TableListener();
        outline.getColumnModel().addColumnModelListener(outlineViewListener);

        /*
         * Add a mouse listener to the child OutlineView (explorer view) to make
         * sure the first column of the table is kept in place.
         */
        outline.getTableHeader().addMouseListener(outlineViewListener);
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
            /*
             * If the given node is not null and has children, set it as the
             * root context of the child OutlineView, otherwise make an
             * "empty"node the root context.
             *
             * IMPORTANT NOTE: This is the first of many times where a
             * getChildren call on the current root node causes all of the
             * children of the root node to be created and defeats lazy child
             * node creation, if it is enabled. It also likely leads to many
             * case database round trips.
             */
            if (rootNode != null && rootNode.getChildren().getNodesCount() > 0) {
                this.rootNode = rootNode;
                this.getExplorerManager().setRootContext(this.rootNode);
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
     * Sets up the Outline view of this tabular result viewer by creating
     * column headers based on the children of the current root node. The
     * persisted column order, sorting and visibility is used.
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

        /*
         * show the horizontal scroll panel and show all the content & header If
         * there is only one column (which was removed from props above) Just
         * let the table resize itself.
         */
        outline.setAutoResizeMode((props.isEmpty()) ? JTable.AUTO_RESIZE_ALL_COLUMNS : JTable.AUTO_RESIZE_OFF);

        assignColumns(props); // assign columns to match the properties
        if (firstProp != null) {
            ((DefaultOutlineModel) outline.getOutlineModel()).setNodesColumnLabel(firstProp.getDisplayName());
        }

        setColumnWidths();

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
         * If one of the child nodes of the root node is to be selected, select
         * it.
         */
        SwingUtilities.invokeLater(() -> {
            if (rootNode instanceof TableFilterNode) {
                NodeSelectionInfo selectedChildInfo = ((TableFilterNode) rootNode).getChildNodeSelectionInfo();
                if (null != selectedChildInfo) {
                    Node[] childNodes = rootNode.getChildren().getNodes(true);
                    for (int i = 0; i < childNodes.length; ++i) {
                        Node childNode = childNodes[i];
                        if (selectedChildInfo.matches(childNode)) {
                            try {
                                this.getExplorerManager().setSelectedNodes(new Node[]{childNode});
                            } catch (PropertyVetoException ex) {
                                logger.log(Level.SEVERE, "Failed to select node specified by selected child info", ex);
                            }
                            break;
                        }
                    }
                    ((TableFilterNode) rootNode).setChildNodeSelectionInfo(null);
                }
            }
        });

        /*
         * The table setup is done, so any added/removed events can now be
         * treated as un-hide/hide.
         */
        outlineViewListener.listenToVisibilityChanges(true);
    }

    /*
     * Populates the column map for the child OutlineView of this tabular
     * result viewer with references to the column objects for use when
     * loading/storing the visibility info.
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
     * viewer.
     */
    private void setColumnWidths() {
        if (rootNode.getChildren().getNodesCount() != 0) {
            final Graphics graphics = outlineView.getGraphics();
            if (graphics != null) {
                final FontMetrics metrics = graphics.getFontMetrics();

                int margin = 4;
                int padding = 8;

                for (int column = 0; column < outline.getModel().getColumnCount(); column++) {
                    int firstColumnPadding = (column == 0) ? 32 : 0;
                    int columnWidthLimit = (column == 0) ? 350 : 300;
                    int valuesWidth = 0;

                    // find the maximum width needed to fit the values for the first 100 rows, at most
                    for (int row = 0; row < Math.min(100, outline.getRowCount()); row++) {
                        TableCellRenderer renderer = outline.getCellRenderer(row, column);
                        Component comp = outline.prepareRenderer(renderer, row, column);
                        valuesWidth = Math.max(comp.getPreferredSize().width, valuesWidth);
                    }

                    int headerWidth = metrics.stringWidth(outline.getColumnName(column));
                    valuesWidth += firstColumnPadding; // add extra padding for first column

                    int columnWidth = Math.max(valuesWidth, headerWidth);
                    columnWidth += 2 * margin + padding; // add margin and regular padding
                    columnWidth = Math.min(columnWidth, columnWidthLimit);

                    outline.getColumnModel().getColumn(column).setPreferredWidth(columnWidth);
                }
            }
        } else {
            // if there's no content just auto resize all columns
            outline.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        }
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
        boolean noPreviousSettings = true;

        final Preferences preferences = NbPreferences.forModule(DataResultViewerTable.class);

        for (Property<?> prop : props) {
            Integer value = preferences.getInt(ResultViewerPersistence.getColumnPositionKey(tfn, prop.getName()), -1);
            if (value >= 0 && value < offset && !propertiesMap.containsKey(value)) {
                propertiesMap.put(value, prop);
                noPreviousSettings = false;
            } else {
                propertiesMap.put(offset, prop);
                offset++;
            }
        }

        // If none of the properties had previous settings, we should decrement
        // each value by the number of properties to make the values 0-indexed.
        if (noPreviousSettings) {
            ArrayList<Integer> keys = new ArrayList<>(propertiesMap.keySet());
            for (int key : keys) {
                propertiesMap.put(key - props.size(), propertiesMap.remove(key));
            }
        }

        return new ArrayList<>(propertiesMap.values());
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

    /**
     * This custom renderer extends the renderer that was already being used by
     * the outline table. This renderer colors a row if the tags property of the
     * node is not empty.
     */
    private class ColorTagCustomRenderer extends DefaultOutlineCellRenderer {

        private static final long serialVersionUID = 1L;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {

            Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
            // only override the color if a node is not selected
            if (rootNode != null && !isSelected) {
                Node node = rootNode.getChildren().getNodeAt(table.convertRowIndexToModel(row));
                boolean tagFound = false;
                if (node != null) {
                    Node.PropertySet[] propSets = node.getPropertySets();
                    if (propSets.length != 0) {
                        // currently, a node has only one property set, named Sheet.PROPERTIES ("properties")
                        Node.Property<?>[] props = propSets[0].getProperties();
                        for (Property<?> prop : props) {
                            if ("Tags".equals(prop.getName())) {//NON-NLS
                                try {
                                    tagFound = !prop.getValue().equals("");
                                } catch (IllegalAccessException | InvocationTargetException ignore) {
                                }
                                break;
                            }
                        }
                    }
                }
                //if the node does have associated tags, set its background color
                if (tagFound) {
                    component.setBackground(TAGGED_ROW_COLOR);
                }
            }
            return component;
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        outlineView = new OutlineView(DataResultViewerTable.FIRST_COLUMN_LABEL);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(outlineView, javax.swing.GroupLayout.DEFAULT_SIZE, 691, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(outlineView, javax.swing.GroupLayout.DEFAULT_SIZE, 366, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private org.openide.explorer.view.OutlineView outlineView;
    // End of variables declaration//GEN-END:variables

}
