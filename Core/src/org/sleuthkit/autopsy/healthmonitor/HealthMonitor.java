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
package org.sleuthkit.autopsy.healthmonitor;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.UUID;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.Random;
import org.apache.commons.dbcp2.BasicDataSource;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.core.UserPreferencesException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadUtils;
import org.sleuthkit.datamodel.CaseDbConnectionInfo;
import org.sleuthkit.datamodel.CaseDbSchemaVersionNumber;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Class for recording data on the health of the system.
 *
 * For timing data: Modules will call getTimingMetric() before the code to be
 * timed to get a TimingMetric object Modules will call submitTimingMetric()
 * with the obtained TimingMetric object to log it
 */
public final class HealthMonitor implements PropertyChangeListener {

    private final static Logger logger = Logger.getLogger(HealthMonitor.class.getName());
    private final static String DATABASE_NAME = "HealthMonitor";
    private final static long DATABASE_WRITE_INTERVAL = 60; // Minutes
    private final static CaseDbSchemaVersionNumber CURRENT_DB_SCHEMA_VERSION = new CaseDbSchemaVersionNumber(1, 2);

    private final static AtomicBoolean isEnabled = new AtomicBoolean(false);
    private static HealthMonitor instance;

    private ScheduledThreadPoolExecutor healthMonitorOutputTimer;
    private final Map<String, TimingInfo> timingInfoMap;
    private final List<UserData> userInfoList;
    private final static int CONN_POOL_SIZE = 10;
    private BasicDataSource connectionPool = null;
    private CaseDbConnectionInfo connectionSettingsInUse = null;
    private String hostName;
    private final String username;

    private HealthMonitor() throws HealthMonitorException {

        // Create the map to collect timing metrics. The map will exist regardless
        // of whether the monitor is enabled.
        timingInfoMap = new HashMap<>();

        // Create the list to hold user information. The list will exist regardless
        // of whether the monitor is enabled.
        userInfoList = new ArrayList<>();

        // Get the host name
        try {
            hostName = java.net.InetAddress.getLocalHost().getHostName();
        } catch (java.net.UnknownHostException ex) {
            // Continue on, but log the error and generate a UUID to use for this session
            hostName = UUID.randomUUID().toString();
            logger.log(Level.SEVERE, "Unable to look up host name - falling back to UUID " + hostName, ex);
        }
        
        // Get the user name
        username = System.getProperty("user.name");

        // Read from the database to determine if the module is enabled
        updateFromGlobalEnabledStatus();

        // Start the timer for database checks and writes
        startTimer();
    }

    /**
     * Get the instance of the HealthMonitor
     *
     * @return the instance
     *
     * @throws HealthMonitorException
     */
    synchronized static HealthMonitor getInstance() throws HealthMonitorException {
        if (instance == null) {
            instance = new HealthMonitor();
            Case.addPropertyChangeListener(instance);
        }
        return instance;
    }

    /**
     * Activate the health monitor. Creates/initialized the database (if
     * needed), clears any existing metrics out of the maps, and sets up the
     * timer for writing to the database.
     *
     * @throws HealthMonitorException
     */
    private synchronized void activateMonitorLocally() throws HealthMonitorException {

        logger.log(Level.INFO, "Activating Servies Health Monitor");

        // Make sure there are no left over connections to an old database
        shutdownConnections();

        if (!UserPreferences.getIsMultiUserModeEnabled()) {
            throw new HealthMonitorException("Multi user mode is not enabled - can not activate health monitor");
        }

        // Set up database (if needed)
        try (CoordinationService.Lock lock = getExclusiveDbLock()) {
            if (lock == null) {
                throw new HealthMonitorException("Error getting database lock");
            }

            // Check if the database exists
            if (!databaseExists()) {

                // If not, create a new one
                createDatabase();
            }

            if (!databaseIsInitialized()) {
                initializeDatabaseSchema();
            }
            
            if (getVersion().compareTo(CURRENT_DB_SCHEMA_VERSION) < 0) {
                upgradeDatabaseSchema();
            }

        } catch (CoordinationService.CoordinationServiceException ex) {
            throw new HealthMonitorException("Error releasing database lock", ex);
        }

        // Clear out any old data
        timingInfoMap.clear();
        userInfoList.clear();
    }

