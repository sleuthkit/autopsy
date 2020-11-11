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
package zookeepernodemigration;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
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

/**
 * Utility to migrate Autopsy coordination service data from one ZK database to
 * another.
 */
public class ZookeeperNodeMigration {

    private static final Logger LOGGER = Logger.getLogger(ZookeeperNodeMigration.class.getName());
    private static final int SESSION_TIMEOUT_MILLISECONDS = 300000;
    private static final int CONNECTION_TIMEOUT_MILLISECONDS = 300000;
    private static final int ZOOKEEPER_SESSION_TIMEOUT_MILLIS = 3000;
    private static final int ZOOKEEPER_CONNECTION_TIMEOUT_MILLIS = 15000;
    private static final String DEFAULT_NAMESPACE_ROOT = "autopsy";
    private static CuratorFramework inputCurator;
    private static CuratorFramework outputCurator;
    private static Map<String, String> categoryNodeToPath;

    private ZookeeperNodeMigration(){
    }
    
    /**
     * Main method.
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {

        String inputZkIpAddr, inputZkPort, outputZkIpAddr, outputZkPort;

        if (args.length == 4) {
            inputZkIpAddr = args[0];
            inputZkPort = args[1];
            outputZkIpAddr = args[2];
            outputZkPort = args[3];
        } else {
            System.out.println("Input needs to be [Input Zookeeper IP Address] [Input Zookeeper Port Number] [Output Zookeeper IP Address] [Output Zookeeper Port Number]");
            LOGGER.log(Level.SEVERE, "Input needs to be [Input Zookeeper IP Address] [Input Zookeeper Port Number] [Output Zookeeper IP Address] [Output Zookeeper Port Number]");
            return;
        }

        if (inputZkIpAddr.isEmpty() || inputZkPort.isEmpty() || outputZkIpAddr.isEmpty() || outputZkPort.isEmpty()) {
            System.out.println("Input needs to be [Input Zookeeper IP Address] [Input Zookeeper Port Number] [Output Zookeeper IP Address] [Output Zookeeper Port Number]");
            LOGGER.log(Level.SEVERE, "Input needs to be [Input Zookeeper IP Address] [Input Zookeeper Port Number] [Output Zookeeper IP Address] [Output Zookeeper Port Number]");
            return;
        }

        inputCurator = initializeCurator(inputZkIpAddr, inputZkPort);
        if (inputCurator == null) {
            System.out.println("Unable to initialize Zookeeper or Curator: " + inputZkIpAddr + ":" + inputZkPort);
            LOGGER.log(Level.SEVERE, "Unable to initialize Zookeeper or Curator: {0}:{1}", new Object[]{inputZkIpAddr, inputZkPort});
            return;
        }

        try {
            categoryNodeToPath = populateCategoryNodes(inputCurator);
        } catch (KeeperException | CoordinationServiceException ex) {
            System.out.println("Unable to initialize Curator: " + inputZkIpAddr + ":" + inputZkPort);
            LOGGER.log(Level.SEVERE, "Unable to initialize Curator: {0}:{1}", new Object[]{inputZkIpAddr, inputZkPort});
            return;
        }

        outputCurator = initializeCurator(outputZkIpAddr, outputZkPort);
        if (outputCurator == null) {
            System.out.println("Unable to initialize Zookeeper or Curator: " + outputZkIpAddr + ":" + outputZkPort);
            LOGGER.log(Level.SEVERE, "Unable to initialize Zookeeper or Curator: {0}:{1}", new Object[]{outputZkIpAddr, outputZkPort});
            return;
        }

        try {
            // if output ZK database is new, we may have to ceate root "autopsy" node and it's sub-nodes
            populateCategoryNodes(outputCurator);
        } catch (KeeperException | CoordinationServiceException ex) {
            System.out.println("Unable to initialize Curator: " + outputZkIpAddr + ":" + outputZkPort);
            LOGGER.log(Level.SEVERE, "Unable to initialize Curator: {0}:{1}", new Object[]{outputZkIpAddr, outputZkPort});
            return;
        }

        copyAllCategoryNodes();

        System.out.println("Done...");
    }

    /**
     * Copy all Autopsy coordination service nodes from one ZK database to
     * another.
     */
    private static void copyAllCategoryNodes() {

        for (CategoryNode category : CategoryNode.values()) {
            List<String> inputNodeList = Collections.EMPTY_LIST;
            try {
                inputNodeList = getNodeList(category);
            } catch (CoordinationServiceException ex) {
                System.out.println("Unable to get ZK nodes for category: " + category.getDisplayName());
                LOGGER.log(Level.SEVERE, "Unable to get ZK nodes for category: " + category.getDisplayName(), ex);
                continue;
            }

            for (String zkNode : inputNodeList) {
                try {
                    final byte[] nodeBytes = getNodeData(category, zkNode);
                    try (Lock manifestLock = tryGetExclusiveLock(outputCurator, category, zkNode)) {
                        setNodeData(outputCurator, category, zkNode, nodeBytes);
                    }
                } catch (CoordinationServiceException | InterruptedException ex) {
                    System.out.println("Unable to write ZK node data for node: " + zkNode);
                    LOGGER.log(Level.SEVERE, "Unable to write ZK node data for node: " + zkNode, ex);
                    continue;
                }
            }
        }
    }

