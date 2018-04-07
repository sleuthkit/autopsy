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
import com.mxgraph.model.mxCell;
import java.util.Collection;
import java.util.HashSet;
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
}
