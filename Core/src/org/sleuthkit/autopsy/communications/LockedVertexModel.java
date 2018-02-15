/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.communications;

import com.google.common.eventbus.EventBus;
import com.mxgraph.model.mxCell;
import java.util.HashSet;
import java.util.Set;
import org.sleuthkit.autopsy.communications.visualization.EventHandler;

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
}
