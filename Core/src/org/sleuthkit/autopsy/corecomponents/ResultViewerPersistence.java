/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.corecomponents;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.prefs.Preferences;
import javax.swing.SortOrder;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.NbPreferences;

final public class ResultViewerPersistence {

    private ResultViewerPersistence() {
    }

    /**
     * Gets a key for the current node and a property of its child nodes to
     * store the column position into a preference file.
     *
     *
     * @return A generated key for the preference file
     */
    static String getColumnPositionKey(TableFilterNode node, String propName) {
        return getColumnKeyBase(node, propName) + ".column";
    }

    static String getColumnSortOrderKey(TableFilterNode node, String propName) {
        return getColumnKeyBase(node, propName) + ".sortOrder";
    }

    static String getColumnSortRankKey(TableFilterNode node, String propName) {
        return getColumnKeyBase(node, propName) + ".sortRank";
    }

    private static String getColumnKeyBase(TableFilterNode node, String propName) {
        return stripNonAlphanumeric(node.getColumnOrderKey()) + "." + stripNonAlphanumeric(propName);
    }

    private static String stripNonAlphanumeric(String str) {
        return str.replaceAll("[^a-zA-Z0-9_]", "");
    }

    static List<Node.Property<?>> getAllChildProperties(Node Node, int maxRows) {
        // This is a set because we add properties of up to 100 child nodes, and we want unique properties
        Set<Node.Property<?>> propertiesAcc = new LinkedHashSet<>();
        getAllChildPropertyHeadersRec(Node, maxRows, propertiesAcc);
        return new ArrayList<>(propertiesAcc);
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
    static private void getAllChildPropertyHeadersRec(Node parent, int rows, Set<Node.Property<?>> propertiesAcc) {
        Children children = parent.getChildren();
        int childCount = 0;
        for (Node child : children.getNodes()) {
            if (++childCount > rows) {
                return;
            }
            for (Node.PropertySet ps : child.getPropertySets()) {
                final Node.Property<?>[] props = ps.getProperties();
                final int propsNum = props.length;
                for (int j = 0; j < propsNum; ++j) {
                    propertiesAcc.add(props[j]);
                }
            }
            getAllChildPropertyHeadersRec(child, rows, propertiesAcc);
        }
    }

    /**
     *
     * @param node the value of node
     */
    static java.util.SortedMap<Integer, SortCriterion> loadCriteria(TableFilterNode node) {
        List<Node.Property<?>> availableProperties = ResultViewerPersistence.getAllChildProperties(node, 100);
        final Preferences preferences = NbPreferences.forModule(DataResultViewerTable.class);
        java.util.SortedMap<Integer, SortCriterion> criteriaMap = new TreeMap<>();
        availableProperties.forEach(prop -> {
            //if the sort rank is undefined, it will be defaulted to 0 => unsorted.
            Integer sortRank = Integer.valueOf(preferences.get(ResultViewerPersistence.getColumnSortRankKey(node, prop.getName()), "0"));

            if (sortRank != 0) {
                Boolean sortOrder = Boolean.valueOf(preferences.get(ResultViewerPersistence.getColumnSortOrderKey(node, prop.getName()), "true"));

                final SortCriterion sortCriterion = new SortCriterion(prop, sortOrder
                        ? SortOrder.ASCENDING
                        : SortOrder.DESCENDING);
                criteriaMap.put(sortRank, sortCriterion);
            }
        });
        return criteriaMap;
    }

    
    
    static class SortCriterion {

        private final Node.Property<?> prop;
        private final SortOrder order;

        Node.Property<?> getProp() {
            return prop;
        }

        SortOrder getOrder() {
            return order;
        }

        SortCriterion(Node.Property<?> prop, SortOrder order) {
            this.prop = prop;
            this.order = order;
        }
    }
}
