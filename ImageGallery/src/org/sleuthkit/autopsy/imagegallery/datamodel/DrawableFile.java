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
import javafx.concurrent.Worker;
import javafx.scene.image.Image;
import javafx.util.Pair;
import javax.annotation.Nonnull;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.WordUtils;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imagegallery.FileTypeUtils;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.autopsy.imagegallery.utils.TaskUtils;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.Tag;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskDataException;

/**
 * A file that contains visual information such as an image or video.
 */
public abstract class DrawableFile {

    private static final Logger LOGGER = Logger.getLogger(DrawableFile.class.getName());

    public static DrawableFile create(AbstractFile abstractFile, boolean analyzed) {
        return create(abstractFile, analyzed, FileTypeUtils.hasVideoMIMEType(abstractFile));
    }

    /**
     * Skip the database query if we have already determined the file type.
     *
     * @param file     The underlying AbstractFile.
     * @param analyzed Is the file analyzed.
     * @param isVideo  Is the file a video.
     *
     * @return
     */
    public static DrawableFile create(AbstractFile file, boolean analyzed, boolean isVideo) {
        return isVideo
                ? new VideoFile(file, analyzed)
                : new ImageFile(file, analyzed);
    }

    public static DrawableFile create(Long fileID, boolean analyzed) throws TskCoreException, NoCurrentCaseException {
        return create(Case.getCurrentCaseThrows().getSleuthkitCase().getAbstractFileById(fileID), analyzed);
    }

    private SoftReference<Image> imageRef;

    private String drawablePath;

    private final AbstractFile file;

    private final SimpleBooleanProperty analyzed;

    private final SimpleObjectProperty<TagName> categoryTagName = new SimpleObjectProperty<>(null);

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

    public DataSource getDataSource() throws TskCoreException, TskDataException {
        return getSleuthkitCase().getDataSource(file.getDataSourceObjectId());
    }

    private Pair<DrawableAttribute<?>, Collection<?>> makeAttributeValuePair(DrawableAttribute<?> attribute) {
        return new Pair<>(attribute, attribute.getValue(this));
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

    public TagName getCategory() {
        updateCategory();
        return categoryTagName.get();
    }

    public SimpleObjectProperty<TagName> categoryProperty() {
        return categoryTagName;
    }

    /**
     * Update the category property.
     */
    private void updateCategory() {    
        try {
            ImageGalleryController controllerForCase = ImageGalleryController.getController(Case.getCurrentCaseThrows());
            if (controllerForCase == null) {
                // This can only happen during case closing, so return without generating an error.
                return;
            }
            
            List<ContentTag> contentTags = getContentTags();
            TagName tag = null;
            for (ContentTag ct : contentTags) {
                TagName tagName = ct.getName();
                if (controllerForCase.getCategoryManager().isCategoryTagName(tagName)) {
                    tag = tagName;
                    break;
                }
            }
            categoryTagName.set(tag);
        } catch (TskCoreException ex) {
            LOGGER.log(Level.WARNING, "problem looking up category for " + this.getContentPathSafe(), ex); //NON-NLS
        } catch (IllegalStateException | NoCurrentCaseException ex) {
            // We get here many times if the case is closed during ingest, so don't print out a ton of warnings.
        }
    }

    private List<ContentTag> getContentTags() throws TskCoreException {
        return getSleuthkitCase().getContentTagsByContent(file);
    }

    public Task<Image> getReadFullSizeImageTask() {
        Image image = (imageRef != null) ? imageRef.get() : null;
        if (image == null || image.isError()) {
            Task<Image> readImageTask = getReadFullSizeImageTaskHelper();
            readImageTask.stateProperty().addListener(stateProperty -> {
                if (readImageTask.getState() == Worker.State.SUCCEEDED) {
                    try {
                        imageRef = new SoftReference<>(readImageTask.get());
                    } catch (InterruptedException | ExecutionException exception) {
                        LOGGER.log(Level.WARNING, getMessageTemplate(exception), getContentPathSafe());
                    }
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
