/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2014 Basis Technology Corp.
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

import java.awt.Cursor;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.dnd.DnDConstants;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.beans.PropertyChangeEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.swing.Action;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
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
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataResultViewer;

/**
 * DataResult sortable table viewer
 */
// @@@ Restore implementation of DataResultViewerTable as a DataResultViewer 
// service provider when DataResultViewers can be made compatible with node 
// multiple selection actions.
//@ServiceProvider(service = DataResultViewer.class)
public class DataResultViewerTable extends AbstractDataResultViewer {

    private String firstColumnLabel = NbBundle.getMessage(DataResultViewerTable.class, "DataResultViewerTable.firstColLbl");
    private Set<Property<?>> propertiesAcc = new LinkedHashSet<>();
    private final DummyNodeListener dummyNodeListener = new DummyNodeListener();
    private static final String DUMMY_NODE_DISPLAY_NAME = NbBundle.getMessage(DataResultViewerTable.class, "DataResultViewerTable.dummyNodeDisplayName");
    private Node currentRoot;

    /**
     * Creates a DataResultViewerTable object that is compatible with node
     * multiple selection actions.
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

        /*
         * TODO (AUT-1849): Correct or remove peristent column reordering code
         *
         * The following lines of code were added for this feature.
         */
//        ov.getOutline().getColumnModel().addColumnModelListener(new TableColumnModelListener() {
//            @Override
//            public void columnAdded(TableColumnModelEvent e) {}
//            @Override
//            public void columnRemoved(TableColumnModelEvent e) {}
//            @Override
//            public void columnMarginChanged(ChangeEvent e) {}
//            @Override
//            public void columnSelectionChanged(ListSelectionEvent e) {}
//
//            @Override
//            public void columnMoved(TableColumnModelEvent e) {
//                // change the order of the column in the array/hashset
//                List<Node.Property<?>> props = new ArrayList<>(propertiesAcc);
//                Node.Property<?> prop = props.remove(e.getFromIndex());
//                props.add(e.getToIndex(), prop);
//                
//                propertiesAcc.clear();
//                for (int j = 0; j < props.size(); ++j) {
//                    propertiesAcc.add(props.get(j));
//                }
//            }
//        });
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
     * Gets regular Bean property set properties from first child of Node.
     *
     * @param parent Node with at least one child to get properties from
     *
     * @return Properties,
     */
    private Node.Property<?>[] getChildPropertyHeaders(Node parent) {
        Node firstChild = parent.getChildren().getNodeAt(0);

        if (firstChild == null) {
            throw new IllegalArgumentException(
                    NbBundle.getMessage(this.getClass(), "DataResultViewerTable.illegalArgExc.noChildFromParent"));
        } else {
            for (PropertySet ps : firstChild.getPropertySets()) {
                if (ps.getName().equals(Sheet.PROPERTIES)) {
                    return ps.getProperties();
                }
            }

            throw new IllegalArgumentException(
                    NbBundle.getMessage(this.getClass(), "DataResultViewerTable.illegalArgExc.childWithoutPropertySet"));
        }
    }

    /**
     * Gets regular Bean property set properties from all first children and,
     * recursively, subchildren of Node. Note: won't work out the box for lazy
     * load - you need to set all children props for the parent by hand
     *
     * @param parent Node with at least one child to get properties from
     *
     * @return Properties,
     */
    @SuppressWarnings("rawtypes")
    private Node.Property[] getAllChildPropertyHeaders(Node parent) {
        Node firstChild = parent.getChildren().getNodeAt(0);

        Property[] properties = null;

        if (firstChild == null) {
            throw new IllegalArgumentException(
                    NbBundle.getMessage(this.getClass(), "DataResultViewerTable.illegalArgExc.noChildFromParent"));
        } else {
            Set<Property> allProperties = new LinkedHashSet<>();
            while (firstChild != null) {
                for (PropertySet ps : firstChild.getPropertySets()) {
                    final Property[] props = ps.getProperties();
                    final int propsNum = props.length;
                    for (int i = 0; i < propsNum; ++i) {
                        allProperties.add(props[i]);
                    }
                }
                firstChild = firstChild.getChildren().getNodeAt(0);
            }

            properties = allProperties.toArray(new Property<?>[0]);
        }
        return properties;

    }

