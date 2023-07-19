/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020-2020 Basis Technology Corp.
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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.datamodel.utils.FileNameTransTask;
import org.sleuthkit.autopsy.texttranslation.TextTranslationService;
import org.sleuthkit.datamodel.Content;

/**
 * An abstract superclass for a node that represents a tag, uses the name of a
 * given Content object as its display name, and has a property sheet with an
 * original name property when machine translation is enabled.
 *
 * The translation of the Content name is done in a background thread. The
 * translated name is made the display name of the node and the untranslated
 * name is put into both the original name property and into the node's tooltip.
 *
 * TODO (Jira-6174): Consider modifying this class to be able to use it more broadly
 * within the Autopsy data model (i.e., AbstractNode suclasses). It's not really
 * specific to a tag node.
 */
@NbBundle.Messages({
    "TagNode.propertySheet.origName=Original Name",
    "TagNode.propertySheet.origNameDisplayName=Original Name"
})
abstract class TagNode extends DisplayableItemNode {

    private final static String ORIG_NAME_PROP_NAME = Bundle.TagNode_propertySheet_origName();
    private final static String ORIG_NAME_PROP_DISPLAY_NAME = Bundle.TagNode_propertySheet_origNameDisplayName();

    private final String originalName;
    private volatile String translatedName;

    /**
     * An abstract superclass for a node that represents a tag, uses the name of
     * a given Content object as its display name, and has a property sheet with
     * an untranslated file name property when machine translation is enabled.
     *
     * @param lookup  The Lookup of the node.
     * @param content The Content to use for the node display name.
     */
    TagNode(Lookup lookup, Content content) {
        super(Children.LEAF, lookup);
        originalName = content.getName();
    }

    @Override
    public boolean isLeafTypeNode() {
        return true;
    }

    @Override
    abstract public String getItemType();

    @Override
    abstract public <T> T accept(DisplayableItemNodeVisitor<T> visitor);

    /**
     * Adds an original name property to the node's property sheet and submits
     * an original name translation task.
     *
     * The translation of the original name is done in a background thread. The
     * translated name is made the display name of the node and the untranslated
     * name is put into both the original name property and into the node's
     * tooltip.
     *
     * @param properties The node's property sheet.
     */
    protected void addOriginalNameProp(Sheet.Set properties) {
        if (TextTranslationService.getInstance().hasProvider() && UserPreferences.displayTranslatedFileNames()) {
            properties.put(new NodeProperty<>(
                    ORIG_NAME_PROP_NAME,
                    ORIG_NAME_PROP_DISPLAY_NAME,
                    "",
                    translatedName != null ? originalName : ""));
            if (translatedName == null) {
                new FileNameTransTask(originalName, this, new NameTranslationListener()).submit();
            }
        }
    }

    /**
     * A listener for PropertyChangeEvents from a background task used to
     * translate the original display name associated with the node.
     */
    private class NameTranslationListener implements PropertyChangeListener {

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            String eventType = evt.getPropertyName();
            if (eventType.equals(FileNameTransTask.getPropertyName())) {
                translatedName = evt.getNewValue().toString();
                String originalName = evt.getOldValue().toString();
                setDisplayName(translatedName);
                setShortDescription(originalName);
                updatePropertySheet(new NodeProperty<>(
                        ORIG_NAME_PROP_NAME,
                        ORIG_NAME_PROP_DISPLAY_NAME,
                        "",
                        originalName));
            }
        }
    }

}