    /**
     * Initialize Curator framework.
     *
     * @param zkIpAddr      Zookeeper server IP address.
     * @param zookeeperPort Zookeeper server port number.
     *
     * @return CuratorFramework object
     */
    private static CuratorFramework initializeCurator(String zkIpAddr, String zookeeperPort) {

        try {
            if (!isZooKeeperAccessible(zkIpAddr, zookeeperPort)) {
                System.out.println("Unable to connect to Zookeeper");
                LOGGER.log(Level.SEVERE, "Unable to connect to Zookeeper");
                return null;
            }
        } catch (InterruptedException | IOException ex) {
            System.out.println("Unable to connect to Zookeeper");
            LOGGER.log(Level.SEVERE, "Unable to connect to Zookeeper", ex);
            return null;
        }

        /*
         * Connect to ZooKeeper via Curator.
         */
        RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);
        String connectString = zkIpAddr + ":" + zookeeperPort;
        CuratorFramework curator = CuratorFrameworkFactory.newClient(connectString, SESSION_TIMEOUT_MILLISECONDS, CONNECTION_TIMEOUT_MILLISECONDS, retryPolicy);
        curator.start();
        return curator;
    }

    /*
     * Creates Autopsy coordination service root ZK nodes.
     */
    private static Map<String, String> populateCategoryNodes(CuratorFramework curator) throws KeeperException, CoordinationServiceException {
        /*
         * Create the top-level root and category nodes.
         */
        String rootNodeName = DEFAULT_NAMESPACE_ROOT;
        String rootNode = rootNodeName;

        if (!rootNode.startsWith("/")) {
            rootNode = "/" + rootNode;
        }
        Map<String, String> categoryPaths = new ConcurrentHashMap<>();
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
            categoryPaths.put(node.getDisplayName(), nodePath);
        }
        return categoryPaths;
    }

    /**
     * Determines if ZooKeeper is accessible with the current settings. Closes
     * the connection prior to returning.
     *
     * @return true if a connection was achieved, false otherwise
     *
     * @throws InterruptedException
     * @throws IOException
     */
    private static boolean isZooKeeperAccessible(String solrIpAddr, String zookeeperPort) throws InterruptedException, IOException {
        boolean result = false;
        Object workerThreadWaitNotifyLock = new Object();
        String connectString = solrIpAddr + ":" + zookeeperPort;
        ZooKeeper zooKeeper = new ZooKeeper(connectString, ZOOKEEPER_SESSION_TIMEOUT_MILLIS,
                (WatchedEvent event) -> {
                    synchronized (workerThreadWaitNotifyLock) {
                        workerThreadWaitNotifyLock.notifyAll();
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
    private static Lock tryGetExclusiveLock(CuratorFramework curator, CategoryNode category, String nodePath) throws CoordinationServiceException {
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
    private static byte[] getNodeData(CategoryNode category, String nodePath) throws CoordinationServiceException, InterruptedException {
        String fullNodePath = getFullyQualifiedNodePath(category, nodePath);
        try {
            return inputCurator.getData().forPath(fullNodePath);
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
    private static void setNodeData(CuratorFramework curator, CategoryNode category, String nodePath, byte[] data) throws CoordinationServiceException, InterruptedException {
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
     * Delete the specified node.
     *
     * @param category The desired category in the namespace.
     * @param nodePath The node to delete.
     *
     * @throws CoordinationServiceException If there is an error setting the
     *                                      node data.
     * @throws InterruptedException         If interrupted while blocked during
     *                                      setting of node data.
     */
    private static void deleteNode(CategoryNode category, String nodePath) throws CoordinationServiceException, InterruptedException {
        String fullNodePath = getFullyQualifiedNodePath(category, nodePath);
        try {
            inputCurator.delete().forPath(fullNodePath);
        } catch (Exception ex) {
            if (ex instanceof InterruptedException) {
                throw (InterruptedException) ex;
            } else {
                throw new CoordinationServiceException(String.format("Failed to set data for %s", fullNodePath), ex);
            }
        }
    }

    /**
     * Gets a list of the child nodes of a category in the namespace.
     *
     * @param category The desired category in the namespace.
     *
     * @return A list of child node names.
     *
     * @throws CoordinationServiceException If there is an error getting the
     *                                      node list.
     */
    private static List<String> getNodeList(CategoryNode category) throws CoordinationServiceException {
        try {
            List<String> list = inputCurator.getChildren().forPath(categoryNodeToPath.get(category.getDisplayName()));
            return list;
        } catch (Exception ex) {
            throw new CoordinationServiceException(String.format("Failed to get node list for %s", category.getDisplayName()), ex);
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
    private static String getFullyQualifiedNodePath(CategoryNode category, String nodePath) {
        // nodePath on Unix systems starts with a "/" and ZooKeeper doesn't like two slashes in a row
        if (nodePath.startsWith("/")) {
            return categoryNodeToPath.get(category.getDisplayName()) + nodePath.toUpperCase();
        } else {
            return categoryNodeToPath.get(category.getDisplayName()) + "/" + nodePath.toUpperCase();
        }
    }

    /**
     * Exception type thrown by the coordination service.
     */
    private final static class CoordinationServiceException extends Exception {

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
    private static class Lock implements AutoCloseable {

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
        CONFIG("config"),
        CENTRAL_REPO("centralRepository"),
        HEALTH_MONITOR("healthMonitor");

        private final String displayName;

        private CategoryNode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
