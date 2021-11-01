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

import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.mainui.nodes.actions.ActionContext;
import org.sleuthkit.autopsy.mainui.nodes.actions.ActionsFactory.ActionGroup;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;

/**
 * An abstract class of an AbstractNode that completely implements ActionContext
 * having each method return a default value of either false or null.
 *
 * Subclass need only to implement the method for their supported actions.
 */
abstract class AbstractAutopsyNode extends AbstractNode implements ActionContext {

    AbstractAutopsyNode(Children children) {
        super(children);
    }

    AbstractAutopsyNode(Children children, Lookup lookup) {
        super(children, lookup);
    }

    @Override
    public BlackboardArtifact getArtifact() {
        return null;
    }

    @Override
    public Content getSourceContent() {
        return null;
    }

    @Override
    public boolean hasArtifact() {
        return false;
    }

    @Override
    public BlackboardArtifact.Type getArtifactType() {
        return null;
    }

    @Override
    public boolean hasLinkedFile() {
        return false;
    }

    @Override
    public AbstractFile getLinkedFile() {
        return null;
    }

    @Override
    public boolean supportsNodeSpecificActions() {
        return false;
    }

    @Override
    public ActionGroup getNodeSpecificActions() {
        return null;
    }

    @Override
    public boolean supportsNewWindowAction() {
        return false;
    }

    @Override
    public Node getNewWindowActionNode() {
        return null;
    }

    @Override
    public boolean supportsExternalViewerAction() {
        return false;
    }

    @Override
    public Node getExternalViewerActionNode() {
        return null;
    }

    @Override
    public boolean supportsExtractAction() {
        return false;
    }

    @Override
    public boolean supportsExportCSVAction() {
        return false;
    }

    @Override
    public boolean supportsViewArtifactInTimeline() {
        return false;
    }

    @Override
    public BlackboardArtifact getArtifactForTimeline() {
        return null;
    }

    @Override
    public boolean supportsViewFileInTimeline() {
        return false;
    }

    @Override
    public AbstractFile getFileForTimeline() {
        return null;
    }

    @Override
    public boolean supportsViewSourceContentActions() {
        return false;
    }

    @Override
    public boolean supportsViewSourceContentTimelineActions() {
        return false;
    }

    @Override
    public AbstractFile getSourceContextForTimelineAction() {
        return null;
    }

    @Override
    public boolean supportsAssociatedFileActions() {
        return false;
    }

    @Override
    public boolean supportsContentTagAction() {
        return false;
    }

    @Override
    public boolean supportsArtifactTagAction() {
        return false;
    }

    @Override
    public boolean supportsExtractArchiveWithPasswordAction() {
        return false;
    }

    @Override
    public AbstractFile getExtractArchiveWithPasswordActionFile() {
        return null;
    }

}
