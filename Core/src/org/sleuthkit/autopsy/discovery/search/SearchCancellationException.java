/*
 * Autopsy
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.discovery.search;

import java.util.concurrent.CancellationException;

/**
 * Exception to be thrown when the search has been intentionally cancelled to
 * provide information on where the code was when the cancellation took place.
 */
public class SearchCancellationException extends CancellationException {

    private static final long serialVersionUID = 1L;

    /**
     * Construct a new SearchCancellationException with the specified message.
     *
     * @param message The text to use as the message for the exception.
     */
    SearchCancellationException(String message) {
        super(message);
    }

}
