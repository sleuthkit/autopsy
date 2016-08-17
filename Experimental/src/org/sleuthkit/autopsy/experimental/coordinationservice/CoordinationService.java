/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.coordinationservice;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooDefs;
import org.apache.curator.RetryPolicy;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.framework.recipes.locks.InterProcessReadWriteLock;
import org.apache.zookeeper.KeeperException;
import org.sleuthkit.autopsy.core.UserPreferences;
import java.io.IOException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.KeeperException.NoNodeException;

/**
 * A centralized service for maintaining configuration information and providing
 * distributed synchronization using a shared hierarchical namespace of nodes.
 */
public final class CoordinationService {

    /**
     * Category nodes are the immediate children of the root node of a shared
     * hierarchical namespace managed by the coordination service.
     */
    public enum CategoryNode { // RJCTODO: Move this to CoordinationServiceNamespace

        CASES("cases"),
        MANIFESTS("manifests"),
        CONFIG("config");

        private final String displayName;

        private CategoryNode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Exception type thrown by the coordination service.
     */
    public final static class CoordinationServiceException extends Exception {

        private static final long serialVersionUID = 1L;

        private CoordinationServiceException(String message) {
            super(message);
        }

        private CoordinationServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * An opaque encapsulation of a lock for use in distributed synchronization.
     * Instances are obtained by calling a get lock method and must be passed to
     * a release lock method.
     */
    public static class Lock implements AutoCloseable {

        /**
         * This implementation uses the Curator read/write lock. see
         * http://curator.apache.org/curator-recipes/shared-reentrant-read-write-lock.html
         */
        private final InterProcessMutex interProcessLock;
        private final String nodePath;

        private Lock(String nodePath, InterProcessMutex lock) {
            this.nodePath = nodePath;
            this.interProcessLock = lock;
        }

        public String getNodePath() {
            return nodePath;
        }

        public void release() throws CoordinationServiceException {
            try {
                this.interProcessLock.release();
            } catch (Exception ex) {
                throw new CoordinationServiceException(String.format("Failed to release the lock on %s", nodePath), ex);
            }
        }

        @Override
        public void close() throws CoordinationServiceException {
            release();
        }
    }

    private static CuratorFramework curator = null;
    private static final Map<String, CoordinationService> rootNodesToServices = new HashMap<>();
    private final Map<String, String> categoryNodeToPath = new HashMap<>();
    private static final int SESSION_TIMEOUT_MILLISECONDS = 300000;
    private static final int CONNECTION_TIMEOUT_MILLISECONDS = 300000;
    private static final int ZOOKEEPER_SESSION_TIMEOUT_MILLIS = 3000;
    private static final int ZOOKEEPER_CONNECTION_TIMEOUT_MILLIS = 15000;
    private static final int PORT_OFFSET = 1000;

    /**
     * Gets an instance of the centralized coordination service for a specific
     * namespace.
     *
     * @param rootNode The name of the root node that defines the namespace.
     *
     * @return The service for the namespace defined by the root node name.
     *
     * @throws CoordinationServiceException If an instaNce of the coordination
     *                                      service cannot be created.
     */
    public static synchronized CoordinationService getInstance(String rootNode) throws CoordinationServiceException {
        if (null == curator) {
            RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);
            // When run in Solr, ZooKeeper defaults to Solr port + 1000
            int zooKeeperServerPort = Integer.valueOf(UserPreferences.getIndexingServerPort()) + PORT_OFFSET;
            String connectString = UserPreferences.getIndexingServerHost() + ":" + zooKeeperServerPort;
            curator = CuratorFrameworkFactory.newClient(connectString, SESSION_TIMEOUT_MILLISECONDS, CONNECTION_TIMEOUT_MILLISECONDS, retryPolicy);
            curator.start();
        }

        /*
         * Get or create a coordination service for the namespace defined by the
         * specified root node.
         */
        if (rootNodesToServices.containsKey(rootNode)) {
            return rootNodesToServices.get(rootNode);
        } else {
            CoordinationService service;
            try {
                service = new CoordinationService(rootNode);
            } catch (Exception ex) {
                throw new CoordinationServiceException("Failed to create coordination service", ex);
            }
            rootNodesToServices.put(rootNode, service);
            return service;
        }
    }

