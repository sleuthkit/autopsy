/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018-2018 Basis Technology Corp.
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

/**
 * Adds the functionality of enabling and disabling a NodeProperty (column in the UI).
 */
class ToggleableNodeProperty extends NodeProperty<Object> {

    /**
     * Wraps the super constructor. In our use cases, we want the name and display
     * name of the column to be the exact same, so to avoid redundancy we accept the name
     * just once and  pass it twice to the NodeProperty.
     * 
     * @param name Name of the property to be displayed
     * @param desc Description of the property when hovering over the column
     * @param value Value to be displayed in that column
     */
    public ToggleableNodeProperty(String name, String desc, Object value) {
        super(name, name, desc, value);
    }
    
    /**
     * Allows a property to be either enabled or disabled. When creating a sheet,
     * this method is used to filter out from displaying in the UI.
     * 
     * @return boolean denoting the availiability of this property. True by default.
     */
    public boolean isEnabled() {
        return true;
    }
}
