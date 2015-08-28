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
            = new DrawableAttribute<>(AttributeName.MD5_HASH, "MD5 Hash", false, "icon-hashtag.png", f -> Collections.singleton(f.getMd5Hash()));

    public final static DrawableAttribute<String> NAME
            = new DrawableAttribute<>(AttributeName.NAME, "Name", true, "folder-rename.png", f -> Collections.singleton(f.getName()));

    public final static DrawableAttribute<Boolean> ANALYZED
            = new DrawableAttribute<>(AttributeName.ANALYZED, "Analyzed", true, "", f -> Collections.singleton(f.isAnalyzed()));

    /**
     * since categories are really just tags in autopsy, they are not dealt with
     * in the DrawableDB. they have special code in various places to make this
     * transparent.
     *
     * //TODO: this has lead to awkward hard to maintain code, and little
     * advantage. move categories into DrawableDB?
     */
    public final static DrawableAttribute<Category> CATEGORY
            = new DrawableAttribute<>(AttributeName.CATEGORY, "Category", false, "category-icon.png", f -> Collections.singleton(f.getCategory()));

    public final static DrawableAttribute<TagName> TAGS
            = new DrawableAttribute<>(AttributeName.TAGS, "Tags", false, "tag_red.png", DrawableFile::getTagNames);

    public final static DrawableAttribute<String> PATH
            = new DrawableAttribute<>(AttributeName.PATH, "Path", true, "folder_picture.png", f -> Collections.singleton(f.getDrawablePath()));

    public final static DrawableAttribute<String> CREATED_TIME
            = new DrawableAttribute<>(AttributeName.CREATED_TIME, "Created Time", true, "clock--plus.png", f -> Collections.singleton(ContentUtils.getStringTime(f.getCrtime(), f)));

    public final static DrawableAttribute<String> MODIFIED_TIME
            = new DrawableAttribute<>(AttributeName.MODIFIED_TIME, "Modified Time", true, "clock--pencil.png", f -> Collections.singleton(ContentUtils.getStringTime(f.getMtime(), f)));

    public final static DrawableAttribute<String> MAKE
            = new DrawableAttribute<>(AttributeName.MAKE, "Camera Make", true, "camera.png", f -> Collections.singleton(f.getMake()));

    public final static DrawableAttribute<String> MODEL
            = new DrawableAttribute<>(AttributeName.MODEL, "Camera Model", true, "camera.png", f -> Collections.singleton(f.getModel()));

    public final static DrawableAttribute<String> HASHSET
            = new DrawableAttribute<>(AttributeName.HASHSET, "Hashset", true, "hashset_hits.png", DrawableFile::getHashSetNamesUnchecked);

    public final static DrawableAttribute<Long> OBJ_ID
            = new DrawableAttribute<>(AttributeName.OBJ_ID, "Internal Object ID", true, "", f -> Collections.singleton(f.getId()));

    public final static DrawableAttribute<Double> WIDTH
            = new DrawableAttribute<>(AttributeName.WIDTH, "Width", true, "arrow-resize.png", f -> Collections.singleton(f.getWidth()));

    public final static DrawableAttribute<Double> HEIGHT
            = new DrawableAttribute<>(AttributeName.HEIGHT, "Height", true, "arrow-resize-090.png", f -> Collections.singleton(f.getHeight()));

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
                this.icon = new Image("org/sleuthkit/autopsy/imagegallery/images/" + imageName, true);
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
