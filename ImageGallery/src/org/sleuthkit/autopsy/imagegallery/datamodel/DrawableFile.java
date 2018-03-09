/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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

import org.sleuthkit.autopsy.datamodel.DhsImageCategory;
import java.lang.ref.SoftReference;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.concurrent.Task;
import javafx.scene.image.Image;
import javafx.util.Pair;
import javax.annotation.Nonnull;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imagegallery.FileTypeUtils;
import org.sleuthkit.autopsy.imagegallery.ThumbnailCache;
import org.sleuthkit.autopsy.imagegallery.utils.TaskUtils;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.Tag;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A file that contains visual information such as an image or video.
 */
public abstract class DrawableFile {

    private static final Logger LOGGER = Logger.getLogger(DrawableFile.class.getName());

    public static DrawableFile create(AbstractFile abstractFileById, boolean analyzed) {
        return create(abstractFileById, analyzed, FileTypeUtils.hasVideoMIMEType(abstractFileById));
    }

    /**
     * Skip the database query if we have already determined the file type.
     */
    public static DrawableFile create(AbstractFile abstractFileById, boolean analyzed, boolean isVideo) {
        return isVideo
                ? new VideoFile(abstractFileById, analyzed)
                : new ImageFile(abstractFileById, analyzed);
    }

    public static DrawableFile create(Long id, boolean analyzed) throws TskCoreException, NoCurrentCaseException {
        return create(Case.getOpenCase().getSleuthkitCase().getAbstractFileById(id), analyzed);
    }

    private SoftReference<Image> imageRef;

    private String drawablePath;

    private final AbstractFile file;

    private final SimpleBooleanProperty analyzed;

    private final SimpleObjectProperty<DhsImageCategory> category = new SimpleObjectProperty<>(null);

    private String make;

    private String model;

    protected DrawableFile(AbstractFile file, Boolean analyzed) {
        this.analyzed = new SimpleBooleanProperty(analyzed);
        this.file = file;
    }

    public abstract boolean isVideo();

    public List<Pair<DrawableAttribute<?>, Collection<?>>> getAttributesList() {
        return DrawableAttribute.getValues().stream()
                .map(this::makeAttributeValuePair)
                .collect(Collectors.toList());
    }

    public String getMIMEType() {
        return file.getMIMEType();
    }

    public long getId() {
        return file.getId();
    }

    public long getCtime() {
        return file.getCtime();
    }

    public long getCrtime() {
        return file.getCrtime();
    }

    public long getAtime() {
        return file.getAtime();
    }

    public long getMtime() {
        return file.getMtime();
    }

    public String getMd5Hash() {
        return file.getMd5Hash();
    }

    public String getName() {
        return file.getName();
    }

    public String getAtimeAsDate() {
        return file.getAtimeAsDate();
    }

    public synchronized String getUniquePath() throws TskCoreException {
        return file.getUniquePath();
    }

    public SleuthkitCase getSleuthkitCase() {
        return file.getSleuthkitCase();
    }

    private Pair<DrawableAttribute<?>, Collection<?>> makeAttributeValuePair(DrawableAttribute<?> t) {
        return new Pair<>(t, t.getValue(DrawableFile.this));
    }

    public String getModel() {
        if (model == null) {
            model = WordUtils.capitalizeFully((String) getValueOfBBAttribute(ARTIFACT_TYPE.TSK_METADATA_EXIF, ATTRIBUTE_TYPE.TSK_DEVICE_MODEL));
        }
        return model;
    }

    public String getMake() {
        if (make == null) {
            make = WordUtils.capitalizeFully((String) getValueOfBBAttribute(ARTIFACT_TYPE.TSK_METADATA_EXIF, ATTRIBUTE_TYPE.TSK_DEVICE_MAKE));
        }
        return make;
    }

    public Set<TagName> getTagNames() {
        try {

            return getContentTags().stream()
                    .map(Tag::getName)
                    .collect(Collectors.toSet());
        } catch (TskCoreException ex) {
            Logger.getAnonymousLogger().log(Level.WARNING, "problem looking up " + DrawableAttribute.TAGS.getDisplayName() + " for " + file.getName(), ex); //NON-NLS
        } catch (IllegalStateException ex) {
            Logger.getAnonymousLogger().log(Level.WARNING, "there is no case open; failed to look up " + DrawableAttribute.TAGS.getDisplayName() + " for " + getContentPathSafe(), ex); //NON-NLS
        }
        return Collections.emptySet();
    }

