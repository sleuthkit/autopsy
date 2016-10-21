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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
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
import org.openide.nodes.Sheet;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataResultViewer;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * DataResult sortable table viewer
 */
// @@@ Restore implementation of DataResultViewerTable as a DataResultViewer 
// service provider when DataResultViewers can be made compatible with node 
// multiple selection actions.
//@ServiceProvider(service = DataResultViewer.class)
public class DataResultViewerTable extends AbstractDataResultViewer {

    private final String firstColumnLabel = NbBundle.getMessage(DataResultViewerTable.class, "DataResultViewerTable.firstColLbl");
    // This is a set because we add properties of up to 100 child nodes, and we want unique properties
    private final Set<Property<?>> propertiesAcc = new LinkedHashSet<>();
    private final DummyNodeListener dummyNodeListener = new DummyNodeListener();
    private static final String DUMMY_NODE_DISPLAY_NAME = NbBundle.getMessage(DataResultViewerTable.class, "DataResultViewerTable.dummyNodeDisplayName");
    private static final Color TAGGED_COLOR = new Color(230, 235, 240);
    private Node currentRoot;
    // The following two variables keep track of whether the user is trying
    // to move the first column, which is not allowed.
    private int oldColumnIndex = -1;
    private int newColumnIndex = -1;

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
                // to keep track of attempts to move the first column
                if (oldColumnIndex == -1) {
                    oldColumnIndex = fromIndex;
                }
                newColumnIndex = toIndex;

