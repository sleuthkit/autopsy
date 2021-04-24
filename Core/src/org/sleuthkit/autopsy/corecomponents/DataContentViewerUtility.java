/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018-2019 Basis Technology Corp.
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
 * Utility methods for content viewers.
 */
public class DataContentViewerUtility {

    /**
     * Gets a Content object from the Lookup of a display Node object,
     * preferring to return any Content object other than a BlackboardArtifact
     * object.
     *
     * @param node A display Node object.
     *
     * @return If there are multiple Content objects associated with the Node,
     *         the first Content object that is not a BlackboardArtifact object
     *         is returned. If no Content objects other than artifacts are found,
     *         the first BlackboardArtifact object found is returned. If no
     *         Content objects are found, null is returned.
     */
    public static Content getDefaultContent(Node node) {
        Content artifact = null;
        for (Content content : node.getLookup().lookupAll(Content.class)) {
            if (content instanceof BlackboardArtifact && artifact == null) {
                artifact = content;
            } else {
                return content;
            }
        }
        return artifact;
    }

    /*
     * Private constructor to prevent instantiation of utility class.
     */
    private DataContentViewerUtility() {
    }

}
