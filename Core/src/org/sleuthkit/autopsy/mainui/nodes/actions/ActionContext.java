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
package org.sleuthkit.autopsy.mainui.nodes.actions;

import java.util.Optional;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.mainui.nodes.actions.ActionsFactory.ActionGroup;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;

/**
 * Interface for nodes that want to use the ActionFactory to build their popup
 * menu;
 *
 */
public interface ActionContext {

    /**
     * Return the source content.
     *
     * @return The source content object.
     */
    default Optional<Content> getSourceContent() {
        return Optional.empty();
    }

    /**
     * Returns an ActionGroup containing the Actions that are specific to the
     * node.
     *
     * @return ActionGroup of actions.
     */
    default Optional<ActionGroup> getNodeSpecificActions() {
        return Optional.empty();
    }

    /**
     * Return the linked/associated file for the context. This method must
     * return a file if hasLinkedFile returns true.
     *
     * @return An AbstractFile.
     */
    default Optional<AbstractFile> getLinkedFile() {
        return Optional.empty();
    }

    /**
     * Returns an instance of an BlackboardArtifact.
     *
     * @return An artifact or null if the ActionContext does not have an
     *         artifact.
     */
    default Optional<BlackboardArtifact> getArtifact() {
        return Optional.empty();
    }

    /**
     * Returns true if this context supports showing an artifact or a file in
     * the Timeline viewer.
     *
     * @return True if context supports this action.
     */
    default boolean supportsViewInTimeline() {
        return false;
    }

    /**
     * Returns the artifact that should appear for the node in the Timeline
     * viewer.
     *
     * @return The artifact to show in the timeline window.
     */
    default Optional<BlackboardArtifact> getArtifactForTimeline() {
        return Optional.empty();
    }

    /**
     * Returns the file that should appear for the node in the Timeline viewer.
     *
     * @return The file to show in the timeline window.
     */
    default Optional<AbstractFile> getFileForViewInTimelineAction() {
        return Optional.empty();
    }

    /**
     * True if the context supports an action to navigate to source content in
     * tree hierarchy.
     *
     * @return True if this action is supported.
     */
    default boolean supportsSourceContentActions() {
        return false;
    }

    /**
     * Returns the source AbstractFile for to be viewed in the Timeline window.
     *
     * @return The source file.
     */
    default Optional<AbstractFile> getSourceFileForTimelineAction() {
        return Optional.empty();
    }

    /**
     * Returns true if the context supports the associated/link file actions.
     *
     * @return True if this action is supported.
     */
    default boolean supportsAssociatedFileActions() {
        return false;
    }

    /**
     * True if the ActionContext supports showing a node in a new content
     * panel.
     *
     * @return True if this action is supported.
     */
    default boolean supportsSourceContentViewerActions() {
        return false;
    }

    /**
     * Returns the node to be display in a new content panel as launched by
     * NewWindowAction.
     *
     * @return The node to display.
     */
    default Optional<Node> getNewWindowActionNode() {
        return Optional.empty();
    }

    /**
     * Returns the node to be display in an external viewer.
     *
     * @return The node to be display.
     */
    default Optional<Node> getExternalViewerActionNode() {
        return Optional.empty();
    }

    /**
     * Returns true if the context supported the extract actions
     * for nodes in the table view.
     *
     * @return True if the action is supported.
     */
    default boolean supportsTableExtractActions() {
        return false;
    }
    
    /**
     * Returns true if the context supported the extract actions
     * for nodes in the tree view.
     *
     * @return True if the action is supported.
     */
    default boolean supportsTreeExtractActions() {
        return false;
    }

    /**
     * Returns true if the context supports the context tag actions.
     *
     * @return True if the action is supported.
     */
    default boolean supportsContentTagAction() {
        return false;
    }

    /**
     * Returns true of the context supported the artifact tag actions.
     *
     * @return True if the action is supported.
     */
    default boolean supportsArtifactTagAction() {
        return false;
    }

    /**
     * Returns the file to be extracted.
     *
     * @return True if the action is supported.
     */
    default Optional<AbstractFile> getExtractArchiveWithPasswordActionFile() {
        return Optional.empty();
    }
    
    /**
     * Returns the content object to be passed into the
     * RunIngestModelAction constructor.
     * 
     * @return The content object for ingest.
     */
    default Optional<Content> getContentForRunIngestionModuleAction() {
        return Optional.empty();
    }
    
    default Optional<Content> getDataSourceForActions() {
        return Optional.empty();
    }
    
    default Optional<AbstractFile> getFileForDirectoryBrowseMode() {
        return Optional.empty();
    }
}
