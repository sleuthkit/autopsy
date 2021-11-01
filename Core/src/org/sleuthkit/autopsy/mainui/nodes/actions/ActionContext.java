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
     * Return the source content object.
     *
     * @return The source content object.
     */
    Content getSourceContent();

    /**
     * Returns true if the implementing node has node specific actions. These
     * actions will appear at the top of the menu.
     *
     * @return True if the node has actions specific to the node.
     */
    boolean supportsNodeSpecificActions();

    /**
     * Returns an ActionGroup containing the Actions that are specific to the
     * node.
     *
     * @return ActionGroup of actions.
     */
    ActionGroup getNodeSpecificActions();

    /**
     * Returns true if the node has a linked\associated file. Returning true
     * will cause Actions specific to to the linked files to be added to the
     * menu.
     *
     * @return True if the node has a linked\associated file.
     */
    boolean hasLinkedFile();

    /**
     * Return the linked\associated file for the context. This method must
     * return a file if hasLinkedFile returns true.
     *
     * @return An AbstractFile.
     */
    AbstractFile getLinkedFile();

    /**
     * Returns true if the ActionContext contains an artifact.
     *
     * @return True if the ActionContext has an artifact.
     */
    boolean hasArtifact();

    /**
     * Returns an instance of an BlackboardArtifact. Method must return an
     * artifact if hasArtifact returns true.
     *
     * @return An artifact or null if the ActionContext does not have an
     *         artifact.
     */
    BlackboardArtifact getArtifact();

    /**
     * Returns the type of the artifact if one is contained in the
     * ActionContext. This method must return the artifact type if hasArtifact
     * returns true.
     *
     * @return The type of the artifact returned from getAritfact or null if
     *         hasArtifact is false.
     */
    BlackboardArtifact.Type getArtifactType();

    /**
     * Returns true if this context supports showing an artifact in the Timeline
     * viewer.
     *
     * If this method returns true, getArtifactForTimeline must return a
     * non-null artifact.
     *
     * @return True if context supports this action.
     */
    boolean supportsViewArtifactInTimeline();

    /**
     * Returns the artifact that should appear for the node in the Timeline
     * viewer.
     *
     * This method must return a non-null value if
     * supportsViewArtifactInTimeline returns true.
     *
     * @return The artifact to show in the timeline window.
     */
    BlackboardArtifact getArtifactForTimeline();

    /**
     * Returns true if the implementing node supports viewing a file int the
     * Timeline Viewer.
     *
     * If this method returns true, getFileForTimeline must return a non-null
     * value.
     *
     * @return True if the context supports the action.
     */
    boolean supportsViewFileInTimeline();

    /**
     * Returns the file that should appear for the node in the Timeline viewer.
     *
     * This method must return a non-null value if supportsViewFileInTimeline
     * returns true.
     *
     * @return The file to show in the timeline window.
     */
    AbstractFile getFileForTimeline();

    /**
     * True if the context supports an action to navigate to source content in
     * tree hierarchy.
     *
     * @return True if this action is supported.
     */
    boolean supportsViewSourceContentActions();

    /**
     * True if the implementing node supports an action to navigate to source
     * context in the timeline window.
     *
     * If this method returns true getSourceContextForTimelineAction must return
     * a non-null value.
     *
     * @return True if this action is supported.
     */
    boolean supportsViewSourceContentTimelineActions();

    /**
     * Returns the source AbstractFile for to be viewed in the Timeline window.
     *
     * Must return a non-null value if supportsViewSourceContentTimelineActions
     * returns true;
     *
     * @return The source file.
     */
    AbstractFile getSourceContextForTimelineAction();

    /**
     * Returns true if the context supports the associated\link file actions.
     *
     * If this method returns true, hasLinkedFile and getLinked file should
     * return true and a non-null value.
     *
     * @return True if this action is supported.
     */
    boolean supportsAssociatedFileActions();

    /**
     * True if the ActionContext supports showing a node int a new content
     * panel.
     *
     * If true, getNewwindowActionNode must return a non-null value.
     *
     * @return True if this action is supported.
     */
    boolean supportsNewWindowAction();

    /**
     * Returns the node to be display in a new content panel as launched by
     * NewWindowAction.
     *
     * If supportsNewWindowAction returns true, this method must return a
     * non-null value.
     *
     * @return The node to display.
     */
    Node getNewWindowActionNode();

    /**
     * Returns true if the context supports the showing a node in an external
     * viewer.
     *
     * If this method returns true, getExternalViewerActionNode must return a
     * non-null value.
     *
     * @return True if the action is supported.
     */
    boolean supportsExternalViewerAction();

    /**
     * Returns the node to be display in an external viewer.
     *
     * If supportsExternalViewerAction returns true, this method must return a
     * non-null value.
     *
     * @return The node to be display.
     */
    Node getExternalViewerActionNode();

    /**
     * Returns true if the context supported the extract action.
     *
     * @return True if the action is supported.
     */
    boolean supportsExtractAction();

    /**
     * Returns true if the context supports the export to csv action.
     *
     * @return True if the action is supported.
     */
    boolean supportsExportCSVAction();

    /**
     * Returns true if the context supports the context tag actions.
     *
     * @return True if the action is supported.
     */
    boolean supportsContentTagAction();

    /**
     * Returns true of the context supported the artifact tag actions.
     *
     * @return True if the action is supported.
     */
    boolean supportsArtifactTagAction();

    /**
     * Returns true if encryption was deteched and the context supported the
     * extraction.
     *
     * @return True if the action is supported.
     */
    boolean supportsExtractArchiveWithPasswordAction();

    /**
     * Returns the file to be extracted.
     *
     * If supportsExtractArchiveWithPasswordAction is true this method must
     * return a non-null value.
     *
     * @return True if the action is supported.
     */
    AbstractFile getExtractArchiveWithPasswordActionFile();
}
