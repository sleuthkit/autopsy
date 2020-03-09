/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datamodel.utils;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import org.openide.nodes.AbstractNode;
import org.openide.util.WeakListeners;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.AbstractContentNode;

/**
 * A utility that allows AbstractNode subclasses to execute background tasks in
 * threads in a thread pool. An AbstractNode subclass client needs to provide an
 * implementation of the NodeTask interface that does the background task and
 * returns the task result in the form of a PropertyChangeEvent. It also needs
 * to provide an implementation of a PropertyChangeListener to handle the
 * PropertyChangeEvent returned by the NodeTask. The utility uses weak
 * references to the AbstractNode and the PropertyChangeListener to safely
 * return results to the node, if it has not been destroyed by the NetBeans
 * framework at the time the task is completed.
 */
public final class BackgroundTaskRunner {

    private static final Logger logger = Logger.getLogger(AbstractContentNode.class.getName());
    private static final Integer THREAD_POOL_SIZE = 10;
    private static final ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE, new ThreadFactoryBuilder().setNameFormat("node-background-task-%d").build());

    /**
     * Implementations of this interface do a task in a background thread
     * supplied by this utility.
     */
    @FunctionalInterface
    public interface NodeTask {

        /**
         * Performs a task in a thread supplied by this utility.
         *
         * @param future The future of the actual background task executing this
         *               method, may be checked for task cancellation.
         *
         * @return A PropertyChangeEvent holding the result of the task.
         *
         * @throws Exception If there is an error performing the task.
         */
        PropertyChangeEvent run(Future<?> future) throws Exception;
    }

    /**
     * Submits a background task for an AbstractNode to a dedicated thread pool.
     *
     * @param node     The AbstractNode.
     * @param task     The task to be done in the background.
     * @param listener A PropertyChangeListener for the AbstractNode to handle
     *                 the PropertyChangeEvent produced by the task.
     *
     * @return The Future for the Runnable used to run the task.
     */
    public static Future<?> submitTask(AbstractNode node, NodeTask task, PropertyChangeListener listener) {
        NodeBackgroundTask backgroundTask = new NodeBackgroundTask(node, task, listener);
        Future<?> future = executor.submit(backgroundTask);
        backgroundTask.setFuture(future);
        return future;
    }

    /**
     * A Runnable that uses weak references to an AbstractNode and the
     * PropertyChangeListener for the node to safely return results to the node,
     * if it has not been destroyed by the NetBeans framework at the time the
     * task is completed.
     */
    private static class NodeBackgroundTask implements Runnable {

        private final WeakReference<AbstractNode> weakNodeRef;
        private final NodeTask task;
        private final PropertyChangeListener weakListenerRef;
        private Future<?> future;

        /**
         * Constructs a Runnable that uses weak references to an AbstractNode
         * and the PropertyChangeListener for the node to safely return results
         * to the node, if it still exists at the time the task is completed.
         *
         * @param node     The AbstractNode.
         * @param task     The task to be done in the background.
         * @param listener A PropertyChangeListener for the AbstractNode to
         *                 handle the PropertyChangeEvent produced by the task.
         */
        private NodeBackgroundTask(AbstractNode node, NodeTask task, PropertyChangeListener listener) {
            this.weakNodeRef = new WeakReference<>(node);
            this.task = task;
            this.weakListenerRef = WeakListeners.propertyChange(listener, null);
        }

        @Override
        public void run() {
            AbstractNode node = weakNodeRef.get();
            if (node == null) {
                return;
            }

            if (future.isCancelled()) {
                return;
            }

            PropertyChangeEvent changeEvent = null;
            try {
                changeEvent = task.run(future);
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Error executing AbstractNode background task", ex);
            }

            if (future.isCancelled()) {
                return;
            }

            if (changeEvent != null && weakListenerRef != null) {
                weakListenerRef.propertyChange(changeEvent);
            }

        }

        /**
         * Provides this Runnable with access to its Future when it has been
         * submitted to an ExecutrService.
         *
         * @param future The Future.
         */
        private void setFuture(Future<?> future) {
            this.future = future;
        }

    }

    /**
     * A private constructor to prevent instatiation of this utility class.
     */
    private BackgroundTaskRunner() {
    }

}
