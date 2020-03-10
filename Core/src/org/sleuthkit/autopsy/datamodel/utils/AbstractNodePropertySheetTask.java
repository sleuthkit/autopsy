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
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.AbstractContentNode;

/**
 * An abstract base class for background tasks needed to compute values for the
 * property sheet of an AbstractNode.
 *
 * The results of the computation are returned by firing a PropertyChangeEvent
 * and the run method has an exception firewall with logging. These features
 * relieve the AbstractNode from having to create a thread to block on the get()
 * method of the task Future.
 *
 * Only weak references to the AbstractNode and its ProeprtyChangeListener are
 * held prior to task execution so that a queued task does not interfere with
 * garbage collection if the node has been destroyed by the NetBeans framework.
 *
 * A thread pool with descriptively named threads (node-background-task-N) is
 * provided for executing instances of the tasks.
 */
public abstract class AbstractNodePropertySheetTask implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(AbstractContentNode.class.getName());
    private static final Integer THREAD_POOL_SIZE = 10;
    private static final ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE, new ThreadFactoryBuilder().setNameFormat("node-background-task-%d").build());
    private final WeakReference<AbstractNode> weakNodeRef;
    private final WeakReference<PropertyChangeListener> weakListenerRef;

    /**
     * Submits a task to compute values for the property sheet of an
     * AbstractNode to a thread pool dedicated to such tasks with descriptively
     * named threads (node-background-task-N).
     *
     * @param task The task.
     *
     * @return The Future of the task, may be used for task cancellation by
     *         calling Future.cancel(true).
     */
    public static Future<?> submitTask(AbstractNodePropertySheetTask task) {
        return executor.submit(task);
    }

    /**
     * Constructs an abstract base class for background tasks needed to compute
     * values for the property sheet of an AbstractNode.
     *
     * The results of the computation are returned by firing a
     * PropertyChangeEvent and the run method has an exception firewall with
     * logging. These features relieve the AbstractNode from having to create a
     * thread to block on the get() method of the task Future.
     *
     * Only weak references to the AbstractNode and its ProeprtyChangeListener
     * are held prior to task execution so that a queued task does not interfere
     * with garbage collection if the node has been destroyed by the NetBeans
     * framework.
     *
     * A thread pool with descriptively named threads (node-background-task-N)
     * is provided for executing instances of the tasks.
     *
     * @param node     The node.
     * @param listener A property change listener for the node.
     */
    protected AbstractNodePropertySheetTask(AbstractNode node, PropertyChangeListener listener) {
        this.weakNodeRef = new WeakReference<>(node);
        this.weakListenerRef = new WeakReference<>(listener);
    }

    /**
     * Computes the values for the property sheet of an AbstractNode. The
     * results of the computation are returned as a PropertyChangeEvent which is
     * fired to the PropertyChangeEventListener of the node.
     *
     * @param node The ABstractNode.
     *
     * @return The result of the computation as a PropertyChangeEvent.
     */
    protected abstract PropertyChangeEvent computePropertyValue(AbstractNode node) throws Exception;

    @Override
    final public void run() {
        try {
            AbstractNode node = this.weakNodeRef.get();
            PropertyChangeListener listener = this.weakListenerRef.get();
            if (node == null || listener== null) {
                return;
            }

            if (Thread.currentThread().isInterrupted()) {
                return;
            }

            PropertyChangeEvent changeEvent = computePropertyValue(node);
            
            if (Thread.currentThread().isInterrupted()) {
                return;
            }

            if (changeEvent != null) {
                listener.propertyChange(changeEvent);
            }

        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Error executing property sheet values computation background task", ex);
        }

    }

}
