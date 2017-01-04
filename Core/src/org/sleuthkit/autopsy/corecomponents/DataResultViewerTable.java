/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2013-2016 Basis Technology Corp.
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
import java.beans.PropertyChangeEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.table.TableCellRenderer;
import org.netbeans.swing.outline.DefaultOutlineCellRenderer;
import org.netbeans.swing.outline.DefaultOutlineModel;
import org.openide.explorer.ExplorerManager;
import org.openide.explorer.view.OutlineView;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Node.Property;
import org.openide.nodes.Node.PropertySet;
import org.openide.nodes.NodeEvent;
import org.openide.nodes.NodeListener;
import org.openide.nodes.NodeMemberEvent;
import org.openide.nodes.NodeReorderEvent;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataResultViewer;

/**
 * DataResult sortable table viewer
 */
// @@@ Restore implementation of DataResultViewerTable as a DataResultViewer
// service provider when DataResultViewers can be made compatible with node
// multiple selection actions.
//@ServiceProvider(service = DataResultViewer.class)
public class DataResultViewerTable extends AbstractDataResultViewer {

    private static final long serialVersionUID = 1L;

    private final String firstColumnLabel = NbBundle.getMessage(DataResultViewerTable.class, "DataResultViewerTable.firstColLbl");
    /* The properties map maps
     * key: stored value of column index -> value: property at that index
     * We move around stored values instead of directly using the column indices
     * in order to not override settings for a column that may not appear in the
     * current table view due to its collection of its children's properties.
     */
    private final Map<Integer, Property<?>> propertiesMap = new TreeMap<>();
    private final DummyNodeListener dummyNodeListener = new DummyNodeListener();
    private static final String DUMMY_NODE_DISPLAY_NAME = NbBundle.getMessage(DataResultViewerTable.class, "DataResultViewerTable.dummyNodeDisplayName");
    private static final Color TAGGED_COLOR = new Color(200, 210, 220);
    private Node currentRoot;
    // When a column in the table is moved, these two variables keep track of where
    // the column started and where it ended up.
    private int startColumnIndex = -1;
    private int endColumnIndex = -1;

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

        OutlineView ov = ((OutlineView) this.tableScrollPanel);
        ov.setAllowedDragActions(DnDConstants.ACTION_NONE);

        ov.getOutline().setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        // don't show the root node
        ov.getOutline().setRootVisible(false);
        ov.getOutline().setDragEnabled(false);

        // add a listener so that when columns are moved, the new order is stored
        ov.getOutline().getColumnModel().addColumnModelListener(new TableColumnModelListener() {
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
            @Override
            public void columnMoved(TableColumnModelEvent e) {
                int fromIndex = e.getFromIndex();
                int toIndex = e.getToIndex();
                if (fromIndex == toIndex) {
                    return;
                }

                /* Because a column may be dragged to several different positions before
                 * the mouse is released (thus causing multiple TableColumnModelEvents to
                 * be fired), we want to keep track of the starting column index in this
                 * potential series of movements. Therefore we only keep track of the
                 * original fromIndex in startColumnIndex, but we always update
                 * endColumnIndex to know the final position of the moved column.
                 * See the MouseListener mouseReleased method.
                */
                if (startColumnIndex == -1) {
                    startColumnIndex = fromIndex;
                }
                endColumnIndex = toIndex;

                // This array contains the keys of propertiesMap in order
                int[] indicesList = new int[propertiesMap.size()];
                int pos = 0;
                for (int key : propertiesMap.keySet()) {
                    indicesList[pos++] = key;
                }
                int leftIndex = Math.min(fromIndex, toIndex);
                int rightIndex = Math.max(fromIndex, toIndex);
                // Now we can copy the range of keys that have been affected by
                // the column movement
                int[] range = Arrays.copyOfRange(indicesList, leftIndex, rightIndex + 1);
                int rangeSize = range.length;

                // column moved right, shift all properties left, put in moved
                // property at the rightmost index
                if (fromIndex < toIndex) {
                    Property<?> movedProp = propertiesMap.get(range[0]);
                    for (int i = 0; i < rangeSize - 1; i++) {
                        propertiesMap.put(range[i], propertiesMap.get(range[i + 1]));
                    }
                    propertiesMap.put(range[rangeSize - 1], movedProp);
                }
                // column moved left, shift all properties right, put in moved
                // property at the leftmost index
                else {
                    Property<?> movedProp = propertiesMap.get(range[rangeSize - 1]);
                    for (int i = rangeSize - 1; i > 0; i--) {
                        propertiesMap.put(range[i], propertiesMap.get(range[i - 1]));
                    }
                    propertiesMap.put(range[0], movedProp);
                }

                storeState();
            }
        });

