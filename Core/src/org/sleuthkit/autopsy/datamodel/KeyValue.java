/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datamodel;

import java.util.Map;

/**
 * An object that holds a map of keys and values. Also has a default
 * StringContent implementation so that the string viewer can show it's content.
 */
public class KeyValue implements StringContent {

    Map<String, Object> map;
    int id;
    String name;

    /**
     * @param name name of the key value object that the key/value map is
     *             associated with
     * @param map  Key to value map. Must iterate it keys and values in a
     *             consistent order (use of LinkedHashMap is recommended)
     * @param id   Caller-defined ID. Can represent the type of the thing.
     */
    public KeyValue(String name, Map<String, Object> map, int id) {
        this.name = name;
        this.map = map;
        this.id = id;
    }

    /**
     * @param name name of the key value object that the key/value map is
     *             associated with
     * @param id   Caller-defined ID. Can represent the type of the thing.
     */
    public KeyValue(String name, int id) {
        this.name = name;
        this.map = null;
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public Map<String, Object> getMap() {
        return map;
    }

    public String getName() {
        return name;
    }

    /**
     * Add a map to an already defined object.
     *
     * @param inMap Key/value map
     */
    public void addMap(Map<String, Object> inMap) {
        this.map = inMap;
    }

    @Override
    public String getString() {
        StringBuilder buffer = new StringBuilder();
        buffer.append(name);
        buffer.append("\n");
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            buffer.append(entry.getKey());
            buffer.append(": ");
            buffer.append(entry.getValue().toString());
            buffer.append("\n");
        }
        return buffer.toString();
    }
}
