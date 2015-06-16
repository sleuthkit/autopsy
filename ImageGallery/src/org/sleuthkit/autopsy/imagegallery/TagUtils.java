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
package org.sleuthkit.autopsy.imagegallery;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SplitMenuButton;
import javafx.scene.image.ImageView;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.services.TagsManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imagegallery.actions.AddDrawableTagAction;
import org.sleuthkit.autopsy.imagegallery.datamodel.Category;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableAttribute;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Contains static methods for dealing with Tags in ImageGallery
 */
public class TagUtils {

    private static final String FOLLOW_UP = "Follow Up";

    private static TagName followUpTagName;

    /**
     * Use when closing a case to make sure everything is re-initialized in the
     * next case.
     */
    public static void clearFollowUpTagName() {
        followUpTagName = null;
    }

    private final static List<TagListener> listeners = new ArrayList<>();

    synchronized public static TagName getFollowUpTagName() throws TskCoreException {
        if (followUpTagName == null) {
            followUpTagName = getTagName(FOLLOW_UP);
        }
        return followUpTagName;
    }

    static public Collection<TagName> getNonCategoryTagNames() {
        try {
            return Case.getCurrentCase().getServices().getTagsManager().getAllTagNames().stream()
                    .filter(Category::isCategoryTagName)
                    .collect(Collectors.toSet());
        } catch (TskCoreException | IllegalStateException ex) {
            Logger.getLogger(TagUtils.class.getName()).log(Level.WARNING, "couldn't access case", ex);
        }
        return Collections.emptySet();
    }

    synchronized static public TagName getTagName(String displayName) throws TskCoreException {
        try {
            final TagsManager tagsManager = Case.getCurrentCase().getServices().getTagsManager();

            for (TagName tn : tagsManager.getAllTagNames()) {
                if (displayName.equals(tn.getDisplayName())) {
                    return tn;
                }
            }
            try {
                return tagsManager.addTagName(displayName);
            } catch (TagsManager.TagNameAlreadyExistsException ex) {
                throw new TskCoreException("tagame exists but wasn't found", ex);
            }
        } catch (IllegalStateException ex) {
            Logger.getLogger(TagUtils.class.getName()).log(Level.SEVERE, "Case was closed out from underneath", ex);
            throw new TskCoreException("Case was closed out from underneath", ex);
        }
    }

    public static void fireChange(Collection<Long> ids) {
        Set<TagUtils.TagListener> listenersCopy = new HashSet<>(listeners);
        synchronized (listeners) {
            listenersCopy.addAll(listeners);
        }
        for (TagListener list : listenersCopy) {
            list.handleTagsChanged(ids);
        }
    }

    public static void registerListener(TagListener aThis) {
        synchronized (listeners) {
            listeners.add(aThis);
        }
    }

    public static void unregisterListener(TagListener aThis) {
        synchronized (listeners) {
            listeners.remove(aThis);
        }
    }

    /**
     * @param tn the value of tn
     */
    static public MenuItem createSelTagMenuItem(final TagName tn, final SplitMenuButton tagSelectedMenuButton) {
        final MenuItem menuItem = new MenuItem(tn.getDisplayName(), new ImageView(DrawableAttribute.TAGS.getIcon()));
        menuItem.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent t) {
                AddDrawableTagAction.getInstance().addTag(tn, "");
                tagSelectedMenuButton.setText(tn.getDisplayName());
                tagSelectedMenuButton.setOnAction(this);
            }
        });
        return menuItem;
    }

    public static interface TagListener {

        public void handleTagsChanged(Collection<Long> ids);
    }
}
