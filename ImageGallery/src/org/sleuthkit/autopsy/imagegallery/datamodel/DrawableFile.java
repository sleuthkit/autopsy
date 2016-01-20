/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-15 Basis Technology Corp.
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
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imagegallery.FileTypeUtils;
import org.sleuthkit.autopsy.imagegallery.ThumbnailCache;
import org.sleuthkit.autopsy.imagegallery.utils.TaskUtils;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.SleuthkitItemVisitor;
import org.sleuthkit.datamodel.Tag;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * @TODO: There is something I don't understand or have done wrong about
 * implementing this class,as it is unreadable by
 * {@link ReadContentInputStream}. As a work around we keep a reference to the
 * original {@link AbstractFile} to use when reading the image. -jm
 */
public abstract class DrawableFile<T extends AbstractFile> extends AbstractFile {

    private static final Logger LOGGER = Logger.getLogger(DrawableFile.class.getName());

    public static DrawableFile<?> create(AbstractFile abstractFileById, boolean analyzed) {
        return create(abstractFileById, analyzed, FileTypeUtils.isVideoFile(abstractFileById));
    }

    /**
     * Skip the database query if we have already determined the file type.
     */
    public static DrawableFile<?> create(AbstractFile abstractFileById, boolean analyzed, boolean isVideo) {
        return isVideo
                ? new VideoFile<>(abstractFileById, analyzed)
                : new ImageFile<>(abstractFileById, analyzed);
    }

    public static DrawableFile<?> create(Long id, boolean analyzed) throws TskCoreException, IllegalStateException {
        return create(Case.getCurrentCase().getSleuthkitCase().getAbstractFileById(id), analyzed);
    }

    SoftReference<Image> imageRef;
//    SoftReference<Image> thumbref;

    private String drawablePath;

    private final T file;

    private final SimpleBooleanProperty analyzed;

    private final SimpleObjectProperty<Category> category = new SimpleObjectProperty<>(null);

    private String make;

    private String model;

    protected DrawableFile(T file, Boolean analyzed) {
        /*
         * @TODO: the two 'new Integer(0).shortValue()' values and null are
         * placeholders because the super constructor expects values i can't get
         * easily at the moment. I assume this is related to why
         * ReadContentInputStream can't read from DrawableFiles.
         */

        super(file.getSleuthkitCase(),
                file.getId(),
                file.getAttrType(),
                file.getAttrId(),
                file.getName(),
                file.getType(),
                file.getMetaAddr(),
                (int) file.getMetaSeq(),
                file.getDirType(),
                file.getMetaType(),
                null,
                new Integer(0).shortValue(),
                file.getSize(),
                file.getCtime(),
                file.getCrtime(),
                file.getAtime(),
                file.getMtime(),
                new Integer(0).shortValue(),
                file.getUid(),
                file.getGid(),
                file.getMd5Hash(),
                file.getKnown(),
                file.getParentPath());
        this.analyzed = new SimpleBooleanProperty(analyzed);
        this.file = file;
    }

    public abstract boolean isVideo();

    @Override
    public boolean isRoot() {
        return false;
    }

    @Override
    public <T> T accept(SleuthkitItemVisitor<T> v) {
        return file.accept(v);
    }

    @Override
    public <T> T accept(ContentVisitor<T> v) {
        return file.accept(v);
    }

    @Override
    public List<Content> getChildren() throws TskCoreException {
        return new ArrayList<>();
    }

    @Override
    public List<Long> getChildrenIds() throws TskCoreException {
        return new ArrayList<>();
    }

    public List<Pair<DrawableAttribute<?>, Collection<?>>> getAttributesList() {
        return DrawableAttribute.getValues().stream()
                .map(this::makeAttributeValuePair)
                .collect(Collectors.toList());
    }

    private Pair<DrawableAttribute<?>, Collection<?>> makeAttributeValuePair(DrawableAttribute<?> t) {
        return new Pair<>(t, t.getValue(DrawableFile.this));
    }

