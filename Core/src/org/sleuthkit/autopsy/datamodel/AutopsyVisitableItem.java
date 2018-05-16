/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011-2016 Basis Technology Corp.
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
;

/**
 * AutopsyVisitableItems are the nodes in the directory tree that are for
 * structure only. They are not associated with content objects.
 */
public interface AutopsyVisitableItem {

    /**
     * visitor pattern support
     *
     * @param visitor visitor
     *
     * @return visitor return value
     */
    public <T> T accept(AutopsyItemVisitor<T> visitor);

}