                List<Node.Property<?>> props = new ArrayList<>(propertiesAcc);
                Node.Property<?> prop = props.remove(fromIndex);
                props.add(toIndex, prop);
                propertiesAcc.clear();
                for (int j = 0; j < props.size(); ++j) {
                    propertiesAcc.add(props.get(j));
                }
                storeState();
            }
        });

        // add a listener to move columns back if user tries to move the first column out of place
        ov.getOutline().getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if (oldColumnIndex != -1 && (oldColumnIndex == 0 || newColumnIndex == 0)) {
                    ov.getOutline().moveColumn(newColumnIndex, oldColumnIndex);
                }
                oldColumnIndex = -1;
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
        final OutlineView ov = ((OutlineView) this.tableScrollPanel);
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

        // get first row's values for the table
        Object[][] content;
        content = getRowValues(root, 1);

        if (content != null) {

            final Graphics graphics = ov.getGraphics();
            if (graphics != null) {
                final FontMetrics metrics = graphics.getFontMetrics();

                int margin = 4;
                int padding = 8;

                for (int column = 0; column < ov.getOutline().getModel().getColumnCount(); column++) {
                    int firstColumnPadding = (column == 0) ? 32 : 0;
                    int columnWidthLimit = (column == 0) ? 250 : 300;
                    int valuesWidth = 0;

                    // find the maximum width needed to fit the values for the first 30 rows, at most
                    for (int row = 0; row < Math.min(30, ov.getOutline().getRowCount()); row++) {
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

                // if there's no content just auto resize all columns
                if (content.length <= 0) {
                    // turn on the auto resize
                    ov.getOutline().setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
                }
            }

            /**
             * This custom renderer extends the renderer that was already being
             * used by the outline table. This renderer colors a row if the file
             * or artifact associated with the row's node is tagged.
             */
            class ColorTagCustomRenderer extends DefaultOutlineCellRenderer {
                private static final long serialVersionUID = 1L;
                @Override
                public Component getTableCellRendererComponent(JTable table,
                        Object value, boolean isSelected, boolean hasFocus, int row, int col) {

                    Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
                    if (!isSelected) {
                        Node node = currentRoot.getChildren().getNodeAt(table.convertRowIndexToModel(row));
                        boolean tagFound = false;
                        if (node != null) {
                            //see if there is a blackboard artifact at the node and whether the artifact is tagged
                            BlackboardArtifact artifact = node.getLookup().lookup(BlackboardArtifact.class);
                            if (artifact != null) {
                                try {
                                    tagFound = !Case.getCurrentCase().getServices().getTagsManager().getBlackboardArtifactTagsByArtifact(artifact).isEmpty();
                                } catch (TskCoreException ex) {
                                    Exceptions.printStackTrace(ex);
                                }
                            }

                            //if no tags have been found yet, see if the abstract file at the node is tagged
                            if (!tagFound) {
                                AbstractFile abstractFile = node.getLookup().lookup(AbstractFile.class);
                                if (abstractFile != null) {
                                    try {
                                        tagFound = !Case.getCurrentCase().getServices().getTagsManager().getContentTagsByContent(abstractFile).isEmpty();
                                    } catch (TskCoreException ex) {
                                        Exceptions.printStackTrace(ex);
                                    }
                                }
                            }

                            //if the node does have associated tags, set its background color
                            if (tagFound) {
                                component.setBackground(TAGGED_COLOR);
                            }
                        }
                    }
                    return component;
                }
            }
            ov.getOutline().setDefaultRenderer(Object.class, new ColorTagCustomRenderer());
        }
    }
    
    /**
     * Store the current column order into a preference file.
     */
    private synchronized void storeState() {
        if (currentRoot == null || propertiesAcc.isEmpty()) {
            return;
        }

        TableFilterNode tfn;
        if (currentRoot instanceof TableFilterNode) {
            tfn = (TableFilterNode) currentRoot;
        } else {
            return;
        }

        // Store the current order of the columns into settings
        List<Node.Property<?>> props = new ArrayList<>(propertiesAcc);
        for (int i = 0; i < props.size(); i++) {
            Property<?> prop = props.get(i);
            NbPreferences.forModule(this.getClass()).put(getPreferenceKey(prop, tfn.getItemType()), String.valueOf(i));
        }
    }

    /**
     * Loads the stored column order from the preference file.
     *
     * @return a List<Node.Property<?>> of the preferences in order
     */
    private synchronized List<Node.Property<?>> loadState() {
        propertiesAcc.clear();
        this.getAllChildPropertyHeadersRec(currentRoot, 100);
        List<Node.Property<?>> props = new ArrayList<>(propertiesAcc);

        // If node is not table filter node, use default order for columns
        TableFilterNode tfn;
        if (currentRoot instanceof TableFilterNode) {
            tfn = (TableFilterNode) currentRoot;
        } else {
            Logger.getLogger(DataResultViewerTable.class.getName()).log(Level.INFO,
                    "Node {0} is not a TableFilterNode, columns are going to be in default order", currentRoot.getName());
            return props;
        }

        /*
         * Use a map instead of a list and replacing its values by index to
         * avoid index out of bounds errors
         */
        Map<Integer, Node.Property<?>> propsFromPreferences = new TreeMap<>();
        int offset = props.size();
        for (Property<?> prop : props) {
            Integer value = Integer.valueOf(NbPreferences.forModule(this.getClass()).get(getPreferenceKey(prop, tfn.getItemType()), "-1"));
            if (value >= 0) {
                propsFromPreferences.put(value, prop);
            } else {
                propsFromPreferences.put(offset, prop);
                offset++;
            }
        }

        propertiesAcc.clear();
        for (Property<?> prop : propsFromPreferences.values()) {
            propertiesAcc.add(prop);
        }
        return new ArrayList<>(propsFromPreferences.values());
    }

    /**
     * Gets a key for the current node and a property of its child nodes to
     * store the column position into a preference file.
     *
     * @param prop Property of the column
     * @param type The type of the current node
     * @return     A generated key for the preference file
     */
    private String getPreferenceKey(Property<?> prop, String type) {
        return type.replaceAll("[^a-zA-Z0-9_]", "") + "."
                + prop.getName().replaceAll("[^a-zA-Z0-9_]", "") + ".column";
    }

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
