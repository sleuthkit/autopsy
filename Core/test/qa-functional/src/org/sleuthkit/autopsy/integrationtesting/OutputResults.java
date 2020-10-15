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
package org.sleuthkit.autopsy.integrationtesting;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.apache.commons.lang.exception.ExceptionUtils;

/**
 * Integration Test results for a case to be written to disk in a text format.
 */
public class OutputResults {

    /**
     * An object representing an exception to be serialized.
     */
    private static class ExceptionObject {

        private final String message;
        private final String stackTrace;
        private final ExceptionObject innerException;

        ExceptionObject(Throwable t) {
            this.message = t.getMessage();
            this.stackTrace = ExceptionUtils.getStackTrace(t);
            this.innerException = (t.getCause() == null) ? null : new ExceptionObject(t.getCause());
        }

        /**
         * @return The message of the exception.
         */
        String getMessage() {
            return message;
        }

        /**
         * @return The stacktrace as a string.
         */
        String getStackTrace() {
            return stackTrace;
        }

        /**
         * @return The inner exception (if any).
         */
        ExceptionObject getInnerException() {
            return innerException;
        }
    }

    /**
     * A mapping of package -> test suite -> test -> output data
     */
    private final Map<String, Map<String, Map<String, Object>>> data = new HashMap<>();

    /**
     * Returns the value for a particular key in a map.  Creates a default value for a key in a map if none exists.
     * @param map The map.
     * @param key The key.
     * @param onNotPresent The means of creating a value if no value exists for that key.
     * @return The value for that key or the default value.
     */
    private static <K, V> V getOrCreate(Map<K, V> map, K key, Supplier<V> onNotPresent) {
        V curValue = map.get(key);
        if (curValue == null) {
            curValue = onNotPresent.get();
            map.put(key, curValue);
        }
        return curValue;
    }

    /**
     * Adds a result for a particular test in a test suite.
     * @param pkgName The package name of the test suite.
     * @param className The name of the test suite.
     * @param methodName The test in the test suite.
     * @param result The result to set for this test.
     */
    public void addResult(String pkgName, String className, String methodName, Object result) {
        Map<String, Map<String, Object>> packageClasses = getOrCreate(data, pkgName, () -> new HashMap<>());
        Map<String, Object> classMethods = getOrCreate(packageClasses, className, () -> new HashMap<>());
        Object toWrite = result instanceof Throwable ? new ExceptionObject((Throwable) result) : result;
        classMethods.put(methodName, toWrite);
    }

    /**
     * @return The data to be serialized.
     */
    public Object getSerializableData() {
        return Collections.unmodifiableMap(data);
    }
}
