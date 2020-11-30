/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
 * contact: carrier <at> sleuthkit <dot> org
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
package org.sleuthkit.autopsy.geolocation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * A k-d tree (short for k-dimensional tree) is a space-partitioning data
 * structure for organizing points in a k-dimensional space. k-d trees are a
 * useful data structure for several applications, such as searches involving a
 * multidimensional search key (e.g. range searches and nearest neighbor
 * searches). k-d trees are a special case of binary space partitioning trees.
 * <p>
 * @see <a href="https://en.wikipedia.org/wiki/K-d_tree">K-D Tree
 * (Wikipedia)</a>
 * <br>
 *
 * Original other was JustinWetherell <phishman3579@gmail.com>.
 *
 * @see
 * <a href="https://github.com/phishman3579/java-algorithms-implementation/blob/master/src/com/jwetherell/algorithms/data_structures/KdTree.java">Original
 * version</a>
 *
 */
public class KdTree<T extends KdTree.XYZPoint> implements Iterable<T> {

    // The code generally supports the idea of a third dimension, but for 
    // simplicity, the code will only use 2 dimensions.  
    private static final int DIMENSIONS = 2;
    private KdNode root = null;

    private static final double EARTH_RADIUS = 6371e3;

    /**
     * Compares two XYZPoints by their X value.
     */
    private static final Comparator<XYZPoint> X_COMPARATOR = new Comparator<XYZPoint>() {
        /**
         * {@inheritDoc}
         */
        @Override
        public int compare(XYZPoint o1, XYZPoint o2) {
            return Double.compare(o1.x, o2.x);
        }
    };

    /**
     * Compares two XYZPoints by their Y value.
     */
    private static final Comparator<XYZPoint> Y_COMPARATOR = new Comparator<XYZPoint>() {
        /**
         * {@inheritDoc}
         */
        @Override
        public int compare(XYZPoint o1, XYZPoint o2) {
            return Double.compare(o1.y, o2.y);
        }
    };

    /**
     * Compares two XYZPoints by their Z value.
     */
    private static final Comparator<XYZPoint> Z_COMPARATOR = new Comparator<XYZPoint>() {
        /**
         * {@inheritDoc}
         */
        @Override
        public int compare(XYZPoint o1, XYZPoint o2) {
            return Double.compare(o1.z, o2.z);
        }
    };

    /**
     * Main constructor.
     */
    public KdTree() {
    }

    /**
     * Constructor that creates a balanced tree with the provided nodes.
     *
     * @param points The points to add and balance.
     */
    public KdTree(List<T> points) {
        this.root = getBalancedNode(null, points, 0);
    }

    static final int X_AXIS = 0;
    static final int Y_AXIS = 1;
    static final int Z_AXIS = 2;

    public KdNode getRoot() {
        return root;
    }

    /**
     * Recursively creates balanced KdNode's from the given list. NOTE: The
     * approach is to: 1) sort the list based on the depth's comparator 2) find
     * a center point 3) For lesser and greater, recurse until base case.
     *
     * There may be more efficient means of achieving balanced nodes.
     *
     * @param parent The parent of this node or null if this will be root.
     * @param points The points to be balanced in the tree.
     * @param depth The current depth (used to determine axis to sort on).
     * @return The balanced KdNode.
     */
    private KdNode getBalancedNode(KdNode parent, List<T> points, int depth) {
        // if no points, return null.
        if (points == null || points.size() < 1) {
            return null;
        }

        // sort with comparator for depth
        points.sort((a, b) -> KdNode.compareTo(depth, a, b));

        // find center point
        int centerPtIdx = points.size() / 2;
        KdNode thisNode = new KdNode(points.get(centerPtIdx), depth, parent);

        // recurse on lesser and greater
        List<T> lesserList = centerPtIdx > 0 ? points.subList(0, centerPtIdx) : null;
        thisNode.setLesser(getBalancedNode(thisNode, lesserList, depth + 1));
        List<T> greaterList = centerPtIdx < points.size() - 1 ? points.subList(centerPtIdx + 1, points.size()) : null;
        thisNode.setGreater(getBalancedNode(thisNode, greaterList, depth + 1));

        return thisNode;
    }

    /**
     * Adds value to the tree. Tree can contain multiple equal values.
     *
     * @param value T to add to the tree.
     *
     * @return True if successfully added to tree.
     */
    public boolean add(T value) {
        if (value == null) {
            return false;
        }

        if (root == null) {
            root = new KdNode(value, 0, null);
            return true;
        }

        KdNode node = root;
        while (true) {
            if (KdNode.compareTo(node.getDepth(), value, node.getPoint()) <= 0) {
                // Lesser
                if (node.getLesser() == null) {
                    KdNode newNode = new KdNode(value, node.getDepth() + 1, node);
                    node.setLesser(newNode);
                    break;
                }
                node = node.getLesser();
            } else {
                // Greater
                if (node.getGreater() == null) {
                    KdNode newNode = new KdNode(value, node.getDepth() + 1, node);
                    node.setGreater(newNode);
                    break;
                }
                node = node.getGreater();
            }
        }

        return true;
    }

