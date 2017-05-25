/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.prefs.Preferences;
import java.util.stream.Stream;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import org.netbeans.swing.etable.ETableColumn;
import org.netbeans.swing.outline.DefaultOutlineCellRenderer;
import org.netbeans.swing.outline.DefaultOutlineModel;
import org.netbeans.swing.outline.Outline;
import org.openide.explorer.ExplorerManager;
import org.openide.explorer.view.OutlineView;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Node.Property;
import org.openide.nodes.Node.PropertySet;
import org.openide.nodes.NodeAdapter;
import org.openide.nodes.NodeMemberEvent;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataResultViewer;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.datamodel.NodeSelectionInfo;

/**
 * A tabular viewer for the results view.
 *
 * TODO (JIRA-2658): Fix DataResultViewer extension point. When this is done,
 * restore implementation of DataResultViewerTable as a DataResultViewer service
 * provider.
 */
//@ServiceProvider(service = DataResultViewer.class)
public class DataResultViewerTable extends AbstractDataResultViewer {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(DataResultViewerTable.class.getName());
    @NbBundle.Messages("DataResultViewerTable.firstColLbl=Name")
    static private final String FIRST_COLUMN_LABEL = Bundle.DataResultViewerTable_firstColLbl();
    @NbBundle.Messages("DataResultViewerTable.pleasewaitNodeDisplayName=Please Wait...")
    private static final String PLEASEWAIT_NODE_DISPLAY_NAME = Bundle.DataResultViewerTable_pleasewaitNodeDisplayName();
    private static final Color TAGGED_COLOR = new Color(200, 210, 220);
    /*
     * The properties map:
     *
     * stored value of column index -> property at that index
     *
     * We move around stored values instead of directly using the column indices
     * in order to not override settings for a column that may not appear in the
     * current table view due to its collection of its children's properties.
     */
    private final Map<Integer, Property<?>> propertiesMap = new TreeMap<>();
    private final PleasewaitNodeListener pleasewaitNodeListener = new PleasewaitNodeListener();

    private Node currentRoot;

    /*
     * Convience reference to internal Outline.
     */
    private Outline outline;

    /**
     * Creates a DataResultViewerTable object that is compatible with node
     * multiple selection actions.
     *
     * @param explorerManager allow for explorer manager sharing
     */
    public DataResultViewerTable(ExplorerManager explorerManager) {
        super(explorerManager);
        initialize();
    }

    /**
     * Creates a DataResultViewerTable object that is NOT compatible with node
     * multiple selection actions.
     */
    public DataResultViewerTable() {
        initialize();
    }

    private void initialize() {
        initComponents();

        outlineView.setAllowedDragActions(DnDConstants.ACTION_NONE);

        outline = outlineView.getOutline();
        outline.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        outline.setRootVisible(false);    // don't show the root node
        outline.setDragEnabled(false);
        outline.setDefaultRenderer(Object.class, new ColorTagCustomRenderer());

        // add a listener so that when columns are moved, the new order is stored
        TableListener tableListener = new TableListener();
        outline.getColumnModel().addColumnModelListener(tableListener);
        // the listener also moves columns back if user tries to move the first column out of place
        outline.getTableHeader().addMouseListener(tableListener);
    }

    /**
     * Expand node
     *
     * @param n Node to expand
     */
    @Override