    /**
     * Upgrade an older database
     */
    private void upgradeDatabaseSchema() throws HealthMonitorException {

        logger.log(Level.INFO, "Upgrading Health Monitor database");
        CaseDbSchemaVersionNumber currentSchema = getVersion();

        Connection conn = connect();
        if (conn == null) {
            throw new HealthMonitorException("Error getting database connection");
        }
        ResultSet resultSet = null;

        try (Statement statement = conn.createStatement()) {
            conn.setAutoCommit(false);

            // NOTE: Due to a bug in the upgrade code, earlier versions of Autopsy will erroneously
            // run the upgrade if the database is a higher version than it expects. Therefore all
            // table changes must account for the possiblility of running multiple times.
            
            // Upgrade from 1.0 to 1.1
            // Changes: user_data table added
            if (currentSchema.compareTo(new CaseDbSchemaVersionNumber(1, 1)) < 0) {

                // Add the user_data table
                statement.execute("CREATE TABLE IF NOT EXISTS user_data ("
                        + "id SERIAL PRIMARY KEY,"
                        + "host text NOT NULL,"
                        + "timestamp bigint NOT NULL,"
                        + "event_type int NOT NULL,"
                        + "is_examiner boolean NOT NULL,"
                        + "case_name text NOT NULL"
                        + ")");
            }
            
            // Upgrade from 1.1 to 1.2
            // Changes: username added to user_data table
            if (currentSchema.compareTo(new CaseDbSchemaVersionNumber(1, 2)) < 0) {

                resultSet = statement.executeQuery("SELECT column_name " +
                        "FROM information_schema.columns " +
                        "WHERE table_name='user_data' and column_name='username'");
                if (! resultSet.next()) {
                    // Add the user_data table
                    statement.execute("ALTER TABLE user_data ADD COLUMN username text");
                }
            }

            // Update the schema version
            statement.execute("UPDATE db_info SET value='" + CURRENT_DB_SCHEMA_VERSION.getMajor() + "' WHERE name='SCHEMA_VERSION'");
            statement.execute("UPDATE db_info SET value='" + CURRENT_DB_SCHEMA_VERSION.getMinor() + "' WHERE name='SCHEMA_MINOR_VERSION'");

            conn.commit();
            logger.log(Level.INFO, "Health Monitor database upgraded to version {0}", CURRENT_DB_SCHEMA_VERSION.toString());
        } catch (SQLException ex) {
            try {
                conn.rollback();
            } catch (SQLException ex2) {
                logger.log(Level.SEVERE, "Rollback error");
            }
            throw new HealthMonitorException("Error upgrading database", ex);
        } finally {
            if (resultSet != null) {
                try {
                    resultSet.close();
                } catch (SQLException ex2) {
                    logger.log(Level.SEVERE, "Error closing result set");
                }   
            }
            try {
                conn.close();
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, "Error closing connection.", ex);
            }
        }
    }

    /**
     * Deactivate the health monitor. This should only be used when disabling
     * the monitor, not when Autopsy is closing. Clears out any metrics that
     * haven't been written, stops the database write timer, and shuts down the
     * connection pool.
     *
     * @throws HealthMonitorException
     */
    private synchronized void deactivateMonitorLocally() throws HealthMonitorException {

        logger.log(Level.INFO, "Deactivating Servies Health Monitor");

        // Clear out the collected data
        timingInfoMap.clear();

        // Shut down the connection pool
        shutdownConnections();
    }

    /**
     * Start the ScheduledThreadPoolExecutor that will handle the database
     * writes.
     */
    private synchronized void startTimer() {
        // Make sure the previous executor (if it exists) has been stopped
        stopTimer();

        healthMonitorOutputTimer = new ScheduledThreadPoolExecutor(1, new ThreadFactoryBuilder().setNameFormat("health_monitor_timer").build());
        healthMonitorOutputTimer.scheduleWithFixedDelay(new PeriodicHealthMonitorTask(), DATABASE_WRITE_INTERVAL, DATABASE_WRITE_INTERVAL, TimeUnit.MINUTES);
    }

    /**
     * Stop the ScheduledThreadPoolExecutor to prevent further database writes.
     */
    private synchronized void stopTimer() {
        if (healthMonitorOutputTimer != null) {
            ThreadUtils.shutDownTaskExecutor(healthMonitorOutputTimer);
        }
    }

    /**
     * Called from the installer to set up the Health Monitor instance at
     * startup.
     *
     * @throws HealthMonitorException
     */
    static synchronized void startUpIfEnabled() throws HealthMonitorException {
        getInstance().addUserEvent(UserEvent.LOG_ON);
    }

    /**
     * Called when the application is closing. Create a log off event and write
     * all existing metrics to the database
     *
     * @throws HealthMonitorException
     */
    static synchronized void shutdown() throws HealthMonitorException {
        getInstance().addUserEvent(UserEvent.LOG_OFF);
        recordMetrics();
    }

    /**
     * Enabled/disable the health monitor.
     *
     * @param enabled true to enable the monitor, false to disable it
     *
     * @throws HealthMonitorException
     */
    static synchronized void setEnabled(boolean enabled) throws HealthMonitorException {
        if (enabled == isEnabled.get()) {
            // The setting has not changed, so do nothing
            return;
        }

        if (enabled) {
            getInstance().activateMonitorLocally();

            // If activateMonitor fails, we won't update this
            getInstance().setGlobalEnabledStatusInDB(true);
            isEnabled.set(true);
        } else {
            if (isEnabled.get()) {
                // If we were enabled before, set the global state to disabled
                getInstance().setGlobalEnabledStatusInDB(false);
            }
            isEnabled.set(false);
            getInstance().deactivateMonitorLocally();
        }
    }

    /**
     * Get a metric that will measure the time to execute a section of code.
     * Call this before the section of code to be timed and then submit it
     * afterward using submitTimingMetric(). This method is safe to call
     * regardless of whether the health monitor is enabled.
     *
     * @param name A short but descriptive name describing the code being timed.
     *             This name will appear in the UI.
     *
     * @return The TimingMetric object
     */
    public static TimingMetric getTimingMetric(String name) {
        if (isEnabled.get()) {
            return new TimingMetric(name);
        }
        return null;
    }

    /**
     * Submit the metric that was previously obtained through getTimingMetric().
     * Call this immediately after the section of code being timed. This method
     * is safe to call regardless of whether the health monitor is enabled.
     *
     * @param metric The TimingMetric object obtained from getTimingMetric()
     */
    public static void submitTimingMetric(TimingMetric metric) {
        if (isEnabled.get() && (metric != null)) {
            metric.stopTiming();
            try {
                getInstance().addTimingMetric(metric);
            } catch (HealthMonitorException ex) {
                // We don't want calling methods to have to check for exceptions, so just log it
                logger.log(Level.SEVERE, "Error adding timing metric", ex);
            }
        }
    }

    /**
     * Submit the metric that was previously obtained through getTimingMetric(),
     * incorporating a count that the time should be divided by. Call this
     * immediately after the section of code being timed. This method is safe to
     * call regardless of whether the health monitor is enabled.
     *
     * @param metric        The TimingMetric object obtained from
     *                      getTimingMetric()
     * @param normalization The number to divide the time by (a zero here will
     *                      be treated as a one)
     */
    public static void submitNormalizedTimingMetric(TimingMetric metric, long normalization) {
        if (isEnabled.get() && (metric != null)) {
            metric.stopTiming();
            try {
                metric.normalize(normalization);
                getInstance().addTimingMetric(metric);
            } catch (HealthMonitorException ex) {
                // We don't want calling methods to have to check for exceptions, so just log it
                logger.log(Level.SEVERE, "Error adding timing metric", ex);
            }
        }
    }

    /**
     * Add the timing metric data to the map.
     *
     * @param metric The metric to add. stopTiming() should already have been
     *               called.
     */
    private void addTimingMetric(TimingMetric metric) throws HealthMonitorException {

        // Do as little as possible within the synchronized block to minimize
        // blocking with multiple threads.
        synchronized (this) {
            // There's a small check-then-act situation here where isEnabled
            // may have changed before reaching this code. This is fine - 
            // the map still exists and any extra data added after the monitor
            // is disabled will be deleted if the monitor is re-enabled. This
            // seems preferable to doing another check on isEnabled within
            // the synchronized block.
            if (timingInfoMap.containsKey(metric.getName())) {
                timingInfoMap.get(metric.getName()).addMetric(metric);
            } else {
                timingInfoMap.put(metric.getName(), new TimingInfo(metric));
            }
        }
    }

    /**
     * Add a user event to the list.
     *
     * @param eventType
     */
    private void addUserEvent(UserEvent eventType) {
        UserData userInfo = new UserData(eventType);
        synchronized (this) {
            userInfoList.add(userInfo);
        }
    }

    /**
     * Time a database query. Database queries are hard to test in normal
     * processing because the time is so dependent on the size of the tables
     * being queried. We use getImages here because every table it queries is
     * the same size (one entry for each image) so we a) know the size of the
     * tables and b) can use that table size to do normalization.
     *
     * @throws HealthMonitorException
     */
    private void performDatabaseQuery() throws HealthMonitorException {
        try {
            SleuthkitCase skCase = Case.getCurrentCaseThrows().getSleuthkitCase();
            TimingMetric metric = HealthMonitor.getTimingMetric("Database: getImages query");
            List<Image> images = skCase.getImages();

            // Through testing we found that this normalization gives us fairly 
            // consistent results for different numbers of data sources.
            long normalization = images.size();
            if (images.isEmpty()) {
                normalization += 2;
            } else if (images.size() == 1) {
                normalization += 3;
            } else if (images.size() < 10) {
                normalization += 5;
            } else {
                normalization += 7;
            }

            HealthMonitor.submitNormalizedTimingMetric(metric, normalization);
        } catch (NoCurrentCaseException ex) {
            // If there's no case open, we just can't do the metrics.
        } catch (TskCoreException ex) {
            throw new HealthMonitorException("Error running getImages()", ex);
        }
    }

    /**
     * Collect metrics at a scheduled time.
     *
     * @throws HealthMonitorException
     */
    private void gatherTimerBasedMetrics() throws HealthMonitorException {
        performDatabaseQuery();
    }

    /**
     * Write the collected metrics to the database.
     *
     * @throws HealthMonitorException
     */
    private void writeCurrentStateToDatabase() throws HealthMonitorException {

        Map<String, TimingInfo> timingMapCopy;
        List<UserData> userDataCopy;

        // Do as little as possible within the synchronized block since it will
        // block threads attempting to record metrics.
        synchronized (this) {
            if (!isEnabled.get()) {
                return;
            }

            // Make a shallow copy of the timing map. The map should be small - one entry
            // per metric name.
            timingMapCopy = new HashMap<>(timingInfoMap);
            timingInfoMap.clear();

            userDataCopy = new ArrayList<>(userInfoList);
            userInfoList.clear();
        }

        // Check if there's anything to report
        if (timingMapCopy.keySet().isEmpty() && userDataCopy.isEmpty()) {
            return;
        }

        logger.log(Level.INFO, "Writing health monitor metrics to database");

        // Write to the database        
        try (CoordinationService.Lock lock = getSharedDbLock()) {
            if (lock == null) {
                throw new HealthMonitorException("Error getting database lock");
            }

            Connection conn = connect();
            if (conn == null) {
                throw new HealthMonitorException("Error getting database connection");
            }

            // Add metrics to the database
            String addTimingInfoSql = "INSERT INTO timing_data (name, host, timestamp, count, average, max, min) VALUES (?, ?, ?, ?, ?, ?, ?)";
            String addUserInfoSql = "INSERT INTO user_data (host, username, timestamp, event_type, is_examiner, case_name) VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement timingStatement = conn.prepareStatement(addTimingInfoSql);
                    PreparedStatement userStatement = conn.prepareStatement(addUserInfoSql)) {

                for (String name : timingMapCopy.keySet()) {
                    TimingInfo info = timingMapCopy.get(name);

                    timingStatement.setString(1, name);
                    timingStatement.setString(2, hostName);
                    timingStatement.setLong(3, System.currentTimeMillis());
                    timingStatement.setLong(4, info.getCount());
                    timingStatement.setDouble(5, info.getAverage());
                    timingStatement.setDouble(6, info.getMax());
                    timingStatement.setDouble(7, info.getMin());

                    timingStatement.execute();
                }

                for (UserData userInfo : userDataCopy) {
                    userStatement.setString(1, hostName);
                    userStatement.setString(2, username);
                    userStatement.setLong(3, userInfo.getTimestamp());
                    userStatement.setInt(4, userInfo.getEventType().getEventValue());
                    userStatement.setBoolean(5, userInfo.isExaminerNode());
                    userStatement.setString(6, userInfo.getCaseName());
                    userStatement.execute();
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
        } catch (CoordinationService.CoordinationServiceException ex) {
            throw new HealthMonitorException("Error releasing database lock", ex);
        }
    }

    /**
     * Check whether the health monitor database exists. Does not check the
     * schema.
     *
     * @return true if the database exists, false otherwise
     *
     * @throws HealthMonitorException
     */
    private boolean databaseExists() throws HealthMonitorException {
        try {
            // Use the same database settings as the case
            CaseDbConnectionInfo db = UserPreferences.getDatabaseConnectionInfo();
            Class.forName("org.postgresql.Driver"); //NON-NLS
            ResultSet rs = null;
            try (Connection connection = DriverManager.getConnection("jdbc:postgresql://" + db.getHost() + ":" + db.getPort() + "/postgres", db.getUserName(), db.getPassword()); //NON-NLS
                    Statement statement = connection.createStatement();) {
                String createCommand = "SELECT 1 AS result FROM pg_database WHERE datname='" + DATABASE_NAME + "'";
                rs = statement.executeQuery(createCommand);
                if (rs.next()) {
                    return true;
                }
            } finally {
                if (rs != null) {
                    rs.close();
                }
            }
        } catch (UserPreferencesException | ClassNotFoundException | SQLException ex) {
            throw new HealthMonitorException("Failed check for health monitor database", ex);
        }
        return false;
    }

    /**
     * Create a new health monitor database.
     *
     * @throws HealthMonitorException
     */
    private void createDatabase() throws HealthMonitorException {
        try {
            // Use the same database settings as the case
            CaseDbConnectionInfo db = UserPreferences.getDatabaseConnectionInfo();
            Class.forName("org.postgresql.Driver"); //NON-NLS
            try (Connection connection = DriverManager.getConnection("jdbc:postgresql://" + db.getHost() + ":" + db.getPort() + "/postgres", db.getUserName(), db.getPassword()); //NON-NLS
                    Statement statement = connection.createStatement();) {
                String createCommand = "CREATE DATABASE \"" + DATABASE_NAME + "\" OWNER \"" + db.getUserName() + "\""; //NON-NLS
                statement.execute(createCommand);
            }
            logger.log(Level.INFO, "Created new health monitor database " + DATABASE_NAME);
        } catch (UserPreferencesException | ClassNotFoundException | SQLException ex) {
            throw new HealthMonitorException("Failed to delete health monitor database", ex);
        }
    }

    /**
     * Setup a connection pool for db connections.
     *
     * @throws HealthMonitorException
     */
    private void setupConnectionPool() throws HealthMonitorException {
        try {
            CaseDbConnectionInfo db = UserPreferences.getDatabaseConnectionInfo();
            connectionSettingsInUse = db;

            connectionPool = new BasicDataSource();
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
            connectionPool.setInitialSize(3); // start with 3 connections
            connectionPool.setMaxIdle(CONN_POOL_SIZE); // max of 10 idle connections
            connectionPool.setValidationQuery("SELECT version()");
        } catch (UserPreferencesException ex) {
            throw new HealthMonitorException("Error loading database configuration", ex);
        }
    }

    /**
     * Shut down the connection pool
     *
     * @throws HealthMonitorException
     */
    private void shutdownConnections() throws HealthMonitorException {
        try {
            synchronized (this) {
                if (connectionPool != null) {
                    connectionPool.close();
                    connectionPool = null; // force it to be re-created on next connect()
                }
            }
        } catch (SQLException ex) {
            throw new HealthMonitorException("Failed to close existing database connections.", ex); // NON-NLS
        }
    }

    /**
     * Get a database connection. Sets up the connection pool if needed.
     *
     * @return The Connection object
     *
     * @throws HealthMonitorException
     */
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

    /**
     * Test whether the database schema has been initialized. We do this by
     * looking for the version number.
     *
     * @return True if it has been initialized, false otherwise.
     *
     * @throws HealthMonitorException
     */
    private boolean databaseIsInitialized() throws HealthMonitorException {
        Connection conn = connect();
        if (conn == null) {
            throw new HealthMonitorException("Error getting database connection");
        }
        ResultSet resultSet = null;

        try (Statement statement = conn.createStatement()) {
            resultSet = statement.executeQuery("SELECT value FROM db_info WHERE name='SCHEMA_VERSION'");
            return resultSet.next();
        } catch (SQLException ex) {
            // This likely just means that the db_info table does not exist
            return false;
        } finally {
            if (resultSet != null) {
                try {
                    resultSet.close();
                } catch (SQLException ex) {
                    logger.log(Level.SEVERE, "Error closing result set", ex);
                }
            }
            try {
                conn.close();
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, "Error closing Connection.", ex);
            }
        }
    }

    /**
     * Return whether the health monitor is locally enabled. This does not query
     * the database.
     *
     * @return true if it is enabled, false otherwise
     */
    static boolean monitorIsEnabled() {
        return isEnabled.get();
    }

    /**
     * Check whether monitoring should be enabled from the monitor database and
     * enable/disable as needed.
     *
     * @throws HealthMonitorException
     */
    synchronized void updateFromGlobalEnabledStatus() throws HealthMonitorException {

        boolean previouslyEnabled = monitorIsEnabled();

        // We can't even check the database if multi user settings aren't enabled.
        if (!UserPreferences.getIsMultiUserModeEnabled()) {
            isEnabled.set(false);

            if (previouslyEnabled) {
                deactivateMonitorLocally();
            }
            return;
        }

        // If the health monitor database doesn't exist or if it is not initialized, 
        // then monitoring isn't enabled
        if ((!databaseExists()) || (!databaseIsInitialized())) {
            isEnabled.set(false);

            if (previouslyEnabled) {
                deactivateMonitorLocally();
            }
            return;
        }

        // If we're currently enabled, check whether the multiuser settings have changed.
        // If they have, force a reset on the connection pool.
        if (previouslyEnabled && (connectionSettingsInUse != null)) {
            try {
                CaseDbConnectionInfo currentSettings = UserPreferences.getDatabaseConnectionInfo();
                if (!(connectionSettingsInUse.getUserName().equals(currentSettings.getUserName())
                        && connectionSettingsInUse.getPassword().equals(currentSettings.getPassword())
                        && connectionSettingsInUse.getPort().equals(currentSettings.getPort())
                        && connectionSettingsInUse.getHost().equals(currentSettings.getHost()))) {
                    shutdownConnections();
                }
            } catch (UserPreferencesException ex) {
                throw new HealthMonitorException("Error reading database connection info", ex);
            }
        }

        boolean currentlyEnabled = getGlobalEnabledStatusFromDB();
        if (currentlyEnabled != previouslyEnabled) {
            if (!currentlyEnabled) {
                isEnabled.set(false);
                deactivateMonitorLocally();
            } else {
                isEnabled.set(true);
                activateMonitorLocally();
            }
        }
    }

    /**
     * Read the enabled status from the database. Check that the health monitor
     * database exists before calling this.
     *
     * @return true if the database is enabled, false otherwise
     *
     * @throws HealthMonitorException
     */
    private boolean getGlobalEnabledStatusFromDB() throws HealthMonitorException {

        try (Connection conn = connect();
                Statement statement = conn.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT value FROM db_info WHERE name='MONITOR_ENABLED'")) {

            if (resultSet.next()) {
                return (resultSet.getBoolean("value"));
            }
            throw new HealthMonitorException("No enabled status found in database");
        } catch (SQLException ex) {
            throw new HealthMonitorException("Error initializing database", ex);
        }
    }

    /**
     * Set the global enabled status in the database.
     *
     * @throws HealthMonitorException
     */
    private void setGlobalEnabledStatusInDB(boolean status) throws HealthMonitorException {

        try (Connection conn = connect();
                Statement statement = conn.createStatement();) {
            statement.execute("UPDATE db_info SET value='" + status + "' WHERE name='MONITOR_ENABLED'");
        } catch (SQLException ex) {
            throw new HealthMonitorException("Error setting enabled status", ex);
        }
    }

    /**
     * Get the current schema version
     *
     * @return the current schema version
     *
     * @throws HealthMonitorException
     */
    private CaseDbSchemaVersionNumber getVersion() throws HealthMonitorException {
        Connection conn = connect();
        if (conn == null) {
            throw new HealthMonitorException("Error getting database connection");
        }
        ResultSet resultSet = null;

        try (Statement statement = conn.createStatement()) {
            int minorVersion = 0;
            int majorVersion = 0;
            resultSet = statement.executeQuery("SELECT value FROM db_info WHERE name='SCHEMA_MINOR_VERSION'");
            if (resultSet.next()) {
                String minorVersionStr = resultSet.getString("value");
                try {
                    minorVersion = Integer.parseInt(minorVersionStr);
                } catch (NumberFormatException ex) {
                    throw new HealthMonitorException("Bad value for schema minor version (" + minorVersionStr + ") - database is corrupt");
                }
            }

            resultSet = statement.executeQuery("SELECT value FROM db_info WHERE name='SCHEMA_VERSION'");
            if (resultSet.next()) {
                String majorVersionStr = resultSet.getString("value");
                try {
                    majorVersion = Integer.parseInt(majorVersionStr);
                } catch (NumberFormatException ex) {
                    throw new HealthMonitorException("Bad value for schema version (" + majorVersionStr + ") - database is corrupt");
                }
            }

            return new CaseDbSchemaVersionNumber(majorVersion, minorVersion);
        } catch (SQLException ex) {
            throw new HealthMonitorException("Error initializing database", ex);
        } finally {
            if (resultSet != null) {
                try {
                    resultSet.close();
                } catch (SQLException ex) {
                    logger.log(Level.SEVERE, "Error closing result set", ex);
                }
            }
            try {
                conn.close();
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, "Error closing Connection.", ex);
            }
        }
    }

    /**
     * Initialize the database.
     *
     * @throws HealthMonitorException
     */
    private void initializeDatabaseSchema() throws HealthMonitorException {
        Connection conn = connect();
        if (conn == null) {
            throw new HealthMonitorException("Error getting database connection");
        }

        try (Statement statement = conn.createStatement()) {
            conn.setAutoCommit(false);

            statement.execute("CREATE TABLE IF NOT EXISTS timing_data ("
                    + "id SERIAL PRIMARY KEY,"
                    + "name text NOT NULL,"
                    + "host text NOT NULL,"
                    + "timestamp bigint NOT NULL,"
                    + "count bigint NOT NULL,"
                    + "average double precision NOT NULL,"
                    + "max double precision NOT NULL,"
                    + "min double precision NOT NULL"
                    + ")");

            statement.execute("CREATE TABLE IF NOT EXISTS db_info ("
                    + "id SERIAL PRIMARY KEY NOT NULL,"
                    + "name text NOT NULL,"
                    + "value text NOT NULL"
                    + ")");

            statement.execute("CREATE TABLE IF NOT EXISTS user_data ("
                    + "id SERIAL PRIMARY KEY,"
                    + "host text NOT NULL,"
                    + "timestamp bigint NOT NULL,"
                    + "event_type int NOT NULL,"
                    + "is_examiner BOOLEAN NOT NULL,"
                    + "case_name text NOT NULL,"
                    + "username text"
                    + ")");

            statement.execute("INSERT INTO db_info (name, value) VALUES ('SCHEMA_VERSION', '" + CURRENT_DB_SCHEMA_VERSION.getMajor() + "')");
            statement.execute("INSERT INTO db_info (name, value) VALUES ('SCHEMA_MINOR_VERSION', '" + CURRENT_DB_SCHEMA_VERSION.getMinor() + "')");
            statement.execute("INSERT INTO db_info (name, value) VALUES ('MONITOR_ENABLED', 'true')");

            conn.commit();
        } catch (SQLException ex) {
            try {
                conn.rollback();
            } catch (SQLException ex2) {
                logger.log(Level.SEVERE, "Rollback error");
            }
            throw new HealthMonitorException("Error initializing database", ex);
        } finally {
            try {
                conn.close();
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, "Error closing connection.", ex);
            }
        }
    }

    /**
     * The task called by the ScheduledThreadPoolExecutor to handle the periodic
     * database update
     */
    static final class PeriodicHealthMonitorTask implements Runnable {

        @Override
        public void run() {
            recordMetrics();
        }
    }

    /**
     * Perform all periodic tasks: - Check if monitoring has been enabled /
     * disabled in the database - Gather any additional metrics - Write current
     * metric data to the database Do not run this from a new thread if the
     * case/application is closing.
     */
    private static void recordMetrics() {
        try {
            getInstance().updateFromGlobalEnabledStatus();
            if (monitorIsEnabled()) {
                getInstance().gatherTimerBasedMetrics();
                getInstance().writeCurrentStateToDatabase();
            }
        } catch (HealthMonitorException ex) {
            logger.log(Level.SEVERE, "Error recording health monitor metrics", ex); //NON-NLS
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {

        switch (Case.Events.valueOf(evt.getPropertyName())) {

            case CURRENT_CASE:
                if ((null == evt.getNewValue()) && (evt.getOldValue() instanceof Case)) {
                    // Case is closing
                    addUserEvent(UserEvent.CASE_CLOSE);

                } else if ((null == evt.getOldValue()) && (evt.getNewValue() instanceof Case)) {
                    // Case is opening
                    addUserEvent(UserEvent.CASE_OPEN);
                }
                break;
        }
    }

    /**
     * Debugging method to generate sample data for the database. It will delete
     * all current timing data and replace it with randomly generated values. If
     * there is more than one node, the second node's times will trend upwards.
     */
    void populateDatabaseWithSampleData(int nDays, int nNodes, boolean createVerificationData) throws HealthMonitorException {

        if (!isEnabled.get()) {
            throw new HealthMonitorException("Can't populate database - monitor not enabled");
        }

        // Get the database lock
        CoordinationService.Lock lock = getSharedDbLock();
        if (lock == null) {
            throw new HealthMonitorException("Error getting database lock");
        }

        String[] metricNames = {"Disk Reads: Hash calculation", "Database: getImages query", "Solr: Index chunk", "Solr: Connectivity check",
            "Central Repository: Notable artifact query", "Central Repository: Bulk insert"}; // NON-NLS 

        Random rand = new Random();

        long maxTimestamp = System.currentTimeMillis();
        long millisPerHour = 1000 * 60 * 60;
        long minTimestamp = maxTimestamp - (nDays * (millisPerHour * 24));

        Connection conn = null;
        try {
            conn = connect();
            if (conn == null) {
                throw new HealthMonitorException("Error getting database connection");
            }

            try (Statement statement = conn.createStatement()) {

                statement.execute("DELETE FROM timing_data"); // NON-NLS
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, "Error clearing timing data", ex);
                return;
            }

            // Add timing metrics to the database
            String addTimingInfoSql = "INSERT INTO timing_data (name, host, timestamp, count, average, max, min) VALUES (?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement statement = conn.prepareStatement(addTimingInfoSql)) {

                for (String metricName : metricNames) {

                    long baseIndex = rand.nextInt(900) + 100;
                    int multiplier = rand.nextInt(5);
                    long minIndexTimeNanos;
                    switch (multiplier) {
                        case 0:
                            minIndexTimeNanos = baseIndex;
                            break;
                        case 1:
                            minIndexTimeNanos = baseIndex * 1000;
                            break;
                        default:
                            minIndexTimeNanos = baseIndex * 1000 * 1000;
                            break;
                    }

                    long maxIndexTimeOverMin = minIndexTimeNanos * 3;

                    for (int node = 0; node < nNodes; node++) {

                        String host = "testHost" + node; // NON-NLS

                        double count = 0;
                        double maxCount = nDays * 24 + 1;

                        // Record data every hour, with a small amount of randomness about when it starts
                        for (long timestamp = minTimestamp + rand.nextInt(1000 * 60 * 55); timestamp < maxTimestamp; timestamp += millisPerHour) {

                            double aveTime;

                            // This creates data that increases in the last couple of days of the simulated
                            // collection
                            count++;
                            double slowNodeMultiplier = 1.0;
                            if ((maxCount - count) <= 3 * 24) {
                                slowNodeMultiplier += (3 - (maxCount - count) / 24) * 0.33;
                            }

                            if (!createVerificationData) {
                                // Try to make a reasonable sample data set, with most points in a small range
                                // but some higher and lower
                                int outlierVal = rand.nextInt(30);
                                long randVal = rand.nextLong();
                                if (randVal < 0) {
                                    randVal *= -1;
                                }
                                if (outlierVal < 2) {
                                    aveTime = minIndexTimeNanos + maxIndexTimeOverMin + randVal % maxIndexTimeOverMin;
                                } else if (outlierVal == 2) {
                                    aveTime = (minIndexTimeNanos / 2) + randVal % (minIndexTimeNanos / 2);
                                } else if (outlierVal < 17) {
                                    aveTime = minIndexTimeNanos + randVal % (maxIndexTimeOverMin / 2);
                                } else {
                                    aveTime = minIndexTimeNanos + randVal % maxIndexTimeOverMin;
                                }

                                if (node == 1) {
                                    aveTime = aveTime * slowNodeMultiplier;
                                }
                            } else {
                                // Create a data set strictly for testing that the display is working
                                // correctly. The average time will equal the day of the month from
                                // the timestamp (in milliseconds)
                                Calendar thisDate = new GregorianCalendar();
                                thisDate.setTimeInMillis(timestamp);
                                int day = thisDate.get(Calendar.DAY_OF_MONTH);
                                aveTime = day * 1000000;
                            }

                            statement.setString(1, metricName);
                            statement.setString(2, host);
                            statement.setLong(3, timestamp);
                            statement.setLong(4, 0);
                            statement.setDouble(5, aveTime / 1000000);
                            statement.setDouble(6, 0);
                            statement.setDouble(7, 0);

                            statement.execute();
                        }
                    }
                }
            } catch (SQLException ex) {
                throw new HealthMonitorException("Error saving metric data to database", ex);
            }
        } finally {
            try {
                if (conn != null) {
                    conn.close();
                }
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, "Error closing Connection.", ex);
            }
            try {
                lock.release();
            } catch (CoordinationService.CoordinationServiceException ex) {
                throw new HealthMonitorException("Error releasing database lock", ex);
            }
        }
    }

    /**
     * Get timing metrics currently stored in the database.
     *
     * @param timeRange Maximum age for returned metrics (in milliseconds)
     *
     * @return A map with metric name mapped to a list of data
     *
     * @throws HealthMonitorException
     */
    Map<String, List<DatabaseTimingResult>> getTimingMetricsFromDatabase(long timeRange) throws HealthMonitorException {

        // Make sure the monitor is enabled. It could theoretically get disabled after this
        // check but it doesn't seem worth holding a lock to ensure that it doesn't since that
        // may slow down ingest.
        if (!isEnabled.get()) {
            throw new HealthMonitorException("Health Monitor is not enabled");
        }

        // Calculate the smallest timestamp we should return
        long minimumTimestamp = System.currentTimeMillis() - timeRange;

        try (CoordinationService.Lock lock = getSharedDbLock()) {
            if (lock == null) {
                throw new HealthMonitorException("Error getting database lock");
            }

            Connection conn = connect();
            if (conn == null) {
                throw new HealthMonitorException("Error getting database connection");
            }

            Map<String, List<DatabaseTimingResult>> resultMap = new HashMap<>();

            try (Statement statement = conn.createStatement();
                    ResultSet resultSet = statement.executeQuery("SELECT * FROM timing_data WHERE timestamp > " + minimumTimestamp)) {

                while (resultSet.next()) {
                    String name = resultSet.getString("name");
                    DatabaseTimingResult timingResult = new DatabaseTimingResult(resultSet);

                    if (resultMap.containsKey(name)) {
                        resultMap.get(name).add(timingResult);
                    } else {
                        List<DatabaseTimingResult> resultList = new ArrayList<>();
                        resultList.add(timingResult);
                        resultMap.put(name, resultList);
                    }
                }
                return resultMap;
            } catch (SQLException ex) {
                throw new HealthMonitorException("Error reading timing metrics from database", ex);
            } finally {
                try {
                    conn.close();
                } catch (SQLException ex) {
                    logger.log(Level.SEVERE, "Error closing Connection.", ex);
                }
            }
        } catch (CoordinationService.CoordinationServiceException ex) {
            throw new HealthMonitorException("Error getting database lock", ex);
        }
    }

    /**
     * Get user metrics currently stored in the database.
     *
     * @param timeRange Maximum age for returned metrics (in milliseconds)
     *
     * @return A list of user metrics
     *
     * @throws HealthMonitorException
     */
    List<UserData> getUserMetricsFromDatabase(long timeRange) throws HealthMonitorException {

        // Make sure the monitor is enabled. It could theoretically get disabled after this
        // check but it doesn't seem worth holding a lock to ensure that it doesn't since that
        // may slow down ingest.
        if (!isEnabled.get()) {
            throw new HealthMonitorException("Health Monitor is not enabled");
        }

        // Calculate the smallest timestamp we should return
        long minimumTimestamp = System.currentTimeMillis() - timeRange;

        try (CoordinationService.Lock lock = getSharedDbLock()) {
            if (lock == null) {
                throw new HealthMonitorException("Error getting database lock");
            }

            List<UserData> resultList = new ArrayList<>();

            try (Connection conn = connect();
                    Statement statement = conn.createStatement();
                    ResultSet resultSet = statement.executeQuery("SELECT * FROM user_data WHERE timestamp > " + minimumTimestamp)) {

                while (resultSet.next()) {
                    resultList.add(new UserData(resultSet));
                }
                return resultList;
            } catch (SQLException ex) {
                throw new HealthMonitorException("Error reading user metrics from database", ex);
            }
        } catch (CoordinationService.CoordinationServiceException ex) {
            throw new HealthMonitorException("Error getting database lock", ex);
        }
    }

    /**
     * Get an exclusive lock for the health monitor database. Acquire this
     * before creating, initializing, or updating the database schema.
     *
     * @return The lock
     *
     * @throws HealthMonitorException
     */
    private CoordinationService.Lock getExclusiveDbLock() throws HealthMonitorException {
        try {
            CoordinationService.Lock lock = CoordinationService.getInstance().tryGetExclusiveLock(CoordinationService.CategoryNode.HEALTH_MONITOR, DATABASE_NAME, 5, TimeUnit.MINUTES);

            if (lock != null) {
                return lock;
            }
            throw new HealthMonitorException("Error acquiring database lock");
        } catch (InterruptedException | CoordinationService.CoordinationServiceException ex) {
            throw new HealthMonitorException("Error acquiring database lock", ex);
        }
    }

    /**
     * Get a shared lock for the health monitor database. Acquire this before
     * database reads or writes.
     *
     * @return The lock
     *
     * @throws HealthMonitorException
     */
    private CoordinationService.Lock getSharedDbLock() throws HealthMonitorException {
        try {
            String databaseNodeName = DATABASE_NAME;
            CoordinationService.Lock lock = CoordinationService.getInstance().tryGetSharedLock(CoordinationService.CategoryNode.HEALTH_MONITOR, databaseNodeName, 5, TimeUnit.MINUTES);

            if (lock != null) {
                return lock;
            }
            throw new HealthMonitorException("Error acquiring database lock");
        } catch (InterruptedException | CoordinationService.CoordinationServiceException ex) {
            throw new HealthMonitorException("Error acquiring database lock");
        }
    }

    /**
     * Types of user events being logged
     */
    enum UserEvent {
        LOG_ON(0),
        LOG_OFF(1),
        CASE_OPEN(2),
        CASE_CLOSE(3);

        int value;

        UserEvent(int value) {
            this.value = value;
        }

        /**
         * Get the integer value of the event to store in the database.
         *
         * @return value corresponding to the event
         */
        int getEventValue() {
            return value;
        }

        /**
         * Get the UserEvent from the value stored in the database
         *
         * @param value
         *
         * @return the corresponding UserEvent object
         *
         * @throws HealthMonitorException
         */
        static UserEvent valueOf(int value) throws HealthMonitorException {
            for (UserEvent v : UserEvent.values()) {
                if (v.value == value) {
                    return v;
                }
            }
            throw new HealthMonitorException("Can not create UserEvent from unknown value " + value);
        }

        /**
         * Return whether a case is considered to be open given this event as
         * the last recorded event.
         *
         * @return true if a case is open, false otherwise
         */
        boolean caseIsOpen() {
            return (this.equals(CASE_OPEN));
        }

        /**
         * Return whether a user is considered to be logged in given this event
         * as the last recorded event.
         *
         * @return true if a the user is logged in, false otherwise
         */
        boolean userIsLoggedIn() {
            // LOG_ON, CASE_OPEN, and CASE_CLOSED events all imply that the user
            // is logged in
            return (!this.equals(LOG_OFF));
        }
    }

    /**
     * Class holding user metric data. Can be used for storing new events or
     * retrieving events out of the database.
     */
    static class UserData implements Comparable<UserData> {

        private final UserEvent eventType;
        private long timestamp;
        private final boolean isExaminer;
        private final String hostname;
        private String username;
        private String caseName;

        /**
         * Create a new UserData object using the given event type and the
         * current settings.
         *
         * @param eventType The type of event being recorded
         */
        private UserData(UserEvent eventType) {
            this.eventType = eventType;
            this.timestamp = System.currentTimeMillis();
            this.isExaminer = (UserPreferences.SelectedMode.STANDALONE == UserPreferences.getMode());
            this.hostname = "";
            this.username = "";

            // If there's a case open, record the name
            try {
                this.caseName = Case.getCurrentCaseThrows().getDisplayName();
            } catch (NoCurrentCaseException ex) {
                // It's not an error if there's no case open
                this.caseName = "";
            }
        }

        /**
         * Create a UserData object from a database result set.
         *
         * @param resultSet The result set containing the data
         *
         * @throws SQLException
         * @throws HealthMonitorException
         */
        UserData(ResultSet resultSet) throws SQLException, HealthMonitorException {
            this.timestamp = resultSet.getLong("timestamp");
            this.hostname = resultSet.getString("host");
            this.eventType = UserEvent.valueOf(resultSet.getInt("event_type"));
            this.isExaminer = resultSet.getBoolean("is_examiner");
            this.caseName = resultSet.getString("case_name");
            this.username = resultSet.getString("username");
            if (this.username == null) {
                this.username = "";
            }
        }

        /**
         * This should only be used to make a dummy object to use for timestamp
         * comparisons.
         *
         * @param timestamp
         *
         * @return A UserData object with the given timestamp
         */
        static UserData createDummyUserData(long timestamp) {
            UserData userData = new UserData(UserEvent.CASE_CLOSE);
            userData.timestamp = timestamp;
            return userData;
        }

        /**
         * Get the timestamp for the event
         *
         * @return Timestamp in milliseconds
         */
        long getTimestamp() {
            return timestamp;
        }

        /**
         * Get the host that created the metric
         *
         * @return the host name
         */
        String getHostname() {
            return hostname;
        }

        /**
         * Get the type of event
         *
         * @return the event type
         */
        UserEvent getEventType() {
            return eventType;
        }

        /**
         * Check whether this node is an examiner node or an auto ingest node
         *
         * @return true if it is an examiner node
         */
        boolean isExaminerNode() {
            return isExaminer;
        }

        /**
         * Get the name of the case for this metric
         *
         * @return the case name. Will be the empty string if no case was open.
         */
        String getCaseName() {
            return caseName;
        }
        
        /**
         * Get the user name for this metric
         *
         * @return the user name. Will be the empty string for older data.
         */
        String getUserName() {
            return username;
        }
        
        @Override
        public int compareTo(UserData otherData) {
            return Long.compare(getTimestamp(), otherData.getTimestamp());
        }
    }

    /**
     * Internal class for collecting timing metrics. Instead of storing each
     * TimingMetric, we only store the min and max seen and the number of
     * metrics and total duration to compute the average later. One TimingInfo
     * instance should be created per metric name, and additional timing metrics
     * will be added to it.
     */
    private class TimingInfo {

        private long count; // Number of metrics collected
        private double sum;   // Sum of the durations collected (nanoseconds)
        private double max;   // Maximum value found (nanoseconds)
        private double min;   // Minimum value found (nanoseconds)

        TimingInfo(TimingMetric metric) throws HealthMonitorException {
            count = 1;
            sum = metric.getDuration();
            max = metric.getDuration();
            min = metric.getDuration();
        }

        /**
         * Add a new TimingMetric to an existing TimingInfo object. This is
         * called in a synchronized block for almost all new TimingMetric
         * objects, so do as little processing here as possible.
         *
         * @param metric The new metric
         *
         * @throws HealthMonitorException Will be thrown if the metric hasn't
         *                                been stopped
         */
        void addMetric(TimingMetric metric) throws HealthMonitorException {

            // Keep track of needed info to calculate the average
            count++;
            sum += metric.getDuration();

            // Check if this is the longest duration seen
            if (max < metric.getDuration()) {
                max = metric.getDuration();
            }

            // Check if this is the lowest duration seen
            if (min > metric.getDuration()) {
                min = metric.getDuration();
            }
        }

        /**
         * Get the average duration
         *
         * @return average duration (milliseconds)
         */
        double getAverage() {
            return sum / count;
        }

        /**
         * Get the maximum duration
         *
         * @return maximum duration (milliseconds)
         */
        double getMax() {
            return max;
        }

        /**
         * Get the minimum duration
         *
         * @return minimum duration (milliseconds)
         */
        double getMin() {
            return min;
        }

        /**
         * Get the total number of metrics collected
         *
         * @return number of metrics collected
         */
        long getCount() {
            return count;
        }
    }

    /**
     * Class for retrieving timing metrics from the database to display to the
     * user. All times will be in milliseconds.
     */
    static class DatabaseTimingResult {

        private final long timestamp; // Time the metric was recorded
        private final String hostname; // Host that recorded the metric
        private final long count; // Number of metrics collected
        private final double average;   // Average of the durations collected (milliseconds)
        private final double max;   // Maximum value found (milliseconds)
        private final double min;   // Minimum value found (milliseconds)

        DatabaseTimingResult(ResultSet resultSet) throws SQLException {
            this.timestamp = resultSet.getLong("timestamp");
            this.hostname = resultSet.getString("host");
            this.count = resultSet.getLong("count");
            this.average = resultSet.getDouble("average");
            this.max = resultSet.getDouble("max");
            this.min = resultSet.getDouble("min");
        }

        /**
         * Get the timestamp for when the metric was recorded
         *
         * @return
         */
        long getTimestamp() {
            return timestamp;
        }

        /**
         * Get the average duration
         *
         * @return average duration (milliseconds)
         */
        double getAverage() {
            return average;
        }

        /**
         * Get the maximum duration
         *
         * @return maximum duration (milliseconds)
         */
        double getMax() {
            return max;
        }

        /**
         * Get the minimum duration
         *
         * @return minimum duration (milliseconds)
         */
        double getMin() {
            return min;
        }

        /**
         * Get the total number of metrics collected
         *
         * @return number of metrics collected
         */
        long getCount() {
            return count;
        }

        /**
         * Get the name of the host that recorded this metric
         *
         * @return the host
         */
        String getHostName() {
            return hostname;
        }
    }
}
