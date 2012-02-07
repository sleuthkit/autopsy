/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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

import java.util.Collection;
import java.util.Collections;
import org.sleuthkit.datamodel.DisplayableItem;

/**
 * Children implementation for the root node of a ContentNode tree. Accepts a
 * list of root Content objects for the tree.
 */
public class RootContentChildren extends AbstractContentChildren {
    private Collection contentKeys;
    
    /**
     * @param contentKeys root Content objects for the Node tree
     */
    public RootContentChildren(Collection<? extends DisplayableItem> contentKeys) {
        super();
        this.contentKeys = contentKeys;
    }
    
    @Override
    protected void addNotify() {
        setKeys(contentKeys);
    }
    
    @Override
    protected void removeNotify() {
        setKeys(Collections.EMPTY_SET);
    }
}