    /**
     * Does the tree contain the value.
     *
     * @param value T to locate in the tree.
     *
     * @return True if tree contains value.
     */
    public boolean contains(T value) {
        if (value == null || root == null) {
            return false;
        }

        KdNode node = getNode(this, value);
        return (node != null);
    }

    /**
     * Locates T in the tree.
     *
     * @param tree to search.
     * @param value to search for.
     *
     * @return KdNode or NULL if not found
     */
    private <T extends KdTree.XYZPoint> KdNode getNode(KdTree<T> tree, T value) {
        if (tree == null || tree.getRoot() == null || value == null) {
            return null;
        }

        KdNode node = tree.getRoot();
        while (true) {
            if (node.getPoint().equals(value)) {
                return node;
            } else if (KdNode.compareTo(node.getDepth(), value, node.getPoint()) <= 0) {
                // Lesser
                if (node.getLesser() == null) {
                    return null;
                }
                node = node.getLesser();
            } else {
                // Greater
                if (node.getGreater() == null) {
                    return null;
                }
                node = node.getGreater();
            }
        }
    }

    /**
     * Searches for numNeighbors nearest neighbor.
     *
     * @param numNeighbors Number of neighbors to retrieve. Can return more than
     * numNeighbors, if last nodes are equal distances.
     * @param value to find neighbors of.
     *
     * @return Collection of T neighbors.
     */
    @SuppressWarnings("unchecked")
    public Collection<T> nearestNeighbourSearch(int numNeighbors, T value) {
        if (value == null || root == null) {
            return Collections.EMPTY_LIST;
        }

        // Map used for results
        TreeSet<KdNode> results = new TreeSet<>(new EuclideanComparator(value));

        // Find the closest leaf node
        KdNode prev = null;
        KdNode node = root;
        while (node != null) {
            if (KdNode.compareTo(node.getDepth(), value, node.getPoint()) <= 0) {
                // Lesser
                prev = node;
                node = node.getLesser();
            } else {
                // Greater
                prev = node;
                node = node.getGreater();
            }
        }
        KdNode leaf = prev;

        if (leaf != null) {
            // Used to not re-examine nodes
            Set<KdNode> examined = new HashSet<>();

            // Go up the tree, looking for better solutions
            node = leaf;
            while (node != null) {
                // Search node
                searchNode(value, node, numNeighbors, results, examined);
                node = node.getParent();
            }
        }

        // Load up the collection of the results
        Collection<T> collection = new ArrayList<>(numNeighbors);
        for (KdNode kdNode : results) {
            collection.add((T) kdNode.getPoint());
        }
        return collection;
    }

    /**
     * Searches the tree to find any nodes that are closer than what is
     * currently in results.
     *
     * @param value Nearest value search point
     * @param node Search starting node
     * @param numNeighbors Number of nearest neighbors to return
     * @param results Current result set
     * @param examined List of examined nodes
     */
    private <T extends KdTree.XYZPoint> void searchNode(T value, KdNode node, int numNeighbors, TreeSet<KdNode> results, Set<KdNode> examined) {
        examined.add(node);

        // Search node
        KdNode lastNode;
        Double lastDistance = Double.MAX_VALUE;
        if (results.size() > 0) {
            lastNode = results.last();
            lastDistance = lastNode.getPoint().euclideanDistance(value);
        }
        Double nodeDistance = node.getPoint().euclideanDistance(value);
        if (nodeDistance.compareTo(lastDistance) < 0) {
            results.add(node);
        } else if (nodeDistance.equals(lastDistance)) {
            results.add(node);
        } else if (results.size() < numNeighbors) {
            results.add(node);
        }
        lastNode = results.last();
        lastDistance = lastNode.getPoint().euclideanDistance(value);

        int axis = node.getDepth() % DIMENSIONS;
        KdNode lesser = node.getLesser();
        KdNode greater = node.getGreater();

        // Search children branches, if axis aligned distance is less than
        // current distance
        if (lesser != null && !examined.contains(lesser)) {
            examined.add(lesser);

            double nodePoint;
            double valuePlusDistance;
            switch (axis) {
                case X_AXIS:
                    nodePoint = node.getPoint().x;
                    valuePlusDistance = value.x - lastDistance;
                    break;
                case Y_AXIS:
                    nodePoint = node.getPoint().y;
                    valuePlusDistance = value.y - lastDistance;
                    break;
                default: // Z_AXIS
                    nodePoint = node.getPoint().z;
                    valuePlusDistance = value.z - lastDistance;
                    break;
            }
            boolean lineIntersectsCube = valuePlusDistance <= nodePoint;

            // Continue down lesser branch
            if (lineIntersectsCube) {
                searchNode(value, lesser, numNeighbors, results, examined);
            }
        }
        if (greater != null && !examined.contains(greater)) {
            examined.add(greater);

            double nodePoint;
            double valuePlusDistance;
            switch (axis) {
                case X_AXIS:
                    nodePoint = node.getPoint().x;
                    valuePlusDistance = value.x + lastDistance;
                    break;
                case Y_AXIS:
                    nodePoint = node.getPoint().y;
                    valuePlusDistance = value.y + lastDistance;
                    break;
                default: //Z_AXIS
                    nodePoint = node.getPoint().z;
                    valuePlusDistance = value.z + lastDistance;
                    break;
            }
            boolean lineIntersectsCube = valuePlusDistance >= nodePoint;

            // Continue down greater branch
            if (lineIntersectsCube) {
                searchNode(value, greater, numNeighbors, results, examined);
            }
        }
    }

