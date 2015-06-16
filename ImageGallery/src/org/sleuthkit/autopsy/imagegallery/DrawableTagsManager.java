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

import com.google.common.eventbus.EventBus;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.sleuthkit.autopsy.casemodule.services.TagsManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imagegallery.datamodel.Category;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableFile;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Manages Tags, Tagging, and the relationship between Categories and Tags in
 * the autopsy Db. delegates some, work to the backing {@link TagsManager}.
 */
public class DrawableTagsManager {

    private static final String FOLLOW_UP = "Follow Up";

    private TagsManager autopsyTagsManager;

    /** Used to distribute {@link TagsChangeEvent}s */
    private final EventBus tagsEventBus = new EventBus("Tags Event Bus");

    /** The tag name corresponging to the "built-in" tag "Follow Up" */
    private TagName followUpTagName;

    public DrawableTagsManager(TagsManager autopsyTagsManager) {
        this.autopsyTagsManager = autopsyTagsManager;

    }

    /**
     * assign a new TagsManager to back this one, ie when the current case
     * changes
     *
     * @param autopsyTagsManager
     */
    public synchronized void setAutopsyTagsManager(TagsManager autopsyTagsManager) {
        this.autopsyTagsManager = autopsyTagsManager;
        clearFollowUpTagName();
    }

    /**
     * Use when closing a case to make sure everything is re-initialized in the
     * next case.
     */
    public synchronized void clearFollowUpTagName() {
        followUpTagName = null;
    }

    /**
     * fire a CategoryChangeEvent with the given fileIDs
     *
     * @param fileIDs
     */
    public final void fireChange(Collection<Long> fileIDs) {
        tagsEventBus.post(new TagsChangeEvent(fileIDs));
    }

    /**
     * register an object to receive CategoryChangeEvents
     *
     * @param listner
     */
    public void registerListener(Object listner) {
        tagsEventBus.register(listner);
    }

    /**
     * unregister an object from receiving CategoryChangeEvents
     *
     * @param listener
     */
    public void unregisterListener(Object listener) {
        tagsEventBus.unregister(listener);
    }

    /**
     * get the (cached) follow up TagName
     *
     * @return
     *
     * @throws TskCoreException
     */
    synchronized public TagName getFollowUpTagName() throws TskCoreException {
        if (followUpTagName == null) {
            followUpTagName = getTagName(FOLLOW_UP);
        }
        return followUpTagName;
    }

    public Collection<TagName> getNonCategoryTagNames() {
        try {
            return autopsyTagsManager.getAllTagNames().stream()
                    .filter(Category::isCategoryTagName)
                    .collect(Collectors.toSet());
        } catch (TskCoreException | IllegalStateException ex) {
            Logger.getLogger(DrawableTagsManager.class.getName()).log(Level.WARNING, "couldn't access case", ex);
        }
        return Collections.emptySet();
    }

    public synchronized TagName getTagName(String displayName) throws TskCoreException {
        try {
            for (TagName tn : autopsyTagsManager.getAllTagNames()) {
                if (displayName.equals(tn.getDisplayName())) {
                    return tn;
                }
            }
            try {
                return autopsyTagsManager.addTagName(displayName);
            } catch (TagsManager.TagNameAlreadyExistsException ex) {
                throw new TskCoreException("tagame exists but wasn't found", ex);
            }
        } catch (IllegalStateException ex) {
            Logger.getLogger(DrawableTagsManager.class.getName()).log(Level.SEVERE, "Case was closed out from underneath", ex);
            throw new TskCoreException("Case was closed out from underneath", ex);
        }
    }

    public synchronized TagName getTagName(Category cat) {
        try {
            return getTagName(cat.getDisplayName());
        } catch (TskCoreException ex) {
            return null;
        }
    }

    public void addContentTag(DrawableFile<?> file, TagName tagName, String comment) throws TskCoreException {
        autopsyTagsManager.addContentTag(file, tagName, comment);
    }

    public List<ContentTag> getContentTagsByTagName(TagName t) throws TskCoreException {
        return autopsyTagsManager.getContentTagsByTagName(t);
    }

}
