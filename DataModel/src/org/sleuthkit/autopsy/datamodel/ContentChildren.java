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

import java.util.Collections;
import java.util.List;
import org.sleuthkit.datamodel.Content;

/**
 * Class for Children of all ContentNodes. Handles creating child ContentNodes.
 */
class ContentChildren extends AbstractContentChildren {
    
    private static final int MAX_CHILD_COUNT = 10000;

    private Content parent;

    ContentChildren(Content parent) {
        this.parent = parent;
    }

    @Override
    protected void addNotify() {
        List<Content> children = ContentHierarchyVisitor.getChildren(parent);
        setKeys(children.subList(0, Math.min(children.size(), MAX_CHILD_COUNT)));
    }

    @Override
    protected void removeNotify() {
        setKeys(Collections.emptySet());
    }
}
