/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.communications;

import org.apache.commons.lang3.StringUtils;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.BlackboardArtifactNode;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 *
 * 
 */
final class MessageNode extends BlackboardArtifactNode {
    private static final Logger logger = Logger.getLogger(RelationshipNode.class.getName());
    
    MessageNode(BlackboardArtifact artifact) {
        super(artifact);
        // Grabbed this from RelationshipNode, does always work even with internalization?
        final String stripEnd = StringUtils.stripEnd(artifact.getDisplayName(), "s"); // NON-NLS
        String removeEndIgnoreCase = StringUtils.removeEndIgnoreCase(stripEnd, "message"); // NON-NLS
        setDisplayName(removeEndIgnoreCase.isEmpty() ? stripEnd : removeEndIgnoreCase);
    }
}
