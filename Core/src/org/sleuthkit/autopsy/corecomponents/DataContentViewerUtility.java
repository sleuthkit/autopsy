/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.corecomponents;

import org.sleuthkit.datamodel.Content;
import org.openide.nodes.Node;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 * Utility classes for content viewers.
 * In theory, this would live in the contentviewer package,
 * but the initial method was needed only be viewers in 
 * corecomponents and therefore can stay out of public API.
 */
public class DataContentViewerUtility {
    /**
     * Returns the first non-Blackboard Artifact from a Node.
     * Needed for (at least) Hex and Strings that want to view
     * all types of content (not just AbstractFile), but don't want
     * to display an artifact unless that's the only thing there. 
     * Scenario is hash hit or interesting item hit.
     * 
     * @param node Node passed into content viewer
     * @return highest priority content or null if there is no content
     */
    public static Content getDefaultContent(Node node) {
        Content bbContentSeen = null;
        for (Content content :  (node).getLookup().lookupAll(Content.class)) {
            if (content instanceof BlackboardArtifact) {
                bbContentSeen = content;
            }
            else {
                return content;
            }
        }
        return bbContentSeen;
    }
}
