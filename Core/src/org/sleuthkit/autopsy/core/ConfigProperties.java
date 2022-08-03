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
package org.sleuthkit.autopsy.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.logging.Level;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;

/**
 * Implements java.util.prefs.Preferences API saving to a file after every
 * change.
 */
class ConfigProperties extends AbstractPreferences {

    // use java util logger; 
    // autopsy core logger relies on UserPreferences which relies on this class
    private static java.util.logging.Logger logger = null;

    /**
     * Instantiates an autopsy logger (after instantiation and on demand to
     * remove circular dependency between ConfigProperties, UserPreferences, and
     * autopsy Logger).
     *
     * @return The autopsy logger for this class.
     */
    private static java.util.logging.Logger getLogger() {
        if (logger == null) {
            logger = org.sleuthkit.autopsy.coreutils.Logger.getLogger(ConfigProperties.class.getName());
        }
        return logger;
    }

    private final Properties inMemoryProperties = new Properties();
    private final String configPath;

    /**
     * Main constructor.
     *
     * @param configPath The path to the config file (if null, no properties
     *                   initially).
     */
    public ConfigProperties(String configPath) {
        super(null, "");
        this.configPath = configPath;
    }

    /**
     * Loads properties from disk if they exist.
     *
     * @throws IOException
     */
    public synchronized void load() throws IOException {
        Properties loaded = loadSavedProperties(this.configPath);
        this.inMemoryProperties.clear();
        mergeProperties(loaded, this.inMemoryProperties, true);
    }

    @Override
    protected void putSpi(String key, String value) {
        inMemoryProperties.put(key, value);
        tryFlush();
    }

    @Override
    protected String getSpi(String key) {
        Object val = inMemoryProperties.get(key);
        return val == null
                ? null
                : val.toString();
    }

    @Override
    protected void removeSpi(String key) {
        inMemoryProperties.remove(key);
        tryFlush();
    }

    @Override
    protected void removeNodeSpi() throws BackingStoreException {
        inMemoryProperties.clear();
        tryFlush();
    }

    @Override
    protected String[] keysSpi() throws BackingStoreException {
        return inMemoryProperties.keySet().toArray(new String[inMemoryProperties.size()]);
    }

    @Override
    protected String[] childrenNamesSpi() throws BackingStoreException {
        return new String[0];
    }

    @Override
    protected AbstractPreferences childSpi(String name) {
        throw new IllegalArgumentException("Cannot create new child nodes");
    }

    @Override
    protected void syncSpi() throws BackingStoreException {
        try {
            Properties onDiskProps = loadSavedProperties(this.configPath);
            mergeProperties(onDiskProps, this.inMemoryProperties, false);
            flushSpi();
        } catch (IOException ex) {
            throw new BackingStoreException(new IOException("An error occurred while saving to: " + this.configPath, ex));
        }
    }
    
    /**
     * Attempts to flush setting logging any error that occurs.
     */
    private void tryFlush() {
        try {
            flushSpi();
        } catch (BackingStoreException ex) {
            getLogger().log(Level.SEVERE, "An error occurred when writing to disk at: " + this.configPath, ex);
        }
    }

    @Override
    protected void flushSpi() throws BackingStoreException {
        try (FileOutputStream fos = new FileOutputStream(this.configPath)) {
            this.inMemoryProperties.store(fos, "Set settings (batch)");
        } catch (IOException ex) {
            throw new BackingStoreException(new IOException("An error occurred while saving to: " + this.configPath, ex));

        }
    }

    /**
     * Load saved properties from disk at the given path.
     *
     * @param path The path.
     *
     * @return The loaded properties or empty properties if file does not exist
     *         on disk.
     *
     * @throws IOException
     */
    private static Properties loadSavedProperties(String path) throws IOException {
        Properties props = new Properties();

        File propFile = new File(path);
        if (propFile.exists()) {
            try (InputStream inputStream = new FileInputStream(propFile)) {
                props.load(inputStream);
            }
        }

        return props;
    }

    /**
     * Merges properties from src to dest.
     *
     * @param src       The source properties.
     * @param dest      The destination properties.
     * @param overwrite If true, properties from source overwrite those in dest
     *                  if they exist in dest.
     */
    private static void mergeProperties(Properties src, Properties dest, boolean overwrite) {
        if (overwrite) {
            dest.putAll(src);
        } else {
            for (Entry<Object, Object> entry : dest.entrySet()) {
                dest.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
    }
}
