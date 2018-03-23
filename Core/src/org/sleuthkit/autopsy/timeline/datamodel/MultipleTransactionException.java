/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.datamodel;

/**
 *
 */
public class MultipleTransactionException extends IllegalStateException {

    private static final long serialVersionUID = 1L;
    private static final String CANNOT_HAVE_MORE_THAN_ONE_OPEN_TRANSACTION = "Cannot have more than one open transaction."; // NON-NLS

    public MultipleTransactionException() {
        super(CANNOT_HAVE_MORE_THAN_ONE_OPEN_TRANSACTION);
    }
}
