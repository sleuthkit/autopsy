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

/**
 * Basic interface for integration test group.
 */
public interface IntegrationTestGroup {

    /**
     * Allows for a test to perform any necessary setup.
     */
    default void setup() {
    }

    /**
     * Allows for a test to perform any necessary disposing of resources.
     */
    default void tearDown() {
    }

    /**
     * Allows for this suite of tests to perform any necessary setup.
     */
    default void setupClass() {
    }

    /**
     * Allows for this suite of tests to dispose of resources.
     */
    default void tearDownClass() {
    }
}
