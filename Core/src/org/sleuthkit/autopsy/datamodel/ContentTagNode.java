/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013 Basis Technology Corp.
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

import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import javax.swing.Action;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.actions.DeleteContentTagAction;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.timeline.actions.ViewFileInTimelineAction;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Instances of this class wrap ContentTag objects. In the Autopsy presentation
 * of the SleuthKit data model, they are leaf nodes of a tree consisting of
 * content and blackboard artifact tags, grouped first by tag type, then by tag
 * name.
 */
class ContentTagNode extends DisplayableItemNode {

    private static final String ICON_PATH = "org/sleuthkit/autopsy/images/blue-tag-icon-16.png"; //NON-NLS
    private final ContentTag tag;

    public ContentTagNode(ContentTag tag) {
        super(Children.LEAF, Lookups.fixed(tag, tag.getContent()));
        super.setName(tag.getContent().getName());
        super.setDisplayName(tag.getContent().getName());
        this.setIconBaseWithExtension(ICON_PATH);
        this.tag = tag;
    }

    @Override
    protected Sheet createSheet() {
        Content content = tag.getContent();
        String contentPath;
        try {
            contentPath = content.getUniquePath();
        } catch (TskCoreException ex) {
            Logger.getLogger(ContentTagNode.class.getName()).log(Level.SEVERE, "Failed to get path for content (id = " + content.getId() + ")", ex); //NON-NLS
            contentPath = NbBundle.getMessage(this.getClass(), "ContentTagNode.createSheet.unavail.path");
        }
        AbstractFile file = content instanceof AbstractFile ? (AbstractFile) content : null;

        Sheet propertySheet = super.createSheet();
        Sheet.Set properties = propertySheet.get(Sheet.PROPERTIES);
        if (properties == null) {
            properties = Sheet.createPropertiesSet();
            propertySheet.put(properties);
        }
        properties.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "ContentTagNode.createSheet.file.name"),
                NbBundle.getMessage(this.getClass(), "ContentTagNode.createSheet.file.displayName"),
                "",
                content.getName()));
        properties.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "ContentTagNode.createSheet.filePath.name"),
                NbBundle.getMessage(this.getClass(), "ContentTagNode.createSheet.filePath.displayName"),
                "",
                contentPath));
        properties.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "ContentTagNode.createSheet.comment.name"),
                NbBundle.getMessage(this.getClass(), "ContentTagNode.createSheet.comment.displayName"),
                "",
                tag.getComment()));
        properties.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "ContentTagNode.createSheet.fileModifiedTime.name"),
                NbBundle.getMessage(this.getClass(), "ContentTagNode.createSheet.fileModifiedTime.displayName"),
                "",
                file != null ? ContentUtils.getStringTime(file.getMtime(), file) : ""));
        properties.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "ContentTagNode.createSheet.fileChangedTime.name"),
                NbBundle.getMessage(this.getClass(), "ContentTagNode.createSheet.fileChangedTime.displayName"),
                "",
                file != null ? ContentUtils.getStringTime(file.getCtime(), file) : ""));
        properties.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "ContentTagNode.createSheet.fileAccessedTime.name"),
                NbBundle.getMessage(this.getClass(), "ContentTagNode.createSheet.fileAccessedTime.displayName"),
                "",
                file != null ? ContentUtils.getStringTime(file.getAtime(), file) : ""));
        properties.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "ContentTagNode.createSheet.fileCreatedTime.name"),
                NbBundle.getMessage(this.getClass(), "ContentTagNode.createSheet.fileCreatedTime.displayName"),
                "",
                file != null ? ContentUtils.getStringTime(file.getCrtime(), file) : ""));
        properties.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "ContentTagNode.createSheet.fileSize.name"),
                NbBundle.getMessage(this.getClass(), "ContentTagNode.createSheet.fileSize.displayName"),
                "",
                content.getSize()));
        return propertySheet;
    }

    @Override
    public Action[] getActions(boolean context) {
        List<Action> actions = DataModelActionsFactory.getActions(tag.getContent(), false);
        actions.addAll(Arrays.asList(super.getActions(context)));

        AbstractFile file = getLookup().lookup(AbstractFile.class);
        if (file != null) {
            actions.add(ViewFileInTimelineAction.createViewFileAction(file));
        }
        actions.add(null); // Adds a menu item separator. 
        actions.add(DeleteContentTagAction.getInstance());
        return actions.toArray(new Action[actions.size()]);
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> v) {
        return v.visit(this);
    }

    @Override
    public boolean isLeafTypeNode() {
        return true;
    }

    /*
     * TODO (AUT-1849): Correct or remove peristent column reordering code
     *
     * Added to support this feature.
     */
//    @Override
//    public String getItemType() {
//        return "ContentTag"; //NON-NLS
//    }
}
