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
 * A tag name definition consisting of a display name, description and color.
 */
@Immutable
final class TagNameDefiniton implements Comparable<TagNameDefiniton> {

    private static final String TAGS_SETTINGS_NAME = "Tags"; //NON-NLS
    private static final String TAG_NAMES_SETTING_KEY = "TagNames"; //NON-NLS    
    private final String displayName;
    private final String description;
    private final TagName.HTML_COLOR color;

    /**
     * Constructs a tag name definition consisting of a display name,
     * description and color.
     *
     * @param displayName The display name for the tag name.
     * @param description The description for the tag name.
     * @param color       The color for the tag name.
     */
    TagNameDefiniton(String displayName, String description, TagName.HTML_COLOR color) {
        this.displayName = displayName;
        this.description = description;
        this.color = color;
    }

    /**
     * Gets the display name for the tag name.
     *
     * @return The display name.
     */
    String getDisplayName() {
        return displayName;
    }

    /**
     * Gets the description for the tag name.
     *
     * @return The description.
     */
    String getDescription() {
        return description;
    }

    /**
     * Gets the color for the tag name.
     *
     * @return The color.
     */
    TagName.HTML_COLOR getColor() {
        return color;
    }

    /**
     * Compares this tag name definition with the specified tag name definition
     * for order.
     *
     * @param other The tag name definition to which to compare this tag name
     *              definition.
     *
     * @return Negative integer, zero, or a positive integer to indicate that
     *         this tag name definition is less than, equal to, or greater than
     *         the specified tag name definition.
     */
    @Override
    public int compareTo(TagNameDefiniton other) {
        return this.getDisplayName().toLowerCase().compareTo(other.getDisplayName().toLowerCase());
    }

    /**
     * Returns a hash code value for this tag name definition.
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
     * Indicates whether some other object is "equal to" this tag name
     * definition.
     *
     * @param obj The object to test for equality.
     *
     * @return True or false.
     */
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof TagNameDefiniton)) {
            return false;
        }
        TagNameDefiniton thatTagName = (TagNameDefiniton) obj;
        return this.getDisplayName().equals(thatTagName.getDisplayName());
    }

    /**
     * A string representation of this tag name definition.
     *
     * @return The display name of the tag type.
     */
    @Override
    public String toString() {
        return displayName;
    }

    /**
     * @return A string representation of the tag name definition in the format
     *         that is used by the tags settings file.
     */
    private String toSettingsFormat() {
        return displayName + "," + description + "," + color.name();
    }

    /**
     * Gets tag name definitions from the tag settings file.
     *
     * @return A set of tag name definition objects.
     */
    static synchronized Set<TagNameDefiniton> getTagNameDefinitions() {
        Set<TagNameDefiniton> tagNames = new HashSet<>();
        String setting = ModuleSettings.getConfigSetting(TAGS_SETTINGS_NAME, TAG_NAMES_SETTING_KEY);
        if (null != setting && !setting.isEmpty()) {
            List<String> tagNameTuples = Arrays.asList(setting.split(";"));
            for (String tagNameTuple : tagNameTuples) {
                String[] tagNameAttributes = tagNameTuple.split(",");
                tagNames.add(new TagNameDefiniton(tagNameAttributes[0], tagNameAttributes[1], TagName.HTML_COLOR.valueOf(tagNameAttributes[2])));
            }
        }
        return tagNames;
    }

    /**
     * Sets the tag name definitions in the tag settings file.
     *
     * @param tagNames A set of tag name definition objects.
     */
    static synchronized void setTagNameDefinitions(Set<TagNameDefiniton> tagNames) {
        StringBuilder setting = new StringBuilder();
        for (TagNameDefiniton tagName : tagNames) {
            if (setting.length() != 0) {
                setting.append(";");
            }
            setting.append(tagName.toSettingsFormat());
        }
        ModuleSettings.setConfigSetting(TAGS_SETTINGS_NAME, TAG_NAMES_SETTING_KEY, setting.toString());
    }

}
