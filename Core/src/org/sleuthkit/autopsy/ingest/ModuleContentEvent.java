/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2015 Basis Technology Corp.
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
package org.sleuthkit.autopsy.ingest;

import javax.swing.event.ChangeEvent;
import org.sleuthkit.datamodel.Content;

/**
 * Event data that is published when content is added to case.
 */
public class ModuleContentEvent extends ChangeEvent {

    private String moduleName = "";

    /**
     * Constructs event data that is published when content is added to case.
     *
     * @param content A reference to the new content.
     */
    public ModuleContentEvent(Content content) {
        super(content);
    }

    /**
     * Constructs event data that is published when content is added to case.
     *
     * @param moduleName The name of the module that added the content.
     * @param content    A reference to the new content.
     */
    public ModuleContentEvent(String moduleName, Content content) {
        super(content);
        this.moduleName = moduleName;
    }

    /**
     * Gets the name of the module that added the content, if the module name
     * has been provided.
     *
     * @return The module name as a string. May be empty.
     */
    public String getModuleName() {
        return moduleName;
    }

}