    /**
     * Constructs an instance of the centralized coordination service for a
     * specific namespace.
     *
     * @param rootNodeName The name of the root node that defines the namespace.
     */
    private CoordinationService(String rootNodeName) throws Exception {

        if (false == isZooKeeperAccessible()) {
            throw new Exception("Unable to access ZooKeeper");
        }

        String rootNode = rootNodeName;
        if (!rootNode.startsWith("/")) {
            rootNode = "/" + rootNode;
        }

        for (CategoryNode node : CategoryNode.values()) {
            String nodePath = rootNode + "/" + node.getDisplayName();
            try {
                curator.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).withACL(ZooDefs.Ids.OPEN_ACL_UNSAFE).forPath(nodePath);
            } catch (KeeperException ex) {
                if (ex.code() != KeeperException.Code.NODEEXISTS) {
                    throw ex;
                }
            }
            categoryNodeToPath.put(node.getDisplayName(), nodePath);
        }
    }

    /**
     * Tries to get an exclusive lock on a node path appended to a category path
     * in the namespace managed by this coordination service. Blocks until the
     * lock is obtained or the time out expires.
     *
     * @param category The desired category in the namespace.
     * @param nodePath The node path to use as the basis for the lock.
     * @param timeOut  Length of the time out.
     * @param timeUnit Time unit for the time out.
     *
     * @return The lock, or null if lock acquisition timed out.
     *
     * @throws CoordinationServiceException If there is an error during lock
     *                                      acquisition.
     * @throws InterruptedException         If interrupted while blocked during
     *                                      lock acquisition.
     */
    public Lock tryGetExclusiveLock(CategoryNode category, String nodePath, int timeOut, TimeUnit timeUnit) throws CoordinationServiceException, InterruptedException {
        String fullNodePath = getFullyQualifiedNodePath(category, nodePath);
        try {
            InterProcessReadWriteLock lock = new InterProcessReadWriteLock(curator, fullNodePath);
            if (lock.writeLock().acquire(timeOut, timeUnit)) {
                return new Lock(nodePath, lock.writeLock());
            } else {
                return null;
            }
        } catch (Exception ex) {
            if (ex instanceof InterruptedException) {
                throw (InterruptedException) ex;
            } else {
                throw new CoordinationServiceException(String.format("Failed to get exclusive lock for %s", fullNodePath), ex);
            }
        }
    }

    /**
     * Tries to get an exclusive lock on a node path appended to a category path
     * in the namespace managed by this coordination service. Returns
     * immediately if the lock can not be acquired.
     *
     * @param category The desired category in the namespace.
     * @param nodePath The node path to use as the basis for the lock.
     *
     * @return The lock, or null if the lock could not be obtained.
     *
     * @throws CoordinationServiceException If there is an error during lock
     *                                      acquisition.
     */
    public Lock tryGetExclusiveLock(CategoryNode category, String nodePath) throws CoordinationServiceException {
        String fullNodePath = getFullyQualifiedNodePath(category, nodePath);
        try {
            InterProcessReadWriteLock lock = new InterProcessReadWriteLock(curator, fullNodePath);
            if (!lock.writeLock().acquire(0, TimeUnit.SECONDS)) {
                return null;
            }
            return new Lock(nodePath, lock.writeLock());
        } catch (Exception ex) {
            throw new CoordinationServiceException(String.format("Failed to get exclusive lock for %s", fullNodePath), ex);
        }
    }

    /**
     * Tries to get a shared lock on a node path appended to a category path in
     * the namespace managed by this coordination service. Blocks until the lock
     * is obtained or the time out expires.
     *
     * @param category The desired category in the namespace.
     * @param nodePath The node path to use as the basis for the lock.
     * @param timeOut  Length of the time out.
     * @param timeUnit Time unit for the time out.
     *
     * @return The lock, or null if lock acquisition timed out.
     *
     * @throws CoordinationServiceException If there is an error during lock
     *                                      acquisition.
     * @throws InterruptedException         If interrupted while blocked during
     *                                      lock acquisition.
     */
    public Lock tryGetSharedLock(CategoryNode category, String nodePath, int timeOut, TimeUnit timeUnit) throws CoordinationServiceException, InterruptedException {
        String fullNodePath = getFullyQualifiedNodePath(category, nodePath);
        try {
            InterProcessReadWriteLock lock = new InterProcessReadWriteLock(curator, fullNodePath);
            if (lock.readLock().acquire(timeOut, timeUnit)) {
                return new Lock(nodePath, lock.readLock());
            } else {
                return null;
            }
        } catch (Exception ex) {
            if (ex instanceof InterruptedException) {
                throw (InterruptedException) ex;
            } else {
                throw new CoordinationServiceException(String.format("Failed to get shared lock for %s", fullNodePath), ex);
            }
        }
    }

