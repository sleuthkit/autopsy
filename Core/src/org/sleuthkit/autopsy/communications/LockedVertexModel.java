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
import com.mxgraph.model.mxCell;
import java.util.HashSet;
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
}
