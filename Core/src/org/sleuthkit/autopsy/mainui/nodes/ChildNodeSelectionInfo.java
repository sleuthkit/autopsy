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
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.OsAccount;

/**
 * An interface for nodes that support the view selected file\directory.
 */
public interface ChildNodeSelectionInfo {

    /**
     * Determine of the given node represents the child content to be selected.
     *
     * @param node
     *
     * @return True if there is a match.
     */
    boolean matches(Node node);

    public class ContentNodeSelectionInfo implements ChildNodeSelectionInfo {

        private final Long contentId;

        public ContentNodeSelectionInfo(Long contentId) {
            this.contentId = contentId;
        }

        @Override
        public boolean matches(Node node) {
            Content content = node.getLookup().lookup(Content.class);
            if (content != null && contentId != null) {
                return contentId.equals(content.getId());
            }

            return false;
        }
    }

    public class BlackboardArtifactNodeSelectionInfo implements ChildNodeSelectionInfo {

        private final long objId;

        public BlackboardArtifactNodeSelectionInfo(BlackboardArtifact artifact) {
            this.objId = artifact.getId();
        }

        @Override
        public boolean matches(Node node) {
            BlackboardArtifact nodeArtifact = node.getLookup().lookup(BlackboardArtifact.class);
            if (nodeArtifact != null) {
                return objId == nodeArtifact.getId();
            }

            return false;
        }
    }

    /**
     * The selection of an os account.
     */
    public class OsAccountNodeSelectionInfo implements ChildNodeSelectionInfo {

        private final long osAccountId;

        /**
         * Main constructor.
         * @param osAccountId The os account id.
         */
        public OsAccountNodeSelectionInfo(long osAccountId) {
            this.osAccountId = osAccountId;
        }

        @Override
        public boolean matches(Node node) {
            OsAccount osAccount = node.getLookup().lookup(OsAccount.class);
            return osAccount != null && osAccount.getId() == osAccountId;
        }
    }
}
