/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-18 Basis Technology Corp.
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
package org.sleuthkit.autopsy.imagegallery.datamodel;

import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import java.util.Collections;
import java.util.List;
import static java.util.Objects.isNull;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javax.annotation.Nonnull;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.events.ContentTagAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.ContentTagDeletedEvent;
import org.sleuthkit.autopsy.casemodule.services.TagsManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.DhsImageCategory;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Manages Tags, Tagging, and the relationship between Categories and Tags in
 * the autopsy Db. Delegates some work to the backing {@link TagsManager}.
 */
@NbBundle.Messages({"DrawableTagsManager.followUp=Follow Up",
    "DrawableTagsManager.bookMark=Bookmark"})
public class DrawableTagsManager {

    private static final Logger logger = Logger.getLogger(DrawableTagsManager.class.getName());

    private static final Image FOLLOW_UP_IMAGE = new Image("/org/sleuthkit/autopsy/imagegallery/images/flag_red.png");
    private static final Image BOOKMARK_IMAGE = new Image("/org/sleuthkit/autopsy/images/star-bookmark-icon-16.png");

    final private Object autopsyTagsManagerLock = new Object();
    private TagsManager autopsyTagsManager;

    /**
     * Used to distribute {@link TagsChangeEvent}s
     */
    private final EventBus tagsEventBus = new AsyncEventBus(
            Executors.newSingleThreadExecutor(
                    new BasicThreadFactory.Builder()
                            .namingPattern("Tags Event Bus")//NON-NLS
                            .uncaughtExceptionHandler((Thread thread, Throwable throwable)
                                    -> logger.log(Level.SEVERE, "uncaught exception in event bus handler", throwable))//NON-NLS  
                            .build()
            ));

    /**
     * The tag name corresponding to the "built-in" tag "Follow Up"
     */
    private TagName followUpTagName;
    private TagName bookmarkTagName;

    public DrawableTagsManager(TagsManager autopsyTagsManager) {
        this.autopsyTagsManager = autopsyTagsManager;

    }

    /**
     * register an object to receive CategoryChangeEvents
     *
     * @param listener
     */
    public void registerListener(Object listener) {
        tagsEventBus.register(listener);
    }

    /**
     * unregister an object from receiving CategoryChangeEvents
     *
     * @param listener
     */
    public void unregisterListener(Object listener) {
        tagsEventBus.unregister(listener);
    }

    public void fireTagAddedEvent(ContentTagAddedEvent event) {
        tagsEventBus.post(event);
    }

    public void fireTagDeletedEvent(ContentTagDeletedEvent event) {
        tagsEventBus.post(event);
    }

    /**
     * assign a new TagsManager to back this one, ie when the current case
     * changes
     *
     * @param autopsyTagsManager
     */
    public void setAutopsyTagsManager(TagsManager autopsyTagsManager) {
        synchronized (autopsyTagsManagerLock) {
            this.autopsyTagsManager = autopsyTagsManager;
            clearFollowUpTagName();
        }
    }

    /**
     * Use when closing a case to make sure everything is re-initialized in the
     * next case.
     */
    public void clearFollowUpTagName() {
        synchronized (autopsyTagsManagerLock) {
            followUpTagName = null;
        }
    }

    /**
     * get the (cached) follow up TagName
     *
     * @return
     *
     * @throws TskCoreException
     */
    public TagName getFollowUpTagName() throws TskCoreException {
        synchronized (autopsyTagsManagerLock) {
            if (isNull(followUpTagName)) {
                followUpTagName = getTagName(Bundle.DrawableTagsManager_followUp());
            }
            return followUpTagName;
        }
    }

    private Object getBookmarkTagName() throws TskCoreException {
        synchronized (autopsyTagsManagerLock) {
            if (isNull(bookmarkTagName)) {
                bookmarkTagName = getTagName(Bundle.DrawableTagsManager_bookMark());
            }
            return bookmarkTagName;
        }
    }