    public void expandNode(Node n) {
        super.expandNode(n);

        outlineView.expandNode(n);
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

    @Override
    public boolean isSupported(Node selectedNode) {
        return true;
    }

    @Override
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    public void setNode(Node selectedNode) {

        /*
         * The quick filter must be reset because when determining column width,
         * ETable.getRowCount is called, and the documentation states that quick
         * filters must be unset for the method to work "If the quick-filter is
         * applied the number of rows do not match the number of rows in the
         * model."
         */
        outline.unsetQuickFilter();
        // change the cursor to "waiting cursor" for this operation
        this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            boolean hasChildren = false;
            if (selectedNode != null) {
                // @@@ This just did a DB round trip to get the count and the results were not saved...
                hasChildren = selectedNode.getChildren().getNodesCount() > 0;
            }

            Node oldNode = this.em.getRootContext();
            if (oldNode != null) {
                oldNode.removeNodeListener(pleasewaitNodeListener);
            }

            if (hasChildren) {
                currentRoot = selectedNode;
                pleasewaitNodeListener.reset();
                currentRoot.addNodeListener(pleasewaitNodeListener);
                setupTable(selectedNode);
                NodeSelectionInfo selectedChildInfo = ((TableFilterNode) currentRoot).getChildNodeSelectionInfo();
                if (null != selectedChildInfo) {
                    Node[] childNodes = currentRoot.getChildren().getNodes(true);
                    for (int i = 0; i < childNodes.length; ++i) {
                        Node childNode = childNodes[i];
                        if (selectedChildInfo.matches(childNode)) {
                            try {
                                em.setSelectedNodes(new Node[]{childNode});
                            } catch (PropertyVetoException ex) {
                                logger.log(Level.SEVERE, "Failed to select node specified by selected child info", ex);
                            }
                            break;
                        }
                    }
                }
            } else {
                Node emptyNode = new AbstractNode(Children.LEAF);
                em.setRootContext(emptyNode); // make empty node
                outline.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
                outlineView.setPropertyColumns(); // set the empty property header
            }
        } finally {
            this.setCursor(null);
        }
    }

    /**
     * Create Column Headers based on the Content represented by the Nodes in
     * the table.
     *
     * @param root The parent Node of the ContentNodes
     */
    private void setupTable(final Node root) {
        em.setRootContext(root);
        currentRoot = root;

        /**
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
        if (props.isEmpty() == false) {
            Node.Property<?> prop = props.remove(0);
            ((DefaultOutlineModel) outline.getOutlineModel()).setNodesColumnLabel(prop.getDisplayName());
        }
        
        /*
         * show the horizontal scroll panel and show all the content & header If
         * there is only one column (which was removed from props above) Just
         * let the table resize itself.
         */
        outline.setAutoResizeMode((props.isEmpty()) ? JTable.AUTO_RESIZE_ALL_COLUMNS : JTable.AUTO_RESIZE_OFF);

        assignColumns(props);
        setColumnWidths();
        loadColumnSorting();
    }

