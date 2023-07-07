/*
 * Autopsy Forensic Browser
 *
 * Copyright 2023 Basis Technology Corp.
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
package org.sleuthkit.autopsy.casemodule;

import java.util.Map;
import org.sleuthkit.datamodel.ContentStreamProvider;

/**
 * Interface that modules can implement to provide their own The Sleuth Kit
 * ContentProvider implementations
 */
public interface AutopsyContentProvider {

    /**
     * Attempts to create a ContentProvider given the specified args. Returns
     * null if arguments are invalid for this custom content provider.
     *
     * @param args The key value pair of arguments loaded from the .aut xml
     * file.
     * @return The created content provider or null if arguments are invalid.
     */
    ContentStreamProvider load(Map<String, Object> args);

    /**
     * Returns the uniquely identifying name of this FileContentProvider. This
     * name will be stored in the .AUT file and used for lookup when the case is
     * opened.
     *
     * @return The unique name.
     */
    String getName();
}