    /**
     * Tries to get a shared lock on a node path appended to a category path in
     * the namespace managed by this coordination service. Returns immediately
     * if the lock can not be acquired.
     *
     * @param category The desired category in the namespace.
     * @param nodePath The node path to use as the basis for the lock.
     *
     * @return The lock, or null if the lock could not be obtained.
     *
     * @throws CoordinationServiceException If there is an error during lock
     *                                      acquisition.
     */
    public Lock tryGetSharedLock(CategoryNode category, String nodePath) throws CoordinationServiceException {
        String fullNodePath = getFullyQualifiedNodePath(category, nodePath);
        try {
            InterProcessReadWriteLock lock = new InterProcessReadWriteLock(curator, fullNodePath);
            if (!lock.readLock().acquire(0, TimeUnit.SECONDS)) {
                return null;
            }
            return new Lock(nodePath, lock.readLock());
        } catch (Exception ex) {
            throw new CoordinationServiceException(String.format("Failed to get shared lock for %s", fullNodePath), ex);
        }
    }

    /**
     * Retrieve the data associated with the specified node.
     *
     * @param category The desired category in the namespace.
     * @param nodePath The node to retrieve the data for.
     *
     * @return The data associated with the node, if any, or null if the node
     *         has not been created yet.
     *
     * @throws CoordinationServiceException If there is an error setting the
     *                                      node data.
     * @throws InterruptedException         If interrupted while blocked during
     *                                      setting of node data.
     */
    public byte[] getNodeData(CategoryNode category, String nodePath) throws CoordinationServiceException, InterruptedException {
        String fullNodePath = getFullyQualifiedNodePath(category, nodePath);
        try {
            return curator.getData().forPath(fullNodePath);
        } catch (NoNodeException ex) {
            return null;
        } catch (Exception ex) {
            if (ex instanceof InterruptedException) {
                throw (InterruptedException) ex;
            } else {
                throw new CoordinationServiceException(String.format("Failed to get data for %s", fullNodePath), ex);
            }
        }
    }

    /**
     * Store the given data with the specified node.
     *
     * @param category The desired category in the namespace.
     * @param nodePath The node to associate the data with.
     * @param data     The data to store with the node.
     *
     * @throws CoordinationServiceException If there is an error setting the
     *                                      node data.
     * @throws InterruptedException         If interrupted while blocked during
     *                                      setting of node data.
     */
    public void setNodeData(CategoryNode category, String nodePath, byte[] data) throws CoordinationServiceException, InterruptedException {
        String fullNodePath = getFullyQualifiedNodePath(category, nodePath);
        try {
            curator.setData().forPath(fullNodePath, data);
        } catch (Exception ex) {
            if (ex instanceof InterruptedException) {
                throw (InterruptedException) ex;
            } else {
                throw new CoordinationServiceException(String.format("Failed to set data for %s", fullNodePath), ex);
            }
        }
    }

    /**
     * Creates a node path within a given category.
     *
     * @param category A category node.
     * @param nodePath A node path relative to a category node path.
     *
     * @return
     */
    private String getFullyQualifiedNodePath(CategoryNode category, String nodePath) {
        return categoryNodeToPath.get(category.getDisplayName()) + "/" + nodePath.toUpperCase();
    }

    /**
     * Determines if ZooKeeper is accessible with the current settings. Closes
     * the connection prior to returning.
     *
     * @return true if a connection was achieved, false otherwise
     */
    private static boolean isZooKeeperAccessible() {
        boolean result = false;
        Object workerThreadWaitNotifyLock = new Object();
        int zooKeeperServerPort = Integer.valueOf(UserPreferences.getIndexingServerPort()) + PORT_OFFSET;
        String connectString = UserPreferences.getIndexingServerHost() + ":" + zooKeeperServerPort;

        try {
            ZooKeeper zooKeeper = new ZooKeeper(connectString, ZOOKEEPER_SESSION_TIMEOUT_MILLIS,
                    (WatchedEvent event) -> {

                        synchronized (workerThreadWaitNotifyLock) {
                            workerThreadWaitNotifyLock.notify();
                        }
                    });
            synchronized (workerThreadWaitNotifyLock) {
                workerThreadWaitNotifyLock.wait(ZOOKEEPER_CONNECTION_TIMEOUT_MILLIS);
            }
            ZooKeeper.States state = zooKeeper.getState();
            if (state == ZooKeeper.States.CONNECTED || state == ZooKeeper.States.CONNECTEDREADONLY) {
                result = true;
            }
            zooKeeper.close();
        } catch (InterruptedException | IOException ignored) {
        }
        return result;
    }
}
