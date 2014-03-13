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

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Action;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.actions.DeleteContentTagAction;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Instances of this class wrap ContentTag objects. In the Autopsy presentation
 * of the SleuthKit data model, they are leaf nodes of a tree consisting of
 * content and blackboard artifact tags, grouped first by tag type, then by tag
 * name.
 */
class ContentTagNode extends DisplayableItemNode {

    private static final String ICON_PATH = "org/sleuthkit/autopsy/images/blue-tag-icon-16.png";
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
        Sheet propertySheet = super.createSheet();
        Sheet.Set properties = propertySheet.get(Sheet.PROPERTIES);
        if (properties == null) {
            properties = Sheet.createPropertiesSet();
            propertySheet.put(properties);
        }

        properties.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "ContentTagNode.createSheet.file.name"),
                NbBundle.getMessage(this.getClass(), "ContentTagNode.createSheet.file.displayName"),
                "",
                tag.getContent().getName()));
        String contentPath;
        try {
            contentPath = tag.getContent().getUniquePath();
        } catch (TskCoreException ex) {
            Logger.getLogger(ContentTagNode.class.getName()).log(Level.SEVERE, "Failed to get path for content (id = " + tag.getContent().getId() + ")", ex);
            contentPath = NbBundle.getMessage(this.getClass(), "ContentTagNode.createSheet.unavail.path");
        }
        properties.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "ContentTagNode.createSheet.filePath.name"),
                NbBundle.getMessage(this.getClass(), "ContentTagNode.createSheet.filePath.displayName"),
                "",
                contentPath));
        properties.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "ContentTagNode.createSheet.comment.name"),
                NbBundle.getMessage(this.getClass(), "ContentTagNode.createSheet.comment.displayName"),
                "",
                tag.getComment()));

        return propertySheet;
    }

    @Override
    public Action[] getActions(boolean context) {
        List<Action> actions = DataModelActionsFactory.getActions(tag.getContent(), false);
        actions.add(null); // Adds a menu item separator. 
        actions.add(DeleteContentTagAction.getInstance());
        return actions.toArray(new Action[0]);
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> v) {
        return v.visit(this);
    }

    @Override
    public boolean isLeafTypeNode() {
        return true;
    }
}
