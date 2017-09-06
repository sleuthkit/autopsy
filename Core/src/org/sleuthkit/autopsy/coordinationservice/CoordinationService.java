/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.coordinationservice;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.framework.recipes.locks.InterProcessReadWriteLock;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.KeeperException.NoNodeException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.core.UserPreferences;

/**
 * A coordination service for maintaining configuration information and
 * providing distributed synchronization using a shared hierarchical namespace
 * of nodes.
 */
@ThreadSafe
public final class CoordinationService {

    private static final int SESSION_TIMEOUT_MILLISECONDS = 300000;
    private static final int CONNECTION_TIMEOUT_MILLISECONDS = 300000;
    private static final int ZOOKEEPER_SESSION_TIMEOUT_MILLIS = 3000;
    private static final int ZOOKEEPER_CONNECTION_TIMEOUT_MILLIS = 15000;
    private static final int PORT_OFFSET = 1000; // When run in Solr, ZooKeeper defaults to Solr port + 1000
    private static final String DEFAULT_NAMESPACE_ROOT = "autopsy";
    @GuardedBy("CoordinationService.class")
    private static CoordinationService instance;
    private final CuratorFramework curator;
    @GuardedBy("categoryNodeToPath")    
    private final Map<String, String> categoryNodeToPath;

    /**
     * Determines if ZooKeeper is accessible with the current settings. Closes
     * the connection prior to returning.
     *
     * @return true if a connection was achieved, false otherwise
     *
     * @throws InterruptedException
     * @throws IOException
     */
    private static boolean isZooKeeperAccessible() throws InterruptedException, IOException {
        boolean result = false;
        Object workerThreadWaitNotifyLock = new Object();
        int zooKeeperServerPort = Integer.valueOf(UserPreferences.getIndexingServerPort()) + PORT_OFFSET;
        String connectString = UserPreferences.getIndexingServerHost() + ":" + zooKeeperServerPort;
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
        return result;
    }

    /**
     * Gets the coordination service for maintaining configuration information
     * and providing distributed synchronization using a shared hierarchical
     * namespace of nodes.
     *
     * @return The corrdination service.
     *
     * @throws CoordinationServiceException
     */
    public synchronized static CoordinationService getInstance() throws CoordinationServiceException {
        if (null == instance) {
            String rootNode;
            Collection<? extends CoordinationServiceNamespace> providers = Lookup.getDefault().lookupAll(CoordinationServiceNamespace.class);
            Iterator<? extends CoordinationServiceNamespace> it = providers.iterator();
            if (it.hasNext()) {
                rootNode = it.next().getNamespaceRoot();
            } else {
                rootNode = DEFAULT_NAMESPACE_ROOT;
            }
            try {
                instance = new CoordinationService(rootNode);
            } catch (IOException | InterruptedException | KeeperException | CoordinationServiceException ex) {
                throw new CoordinationServiceException("Failed to create coordination service", ex);
            }
        }
        return instance;
    }

    /**
     * Constructs an instance of the coordination service for a specific
     * namespace.
     *
     * @param rootNodeName The name of the root node that defines the namespace.
     *
     * @throws Exception (calls Curator methods that throw Exception instead of
     *                   more specific exceptions)
     */
    private CoordinationService(String rootNodeName) throws InterruptedException, IOException, KeeperException, CoordinationServiceException {

        if (false == isZooKeeperAccessible()) {
            throw new CoordinationServiceException("Unable to access ZooKeeper");
        }

        /*
         * Connect to ZooKeeper via Curator.
         */
        RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);
        int zooKeeperServerPort = Integer.valueOf(UserPreferences.getIndexingServerPort()) + PORT_OFFSET;
        String connectString = UserPreferences.getIndexingServerHost() + ":" + zooKeeperServerPort;
        curator = CuratorFrameworkFactory.newClient(connectString, SESSION_TIMEOUT_MILLISECONDS, CONNECTION_TIMEOUT_MILLISECONDS, retryPolicy);
        curator.start();

        /*
         * Create the top-level root and category nodes.
         */
        String rootNode = rootNodeName;
        
        if (!rootNode.startsWith("/")) {
            rootNode = "/" + rootNode;
        }
        categoryNodeToPath = new ConcurrentHashMap<>();
        for (CategoryNode node : CategoryNode.values()) {
            String nodePath = rootNode + "/" + node.getDisplayName();
            try {
                curator.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).withACL(ZooDefs.Ids.OPEN_ACL_UNSAFE).forPath(nodePath);
            } catch (KeeperException ex) {
                if (ex.code() != KeeperException.Code.NODEEXISTS) {
                    throw ex;
                }
            } catch (Exception ex) {
                throw new CoordinationServiceException("Curator experienced an error", ex);
            }
            categoryNodeToPath.put(node.getDisplayName(), nodePath);
        }
    }

    /**
     * Tries to get an exclusive lock on a node path appended to a category path
     * in the namespace managed by this coordination service. Blocks until the
     * lock is obtained or the time out expires.
     *
     * IMPORTANT: The lock needs to be released in the same thread in which it
     * is acquired.
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
     * IMPORTANT: The lock needs to be released in the same thread in which it
     * is acquired.
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
     * IMPORTANT: The lock needs to be released in the same thread in which it
     * is acquired.
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
     * IMPORTANT: The lock needs to be released in the same thread in which it
     * is acquired.
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
    
    public List<String> getNodeList(CategoryNode category) {
        List<String> list = null;
        
        try {
            list = curator.getChildren().forPath(categoryNodeToPath.get(category.getDisplayName()));
        } catch (Exception ex) {
            Logger.getLogger(CoordinationService.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        return list;
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

    /**
     * Category nodes are the immediate children of the root node of a shared
     * hierarchical namespace managed by a coordination service.
     */
    public enum CategoryNode {

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
}