    /**
     * Adds, in a specified queue, a given node and its related nodes (lesser,
     * greater).
     *
     * @param node Node to check. May be null.
     *
     * @param results Queue containing all found entries. Must not be null.
     */
    @SuppressWarnings("unchecked")
    private <T extends XYZPoint> void search(final KdNode node, final Deque<T> results) {
        if (node != null) {
            results.add((T) node.getPoint());
            search(node.getGreater(), results);
            search(node.getLesser(), results);
        }
    }

    /**
     * A comparator class for doing a euclidean distance comparison of two
     * KdNodes.
     */
    private final class EuclideanComparator implements Comparator<KdNode> {

        private final XYZPoint point;

        EuclideanComparator(XYZPoint point) {
            this.point = point;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int compare(KdNode o1, KdNode o2) {
            Double d1 = point.euclideanDistance(o1.getPoint());
            Double d2 = point.euclideanDistance(o2.getPoint());
            if (d1.compareTo(d2) < 0) {
                return -1;
            } else if (d2.compareTo(d1) < 0) {
                return 1;
            }
            return o1.getPoint().compareTo(o2.getPoint());
        }
    }

    /**
     * Searches all entries from the first to the last entry.
     *
     * @return Iterator allowing to iterate through a collection containing all
     * found entries.
     */
    @Override
    public Iterator<T> iterator() {
        final Deque<T> results = new ArrayDeque<>();
        search(root, results);
        return results.iterator();
    }

    /**
     * Searches all entries from the last to the first entry.
     *
     * @return Iterator allowing to iterate through a collection containing all
     * found entries.
     */
    public Iterator<T> reverse_iterator() {
        final Deque<T> results = new ArrayDeque<>();
        search(root, results);
        return results.descendingIterator();
    }

    /**
     * A node in the KdTree. Each node has a parent node (unless the node is the
     * root) and a lesser and greater child node.
     *
     * The child nodes are set to "lesser" or "greater" depending on a
     * comparison between the nodes XYZPoint and the child's XYZPoint.
     */
    public static class KdNode implements Comparable<KdNode> {

        private final XYZPoint point;
        private final int depth;
        private final KdNode parent;

        private KdNode lesser = null;
        private KdNode greater = null;

        /**
         * Constructs a new KdNode.
         *
         * @param point Node point
         * @param depth Depth of node in the tree, set to 0 if root node
         * @param parent Parent of this node, can be null if root node
         */
        public KdNode(XYZPoint point, int depth, KdNode parent) {
            this.point = point;
            this.depth = depth;
            this.parent = parent;
        }

        /**
         * Compares two XYZPoints. The value used for the comparision is based
         * on the depth of the node in the tree and the tree's dimension.
         *
         * @param depth Depth of node in the tree
         * @param point1 First point to compare
         * @param point2 Second point to compare
         *
         * @return 0 if points are equal -1 if point2 is "less than" point1 1 if
         * point1 is "greater than" point2
         */
        public static int compareTo(int depth, XYZPoint point1, XYZPoint point2) {
            int axis = depth % DIMENSIONS;
            switch (axis) {
                case X_AXIS:
                    return X_COMPARATOR.compare(point1, point2);
                case Y_AXIS:
                    return Y_COMPARATOR.compare(point1, point2);
                default:
                    return Z_COMPARATOR.compare(point1, point2);
            }
        }

        /**
         * Returns the nodes depth in the kdtree.
         *
         * @return Node depth.
         */
        int getDepth() {
            return depth;
        }