    /**
     * Gets regular Bean property set properties from all children and,
     * recursively, subchildren of Node. Note: won't work out the box for lazy
     * load - you need to set all children props for the parent by hand
     *
     * @param parent Node with at least one child to get properties from
     * @param rows   max number of rows to retrieve properties for (can be used
     *               for memory optimization)
     */
    private void getAllChildPropertyHeadersRec(Node parent, int rows) {
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
            getAllChildPropertyHeadersRec(child, rows);
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
                final OutlineView ov = ((OutlineView) this.tableScrollPanel);
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

        /*
         * TODO (AUT-1849): Correct or remove peristent column reordering code
         *
         * The next three lines of code replaced the three lines of code that
         * follow
         */
//        storeState();        
        // set the new root as current
//        currentRoot = root;
//        List<Node.Property<?>> props = loadState();
        propertiesAcc.clear();
        DataResultViewerTable.this.getAllChildPropertyHeadersRec(root, 100);
        List<Node.Property<?>> props = new ArrayList<>(propertiesAcc);

        /*
         * OutlineView makes the first column be the result of
         * node.getDisplayName with the icon. This duplicates our first column,
         * which is the file name, etc. So, pop that property off the list, but
         * use its display name as the header for the column so that the header
         * can change depending on the type of data being displayed.
         *
         * NOTE: This assumes that the first property is always the one tha
         * duplicates getDisplayName(). This seems like a big assumption and
         * could be made more robust.
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
        int totalColumns = props.size();

        //int scrollWidth = ttv.getWidth();
        int margin = 4;
        int startColumn = 1;

        // If there is only one column (which was removed from props above)
        // Just let the table resize itself.
        ov.getOutline().setAutoResizeMode((props.size() > 0) ? JTable.AUTO_RESIZE_OFF : JTable.AUTO_RESIZE_ALL_COLUMNS);

        // get first 100 rows values for the table
        Object[][] content;
        content = getRowValues(root, 100);

        if (content != null) {
            // get the fontmetrics
            final Graphics graphics = ov.getGraphics();
            if (graphics != null) {
                final FontMetrics metrics = graphics.getFontMetrics();

                // for the "Name" column
                int nodeColWidth = Math.min(getMaxColumnWidth(0, metrics, margin, 40, firstColumnLabel, content), 250); // Note: 40 is the width of the icon + node lines. Change this value if those values change!
                ov.getOutline().getColumnModel().getColumn(0).setPreferredWidth(nodeColWidth);

                // get the max for each other column
                for (int colIndex = startColumn; colIndex <= totalColumns; colIndex++) {
                    int colWidth = Math.min(getMaxColumnWidth(colIndex, metrics, margin, 8, props, content), 350);
                    ov.getOutline().getColumnModel().getColumn(colIndex).setPreferredWidth(colWidth);
                }
            }

            // if there's no content just auto resize all columns
            if (content.length <= 0) {
                // turn on the auto resize
                ov.getOutline().setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
            }
        }
    }

    /*
     * TODO (AUT-1849): Correct or remove peristent column reordering code
     * 
     * The following three methods were added for this feature
     */
    // Store the state of current root Node.
//    private void storeState() {
//        if(currentRoot == null || propertiesAcc.isEmpty())
//            return;
//        
//        TableFilterNode tfn;
//        if(currentRoot instanceof TableFilterNode)
//            tfn = (TableFilterNode) currentRoot;
//        else
//            return;
//        
//        List<Node.Property<?>> props = new ArrayList<>(propertiesAcc);
//        for (int i = 0; i < props.size(); i++) {
//            Property<?> prop = props.get(i);
//            NbPreferences.forModule(this.getClass()).put(getUniqueColName(prop, tfn.getItemType()), String.valueOf(i));
//        }
//    }
    // Load the state of current root Node if exists. 
//    private List<Node.Property<?>> loadState() {
//        propertiesAcc.clear();
//        this.getAllChildPropertyHeadersRec(currentRoot, 100);
//        List<Node.Property<?>> props = new ArrayList<>(propertiesAcc);
//
//        // If node is not table filter node, use default order for columns
//        TableFilterNode tfn;
//        if (currentRoot instanceof TableFilterNode) {
//            tfn = (TableFilterNode) currentRoot;
//        } else {
//            Logger.getLogger(DataResultViewerTable.class.getName()).log(Level.INFO,
//                    "Node {0} is not TableFilterNode, columns are going to be in default order", currentRoot.getName());
//            return props;
//        }
//
//        List<Node.Property<?>> orderedProps = new ArrayList<>(propertiesAcc);
//        for (Property<?> prop : props) {
//            Integer value = Integer.valueOf(NbPreferences.forModule(this.getClass()).get(getUniqueColName(prop, tfn.getItemType()), "-1"));
//            if (value >= 0) {
//                /**
//                 * The original contents of orderedProps do not matter when
//                 * setting the new ordered values. The reason we copy
//                 * propertiesAcc into it first is to give it the currect size so
//                 * we can set() in any index.
//                 */
//                orderedProps.set(value, prop);
//            }
//        }
//        propertiesAcc.clear();
//        for (Property<?> prop : orderedProps) {
//            propertiesAcc.add(prop);
//        }
//        return orderedProps;
//    }
//
//    // Get unique name for node and it's property.
//    private String getUniqueColName(Property<?> prop, String type) {
//        return Case.getCurrentCase().getName() + "." + type + "."
//                + prop.getName().replaceAll("[^a-zA-Z0-9_]", "") + ".columnOrder";
//    }

    // Populate a two-dimensional array with rows of property values for up 
    // to maxRows children of the node passed in. 
    private static Object[][] getRowValues(Node node, int maxRows) {
        int numRows = Math.min(maxRows, node.getChildren().getNodesCount());
        Object[][] rowValues = new Object[numRows][];
        int rowCount = 0;
        for (Node child : node.getChildren().getNodes()) {
            if (rowCount >= maxRows) {
                break;
            }
            // BC: I got this once, I think it was because the table
            // refreshed while we were in this method 
            // could be better synchronized.  Or it was from 
            // the lazy nodes updating...  Didn't have time 
            // to fully debug it. 
            if (rowCount > numRows) {
                break;
            }
            PropertySet[] propertySets = child.getPropertySets();
            if (propertySets.length > 0) {
                Property<?>[] properties = propertySets[0].getProperties();
                rowValues[rowCount] = new Object[properties.length];
                for (int j = 0; j < properties.length; ++j) {
                    try {
                        rowValues[rowCount][j] = properties[j].getValue();
                    } catch (IllegalAccessException | InvocationTargetException ignore) {
                        rowValues[rowCount][j] = "n/a"; //NON-NLS
                    }
                }
            }
            ++rowCount;
        }
        return rowValues;
    }

    @Override
    public String getTitle() {
        return NbBundle.getMessage(this.getClass(), "DataResultViewerTable.title");
    }

    @Override
    public DataResultViewer createInstance() {
        return new DataResultViewerTable();
    }

    /**
     * Gets the max width of the column from the given index, header, and table.
     *
     * @param index   the index of the column on the table / header
     * @param metrics the font metrics that this component use
     * @param margin  the left/right margin of the column
     * @param padding the left/right padding of the column
     * @param header  the property headers of the table
     * @param table   the object table
     *
     * @return max the maximum width of the column
     */
    @SuppressWarnings("rawtypes")
    private int getMaxColumnWidth(int index, FontMetrics metrics, int margin, int padding, List<Node.Property<?>> header, Object[][] table) {
        // set the tree (the node / names column) width
        String headerName = header.get(index - 1).getDisplayName();

        return getMaxColumnWidth(index, metrics, margin, padding, headerName, table);
    }

    /**
     * Gets the max width of the column from the given index, header, and table.
     *
     * @param index   the index of the column on the table / header
     * @param metrics the font metrics that this component use
     * @param margin  the left/right margin of the column
     * @param padding the left/right padding of the column
     * @param header  the column header for the comparison
     * @param table   the object table
     *
     * @return max the maximum width of the column
     */
    private synchronized int getMaxColumnWidth(int index, FontMetrics metrics, int margin, int padding, String header, Object[][] table) {
        // set the tree (the node / names column) width
        String headerName = header;
        int headerWidth = metrics.stringWidth(headerName); // length of the header
        int colWidth = 0;

        // Get maximum width of column data
        for (int i = 0; i < table.length; i++) {
            if (table[i] == null || index >= table[i].length) {
                continue;
            }
            String test = table[i][index].toString();
            colWidth = Math.max(colWidth, metrics.stringWidth(test));
        }

        colWidth += padding; // add the padding on the most left gap
        headerWidth += 8; // add the padding to the header (change this value if the header padding value is changed)

        // Set the width
        int width = Math.max(headerWidth, colWidth);
        width += 2 * margin; // Add margin

        return width;
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
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            setupTable(nme.getNode());
                        }
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
