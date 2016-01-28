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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.StringProperty;
import javafx.scene.image.Image;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.datamodel.TagName;

/**
 * psuedo-enum of attributes to filter, sort, and group on. They mostly
 * correspond to the columns in the db.
 *
 * TODO: Review and refactor DrawableAttribute related code with an eye to usage
 * of type paramaters and multivalued attributes
 */
public class DrawableAttribute<T extends Comparable<T>> {

    public final static DrawableAttribute<String> MD5_HASH
            = new DrawableAttribute<>(AttributeName.MD5_HASH, NbBundle.getMessage(DrawableAttribute.class, "DrawableAttribute.md5Hash.txt"), false, "icon-hashtag.png", f -> Collections.singleton(f.getMd5Hash())); // NON-NLS

    public final static DrawableAttribute<String> NAME
            = new DrawableAttribute<>(AttributeName.NAME, NbBundle.getMessage(DrawableAttribute.class, "DrawableAttribute.name.txt"), true, "folder-rename.png", f -> Collections.singleton(f.getName())); // NON-NLS

    public final static DrawableAttribute<Boolean> ANALYZED
            = new DrawableAttribute<>(AttributeName.ANALYZED, NbBundle.getMessage(DrawableAttribute.class, "DrawableAttribute.analyzed.txt"), true, "", f -> Collections.singleton(f.isAnalyzed()));

    /**
     * since categories are really just tags in autopsy, they are not dealt with
     * in the DrawableDB. they have special code in various places to make this
     * transparent.
     *
     * //TODO: this has lead to awkward hard to maintain code, and little
     * advantage. move categories into DrawableDB?
     */
    public final static DrawableAttribute<Category> CATEGORY
            = new DrawableAttribute<>(AttributeName.CATEGORY, NbBundle.getMessage(DrawableAttribute.class, "DrawableAttribute.category.txt"), false, "category-icon.png", f -> Collections.singleton(f.getCategory())); // NON-NLS

    public final static DrawableAttribute<TagName> TAGS
            = new DrawableAttribute<>(AttributeName.TAGS, NbBundle.getMessage(DrawableAttribute.class, "DrawableAttribute.tags.txt"), false, "tag_red.png", DrawableFile::getTagNames); // NON-NLS

    public final static DrawableAttribute<String> PATH
            = new DrawableAttribute<>(AttributeName.PATH, NbBundle.getMessage(DrawableAttribute.class, "DrawableAttribute.path.txt"), true, "folder_picture.png", f -> Collections.singleton(f.getDrawablePath())); // NON-NLS

    public final static DrawableAttribute<String> CREATED_TIME
            = new DrawableAttribute<>(AttributeName.CREATED_TIME, NbBundle.getMessage(DrawableAttribute.class, "DrawableAttribute.createdTime.txt"), true, "clock--plus.png", f -> Collections.singleton(ContentUtils.getStringTime(f.getCrtime(), f))); // NON-NLS

    public final static DrawableAttribute<String> MODIFIED_TIME
            = new DrawableAttribute<>(AttributeName.MODIFIED_TIME, NbBundle.getMessage(DrawableAttribute.class, "DrawableAttribute.modifiedTime.txt"), true, "clock--pencil.png", f -> Collections.singleton(ContentUtils.getStringTime(f.getMtime(), f))); // NON-NLS

    public final static DrawableAttribute<String> MAKE
            = new DrawableAttribute<>(AttributeName.MAKE, NbBundle.getMessage(DrawableAttribute.class, "DrawableAttribute.cameraMake.txt"), true, "camera.png", f -> Collections.singleton(f.getMake())); // NON-NLS

    public final static DrawableAttribute<String> MODEL
            = new DrawableAttribute<>(AttributeName.MODEL, NbBundle.getMessage(DrawableAttribute.class, "DrawableAttribute.cameraModel.txt"), true, "camera.png", f -> Collections.singleton(f.getModel())); // NON-NLS

    public final static DrawableAttribute<String> HASHSET
            = new DrawableAttribute<>(AttributeName.HASHSET, NbBundle.getMessage(DrawableAttribute.class, "DrawableAttribute.hashset.txt"), true, "hashset_hits.png", DrawableFile::getHashSetNamesUnchecked); // NON-NLS

    public final static DrawableAttribute<Long> OBJ_ID
            = new DrawableAttribute<>(AttributeName.OBJ_ID, NbBundle.getMessage(DrawableAttribute.class, "DrawableAttribute.internalObjectId.txt"), true, "", f -> Collections.singleton(f.getId()));

    public final static DrawableAttribute<Double> WIDTH
            = new DrawableAttribute<>(AttributeName.WIDTH, NbBundle.getMessage(DrawableAttribute.class, "DrawableAttribute.width.txt"), true, "arrow-resize.png", f -> Collections.singleton(f.getWidth())); // NON-NLS

    public final static DrawableAttribute<Double> HEIGHT
            = new DrawableAttribute<>(AttributeName.HEIGHT, NbBundle.getMessage(DrawableAttribute.class, "DrawableAttribute.height.txt"), true, "arrow-resize-090.png", f -> Collections.singleton(f.getHeight())); // NON-NLS

    final private static List< DrawableAttribute<?>> groupables
            = Arrays.asList(PATH, HASHSET, CATEGORY, TAGS, MAKE, MODEL);

    final private static List<DrawableAttribute<?>> values
            = Arrays.asList(NAME, ANALYZED, CATEGORY, TAGS, PATH, CREATED_TIME,
                    MODIFIED_TIME, MD5_HASH, HASHSET, MAKE, MODEL, OBJ_ID, WIDTH, HEIGHT);

    private final Function<DrawableFile<?>, Collection<T>> extractor;

    private DrawableAttribute(AttributeName name, String displayName, Boolean isDBColumn, String imageName, Function<DrawableFile<?>, Collection<T>> extractor) {
        this.attrName = name;
        this.displayName = new ReadOnlyStringWrapper(displayName);
        this.isDBColumn = isDBColumn;
        this.imageName = imageName;
        this.extractor = extractor;
    }

    private Image icon;

    public final boolean isDBColumn;

    public final AttributeName attrName;

    private final StringProperty displayName;

    private final String imageName;

    public Image getIcon() {
        if (icon == null) {
            if (StringUtils.isBlank(imageName) == false) {
                this.icon = new Image("org/sleuthkit/autopsy/imagegallery/images/" + imageName, true); //NON-NLS
            }
        }
        return icon;
    }

    public static List<DrawableAttribute<?>> getGroupableAttrs() {
        return Collections.unmodifiableList(groupables);
    }

    public static List<DrawableAttribute<?>> getValues() {
        return Collections.unmodifiableList(values);
    }

    public StringProperty displayName() {
        return displayName;
    }

    public String getDisplayName() {
        return displayName.get();
    }

    public static enum AttributeName {

        NAME,
        ANALYZED,
        CATEGORY,
        TAGS,
        PATH,
        CREATED_TIME,
        MODIFIED_TIME,
        MAKE,
        MODEL,
        HASHSET,
        OBJ_ID,
        WIDTH,
        HEIGHT,
        MD5_HASH;
    }

    public Collection<T> getValue(DrawableFile<?> f) {
        return extractor.apply(f);
    }
}
