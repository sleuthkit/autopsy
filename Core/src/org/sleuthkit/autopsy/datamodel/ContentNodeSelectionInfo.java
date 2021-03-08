/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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

import org.openide.nodes.Node;
import org.sleuthkit.datamodel.Content;

/**
 * Stores sufficient information to identify a content node that is intended to
 * be selected in a view.
 */
public class ContentNodeSelectionInfo implements NodeSelectionInfo {

    private final long contentId;

    /**
     * Constructs an object that stores sufficient information to identify a
     * content node that is intended to be selected in a view.
     *
     * @param content The content represented by the node to be selected.
     */
    public ContentNodeSelectionInfo(Content content) {
        this.contentId = content.getId();
    }

    /**
     * Determines whether or not a given node satisfies the stored node
     * selection criteria.
     *
     * @param candidateNode A node to evaluate.
     *
     * @return True or false.
     */
    @Override
    public boolean matches(Node candidateNode) {
        Content content = candidateNode.getLookup().lookup(Content.class);
        return (content != null && content.getId() == contentId);
    }

}
