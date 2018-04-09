/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018 Basis Technology Corp.
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

import com.google.common.collect.ImmutableSet;
import com.google.common.eventbus.EventBus;
import com.mxgraph.layout.mxGraphLayout;
import com.mxgraph.model.mxCell;
import com.mxgraph.util.mxPoint;
import com.mxgraph.util.mxRectangle;
import com.mxgraph.view.mxGraph;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Model of which vertices in a graph are locked ( not moveable by layout
 * algorithms).
 *
 */
final class LockedVertexModel {

    private final EventBus eventBus = new EventBus();

    /**
     * Set of mxCells (vertices) that are 'locked'. Locked vertices are not
     * affected by layout algorithms, but may be repositioned manually by the
     * user.
     */
    private final Set<mxCell> lockedVertices = new HashSet<>();

    void registerhandler(Object handler) {
        eventBus.register(handler);
    }

    void unregisterhandler(Object handler) {
        eventBus.unregister(handler);
    }

    /**
     * Lock the given vertices so that applying a layout algorithm doesn't move
     * them. The user can still manually position the vertices.
     *
     * @param vertex The vertex to lock.
     */
    void lock(Collection<mxCell> vertices) {
        lockedVertices.addAll(vertices);
        eventBus.post(new VertexLockEvent(true, vertices));
    }

    /**
     * Unlock the given vertices so that applying a layout algorithm can move
     * them.
     *
     * @param vertex The vertex to unlock.
     */
    void unlock(Collection<mxCell> vertices) {
        lockedVertices.removeAll(vertices);
        eventBus.post(new VertexLockEvent(false, vertices));
    }

    boolean isVertexLocked(mxCell vertex) {
        return lockedVertices.contains(vertex);

    }

    void clear() {
        lockedVertices.clear();
    }

    /**
     * Event that represents a change in the locked state of one or more
     * vertices.
     */
    final static class VertexLockEvent {

        private final boolean locked;
        private final Set<mxCell> vertices;

        /**
         * @return The vertices whose locked state has changed.
         */
        public Set<mxCell> getVertices() {
            return vertices;
        }

        /**
         * @return True if the vertices are locked, False if the vertices are
         *         unlocked.
         */
        public boolean isLocked() {
            return locked;
        }

        VertexLockEvent(boolean locked, Collection< mxCell> vertices) {
            this.vertices = ImmutableSet.copyOf(vertices);
            this.locked = locked;
        }
    }

    mxGraphLayout createLockedVertexWrapper(mxGraphLayout layout) {
        return new LockedVertexLayoutWrapper(layout, this);
    }

    /** An mxGraphLayout that wrapps an other layout and ignores locked vertes.
     *
     * @param <L>
     */
    private static final class LockedVertexLayoutWrapper extends mxGraphLayout {

        private final mxGraphLayout wrappedLayout;
        private final LockedVertexModel lockedVertexModel;

        /**
         *
         *
         * @param layout            The layout to wrap
         * @param lockedVertexModel the value of lockedVertexModel2
         */
        private LockedVertexLayoutWrapper(mxGraphLayout layout, LockedVertexModel lockedVertexModel) {
            super(layout.getGraph());
            this.lockedVertexModel = lockedVertexModel;
            wrappedLayout = layout;
        }

        @Override
        public boolean isVertexIgnored(Object vertex) {
            return wrappedLayout.isVertexIgnored(vertex)
                    || lockedVertexModel.isVertexLocked((mxCell) vertex);
        }

        @Override
        public mxRectangle setVertexLocation(Object vertex, double xCoord, double yCoord) {
            if (isVertexIgnored(vertex)) {
                return getVertexBounds(vertex);
            } else {
                return wrappedLayout.setVertexLocation(vertex, xCoord, yCoord);
            }
        }

        @Override
        public void execute(Object parent) {
            wrappedLayout.execute(parent);
        }

        @Override
        public void moveCell(Object cell, double xCoord, double yCoord) {
            wrappedLayout.moveCell(cell, xCoord, yCoord);
        }

        @Override
        public mxGraph getGraph() {
            return wrappedLayout.getGraph();
        }

        @Override
        public Object getConstraint(Object key, Object cell) {
            return wrappedLayout.getConstraint(key, cell);
        }

        @Override
        public Object getConstraint(Object key, Object cell, Object edge, boolean source) {
            return wrappedLayout.getConstraint(key, cell, edge, source);
        }

        @Override
        public boolean isUseBoundingBox() {
            return wrappedLayout.isUseBoundingBox();
        }

        @Override
        public void setUseBoundingBox(boolean useBoundingBox) {
            wrappedLayout.setUseBoundingBox(useBoundingBox);
        }

        @Override
        public boolean isVertexMovable(Object vertex) {
            return wrappedLayout.isVertexMovable(vertex);
        }

        @Override
        public boolean isEdgeIgnored(Object edge) {
            return wrappedLayout.isEdgeIgnored(edge);
        }

        @Override
        public void setEdgeStyleEnabled(Object edge, boolean value) {
            wrappedLayout.setEdgeStyleEnabled(edge, value);
        }

        @Override
        public void setOrthogonalEdge(Object edge, boolean value) {
            wrappedLayout.setOrthogonalEdge(edge, value);
        }

        @Override
        public mxPoint getParentOffset(Object parent) {
            return wrappedLayout.getParentOffset(parent);
        }

        @Override
        public void setEdgePoints(Object edge, List<mxPoint> points) {
            wrappedLayout.setEdgePoints(edge, points);
        }

        @Override
        public mxRectangle getVertexBounds(Object vertex) {
            return wrappedLayout.getVertexBounds(vertex);
        }

        @Override
        public void arrangeGroups(Object[] groups, int border) {
            wrappedLayout.arrangeGroups(groups, border);
        }
    }
}
