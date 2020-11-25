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
package org.sleuthkit.autopsy.modules.yara.rules;

/**
 *
 * An exception class for yara rule sets.
 */
public class RuleSetException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Construct a RuleSetException with the given message.
     *
     * @param msg Exception message.
     */
    RuleSetException(String msg) {
        super(msg);
    }

    /**
     * Construct a RuleSetException with the given message and exception.
     *
     * @param msg Exception message.
     * @param ex  Exception.
     */
    RuleSetException(String msg, Exception ex) {
        super(msg, ex);
    }
}
