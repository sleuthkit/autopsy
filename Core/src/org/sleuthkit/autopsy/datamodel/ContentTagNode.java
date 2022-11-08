/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2021 Basis Technology Corp.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import javax.swing.Action;
import org.apache.commons.lang3.StringUtils;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.TimeZoneUtils;
import org.sleuthkit.autopsy.timeline.actions.ViewFileInTimelineAction;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Instances of this class wrap ContentTag objects. In the Autopsy presentation
 * of the SleuthKit data model, they are leaf nodes of a tree consisting of
 * content and artifact tags, grouped first by tag type, then by tag name.
 */
class ContentTagNode extends TagNode {

    private static final Logger LOGGER = Logger.getLogger(ContentTagNode.class.getName());
    private static final String ICON_PATH = "org/sleuthkit/autopsy/images/blue-tag-icon-16.png"; //NON-NLS
    private final ContentTag tag;

    ContentTagNode(ContentTag tag) {
        super(Lookups.fixed(tag, tag.getContent()), tag.getContent());
        super.setName(tag.getContent().getName());
        super.setDisplayName(tag.getContent().getName());
        this.setIconBaseWithExtension(ICON_PATH);
        this.tag = tag;
    }

    @Messages({
        "ContentTagNode.createSheet.origFileName=Original Name",
        "ContentTagNode.createSheet.artifactMD5.displayName=MD5 Hash",
        "ContentTagNode.createSheet.artifactMD5.name=MD5 Hash",
        "ContentTagNode.createSheet.userName.text=User Name"})
    @Override
    protected Sheet createSheet() {
        Content content = tag.getContent();
        String contentPath;
        try {
            contentPath = content.getUniquePath();
        } catch (TskCoreException ex) {
            LOGGER.log(Level.SEVERE, "Failed to get path for content (id = " + content.getId() + ")", ex); //NON-NLS
            contentPath = NbBundle.getMessage(this.getClass(), "ContentTagNode.createSheet.unavail.path");
        }
        AbstractFile file = content instanceof AbstractFile ? (AbstractFile) content : null;

        Sheet propertySheet = super.createSheet();
        Sheet.Set properties = propertySheet.get(Sheet.PROPERTIES);
        if (properties == null) {
            properties = Sheet.createPropertiesSet();
            propertySheet.put(properties);
        }
        properties.put(new NodeProperty<>(
                NbBundle.getMessage(this.getClass(), "ContentTagNode.createSheet.file.name"),
                NbBundle.getMessage(this.getClass(), "ContentTagNode.createSheet.file.displayName"),
                "",
                content.getName()));
        addOriginalNameProp(properties);
        properties.put(new NodeProperty<>(
                NbBundle.getMessage(this.getClass(), "ContentTagNode.createSheet.filePath.name"),
                NbBundle.getMessage(this.getClass(), "ContentTagNode.createSheet.filePath.displayName"),
                "",
                contentPath));
        properties.put(new NodeProperty<>(
                NbBundle.getMessage(this.getClass(), "ContentTagNode.createSheet.comment.name"),
                NbBundle.getMessage(this.getClass(), "ContentTagNode.createSheet.comment.displayName"),
                "",
                tag.getComment()));
        properties.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "ContentTagNode.createSheet.fileModifiedTime.name"),
                NbBundle.getMessage(this.getClass(), "ContentTagNode.createSheet.fileModifiedTime.displayName"),
                "",
                file != null ? TimeZoneUtils.getFormattedTime(file.getMtime()) : ""));
        properties.put(new NodeProperty<>(
                NbBundle.getMessage(this.getClass(), "ContentTagNode.createSheet.fileChangedTime.name"),
                NbBundle.getMessage(this.getClass(), "ContentTagNode.createSheet.fileChangedTime.displayName"),
                "",
                file != null ? TimeZoneUtils.getFormattedTime(file.getCtime()) : ""));
        properties.put(new NodeProperty<>(
                NbBundle.getMessage(this.getClass(), "ContentTagNode.createSheet.fileAccessedTime.name"),
                NbBundle.getMessage(this.getClass(), "ContentTagNode.createSheet.fileAccessedTime.displayName"),
                "",
                file != null ? TimeZoneUtils.getFormattedTime(file.getAtime()) : ""));
        properties.put(new NodeProperty<>(
                NbBundle.getMessage(this.getClass(), "ContentTagNode.createSheet.fileCreatedTime.name"),
                NbBundle.getMessage(this.getClass(), "ContentTagNode.createSheet.fileCreatedTime.displayName"),
                "",
                file != null ? TimeZoneUtils.getFormattedTime(file.getCrtime()) : ""));
        properties.put(new NodeProperty<>(
                NbBundle.getMessage(this.getClass(), "ContentTagNode.createSheet.fileSize.name"),
                NbBundle.getMessage(this.getClass(), "ContentTagNode.createSheet.fileSize.displayName"),
                "",
                content.getSize()));
        properties.put(new NodeProperty<>(
                Bundle.ContentTagNode_createSheet_artifactMD5_name(),
                Bundle.ContentTagNode_createSheet_artifactMD5_displayName(),
                "",
                file != null ? StringUtils.defaultString(file.getMd5Hash()) : ""));
        properties.put(new NodeProperty<>(
                NbBundle.getMessage(this.getClass(), "ContentTagNode.createSheet.userName.text"),
                NbBundle.getMessage(this.getClass(), "ContentTagNode.createSheet.userName.text"),
                "",
                tag.getUserName()));
        return propertySheet;
    }

    @Override
    public Action[] getActions(boolean context) {
        List<Action> actions = new ArrayList<>();
        

        AbstractFile file = getLookup().lookup(AbstractFile.class);
        if (file != null) {
            actions.add(ViewFileInTimelineAction.createViewFileAction(file));
        }

        actions.addAll(DataModelActionsFactory.getActions(tag, false));
        actions.add(null);
        actions.addAll(Arrays.asList(super.getActions(context))); 
        return actions.toArray(new Action[actions.size()]);
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
