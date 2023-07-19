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
package org.sleuthkit.autopsy.datamodel;

import java.util.ArrayList;
import java.util.List;
import javax.swing.Action;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.sleuthkit.datamodel.UnsupportedContent;
import org.sleuthkit.datamodel.Tag;

/**
 * This class is used to represent the "Node" for an unsupported content object.
 */
public class UnsupportedContentNode extends AbstractContentNode<UnsupportedContent> {

    /**
     *
     * @param unsupportedContent underlying Content instance
     */
    @NbBundle.Messages({
        "UnsupportedContentNode.displayName=Unsupported Content",})
    public UnsupportedContentNode(UnsupportedContent unsupportedContent) {
        super(unsupportedContent);

        // set name, display name, and icon
        this.setDisplayName(Bundle.UnsupportedContentNode_displayName());

        this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/file-icon.png"); //NON-NLS
    }

    /**
     * Right click action for UnsupportedContentNode node
     *
     * @param popup
     *
     * @return
     */
    @Override
    public Action[] getActions(boolean popup) {
        List<Action> actionsList = new ArrayList<>();

        for (Action a : super.getActions(true)) {
            actionsList.add(a);
        }

        return actionsList.toArray(new Action[actionsList.size()]);

    }

    @NbBundle.Messages({
        "UnsupportedContentNode.createSheet.name.name=Name",
        "UnsupportedContentNode.createSheet.name.displayName=Name",
        "UnsupportedContentNode.createSheet.name.desc=no description",})
    @Override
    protected Sheet createSheet() {
        Sheet sheet = super.createSheet();
        Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
        if (sheetSet == null) {
            sheetSet = Sheet.createPropertiesSet();
            sheet.put(sheetSet);
        }

        sheetSet.put(new NodeProperty<>(Bundle.UnsupportedContentNode_createSheet_name_name(),
                Bundle.UnsupportedContentNode_createSheet_name_displayName(),
                Bundle.UnsupportedContentNode_createSheet_name_desc(),
                this.getDisplayName()));

        return sheet;
    }

    @Override
    public <T> T accept(ContentNodeVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public boolean isLeafTypeNode() {
        return false;
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public String getItemType() {
        return getClass().getName();
    }

    /**
     * Reads and returns a list of all tags associated with this content node.
     *
     * Null implementation of an abstract method.
     *
     * @return list of tags associated with the node.
     */
    @Override
    protected List<Tag> getAllTagsFromDatabase() {
        return new ArrayList<>();
    }

}
