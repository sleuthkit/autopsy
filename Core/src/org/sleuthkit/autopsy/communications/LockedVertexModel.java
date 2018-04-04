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

import com.google.common.eventbus.EventBus;
import com.mxgraph.layout.mxGraphLayout;
import com.mxgraph.model.mxCell;
import com.mxgraph.util.mxPoint;
import com.mxgraph.util.mxRectangle;
import com.mxgraph.view.mxGraph;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class LockedVertexModel {

    void registerhandler(EventHandler<VertexLockEvent> handler) {
        eventBus.register(handler);
    }

    void unregisterhandler(EventHandler<VertexLockEvent> handler) {
        eventBus.unregister(handler);
    }

    private final EventBus eventBus = new EventBus();

    /**
     * Set of mxCells (vertices) that are 'locked'. Locked vertices are not
     * affected by layout algorithms, but may be repositioned manually by the
     * user.
     */
    private final Set<mxCell> lockedVertices = new HashSet<>();

    LockedVertexModel() {
    }

    /**
     * Lock the given vertex so that applying a layout algorithm doesn't move
     * it. The user can still manually position the vertex.
     *
     * @param vertex The vertex to lock.
     */
    void lockVertex(mxCell vertex) {
        lockedVertices.add(vertex);
        eventBus.post(new VertexLockEvent(vertex, true));

    }

    /**
     * Lock the given vertex so that applying a layout algorithm can move it.
     *
     * @param vertex The vertex to unlock.
     */
    void unlockVertex(mxCell vertex) {
        lockedVertices.remove(vertex);
        eventBus.post(new VertexLockEvent(vertex, false));

    }

    boolean isVertexLocked(mxCell vertex) {
        return lockedVertices.contains(vertex);

    }

    void clear() {
        lockedVertices.clear();
    }

    static class VertexLockEvent {

        private final mxCell vertex;

        public mxCell getVertex() {
            return vertex;
        }

        public boolean isVertexLocked() {
            return locked;
        }
        private final boolean locked;

        VertexLockEvent(mxCell vertex, boolean locked) {
            this.vertex = vertex;
            this.locked = locked;
        }
    }

    <L extends mxGraphLayout> mxGraphLayout createLockedVertexWrapper(L layout) {
        return new LockedVertexLayoutWrapper<>(layout, this);
    }

    /** An mxGraphLayout that wrapps an other layout and ignores locked vertes.
     *
     * @param <L>
     */
    private static final class LockedVertexLayoutWrapper<L extends mxGraphLayout> extends mxGraphLayout {

        private final L wrappedLayout;
        private final LockedVertexModel lockedVertexModel;

        /**
         *
         *
         * @param layout            the value of graph
         * @param lockedVertexModel the value of lockedVertexModel2
         */
        private LockedVertexLayoutWrapper(L layout, LockedVertexModel lockedVertexModel) {
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
        public mxRectangle setVertexLocation(Object vertex, double x, double y) {
            if (isVertexIgnored(vertex)) {
                return getVertexBounds(vertex);
            } else {
                return wrappedLayout.setVertexLocation(vertex, x, y);
            }
        }

        @Override
        public void execute(Object parent) {
            wrappedLayout.execute(parent);
        }

        @Override
        public void moveCell(Object cell, double x, double y) {
            wrappedLayout.moveCell(cell, x, y);
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