        /**
         * Returns the parent of this node. If parent is null, the node is the
         * tree root node.
         *
         * @return Returns the parent of this node, or null if node is tree
         * root.
         */
        KdNode getParent() {
            return parent;
        }

        /**
         * Sets the lesser child of this node.
         *
         * @param node lesser Child node
         */
        void setLesser(KdNode node) {
            lesser = node;
        }

        /**
         * Returns the nodes lesser child node.
         *
         * @return Returns KdNode or null if one was not set.
         */
        KdNode getLesser() {
            return lesser;
        }

        /**
         * Sets the greater child of this node.
         *
         * @param node
         */
        void setGreater(KdNode node) {
            greater = node;
        }

        /**
         * Returns the nodes lesser child node.
         *
         * @return Returns KdNode or null if one was not set.
         */
        KdNode getGreater() {
            return greater;
        }

        /**
         * Returns the XYZ point of this node which contains the longitude and
         * latitude values.
         *
         * @return XYZPoint
         */
        XYZPoint getPoint() {
            return point;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            return 31 * (DIMENSIONS + this.depth + this.getPoint().hashCode());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (!(obj instanceof KdNode)) {
                return false;
            }

            KdNode kdNode = (KdNode) obj;
            return (this.compareTo(kdNode) == 0);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int compareTo(KdNode o) {
            return compareTo(depth, this.getPoint(), o.getPoint());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder(200);
            builder.append("dimensions=").append(DIMENSIONS).append(" depth=").append(depth).append(" point=").append(getPoint().toString());
            return builder.toString();
        }
    }

    /**
     * An XYZPoint is a representation of a three dimensional point.
     *
     * Z value will always been 0 when using latitude and longitude values.
     */
    public static class XYZPoint implements Comparable<XYZPoint> {

        protected final double x;
        protected final double y;
        protected final double z;

        /**
         * Constructs a new XYZPoint.
         *
         * @param x
         * @param y
         */
        public XYZPoint(Double x, Double y) {
            this.x = x;
            this.y = y;
            z = 0;
        }

        /**
         * Returns the x(latitude) value for the point.
         *
         * @return x(latitude) value for the point
         */
        public double getX() {
            return x;
        }

        /**
         * Returns the y/longitude value for the point.
         *
         * @return Longitude for point
         */
        public double getY() {
            return y;
        }

        /**
         * Returns the z value, will always be 0.
         *
         * @return Always 0.
         */
        public double getZ() {
            return z;
        }

        /**
         * Computes the Euclidean distance from this point to the other.
         *
         * @param o1 other point.
         *
         * @return euclidean distance.
         */
        public double euclideanDistance(XYZPoint o1) {
            return euclideanDistance(o1, this);
        }

        /**
         * Computes the Euclidean distance from one point to the other.
         *
         * Source for the distance calculation:
         * https://www.movable-type.co.uk/scripts/latlong.html
         *
         * @param o1 first point.
         * @param o2 second point.
         *
         * @return euclidean distance.
         */
        private static double euclideanDistance(XYZPoint o1, XYZPoint o2) {
            if (o1.equals(o2)) {
                return 0;
            }

            double lat1Radians = Math.toRadians(o1.getX());
            double lat2Radians = Math.toRadians(o2.getX());

            double deltaLatRadians = Math.toRadians(o2.getX() - o1.getX());
            double deltaLongRadians = Math.toRadians(o2.getY() - o1.getY());

            double a = Math.sin(deltaLatRadians / 2) * Math.sin(deltaLatRadians / 2)
                    + Math.cos(lat1Radians) * Math.cos(lat2Radians)
                    * Math.sin(deltaLongRadians / 2) * Math.sin(deltaLongRadians / 2);

            double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

            return EARTH_RADIUS * c;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            return 31 * (int) (this.x + this.y + this.z);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }

            if (!(obj instanceof XYZPoint)) {
                return false;
            }

            XYZPoint xyzPoint = (XYZPoint) obj;

            return ((Double.compare(this.x, xyzPoint.x) == 0)
                    && (Double.compare(this.y, xyzPoint.y) == 0)
                    && (Double.compare(this.z, xyzPoint.z) == 0));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int compareTo(XYZPoint o) {
            int xComp = X_COMPARATOR.compare(this, o);
            if (xComp != 0) {
                return xComp;
            }

            int yComp = Y_COMPARATOR.compare(this, o);
            if (yComp != 0) {
                return yComp;
            }
            return Z_COMPARATOR.compare(this, o);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder(200);
            builder.append("(");
            builder.append(x).append(", ");
            builder.append(y).append(", ");
            builder.append(z);
            builder.append(")");
            return builder.toString();
        }
    }
}
