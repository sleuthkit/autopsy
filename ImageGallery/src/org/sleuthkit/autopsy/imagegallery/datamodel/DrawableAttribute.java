/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2019 Basis Technology Corp.
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
import java.util.logging.Level;
import java.util.stream.Collectors;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.StringProperty;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.TimeZoneUtils;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.datamodel.TagName;

/**
 * psuedo-enum of attributes to filter, sort, and group on. They mostly
 * correspond to the columns in the db.
 *
 * TODO: Review and refactor DrawableAttribute related code with an eye to usage
 * of type paramaters and multivalued attributes
 */
@NbBundle.Messages({"DrawableAttribute.md5hash=MD5 Hash",
    "DrawableAttribute.name=Name",
    "DrawableAttribute.analyzed=Analyzed",
    "DrawableAttribute.category=Category",
    "DrawableAttribute.tags=Tags",
    "DrawableAttribute.path=Path",
    "DrawableAttribute.createdTime=Created Time",
    "DrawableAttribute.modifiedTime=Modified Time",
    "DrawableAttribute.cameraMake=Camera Make",
    "DrawableAttribute.cameraModel=Camera Model",
    "DrawableAttribute.hashSet=Hashset",
    "DrawableAttribute.intObjID=Internal Object ID",
    "DrawableAttribute.width=Width",
    "DrawableAttribute.height=Height",
    "DrawableAttribute.mimeType=MIME type"})
public class DrawableAttribute<T extends Comparable<T>> {

    private static final Logger logger = Logger.getLogger(DrawableAttribute.class.getName());

    public final static DrawableAttribute<String> MD5_HASH
            = new DrawableAttribute<>(AttributeName.MD5_HASH, Bundle.DrawableAttribute_md5hash(),
                    false,
                    "icon-hashtag.png", // NON-NLS
                    f -> Collections.singleton(f.getMd5Hash()));

    public final static DrawableAttribute<String> NAME
            = new DrawableAttribute<>(AttributeName.NAME, Bundle.DrawableAttribute_name(),
                    true,
                    "folder-rename.png", //NON-NLS
                    f -> Collections.singleton(f.getName()));

    public final static DrawableAttribute<Boolean> ANALYZED
            = new DrawableAttribute<>(AttributeName.ANALYZED, Bundle.DrawableAttribute_analyzed(),
                    true,
                    "",
                    f -> Collections.singleton(f.isAnalyzed()));

    /**
     * since categories are really just tags in autopsy, they are not dealt with
     * in the DrawableDB. they have special code in various places to make this
     * transparent.
     *
     * //TODO: this has lead to awkward hard to maintain code, and little
     * advantage. move categories into DrawableDB?
     */
    public final static DrawableAttribute<TagName> CATEGORY
            = new DrawableAttribute<TagName>(AttributeName.CATEGORY, Bundle.DrawableAttribute_category(),
                    false,
                    "category-icon.png", //NON-NLS
                    f -> Collections.singleton(f.getCategory())) {

        @Override
        public Node getGraphicForValue(TagName val) {

            return null;
            //return val.getGraphic();
        }
    };

    public final static DrawableAttribute<TagName> TAGS
            = new DrawableAttribute<>(AttributeName.TAGS, Bundle.DrawableAttribute_tags(),
                    false,
                    "tag_red.png", //NON-NLS
                    DrawableFile::getTagNames);

    public final static DrawableAttribute<String> PATH
            = new DrawableAttribute<>(AttributeName.PATH, Bundle.DrawableAttribute_path(),
                    true,
                    "folder_picture.png", //NON-NLS
                    f -> Collections.singleton(f.getDrawablePath()));

    public final static DrawableAttribute<String> CREATED_TIME
            = new DrawableAttribute<>(AttributeName.CREATED_TIME, Bundle.DrawableAttribute_createdTime(),
                    true,
                    "clock--plus.png", //NON-NLS
                    f -> Collections.singleton(TimeZoneUtils.getFormattedTime(f.getCrtime())));

    public final static DrawableAttribute<String> MODIFIED_TIME
            = new DrawableAttribute<>(AttributeName.MODIFIED_TIME, Bundle.DrawableAttribute_modifiedTime(),
                    true,
                    "clock--pencil.png", //NON-NLS
                    f -> Collections.singleton(TimeZoneUtils.getFormattedTime(f.getMtime())));

