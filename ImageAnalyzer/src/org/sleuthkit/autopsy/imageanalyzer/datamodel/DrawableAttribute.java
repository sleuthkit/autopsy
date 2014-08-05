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
package org.sleuthkit.autopsy.imageanalyzer.datamodel;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.StringProperty;
import javafx.scene.image.Image;
import org.apache.commons.lang3.StringUtils;
import org.sleuthkit.datamodel.TagName;

/** psuedo-enum of attributes to filter, sort, and group on. They
 * mostly correspond to the columns in the db.
 *
 * TODO: Review and refactor DrawableAttribute related code with an eye to usage
 * of type paramaters and multivalued attributes
 */
public class DrawableAttribute<T> {

    public final static DrawableAttribute<String> NAME
            = new DrawableAttribute<>(AttributeName.NAME, "Name", true, "folder-rename.png");

    public final static DrawableAttribute<Boolean> ANALYZED
            = new DrawableAttribute<>(AttributeName.ANALYZED, "Analyzed", true, "");

    /** since categories are really just tags in autopsy, they are not dealt
     * with in the DrawableDB. they have special code in various places
     * to make this transparent.
     *
     * //TODO: this had lead to awkward hard to maintain code, and little
     * advantage. move categories into DrawableDB
     */
    public final static DrawableAttribute<Category> CATEGORY
            = new DrawableAttribute<>(AttributeName.CATEGORY, "Category", false, "category-icon.png");

    public final static DrawableAttribute<Collection<TagName>> TAGS
            = new DrawableAttribute<>(AttributeName.TAGS, "Tags", false, "tag_red.png");

    public final static DrawableAttribute<String> PATH
            = new DrawableAttribute<>(AttributeName.PATH, "Path", true, "folder_picture.png");

    public final static DrawableAttribute<Long> CREATED_TIME
            = new DrawableAttribute<>(AttributeName.CREATED_TIME, "Created Time", true, "clock--plus.png");

    public final static DrawableAttribute<Long> MODIFIED_TIME
            = new DrawableAttribute<>(AttributeName.MODIFIED_TIME, "Modified Time", true, "clock--pencil.png");

    public final static DrawableAttribute<String> MAKE
            = new DrawableAttribute<>(AttributeName.MAKE, "Camera Make", true, "camera.png");

    public final static DrawableAttribute<String> MODEL
            = new DrawableAttribute<>(AttributeName.MODEL, "Camera Model", true, "camera.png");

    //TODO: should this be DrawableAttribute<Collection<String>>?
    public final static DrawableAttribute<String> HASHSET
            = new DrawableAttribute<>(AttributeName.HASHSET, "Hashset", false, "hashset_hits.png");

    public final static DrawableAttribute<Long> OBJ_ID
            = new DrawableAttribute<>(AttributeName.OBJ_ID, "Internal Object ID", true, "");

    public final static DrawableAttribute<Number> WIDTH
            = new DrawableAttribute<>(AttributeName.WIDTH, "Width", true, "arrow-resize.png");

    public final static DrawableAttribute<Number> HEIGHT
            = new DrawableAttribute<>(AttributeName.HEIGHT, "Height", true, "arrow-resize-090.png");

    final private static List< DrawableAttribute<?>> groupables
            = Arrays.asList(PATH, HASHSET, CATEGORY, TAGS, MAKE, MODEL);

    final private static List<DrawableAttribute<?>> values
            = Arrays.asList(NAME, ANALYZED, CATEGORY, TAGS, PATH, CREATED_TIME,
                            MODIFIED_TIME, HASHSET, CATEGORY, MAKE, MODEL, OBJ_ID,
                            WIDTH, HEIGHT);

    private DrawableAttribute(AttributeName name, String displayName, Boolean isDBColumn, String imageName) {
        this.attrName = name;
        this.displayName = new ReadOnlyStringWrapper(displayName);
        this.isDBColumn = isDBColumn;
        this.imageName = imageName;
    }

    private Image icon;

    public final boolean isDBColumn;

    public final AttributeName attrName;

    private final StringProperty displayName;

    private final String imageName;

    public Image getIcon() {
        if (icon == null) {
            if (StringUtils.isBlank(imageName) == false) {
                this.icon = new Image("org/sleuthkit/autopsy/imageanalyzer/images/" + imageName, true);
            }
        }
        return icon;
    }

    public static List<DrawableAttribute<?>> getGroupableAttrs() {
        return groupables;
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

        NAME, ANALYZED, CATEGORY, TAGS, PATH, CREATED_TIME, MODIFIED_TIME, MAKE,
        MODEL, HASHSET, OBJ_ID, WIDTH, HEIGHT;
    }
}
