/*
 * Central Repository
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
package org.sleuthkit.autopsy.centralrepository.contentviewer;

import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationCase;

/**
 * Class to wrap CorrelationCases or a text message
 */
class CorrelationCaseWrapper {

    private final CorrelationCase corCase;
    private final String message;

    CorrelationCaseWrapper(CorrelationCase corrCase) {
        corCase = corrCase;
        message = corrCase.getDisplayName();
    }

    CorrelationCaseWrapper(String msg) {
        corCase = null;
        message = msg;
    }

    CorrelationCase getCorrelationCase() {
        return corCase;
    }

    String getMessage() {
        return message;
    }
}