    public final static DrawableAttribute<String> MAKE
            = new DrawableAttribute<>(AttributeName.MAKE, Bundle.DrawableAttribute_cameraMake(),
                    true,
                    "camera.png", //NON-NLS
                    f -> Collections.singleton(f.getMake()));

    public final static DrawableAttribute<String> MODEL
            = new DrawableAttribute<>(AttributeName.MODEL, Bundle.DrawableAttribute_cameraModel(),
                    true,
                    "camera.png", //NON-NLS
                    f -> Collections.singleton(f.getModel()));

    public final static DrawableAttribute<String> HASHSET
            = new DrawableAttribute<>(AttributeName.HASHSET, Bundle.DrawableAttribute_hashSet(),
                    true,
                    "hashset_hits.png", //NON-NLS
                    DrawableFile::getHashSetNamesUnchecked);

    public final static DrawableAttribute<Long> OBJ_ID
            = new DrawableAttribute<>(AttributeName.OBJ_ID, Bundle.DrawableAttribute_intObjID(),
                    true,
                    "",
                    f -> Collections.singleton(f.getId()));

    public final static DrawableAttribute<Double> WIDTH
            = new DrawableAttribute<>(AttributeName.WIDTH, Bundle.DrawableAttribute_width(),
                    false,
                    "arrow-resize.png", //NON-NLS
                    f -> Collections.singleton(f.getWidth()));

    public final static DrawableAttribute<Double> HEIGHT
            = new DrawableAttribute<>(AttributeName.HEIGHT, Bundle.DrawableAttribute_height(),
                    false,
                    "arrow-resize-090.png", //NON-NLS
                    f -> Collections.singleton(f.getHeight()));

    public final static DrawableAttribute<String> MIME_TYPE
            = new DrawableAttribute<>(AttributeName.MIME_TYPE, Bundle.DrawableAttribute_mimeType(),
                    false,
                    "mime_types.png", //NON-NLS
                    f -> Collections.singleton(f.getMIMEType()));

    final private static List< DrawableAttribute<?>> groupables
            = Arrays.asList(PATH, HASHSET, CATEGORY, TAGS, MAKE, MODEL, MIME_TYPE);

    final private static List<DrawableAttribute<?>> values
            = Arrays.asList(NAME, ANALYZED, CATEGORY, TAGS, PATH, CREATED_TIME,
                    MODIFIED_TIME, MD5_HASH, HASHSET, MAKE, MODEL, OBJ_ID, WIDTH, HEIGHT, MIME_TYPE);

    private final Function<DrawableFile, Collection<T>> extractor;

    private DrawableAttribute(AttributeName name, String displayName, Boolean isDBColumn, String imageName, Function<DrawableFile, Collection<T>> extractor) {
        this.attrName = name;
        this.displayName = new ReadOnlyStringWrapper(displayName);
        this.isDBColumn = isDBColumn;
        this.extractor = extractor;
        this.imageName = imageName;
    }
    private final String imageName;

    private Image icon;

    public final boolean isDBColumn;

    public final AttributeName attrName;

    private final StringProperty displayName;

    public Image getIcon() {
        /*
         * There is some issue with loading this in the constructor which gets
         * called at class load time, so instead we load them lazily the first
         * time they are needed
         */
        if (null == icon && StringUtils.isNotBlank(imageName)) {
            this.icon = new Image("org/sleuthkit/autopsy/imagegallery/images/" + imageName, true); //NON-NLS
        }
        return icon;
    }

    /**
     * TODO: override this to load per value icons form some attributes like
     * mime-type and category
     */
    public Node getGraphicForValue(T val) {
        return new ImageView(getIcon());
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

    public Collection<T> getValue(DrawableFile f) {
        try {
            return extractor.apply(f).stream()
                    .filter(value -> (value != null && value.toString().isEmpty() == false))
                    .collect(Collectors.toSet());
        } catch (Exception ex) {
            /*
             * There is a catch-all here because the code in the try block
             * executes third-party library calls that throw unchecked
             * exceptions. See JIRA-5144, where an IllegalStateException was
             * thrown because a file's MIME type was incorrectly identified as a
             * picture type.
             */
            logger.log(Level.WARNING, "Exception while getting image attributes", ex); //NON-NLS
            return Collections.emptySet();
        }
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
        MD5_HASH,
        MIME_TYPE;
    }
}
