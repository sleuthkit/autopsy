/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2020 Basis Technology Corp.
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

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import javax.swing.Action;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.timeline.actions.ViewArtifactInTimelineAction;
import org.sleuthkit.autopsy.timeline.actions.ViewFileInTimelineAction;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifactTag;
import org.sleuthkit.datamodel.TskCoreException;
import static org.sleuthkit.autopsy.datamodel.Bundle.*;

/**
 * Instances of this class wrap BlackboardArtifactTag objects. In the Autopsy
 * presentation of the SleuthKit data model, they are leaf nodes of a sub-tree
 * organized as follows: there is a tags root node with tag name child nodes;
 * tag name nodes have tag type child nodes; tag type nodes are the parents of
 * either content or blackboard artifact tag nodes.
 */
public class BlackboardArtifactTagNode extends TagNode {

    private static final Logger LOGGER = Logger.getLogger(BlackboardArtifactTagNode.class.getName());
    private static final String ICON_PATH = "org/sleuthkit/autopsy/images/green-tag-icon-16.png"; //NON-NLS
    private final BlackboardArtifactTag tag;

    public BlackboardArtifactTagNode(BlackboardArtifactTag tag) {
        super(Lookups.fixed(tag, tag.getArtifact(), tag.getContent()), tag.getContent());
        super.setName(tag.getContent().getName());
        super.setDisplayName(tag.getContent().getName());
        this.setIconBaseWithExtension(ICON_PATH);
        this.tag = tag;
    }

    @Messages({"BlackboardArtifactTagNode.createSheet.userName.text=User Name"})
    @Override
    protected Sheet createSheet() {
        Sheet propertySheet = super.createSheet();
        Sheet.Set properties = propertySheet.get(Sheet.PROPERTIES);
        if (properties == null) {
            properties = Sheet.createPropertiesSet();
            propertySheet.put(properties);
        }

        properties.put(new NodeProperty<>(
                NbBundle.getMessage(this.getClass(), "BlackboardArtifactTagNode.createSheet.srcFile.text"),
                NbBundle.getMessage(this.getClass(), "BlackboardArtifactTagNode.createSheet.srcFile.text"),
                "",
                tag.getContent().getName()));
        addOriginalNameProp(properties);
        String contentPath;
        try {
            contentPath = tag.getContent().getUniquePath();
        } catch (TskCoreException ex) {
            Logger.getLogger(ContentTagNode.class.getName()).log(Level.SEVERE, "Failed to get path for content (id = " + tag.getContent().getId() + ")", ex); //NON-NLS
            contentPath = NbBundle.getMessage(this.getClass(), "BlackboardArtifactTagNode.createSheet.unavail.text");
        }
        properties.put(new NodeProperty<>(
                NbBundle.getMessage(this.getClass(), "BlackboardArtifactTagNode.createSheet.srcFilePath.text"),
                NbBundle.getMessage(this.getClass(), "BlackboardArtifactTagNode.createSheet.srcFilePath.text"),
                "",
                contentPath));
        properties.put(new NodeProperty<>(
                NbBundle.getMessage(this.getClass(), "BlackboardArtifactTagNode.createSheet.resultType.text"),
                NbBundle.getMessage(this.getClass(), "BlackboardArtifactTagNode.createSheet.resultType.text"),
                "",
                tag.getArtifact().getDisplayName()));
        properties.put(new NodeProperty<>(
                NbBundle.getMessage(this.getClass(), "BlackboardArtifactTagNode.createSheet.comment.text"),
                NbBundle.getMessage(this.getClass(), "BlackboardArtifactTagNode.createSheet.comment.text"),
                "",
                tag.getComment()));
        properties.put(new NodeProperty<>(
                NbBundle.getMessage(this.getClass(), "BlackboardArtifactTagNode.createSheet.userName.text"),
                NbBundle.getMessage(this.getClass(), "BlackboardArtifactTagNode.createSheet.userName.text"),
                "",
                tag.getUserName()));
        return propertySheet;
    }

    @NbBundle.Messages("BlackboardArtifactTagNode.viewSourceArtifact.text=View Source Result")
    @Override
    public Action[] getActions(boolean context) {
        List<Action> actions = new ArrayList<>();
        actions.addAll(Arrays.asList(super.getActions(context)));
        BlackboardArtifact artifact = getLookup().lookup(BlackboardArtifact.class);
        //if this artifact has a time stamp add the action to view it in the timeline
        try {
            if (ViewArtifactInTimelineAction.hasSupportedTimeStamp(artifact)) {
                actions.add(new ViewArtifactInTimelineAction(artifact));
            }
        } catch (TskCoreException ex) {
            LOGGER.log(Level.SEVERE, MessageFormat.format("Error getting arttribute(s) from blackboard artifact{0}.", artifact.getArtifactID()), ex); //NON-NLS
        }

        // if the artifact links to another file, add an action to go to that file
        try {
            AbstractFile c = findLinked(artifact);
            if (c != null) {
                actions.add(ViewFileInTimelineAction.createViewFileAction(c));
            }
        } catch (TskCoreException ex) {
            LOGGER.log(Level.SEVERE, MessageFormat.format("Error getting linked file from blackboard artifact{0}.", artifact.getArtifactID()), ex); //NON-NLS
        }
        //if this artifact has associated content, add the action to view the content in the timeline
        AbstractFile file = getLookup().lookup(AbstractFile.class);
        if (null != file) {
            actions.add(ViewFileInTimelineAction.createViewSourceFileAction(file));
        }
        actions.add(new ViewTaggedArtifactAction(BlackboardArtifactTagNode_viewSourceArtifact_text(), artifact));
        actions.addAll(DataModelActionsFactory.getActions(tag, true));

        return actions.toArray(new Action[0]);
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public String getItemType() {
        return getClass().getName();
    }
}
