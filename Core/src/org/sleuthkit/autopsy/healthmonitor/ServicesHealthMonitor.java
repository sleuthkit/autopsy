/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.healthmonitor;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import org.apache.commons.dbcp2.BasicDataSource;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.core.UserPreferencesException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.datamodel.CaseDbConnectionInfo;
import org.sleuthkit.datamodel.CaseDbSchemaVersionNumber;

/**
 *
 */
public class ServicesHealthMonitor {
    
    private final static Logger logger = Logger.getLogger(ServicesHealthMonitor.class.getName());
    private final static String DATABASE_NAME = "ServicesHealthMonitor";
    private final static String MODULE_NAME = "ServicesHealthMonitor";
    private final static String IS_ENABLED_KEY = "is_enabled";
    private final static long DATABASE_WRITE_INTERVAL = 1; // Minutes
    public static final CaseDbSchemaVersionNumber CURRENT_DB_SCHEMA_VERSION
            = new CaseDbSchemaVersionNumber(1, 0);
    
    private static final AtomicBoolean isEnabled = new AtomicBoolean(false);
    private static ServicesHealthMonitor instance;
    
    private ScheduledThreadPoolExecutor periodicTasksExecutor;
    private Map<String, TimingInfo> timingInfoMap;
    private static final int CONN_POOL_SIZE = 5;
    private BasicDataSource connectionPool = null;
    
    private ServicesHealthMonitor() throws HealthMonitorException {
        System.out.println("\nCreating ServicesHealthMonitor");
        
        // Create the map to collect timing metrics
        timingInfoMap = new HashMap<>();
        
        if (ModuleSettings.settingExists(MODULE_NAME, IS_ENABLED_KEY)) {
            if(ModuleSettings.getConfigSetting(MODULE_NAME, IS_ENABLED_KEY).equals("true")){
                isEnabled.set(true);
                activateMonitor();
                return;
            }
        }
        isEnabled.set(false);
    }
    
    private synchronized void activateMonitor() throws HealthMonitorException {
        // Set up database (if needed)
        System.out.println("  Setting up database...");
        if (!UserPreferences.getIsMultiUserModeEnabled()) {
            throw new HealthMonitorException("Multi user mode is not enabled - can not activate services health monitor");
        }
        
        CoordinationService.Lock lock = getExclusiveDbLock();
        if(lock == null) {
            throw new HealthMonitorException("Error getting database lock");
        }
        
        try {
            // Check if the database exists
            if (! databaseExists()) {
                
                // If not, create a new one
                System.out.println("  No database exists - setting up new one");
                createDatabase();
                initializeDatabaseSchema();
            } else {
                System.out.println("  Database already exists");
            }
            
            // Any database upgrades would happen here
            
        } finally {
            try {
                lock.release();
            } catch (CoordinationService.CoordinationServiceException ex) {
                throw new HealthMonitorException("Error releasing database lock", ex);
            }
        }
        
        // Prepare metric storage
        System.out.println("  Clearing hash map...");
        timingInfoMap = new HashMap<>();
        
        // Start the timer
        System.out.println("  Starting the timer...");
        if(periodicTasksExecutor != null) {
            // Make sure the previous executor (if it exists) has been stopped
            periodicTasksExecutor.shutdown();
        }
        periodicTasksExecutor = new ScheduledThreadPoolExecutor(1, new ThreadFactoryBuilder().setNameFormat("health_monitor_timer").build());
        periodicTasksExecutor.scheduleWithFixedDelay(new DatabaseWriteTask(), DATABASE_WRITE_INTERVAL, DATABASE_WRITE_INTERVAL, TimeUnit.MINUTES);
 
    }
    
    private synchronized void deactivateMonitor() throws HealthMonitorException {
        // Clear out the collected data
        System.out.println("  Clearing hash map...");
        timingInfoMap.clear();
        
        // Stop the timer
        System.out.println("  Stopping the timer...");
        if(periodicTasksExecutor != null) {
            periodicTasksExecutor.shutdown();
        }
        
        // Shut down the connection pool
        shutdownConnections();
    }
    
    synchronized static ServicesHealthMonitor getInstance() throws HealthMonitorException {
        if (instance == null) {
            instance = new ServicesHealthMonitor();
        }
        return instance;
    }
    
    static synchronized void startUp() throws HealthMonitorException {
        System.out.println("\nServicesHealthMonitor starting up");
        getInstance();
    }
    
    static synchronized void setEnabled(boolean enabled) throws HealthMonitorException {
        System.out.println("\nServicesHealthMonitor setting enabled to " + enabled + "(previous: " + isEnabled.get() + ")");
        if(enabled == isEnabled.get()) {
            // The setting has not changed, so do nothing
            return;
        }
        
        if(enabled) {
            ModuleSettings.setConfigSetting(MODULE_NAME, IS_ENABLED_KEY, "true");
            isEnabled.set(true);
            getInstance().activateMonitor();
        } else {
            ModuleSettings.setConfigSetting(MODULE_NAME, IS_ENABLED_KEY, "false");
            isEnabled.set(false);
            getInstance().deactivateMonitor();
        }
    }
    