    protected Object getValueOfBBAttribute(ARTIFACT_TYPE artType, ATTRIBUTE_TYPE attrType) {
        try {

            //why doesn't file.getArtifacts() work?
            //TODO: this seams like overkill, use a more targeted query
            ArrayList<BlackboardArtifact> artifacts = file.getArtifacts(artType);// getAllArtifacts();

            for (BlackboardArtifact artf : artifacts) {
                if (artf.getArtifactTypeID() == artType.getTypeID()) {
                    for (BlackboardAttribute attr : artf.getAttributes()) {
                        if (attr.getAttributeType().getTypeID() == attrType.getTypeID()) {
                            switch (attr.getAttributeType().getValueType()) {
                                case BYTE:
                                    return attr.getValueBytes();
                                case DOUBLE:
                                    return attr.getValueDouble();
                                case INTEGER:
                                    return attr.getValueInt();
                                case LONG:
                                    return attr.getValueLong();
                                case STRING:
                                    return attr.getValueString();
                                case DATETIME:
                                    return attr.getValueLong();
                            }
                        }
                    }
                }
            }
        } catch (TskCoreException ex) {
            Logger.getAnonymousLogger().log(Level.WARNING, ex,
                    () -> MessageFormat.format("problem looking up {0}/{1}" + " " + " for {2}", new Object[]{artType.getDisplayName(), attrType.getDisplayName(), getContentPathSafe()})); //NON-NLS
        }
        return "";
    }

    public void setCategory(DhsImageCategory category) {
        categoryProperty().set(category);

    }

    public DhsImageCategory getCategory() {
        updateCategory();
        return category.get();
    }

    public SimpleObjectProperty<DhsImageCategory> categoryProperty() {
        return category;
    }

    /**
     * set the category property to the most severe one found
     */
    private void updateCategory() {
        try {
            category.set(getContentTags().stream()
                    .map(Tag::getName).filter(CategoryManager::isCategoryTagName)
                    .map(TagName::getDisplayName)
                    .map(DhsImageCategory::fromDisplayName)
                    .sorted().findFirst() //sort by severity and take the first
                    .orElse(DhsImageCategory.ZERO)
            );
        } catch (TskCoreException ex) {
            LOGGER.log(Level.WARNING, "problem looking up category for " + this.getContentPathSafe(), ex); //NON-NLS
        } catch (IllegalStateException ex) {
            // We get here many times if the case is closed during ingest, so don't print out a ton of warnings.
        }
    }

    private List<ContentTag> getContentTags() throws TskCoreException {
        return getSleuthkitCase().getContentTagsByContent(file);
    }

    @Deprecated
    public Image getThumbnail() {
        try {
            return getThumbnailTask().get();
        } catch (InterruptedException | ExecutionException ex) {
            return null;
        }

    }

    public Task<Image> getThumbnailTask() {
        return ThumbnailCache.getDefault().getThumbnailTask(this);
    }

    @Deprecated //use non-blocking getReadFullSizeImageTask  instead for most cases
    public Image getFullSizeImage() {
        try {
            return getReadFullSizeImageTask().get();
        } catch (InterruptedException | ExecutionException ex) {
            return null;
        }
    }

    public Task<Image> getReadFullSizeImageTask() {
        Image image = (imageRef != null) ? imageRef.get() : null;
        if (image == null || image.isError()) {
            Task<Image> readImageTask = getReadFullSizeImageTaskHelper();
            readImageTask.stateProperty().addListener(stateProperty -> {
                switch (readImageTask.getState()) {
                    case SUCCEEDED:
                        try {
                            imageRef = new SoftReference<>(readImageTask.get());
                        } catch (InterruptedException | ExecutionException exception) {
                            LOGGER.log(Level.WARNING, getMessageTemplate(exception), getContentPathSafe());
                        }
                        break;
                }
            });
            return readImageTask;
        } else {
            return TaskUtils.taskFrom(() -> image);
        }
    }

    abstract String getMessageTemplate(Exception exception);

    abstract Task<Image> getReadFullSizeImageTaskHelper();

    public void setAnalyzed(Boolean analyzed) {
        this.analyzed.set(analyzed);
    }

    public boolean isAnalyzed() {
        return analyzed.get();
    }

    public AbstractFile getAbstractFile() {
        return this.file;
    }

    /**
     * Get the width of the visual content.
     * 
     * @return The width.
     */
    abstract Double getWidth();

    /**
     * Get the height of the visual content.
     * 
     * @return The height.
     */
    abstract Double getHeight();

    public String getDrawablePath() {
        if (drawablePath != null) {
            return drawablePath;
        } else {
            try {
                drawablePath = StringUtils.removeEnd(getUniquePath(), getName());
                return drawablePath;
            } catch (TskCoreException ex) {
                LOGGER.log(Level.WARNING, "failed to get drawablePath from " + getContentPathSafe(), ex); //NON-NLS
                return "";
            }
        }
    }

    public Set<String> getHashSetNames() throws TskCoreException {
        return file.getHashSetNames();
    }

    @Nonnull
    public Set<String> getHashSetNamesUnchecked() {
        try {
            return getHashSetNames();
        } catch (TskCoreException ex) {
            LOGGER.log(Level.WARNING, "Failed to get hash set names", ex); //NON-NLS
            return Collections.emptySet();
        }
    }

    /**
     * Get the unique path for this DrawableFile, or if that fails, just return
     * the name.
     *
     * @param content
     *
     * @return
     */
    public String getContentPathSafe() {
        try {
            return getUniquePath();
        } catch (TskCoreException tskCoreException) {
            String contentName = this.getName();
            LOGGER.log(Level.SEVERE, "Failed to get unique path for " + contentName, tskCoreException); //NOI18N NON-NLS
            return contentName;
        }
    }
}