    /**
     * get all the TagNames that are not categories
     *
     * @return all the TagNames that are not categories, in alphabetical order
     *         by displayName, or, an empty set if there was an exception
     *         looking them up from the db.
     */
    @Nonnull
    public List<TagName> getNonCategoryTagNames() {
        synchronized (autopsyTagsManagerLock) {
            try {
                return autopsyTagsManager.getAllTagNames().stream()
                        .filter(CategoryManager::isNotCategoryTagName)
                        .distinct().sorted()
                        .collect(Collectors.toList());
            } catch (TskCoreException | IllegalStateException ex) {
                logger.log(Level.WARNING, "couldn't access case", ex); //NON-NLS
            }
            return Collections.emptyList();
        }
    }

    /**
     * Gets content tags by content.
     *
     * @param content The content of interest.
     *
     * @return A list, possibly empty, of the tags that have been applied to the
     *         content.
     *
     * @throws TskCoreException if there was an error reading from the db
     */
    public List<ContentTag> getContentTags(Content content) throws TskCoreException {
        synchronized (autopsyTagsManagerLock) {
            return autopsyTagsManager.getContentTagsByContent(content);
        }
    }

    /**
     * Gets content tags by DrawableFile.
     *
     * @param drawable The DrawableFile of interest.
     *
     * @return A list, possibly empty, of the tags that have been applied to the
     *         DrawableFile.
     *
     * @throws TskCoreException if there was an error reading from the db
     */
    public List<ContentTag> getContentTags(DrawableFile drawable) throws TskCoreException {
        return getContentTags(drawable.getAbstractFile());
    }

    public TagName getTagName(String displayName) throws TskCoreException {
        synchronized (autopsyTagsManagerLock) {
            try {
                TagName returnTagName = autopsyTagsManager.getDisplayNamesToTagNamesMap().get(displayName);
                if (returnTagName != null) {
                    return returnTagName;
                }
                try {
                    return autopsyTagsManager.addTagName(displayName);
                } catch (TagsManager.TagNameAlreadyExistsException ex) {
                    returnTagName = autopsyTagsManager.getDisplayNamesToTagNamesMap().get(displayName);
                    if (returnTagName != null) {
                        return returnTagName;
                    }
                    throw new TskCoreException("Tag name exists but an error occured in retrieving it", ex);
                }
            } catch (NullPointerException | IllegalStateException ex) {
                logger.log(Level.SEVERE, "Case was closed out from underneath", ex); //NON-NLS
                throw new TskCoreException("Case was closed out from underneath", ex);
            }
        }
    }

    public TagName getTagName(DhsImageCategory cat) {
        try {
            return getTagName(cat.getDisplayName());
        } catch (TskCoreException ex) {
            return null;
        }
    }

    public ContentTag addContentTag(DrawableFile file, TagName tagName, String comment) throws TskCoreException {
        synchronized (autopsyTagsManagerLock) {
            return autopsyTagsManager.addContentTag(file.getAbstractFile(), tagName, comment);
        }
    }

    public List<ContentTag> getContentTagsByTagName(TagName tagName) throws TskCoreException {
        synchronized (autopsyTagsManagerLock) {
            return autopsyTagsManager.getContentTagsByTagName(tagName);
        }
    }

    public List<TagName> getAllTagNames() throws TskCoreException {
        synchronized (autopsyTagsManagerLock) {
            return autopsyTagsManager.getAllTagNames();
        }
    }

    public List<TagName> getTagNamesInUse() throws TskCoreException {
        synchronized (autopsyTagsManagerLock) {
            return autopsyTagsManager.getTagNamesInUse();
        }
    }

    public void deleteContentTag(ContentTag contentTag) throws TskCoreException {
        synchronized (autopsyTagsManagerLock) {
            autopsyTagsManager.deleteContentTag(contentTag);
        }
    }

    public Node getGraphic(TagName tagname) {
        try {
            if (tagname.equals(getFollowUpTagName())) {
                return new ImageView(FOLLOW_UP_IMAGE);
            } else if (tagname.equals(getBookmarkTagName())) {
                return new ImageView(BOOKMARK_IMAGE);
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Failed to get \"Follow Up\" or \"Bookmark\"tag name from db.", ex);
        }
        return DrawableAttribute.TAGS.getGraphicForValue(tagname);
    }

}