    public static TimingMetric getTimingMetric(String name) {
        if(isEnabled.get()) {
            return new TimingMetric(name);
        }
        return null;
    }
    
    public static void submitTimingMetric(TimingMetric metric) {
        if(isEnabled.get() && (metric != null)) {
            metric.stopTiming();
            try {
                getInstance().addTimingMetric(metric);
            } catch (HealthMonitorException ex) {
                // We don't want calling methods to have to check for exceptions, so just log it
                logger.log(Level.SEVERE, "Error accessing services health monitor", ex);
            }
        }
    }
    
    private void addTimingMetric(TimingMetric metric) {
        try{
            synchronized(this) {
                // There's a small check-then-act situation here where isEnabled
                // may have changed before reaching this code, but it doesn't cause
                // any errors to load a few extra entries into the map after disabling
                // the monitor (they will be deleted if the monitor is re-enabled).
                if(timingInfoMap.containsKey(metric.getName())) {
                    timingInfoMap.get(metric.getName()).addMetric(metric);
                } else {
                    timingInfoMap.put(metric.getName(), new TimingInfo(metric));
                }
            }
        } catch (HealthMonitorException ex) {
            logger.log(Level.SEVERE, "Error adding timing metric", ex);
        }
    }
    
    // Make private once testing is done
    void writeCurrentStateToDatabase() throws HealthMonitorException {
        System.out.println("\nwriteCurrentStateToDatabase");
        
        Map<String, TimingInfo> timingMapCopy;
        synchronized(this) {
            if(! isEnabled.get()) {
                return;
            }
            
            // Make a shallow copy of the map. The map should be small - one entry
            // per metric type.
            timingMapCopy = new HashMap<>(timingInfoMap);
            timingInfoMap.clear();
        }
        
        // Check if there's anything to report
        if(timingMapCopy.keySet().isEmpty()) {
            System.out.println("No timing data to save");
            return;
        }
        
        // Debug
        for(String name:timingMapCopy.keySet()){
            TimingInfo info = timingMapCopy.get(name);
            long timestamp = System.currentTimeMillis();
            System.out.println("  Name: " + name + "\tTimestamp: " + timestamp + "\tAverage: " + info.getAverage() +
                    "\tMax: " + info.getMax() + "\tMin: " + info.getMin());
        }
        
        CoordinationService.Lock lock = getSharedDbLock();
        if(lock == null) {
            throw new HealthMonitorException("Error getting database lock");
        }
        
        try {
            Connection conn = connect();
            if(conn == null) {
                throw new HealthMonitorException("Error getting database connection");
            }

            //"INSERT INTO db_info (name, value) VALUES (?, ?)"
            String addTimingInfoSql = "INSERT INTO timing_data (name, timestamp, count, average, max, min) VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement statement = conn.prepareStatement(addTimingInfoSql)) {

                for(String name:timingMapCopy.keySet()) {
                    TimingInfo info = timingMapCopy.get(name);

                    statement.setString(1, name);
                    statement.setLong(2, System.currentTimeMillis());
                    statement.setLong(3, info.getCount());
                    statement.setLong(4, info.getAverage());
                    statement.setLong(5, info.getMax());
                    statement.setLong(6, info.getMin());

                    statement.execute();
                }

            } catch (SQLException ex) {
                throw new HealthMonitorException("Error saving metric data to database", ex);
            } finally {
                try {
                    conn.close();
                } catch (SQLException ex) {
                    logger.log(Level.SEVERE, "Error closing Connection.", ex);
                }
            }
        } finally {
            try {
                lock.release();
            } catch (CoordinationService.CoordinationServiceException ex) {
                throw new HealthMonitorException("Error releasing database lock", ex);
            }
        }
    }
    
    synchronized void clearCurrentState() {
        timingInfoMap.clear();
    }
    
    static synchronized void close() {
        if(isEnabled.get()) {
            // Stop the timer
            
            // Write current data
            try {
                getInstance().writeCurrentStateToDatabase();
            } catch (HealthMonitorException ex) {
                logger.log(Level.SEVERE, "Error writing final metric data to database", ex);
            }
            
            // Shutdown connection pool
            try {
                getInstance().shutdownConnections();
            } catch (HealthMonitorException ex) {
                logger.log(Level.SEVERE, "Error shutting down connection pool", ex);
            }
            
        }
    }
    
    synchronized void printCurrentState() {
        System.out.println("\nTiming Info Map:");
        for(String name:timingInfoMap.keySet()) {
            System.out.print(name + "\t");
            timingInfoMap.get(name).print();
        }
    }
    
    // Change to private after testing
    boolean databaseExists() throws HealthMonitorException {
        
        System.out.println("\nChecking database existence");
        
        try {
            // Use the same database settings as the case
            CaseDbConnectionInfo db = UserPreferences.getDatabaseConnectionInfo();
            Class.forName("org.postgresql.Driver"); //NON-NLS
            ResultSet rs = null;
            try (Connection connection = DriverManager.getConnection("jdbc:postgresql://" + db.getHost() + ":" + db.getPort() + "/postgres", db.getUserName(), db.getPassword()); //NON-NLS
                    Statement statement = connection.createStatement();) {
                String createCommand = "SELECT 1 AS result FROM pg_database WHERE datname='" + DATABASE_NAME + "'"; 
                System.out.println("  query: " + createCommand);
                rs = statement.executeQuery(createCommand);
                if(rs.next()) {
                    System.out.println("   Exists!");
                    return true;
                }
            } finally {
                if(rs != null) {
                    rs.close();
                }
            }
        } catch (UserPreferencesException | ClassNotFoundException | SQLException ex) {
            throw new HealthMonitorException("Failed check for health monitor database", ex);
        }
        System.out.println("   Does not exist");
        return false;
    }
    
    private void createDatabase() throws HealthMonitorException {
        try {
            System.out.println("\nCreating database " + DATABASE_NAME);
            // Use the same database settings as the case
            CaseDbConnectionInfo db = UserPreferences.getDatabaseConnectionInfo();
            Class.forName("org.postgresql.Driver"); //NON-NLS
            try (Connection connection = DriverManager.getConnection("jdbc:postgresql://" + db.getHost() + ":" + db.getPort() + "/postgres", db.getUserName(), db.getPassword()); //NON-NLS
                    Statement statement = connection.createStatement();) {
                String createCommand = "CREATE DATABASE \"" + DATABASE_NAME + "\" OWNER \"" + db.getUserName() + "\""; //NON-NLS
                statement.execute(createCommand);
            }
        } catch (UserPreferencesException | ClassNotFoundException | SQLException ex) {
            throw new HealthMonitorException("Failed to delete health monitor database", ex);
        }
    }
    
    
    /**
     * Delete the current health monitor database (for testing only)
     * Make private after test
     */
    void deleteDatabase() {
        try {
            // Use the same database settings as the case
            CaseDbConnectionInfo db = UserPreferences.getDatabaseConnectionInfo();
            Class.forName("org.postgresql.Driver"); //NON-NLS
            try (Connection connection = DriverManager.getConnection("jdbc:postgresql://" + db.getHost() + ":" + db.getPort() + "/postgres", db.getUserName(), db.getPassword()); //NON-NLS
                    Statement statement = connection.createStatement();) {
                String deleteCommand = "DROP DATABASE \"" + DATABASE_NAME + "\""; //NON-NLS
                statement.execute(deleteCommand);
            }
        } catch (UserPreferencesException | ClassNotFoundException | SQLException ex) {
            logger.log(Level.SEVERE, "Failed to delete health monitor database", ex);
        }
    }
    
    /**
     * Setup a connection pool for db connections.
     *
     */
    private void setupConnectionPool() throws HealthMonitorException {
        try {
            CaseDbConnectionInfo db = UserPreferences.getDatabaseConnectionInfo();
        
            connectionPool = new BasicDataSource();
            //connectionPool.setUsername(db.getUserName());
            //connectionPool.setPassword(db.getPassword());
            connectionPool.setDriverClassName("org.postgresql.Driver");

            StringBuilder connectionURL = new StringBuilder();
            connectionURL.append("jdbc:postgresql://");
            connectionURL.append(db.getHost());
            connectionURL.append(":");
            connectionURL.append(db.getPort());
            connectionURL.append("/");
            connectionURL.append(DATABASE_NAME);

            connectionPool.setUrl(connectionURL.toString());
            connectionPool.setUsername(db.getUserName());
            connectionPool.setPassword(db.getPassword());

            // tweak pool configuration
            connectionPool.setInitialSize(5); // start with 5 connections
            connectionPool.setMaxIdle(CONN_POOL_SIZE); // max of 10 idle connections
            connectionPool.setValidationQuery("SELECT version()");
        } catch (UserPreferencesException ex) {
            throw new HealthMonitorException("Error loading database configuration", ex);
        }
    }
    
    public void shutdownConnections() throws HealthMonitorException {
        try {
            synchronized(this) {
                if(connectionPool != null){
                    connectionPool.close();
                    connectionPool = null; // force it to be re-created on next connect()
                }
            }
        } catch (SQLException ex) {
            throw new HealthMonitorException("Failed to close existing database connections.", ex); // NON-NLS
        }
    }
    
    private Connection connect() throws HealthMonitorException {
        synchronized (this) {
            if (connectionPool == null) {
                setupConnectionPool();
            }
        }

        try {
            return connectionPool.getConnection();
        } catch (SQLException ex) {
            throw new HealthMonitorException("Error getting connection from connection pool.", ex); // NON-NLS
        }
    }
    
    private void initializeDatabaseSchema() throws HealthMonitorException {
        Connection conn = connect();
        if(conn == null) {
            throw new HealthMonitorException("Error getting database connection");
        }

        try (Statement statement = conn.createStatement()) {
            StringBuilder createTimingTable = new StringBuilder();
            createTimingTable.append("CREATE TABLE IF NOT EXISTS timing_data (");
            createTimingTable.append("id SERIAL PRIMARY KEY,");
            createTimingTable.append("name text NOT NULL,");
            createTimingTable.append("timestamp bigint NOT NULL,");
            createTimingTable.append("count bigint NOT NULL,");
            createTimingTable.append("average bigint NOT NULL,");
            createTimingTable.append("max bigint NOT NULL,");
            createTimingTable.append("min bigint NOT NULL");
            createTimingTable.append(")");
            statement.execute(createTimingTable.toString());
            
            StringBuilder createDbInfoTable = new StringBuilder();
            createDbInfoTable.append("CREATE TABLE IF NOT EXISTS db_info (");
            createDbInfoTable.append("id SERIAL PRIMARY KEY NOT NULL,");
            createDbInfoTable.append("name text NOT NULL,");
            createDbInfoTable.append("value text NOT NULL");
            createDbInfoTable.append(")");
            statement.execute(createDbInfoTable.toString());
            
            statement.execute("INSERT INTO db_info (name, value) VALUES ('SCHEMA_VERSION', '" + CURRENT_DB_SCHEMA_VERSION.getMajor() + "')");
            statement.execute("INSERT INTO db_info (name, value) VALUES ('SCHEMA_MINOR_VERSION', '" + CURRENT_DB_SCHEMA_VERSION.getMinor() + "')");
            
        } catch (SQLException ex) {
            throw new HealthMonitorException("Error initializing database", ex);
        } finally {
            try {
                conn.close();
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, "Error closing Connection.", ex);
            }
        }
    }
    
    private final class DatabaseWriteTask implements Runnable {

        /**
         * Write current metric data to the database
         */
        @Override
        public void run() {
            try {
                System.out.println("\nTimer up - writing to DB");
                getInstance().writeCurrentStateToDatabase();
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Unexpected exception in DatabaseWriteTask", ex); //NON-NLS
            }
        }
    }
    
    private CoordinationService.Lock getExclusiveDbLock() throws HealthMonitorException{
        try {
            String databaseNodeName = DATABASE_NAME;
            CoordinationService.Lock lock = CoordinationService.getInstance().tryGetExclusiveLock(CoordinationService.CategoryNode.HEALTH_MONITOR, databaseNodeName, 5, TimeUnit.MINUTES);

            if(lock != null){
                return lock;
            }
            throw new HealthMonitorException("Error acquiring database lock");
        } catch (InterruptedException | CoordinationService.CoordinationServiceException ex){
            throw new HealthMonitorException("Error acquiring database lock");
        }
    } 
    
    private CoordinationService.Lock getSharedDbLock() throws HealthMonitorException{
        try {
            String databaseNodeName = DATABASE_NAME;
            CoordinationService.Lock lock = CoordinationService.getInstance().tryGetSharedLock(CoordinationService.CategoryNode.HEALTH_MONITOR, databaseNodeName, 5, TimeUnit.MINUTES);

            if(lock != null){
                return lock;
            }
            throw new HealthMonitorException("Error acquiring database lock");
        } catch (InterruptedException | CoordinationService.CoordinationServiceException ex){
            throw new HealthMonitorException("Error acquiring database lock");
        }
    } 
    
    private class TimingInfo {
        private long count;
        private long sum;
        private long max;
        private long min;
        
        TimingInfo(TimingMetric metric) throws HealthMonitorException {
            count = 1;
            sum = metric.getDuration();
            max = metric.getDuration();
            min = metric.getDuration();
        }
        
        void addMetric(TimingMetric metric) throws HealthMonitorException {
            count++;
            sum += metric.getDuration();
            
            if(max < metric.getDuration()) {
                max = metric.getDuration();
            }
            
            if(min > metric.getDuration()) {
                min = metric.getDuration();
            }
        }
        
        long getAverage() {
            return sum / count;
        }
        
        long getMax() {
            return max;
        }
        
        long getMin() {
            return min;
        }
        
        long getCount() {
            return count;
        }
        
        void print() {
            System.out.println("count: " + count + "\tsum: " + sum + "\tmax: " + max + "\tmin: " + min);
        }
    }
}