        // add a listener to move columns back if user tries to move the first column out of place
        ov.getOutline().getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                /* If the startColumnIndex is not -1 (which is the reset value), that
                 * means columns have been moved around. We then check to see if either
                 * the starting or end position is 0 (the first column), and then swap
                 * them back if that is the case because we don't want to allow movement
                 * of the first column. We then reset startColumnIndex to -1, the reset
                 * value.
                 * We check if startColumnIndex is at reset or not because it is
                 * possible for the mouse to be released and a MouseEvent to be fired
                 * without having moved any columns.
                 */
                if (startColumnIndex != -1 && (startColumnIndex == 0 || endColumnIndex == 0)) {
                    ov.getOutline().moveColumn(endColumnIndex, startColumnIndex);
                }
                startColumnIndex = -1;
            }
        });
    }

    /**
     * Expand node
     *
     * @param n Node to expand
     */
    @Override
    public void expandNode(Node n) {
        super.expandNode(n);

        if (this.tableScrollPanel != null) {
            OutlineView ov = ((OutlineView) this.tableScrollPanel);
            ov.expandNode(n);
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

        tableScrollPanel = new OutlineView(this.firstColumnLabel);

        //new TreeTableView()
        tableScrollPanel.addComponentListener(new java.awt.event.ComponentAdapter() {
            public void componentResized(java.awt.event.ComponentEvent evt) {
                tableScrollPanelComponentResized(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(tableScrollPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 691, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(tableScrollPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 366, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    private void tableScrollPanelComponentResized(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_tableScrollPanelComponentResized
    }//GEN-LAST:event_tableScrollPanelComponentResized
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JScrollPane tableScrollPanel;
    // End of variables declaration//GEN-END:variables

    /**
     * Gets regular Bean property set properties from all children and,
     * recursively, subchildren of Node. Note: won't work out the box for lazy
     * load - you need to set all children props for the parent by hand
     *
     * @param parent Node with at least one child to get properties from
     * @param rows   max number of rows to retrieve properties for (can be used
     *               for memory optimization)
     */
    private void getAllChildPropertyHeadersRec(Node parent, int rows, Set<Property<?>> propertiesAcc) {
        Children children = parent.getChildren();
        int childCount = 0;
        for (Node child : children.getNodes()) {
            if (++childCount > rows) {
                return;
            }
            for (PropertySet ps : child.getPropertySets()) {
                final Property<?>[] props = ps.getProperties();
                final int propsNum = props.length;
                for (int j = 0; j < propsNum; ++j) {
                    propertiesAcc.add(props[j]);
                }
            }
            getAllChildPropertyHeadersRec(child, rows, propertiesAcc);
        }
    }

    @Override
    public boolean isSupported(Node selectedNode) {
        return true;
    }

    /**
     * Thread note: Make sure to run this in the EDT as it causes GUI
     * operations.
     *
     * @param selectedNode
     */
    @Override
    public void setNode(Node selectedNode) {
        final OutlineView ov = ((OutlineView) this.tableScrollPanel);
        /* The quick filter must be reset because when determining column width,
         * ETable.getRowCount is called, and the documentation states that quick
         * filters must be unset for the method to work
         * "If the quick-filter is applied the number of rows do not match the number of rows in the model."
         */
        ov.getOutline().unsetQuickFilter();
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
                oldNode.removeNodeListener(dummyNodeListener);
            }

            // if there's no selection node, do nothing
            if (hasChildren) {
                Node root = selectedNode;
                dummyNodeListener.reset();
                root.addNodeListener(dummyNodeListener);
                setupTable(root);
            } else {
                Node emptyNode = new AbstractNode(Children.LEAF);
                em.setRootContext(emptyNode); // make empty node
                ov.getOutline().setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
                ov.setPropertyColumns(); // set the empty property header
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
        final OutlineView ov = ((OutlineView) this.tableScrollPanel);

        if (ov == null) {
            return;
        }
        currentRoot = root;
        List<Node.Property<?>> props = loadState();

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
        if (props.size() > 0) {
            Node.Property<?> prop = props.remove(0);
            ((DefaultOutlineModel) ov.getOutline().getOutlineModel()).setNodesColumnLabel(prop.getDisplayName());
        }

        // Get the columns setup with respect to names and sortability
        String[] propStrings = new String[props.size() * 2];
        for (int i = 0; i < props.size(); i++) {
            props.get(i).setValue("ComparableColumnTTV", Boolean.TRUE); //NON-NLS
            //First property column is sorted initially
            if (i == 0) {
                props.get(i).setValue("TreeColumnTTV", Boolean.TRUE); // Identifies special property representing first (tree) column. NON-NLS
                props.get(i).setValue("SortingColumnTTV", Boolean.TRUE); // TreeTableView should be initially sorted by this property column. NON-NLS
            }
            propStrings[2 * i] = props.get(i).getName();
            propStrings[2 * i + 1] = props.get(i).getDisplayName();
        }

        ov.setPropertyColumns(propStrings);

        // show the horizontal scroll panel and show all the content & header
        // If there is only one column (which was removed from props above)
        // Just let the table resize itself.
        ov.getOutline().setAutoResizeMode((props.size() > 0) ? JTable.AUTO_RESIZE_OFF : JTable.AUTO_RESIZE_ALL_COLUMNS);

        if (root.getChildren().getNodesCount() != 0) {
            final Graphics graphics = ov.getGraphics();
            if (graphics != null) {
                final FontMetrics metrics = graphics.getFontMetrics();

                int margin = 4;
                int padding = 8;

                for (int column = 0; column < ov.getOutline().getModel().getColumnCount(); column++) {
                    int firstColumnPadding = (column == 0) ? 32 : 0;
                    int columnWidthLimit = (column == 0) ? 350 : 300;
                    int valuesWidth = 0;

                    // find the maximum width needed to fit the values for the first 100 rows, at most
                    for (int row = 0; row < Math.min(100, ov.getOutline().getRowCount()); row++) {
                        TableCellRenderer renderer = ov.getOutline().getCellRenderer(row, column);
                        Component comp = ov.getOutline().prepareRenderer(renderer, row, column);
                        valuesWidth = Math.max(comp.getPreferredSize().width, valuesWidth);
                    }

                    int headerWidth = metrics.stringWidth(ov.getOutline().getColumnName(column));
                    valuesWidth += firstColumnPadding; // add extra padding for first column

                    int columnWidth = Math.max(valuesWidth, headerWidth);
                    columnWidth += 2 * margin + padding; // add margin and regular padding
                    columnWidth = Math.min(columnWidth, columnWidthLimit);

                    ov.getOutline().getColumnModel().getColumn(column).setPreferredWidth(columnWidth);
                }
            }
        } else {
            // if there's no content just auto resize all columns
            ov.getOutline().setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        }

        /**
         * This custom renderer extends the renderer that was already being
         * used by the outline table. This renderer colors a row if the
         * tags property of the node is not empty.
         */
        class ColorTagCustomRenderer extends DefaultOutlineCellRenderer {
            private static final long serialVersionUID = 1L;
            @Override
            public Component getTableCellRendererComponent(JTable table,
                    Object value, boolean isSelected, boolean hasFocus, int row, int col) {
                
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
                                if (prop.getName().equals("Tags")) {
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
        ov.getOutline().setDefaultRenderer(Object.class, new ColorTagCustomRenderer());
    }

    /**
     * Store the current column order into a preference file.
     */
    private synchronized void storeState() {
        if (currentRoot == null || propertiesMap.isEmpty()) {
            return;
        }

        TableFilterNode tfn;
        if (currentRoot instanceof TableFilterNode) {
            tfn = (TableFilterNode) currentRoot;
        } else {
            return;
        }

        // Store the current order of the columns into settings
        for (Map.Entry<Integer, Property<?>> entry : propertiesMap.entrySet()) {
            Property<?> prop = entry.getValue();
            int storeValue = entry.getKey();
            NbPreferences.forModule(this.getClass()).put(getColumnPreferenceKey(prop, tfn.getColumnOrderKey()), String.valueOf(storeValue));
        }
    }

    /**
     * Loads the stored column order from the preference file.
     *
     * @return a List<Node.Property<?>> of the preferences in order
     */
    private synchronized List<Node.Property<?>> loadState() {
        // This is a set because we add properties of up to 100 child nodes, and we want unique properties
        Set<Property<?>> propertiesAcc = new LinkedHashSet<>();
        this.getAllChildPropertyHeadersRec(currentRoot, 100, propertiesAcc);

        List<Node.Property<?>> props = new ArrayList<>(propertiesAcc);

        // If node is not table filter node, use default order for columns
        TableFilterNode tfn;
        if (currentRoot instanceof TableFilterNode) {
            tfn = (TableFilterNode) currentRoot;
        } else {
            // The node is not a TableFilterNode, columns are going to be in default order
            return props;
        }

        propertiesMap.clear();
        /*
         * We load column index values into the properties map. If a property's
         * index is outside the range of the number of properties or the index
         * has already appeared as the position of another property, we put that
         * property at the end.
         */
        int offset = props.size();
        boolean noPreviousSettings = true;
        for (Property<?> prop : props) {
            Integer value = Integer.valueOf(NbPreferences.forModule(this.getClass()).get(getColumnPreferenceKey(prop, tfn.getColumnOrderKey()), "-1"));
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
            Integer[] keys = propertiesMap.keySet().toArray(new Integer[propertiesMap.keySet().size()]);
            for (int key : keys) {
                propertiesMap.put(key - props.size(), propertiesMap.get(key));
                propertiesMap.remove(key);
            }
        }

        return new ArrayList<>(propertiesMap.values());
    }

    /**
     * Gets a key for the current node and a property of its child nodes to
     * store the column position into a preference file.
     *
     * @param prop Property of the column
     * @param type The type of the current node
     * @return     A generated key for the preference file
     */
    private String getColumnPreferenceKey(Property<?> prop, String type) {
        return type.replaceAll("[^a-zA-Z0-9_]", "") + "."
                + prop.getName().replaceAll("[^a-zA-Z0-9_]", "") + ".column";
    }

    @Override
    public String getTitle() {
        return NbBundle.getMessage(this.getClass(), "DataResultViewerTable.title");
    }

    @Override
    public DataResultViewer createInstance() {
        return new DataResultViewerTable();
    }

    @Override
    public void clearComponent() {
        this.tableScrollPanel.removeAll();
        this.tableScrollPanel = null;

        super.clearComponent();
    }

    private class DummyNodeListener implements NodeListener {

        private volatile boolean load = true;

        public void reset() {
            load = true;
        }

        @Override
        public void childrenAdded(final NodeMemberEvent nme) {
            Node[] delta = nme.getDelta();
            if (load && containsReal(delta)) {
                load = false;
                if (SwingUtilities.isEventDispatchThread()) {
                    setupTable(nme.getNode());
                } else {
                    SwingUtilities.invokeLater(() -> {
                        setupTable(nme.getNode());
                    });
                }
            }
        }

        private boolean containsReal(Node[] delta) {
            for (Node n : delta) {
                if (!n.getDisplayName().equals(DUMMY_NODE_DISPLAY_NAME)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public void childrenRemoved(NodeMemberEvent nme) {
        }

        @Override
        public void childrenReordered(NodeReorderEvent nre) {
        }

        @Override
        public void nodeDestroyed(NodeEvent ne) {
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
        }
    }
}
