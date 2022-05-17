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
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;

/**
 * Implements java.util.prefs.Preferences API saving to a file.
 */
class ConfigPreferences extends AbstractPreferences {

    private final Properties inMemoryProperties = new Properties();
    private final String configPath;
    private boolean dirty = false;

    /**
     * Main constructor.
     *
     * @param configPath The path to the config file (if null, no properties
     *                   initially).
     */
    public ConfigPreferences(String configPath) {
        super(null, configPath);
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
        dirty = true;
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
        dirty = true;
    }

    @Override
    protected void removeNodeSpi() throws BackingStoreException {
        inMemoryProperties.clear();
        dirty = true;
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
        Properties onDiskProps;
        try {
            onDiskProps = loadSavedProperties(this.configPath);
        } catch (IOException ex) {
            throw new BackingStoreException(new IOException("An error occurred while saving to: " + this.configPath, ex));
        }

        mergeProperties(onDiskProps, this.inMemoryProperties, false);

        if (dirty) {
            flushSpi();
            dirty = false;
        }
    }

    @Override
    protected void flushSpi() throws BackingStoreException {
        try (FileOutputStream fos = new FileOutputStream(this.configPath)) {
            this.inMemoryProperties.store(fos, "Set settings (batch)");
            this.dirty = false;
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
