/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datasourceprocessors.xry;

/**
 * A XRY Key Value pair. XRY Key Value pairs make up the body of XRY entities.
 * 
 * Example:
 * 
 * Attribute:                   Device Name
 * Time:			3/20/2012 12:06:30 AM
 * Status:			Read
 */
class XRYKeyValuePair {
  
    private static final char KEY_VALUE_DELIMITER = ':';
    
    private final String key;
    private final String value;
    private final String namespace;

    XRYKeyValuePair(String key, String value, String namespace) {
        this.key = key.trim();
        this.value = value.trim();
        this.namespace = namespace.trim();
    }

    /**
     * Tests if the key of this pair matches some expected value.
     * 
     * The expected value and the key of this pair will both be 
     * normalized (trimmed and lowercased).
     *
     * @param targetKey Key name to test.
     */
    boolean hasKey(String targetKey) {
        String normalizedKey = key.toLowerCase();
        String normalizedTargetKey = targetKey.trim().toLowerCase();
        return normalizedKey.equals(normalizedTargetKey);
    }

    /**
     * Retrieves the value contained within this pair.
     */
    String getValue() {
        return value;
    }
    
    /**
     * Retrieves the key contained within this pair.
     */
    String getKey() {
        return key;
    }
    
    /**
     * Retrieves the namespace contained within this pair.
     */
    String getNamespace() {
        return namespace;
    }
    
    /**
     * Tests if the input has the structure of a key value pair.
     *
     * @param xryLine XRY entity line to test.
     */
    static boolean isPair(String input) {
        int dataKeyIndex = input.indexOf(KEY_VALUE_DELIMITER);
        //No key structure found.
        return dataKeyIndex != -1;
    }

    /**
     * Extracts a key value pair from the input.
     *
     * This function assumes that there is a key value structure on the line. It
     * can be verified using hasKeyValueForm().
     *
     * @param input Input key value string to parse.
     * @param namespace Namespace to assign to this pair.
     * @return
     */
    static XRYKeyValuePair from(String input, String namespace) {
        if(!isPair(input)) {
            throw new IllegalArgumentException("Input does not have the structure"
                    + " of a key value pair");
        }
        
        int keyIndex = input.indexOf(KEY_VALUE_DELIMITER);
        String parsedKey = input.substring(0, keyIndex);
        String parsedValue = input.substring(keyIndex + 1);
        return new XRYKeyValuePair(parsedKey, parsedValue, namespace);
    }
    
    /**
     * Extracts a key value pair from the input.
     *
     * This function assumes that there is a key value structure on the line. It
     * can be verified using hasKeyValueForm().
     *
     * @param input Input key value string to parse.
     * @return
     */
    static XRYKeyValuePair from(String input) {
        return from(input, "");
    }
    
    @Override
    public String toString() {
        if(namespace.isEmpty()) {
            return key + " : " + value;
        }
        return "(Namespace: " + namespace +") " + key + " : " + value;
    }
}