    private void setColumnWidths() {
        if (currentRoot.getChildren().getNodesCount() != 0) {
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

    private void assignColumns(List<Property<?>> props) {
        // Get the columns setup with respect to names and sortability
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
     * Store the current column order and sort information into a preference
     * file.
     */
    private synchronized void storeColumnState() {
        if (currentRoot == null || propertiesMap.isEmpty()) {
            return;
        }
        if (currentRoot instanceof TableFilterNode) {
            TableFilterNode tfn = (TableFilterNode) currentRoot;
            final Preferences preferences = NbPreferences.forModule(DataResultViewerTable.class);
            // Store the current order of the columns into settings
            for (Map.Entry<Integer, Property<?>> entry : propertiesMap.entrySet()) {
                preferences.put(ResultViewerPersistence.getColumnPositionKey(tfn, entry.getValue().getName()), String.valueOf(entry.getKey()));
            }
            final TableColumnModel columnModel = outline.getColumnModel();

            //store the sorting information
            int numCols = columnModel.getColumnCount();
            for (int i = 0; i < numCols; i++) {
                ETableColumn etc = (ETableColumn) columnModel.getColumn(i);
                String columnName = outline.getColumnName(i);
                final String columnSortOrderKey = ResultViewerPersistence.getColumnSortOrderKey(tfn, columnName);
                final String columnSortRankKey = ResultViewerPersistence.getColumnSortRankKey(tfn, columnName);
                if (etc.isSorted()) {
                    preferences.put(columnSortOrderKey, String.valueOf(etc.isAscending()));
                    preferences.put(columnSortRankKey, String.valueOf(etc.getSortRank()));
                } else {
                    preferences.remove(columnSortOrderKey);
                    preferences.remove(columnSortRankKey);
                }
            }
        }
    }

    /**
     * Reads and applies the column sorting information persisted to the
     * preferences file. Must be called after loadColumnOrder() since it depends
     * on propertiesMap being initialized, and before assignColumns since it
     * cannot set the sort on columns that have not been added to the table.
     */
    private synchronized void loadColumnSorting() {
        if (currentRoot == null || propertiesMap.isEmpty()) {
            return;
        }

        if (currentRoot instanceof TableFilterNode) {
            final TableFilterNode tfn = (TableFilterNode) currentRoot;

            final Preferences preferences = NbPreferences.forModule(DataResultViewerTable.class);
            //organize property sorting information, sorted by rank
            TreeSet<ColumnSortInfo> treeSet = new TreeSet<>(Comparator.comparing(ColumnSortInfo::getRank));
            propertiesMap.entrySet().stream().forEach(entry -> {
                final String propName = entry.getValue().getName();
                //if the sort rank is undefined, it will be defaulted to 0 => unsorted.
                Integer sortRank = Integer.valueOf(preferences.get(ResultViewerPersistence.getColumnSortRankKey(tfn, propName), "0"));
                //default to true => ascending
                Boolean sortOrder = Boolean.valueOf(preferences.get(ResultViewerPersistence.getColumnSortOrderKey(tfn, propName), "true"));

                treeSet.add(new ColumnSortInfo(entry.getKey(), sortRank, sortOrder));
            });

            //apply sort information in rank order.
            treeSet.forEach(sortInfo -> outline.setColumnSorted(sortInfo.modelIndex, sortInfo.order, sortInfo.rank));
        }
    }

    /**
     * Gets a list of child properties (columns) in the order persisted in the
     * preference file. Also initialized the propertiesMap with the column
     * order.
     *
     * @return a List<Node.Property<?>> of the properties in the persisted
     *         order.
     */
    private synchronized List<Node.Property<?>> loadColumnOrder() {

        List<Property<?>> props = ResultViewerPersistence.getAllChildProperties(currentRoot, 100);

        // If node is not table filter node, use default order for columns
        if (!(currentRoot instanceof TableFilterNode)) {
            return props;
        }

        final TableFilterNode tfn = ((TableFilterNode) currentRoot);
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
            Integer value = Integer.valueOf(preferences.get(ResultViewerPersistence.getColumnPositionKey(tfn, prop.getName()), "-1"));
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

    @Override
    @NbBundle.Messages("DataResultViewerTable.title=Table")
    public String getTitle() {
        return Bundle.DataResultViewerTable_title();
    }

    @Override
    public DataResultViewer createInstance() {
        return new DataResultViewerTable();
    }

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
     * and sorting changes
     */
    private class TableListener extends MouseAdapter implements TableColumnModelListener {

        // When a column in the table is moved, these two variables keep track of where
        // the column started and where it ended up.
        private int startColumnIndex = -1;
        private int endColumnIndex = -1;

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

            storeColumnState();
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
            storeColumnState();
        }

        @Override
        public void columnAdded(TableColumnModelEvent e) {
        }

        @Override
        public void columnRemoved(TableColumnModelEvent e) {
        }

        @Override
        public void columnMarginChanged(ChangeEvent e) {
        }

        @Override
        public void columnSelectionChanged(ListSelectionEvent e) {
        }
    }

    private class PleasewaitNodeListener extends NodeAdapter {

        private volatile boolean load = true;

        public void reset() {
            load = true;
        }

        @Override
        public void childrenAdded(final NodeMemberEvent nme) {
            Node[] delta = nme.getDelta();
            if (load && containsReal(delta)) {
                load = false;
                //JMTODO: this looks suspicious
                if (SwingUtilities.isEventDispatchThread()) {
                    setupTable(nme.getNode());
                } else {
                    SwingUtilities.invokeLater(() -> setupTable(nme.getNode()));
                }
            }
        }

        private boolean containsReal(Node[] delta) {
            return Stream.of(delta)
                    .map(Node::getDisplayName)
                    .noneMatch(PLEASEWAIT_NODE_DISPLAY_NAME::equals);
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
            if (!isSelected) {
                Node node = currentRoot.getChildren().getNodeAt(table.convertRowIndexToModel(row));
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
                    component.setBackground(TAGGED_COLOR);
                }
            }
            return component;
        }
    }
}
