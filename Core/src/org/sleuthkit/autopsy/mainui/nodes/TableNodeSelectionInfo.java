/*
 * Autopsy Forensic Browser
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
package org.sleuthkit.autopsy.mainui.nodes;

import org.openide.nodes.Node;
import org.sleuthkit.datamodel.Content;

/**
 * An interface for nodes that support the view selected file\directory.
 */
public interface TableNodeSelectionInfo {

    void setChildIdToSelect(Long contentId);

    Long getChildIdToSelect();

    /**
     * Determine of the given node represents the child content to
     * be selected.
     * 
     * @param node
     * 
     * @return True if there is a match. 
     */
    default boolean matches(Node node) {
        Content content = node.getLookup().lookup(Content.class);
        if (content != null && getChildIdToSelect() != null) {
            return getChildIdToSelect().equals(content.getId());
        }

        return false;
    }
}