    public String getModel() {
        if (model == null) {
            model = WordUtils.capitalizeFully((String) getValueOfBBAttribute(BlackboardArtifact.ARTIFACT_TYPE.TSK_METADATA_EXIF, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DEVICE_MODEL));
        }
        return model;
    }

    public String getMake() {
        if (make == null) {
            make = WordUtils.capitalizeFully((String) getValueOfBBAttribute(BlackboardArtifact.ARTIFACT_TYPE.TSK_METADATA_EXIF, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DEVICE_MAKE));
        }
        return make;
    }

    public Set<TagName> getTagNames() {
        try {

            return getSleuthkitCase().getContentTagsByContent(this).stream()
                    .map(Tag::getName)
                    .collect(Collectors.toSet());
        } catch (TskCoreException ex) {
            Logger.getAnonymousLogger().log(Level.WARNING, "problem looking up " + DrawableAttribute.TAGS.getDisplayName() + " for " + file.getName(), ex);
        } catch (IllegalStateException ex) {
            Logger.getAnonymousLogger().log(Level.WARNING, "there is no case open; failed to look up " + DrawableAttribute.TAGS.getDisplayName() + " for " + file.getName());
        }
        return Collections.emptySet();
    }

    protected Object getValueOfBBAttribute(BlackboardArtifact.ARTIFACT_TYPE artType, BlackboardAttribute.ATTRIBUTE_TYPE attrType) {
        try {

            //why doesn't file.getArtifacts() work?
            //TODO: this seams like overkill, use a more targeted query
            ArrayList<BlackboardArtifact> artifacts = file.getArtifacts(artType);// getAllArtifacts();

            for (BlackboardArtifact artf : artifacts) {
                if (artf.getArtifactTypeID() == artType.getTypeID()) {
                    for (BlackboardAttribute attr : artf.getAttributes()) {
                        if (attr.getAttributeTypeID() == attrType.getTypeID()) {
                            switch (attr.getValueType()) {
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
            Logger.getAnonymousLogger().log(Level.WARNING, "problem looking up {0}/{1}" + " " + " for {2}", new Object[]{artType.getDisplayName(), attrType.getDisplayName(), getName()});
        }
        return "";
    }

    public void setCategory(Category category) {
        categoryProperty().set(category);

    }

    public Category getCategory() {
        updateCategory();
        return category.get();
    }

    public SimpleObjectProperty<Category> categoryProperty() {
        return category;
    }

    /**
     * set the category property to the most severe one found
     */
    private void updateCategory() {
        try {
            category.set(getSleuthkitCase().getContentTagsByContent(this).stream()
                    .map(Tag::getName).filter(CategoryManager::isCategoryTagName)
                    .map(TagName::getDisplayName)
                    .map(Category::fromDisplayName)
                    .sorted().findFirst() //sort by severity and take the first
                    .orElse(Category.ZERO)
            );
        } catch (TskCoreException ex) {
            LOGGER.log(Level.WARNING, "problem looking up category for file " + this.getName() + ex.getLocalizedMessage());
        } catch (IllegalStateException ex) {
            // We get here many times if the case is closed during ingest, so don't print out a ton of warnings.
        }
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

    public T getAbstractFile() {
        return this.file;
    }

    abstract Double getWidth();

    abstract Double getHeight();

    public String getDrawablePath() {
        if (drawablePath != null) {
            return drawablePath;
        } else {
            try {
                drawablePath = StringUtils.removeEnd(getUniquePath(), getName());
                return drawablePath;
            } catch (TskCoreException ex) {
                LOGGER.log(Level.WARNING, "failed to get drawablePath from {0}", getName());
                return "";
            }
        }
    }

    @Nonnull
    public Set<String> getHashSetNamesUnchecked() {
        try {
            return getHashSetNames();
        } catch (TskCoreException ex) {
            LOGGER.log(Level.WARNING, "Failed to get hash set names", ex);
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
            return this.getUniquePath();
        } catch (TskCoreException tskCoreException) {
            String contentName = this.getName();
            LOGGER.log(Level.SEVERE, "Failed to get unique path for " + contentName, tskCoreException); //NOI18N
            return contentName;
        }
    }
}
