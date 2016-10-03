/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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
package org.sleuthkit.autopsy.casemodule.services;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.annotation.concurrent.Immutable;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.datamodel.TagName;

/**
 * A tag type definition consisting of a display name, description and color.
 */
@Immutable
final class TagType implements Comparable<TagType> {

    private static final String TAGS_SETTINGS_NAME = "Tags"; //NON-NLS
    private static final String TAG_NAMES_SETTING_KEY = "TagNames"; //NON-NLS    
    private final String displayName;
    private final String description;
    private final TagName.HTML_COLOR color;

    /**
     * Constructs a tag type definition consisting of a display name,
     * description and color.
     *
     * @param displayName The display name for the tag type.
     * @param description The dcescription of the tag type.
     * @param color       The color to associate with the tag type.
     */
    TagType(String displayName, String description, TagName.HTML_COLOR color) {
        this.displayName = displayName;
        this.description = description;
        this.color = color;
    }

    /**
     * Gets the display name for a tag type.
     *
     * @return The display name.
     */
    String getDisplayName() {
        return displayName;
    }

    /**
     * Gets the description of a tag type.
     *
     * @return The description.
     */
    String getDescription() {
        return description;
    }

    /**
     * Gets the color associated with the tag type.
     *
     * @return The color.
     */
    TagName.HTML_COLOR getColor() {
        return color;
    }

    /**
     * Compares this tag type with the specified tag type for order.
     *
     * @param other The tag type to which to compare this tag type.
     *
     * @return Negative integer, zero, or a positive integer to indicate that
     *         this tag type is less than, equal to, or greater than the
     *         specified tag type.
     */
    @Override
    public int compareTo(TagType other) {
        return this.getDisplayName().toLowerCase().compareTo(other.getDisplayName().toLowerCase());
    }

    /**
     * Returns a hash code value for this tag type.
     *
     * @return The has code.
     */
    @Override
    public int hashCode() {
        int hash = 7;
        hash = 83 * hash + Objects.hashCode(this.displayName);
        return hash;
    }

    /**
     * Indicates whether some other object is "equal to" this tag type.
     *
     * @param obj The object to test for equality.
     *
     * @return True or false.
     */
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof TagType)) {
            return false;
        }
        TagType thatTagName = (TagType) obj;
        return this.getDisplayName().equals(thatTagName.getDisplayName());
    }

    /**
     * A string representation of this tag type.
     *
     * @return The display name of the tag type.
     */
    @Override
    public String toString() {
        return displayName;
    }

    /**
     * @return A string representation of the tag name in the format that is
     *         used by the properties file.
     */
    private String toSettingsFormat() {
        return displayName + "," + description + "," + color.name();
    }

    /**
     * Gets the custom tag types for the current user.
     *
     * @return A set of tag type objects.
     */
    static synchronized Set<TagType> getCustomTagTypes() {
        Set<TagType> tagTypes = new HashSet<>();
        String setting = ModuleSettings.getConfigSetting(TAGS_SETTINGS_NAME, TAG_NAMES_SETTING_KEY);
        if (null != setting && !setting.isEmpty()) {
            List<String> tagNameTuples = Arrays.asList(setting.split(";"));
            for (String tagNameTuple : tagNameTuples) {
                String[] tagNameAttributes = tagNameTuple.split(",");
                tagTypes.add(new TagType(tagNameAttributes[0], tagNameAttributes[1], TagName.HTML_COLOR.valueOf(tagNameAttributes[2])));
            }
        }
        return tagTypes;
    }

    /**
     * Sets the custom tag types for the current user.
     *
     * @param tagTypes A set of tag type objects.
     */
    static synchronized void setCustomTagTypes(Set<TagType> tagTypes) {
        StringBuilder setting = new StringBuilder();
        for (TagType tagType : tagTypes) {
            if (setting.length() != 0) {
                setting.append(";");
            }
            setting.append(tagType.toSettingsFormat());
        }
        ModuleSettings.setConfigSetting(TAGS_SETTINGS_NAME, TAG_NAMES_SETTING_KEY, setting.toString());
    }

}
