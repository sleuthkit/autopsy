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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.annotation.concurrent.Immutable;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.autopsy.datamodel.tags.Category;

/**
 * A tag name definition consisting of a display name, description and color.
 */
@Immutable
final class TagNameDefinition implements Comparable<TagNameDefinition> {

    private static final String TAGS_SETTINGS_NAME = "Tags"; //NON-NLS
    private static final String TAG_NAMES_SETTING_KEY = "TagNames"; //NON-NLS    
    private static final List<String> STANDARD_NOTABLE_TAG_DISPLAY_NAMES = Arrays.asList(TagsManager.getNotableItemText(), Category.ONE.getDisplayName(), Category.TWO.getDisplayName(), Category.THREE.getDisplayName());  // NON-NLS
    static final List<String> STANDARD_TAG_DISPLAY_NAMES = Arrays.asList(TagsManager.getBookmarkText(), TagsManager.getFollowUpText(),
            TagsManager.getNotableItemText(), Category.ONE.getDisplayName(),
            Category.TWO.getDisplayName(), Category.THREE.getDisplayName(),
            Category.FOUR.getDisplayName(), Category.FIVE.getDisplayName());
    static final String NOTABLE = "(Notable)";
    private final String displayName;
    private final String description;
    private final TagName.HTML_COLOR color;
    private final String knownStatusDenoted;

    /**
     * Constructs a tag name definition consisting of a display name,
     * description, color and knownStatus.
     *
     * @param displayName The display name for the tag name.
     * @param description The description for the tag name.
     * @param color       The color for the tag name.
     * @param knownStatus The status denoted by the tag.
     */
    TagNameDefinition(String displayName, String description, TagName.HTML_COLOR color, String knownStatus) {
        this.displayName = displayName;
        this.description = description;
        this.color = color;
        this.knownStatusDenoted = knownStatus;

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
     * Whether or not the status that this tag implies is the Notable status
     *
     * @return true if the Notable status is implied by this tag, false
     *         otherwise.
     */
    boolean isNotable() {
        return knownStatusDenoted.equals(NOTABLE);
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
    public int compareTo(TagNameDefinition other) {
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
        if (!(obj instanceof TagNameDefinition)) {
            return false;
        }
        TagNameDefinition thatTagName = (TagNameDefinition) obj;
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
        return displayName + "," + description + "," + color.name() + "," + knownStatusDenoted;
    }

    /**
     * Gets tag name definitions from the tag settings file as well as the
     * default tag name definitions.
     *
     * @return A set of tag name definition objects.
     */
    static synchronized Set<TagNameDefinition> getTagNameDefinitions() {
        Set<TagNameDefinition> tagNames = new HashSet<>();
        List<String> standardTags = new ArrayList<>(STANDARD_TAG_DISPLAY_NAMES);
        String setting = ModuleSettings.getConfigSetting(TAGS_SETTINGS_NAME, TAG_NAMES_SETTING_KEY);
        if (null != setting && !setting.isEmpty()) {
            List<String> tagNameTuples = Arrays.asList(setting.split(";"));
            List<String> badTags = new ArrayList<>();
            String badTagsStr = ModuleSettings.getConfigSetting("CentralRepository", "db.badTags"); // NON-NLS
            if (badTagsStr == null || badTagsStr.isEmpty()) {
                badTags.addAll(STANDARD_NOTABLE_TAG_DISPLAY_NAMES);
            } else {
                badTags.addAll(Arrays.asList(badTagsStr.split(",")));
            }
            for (String tagNameTuple : tagNameTuples) {
                String[] tagNameAttributes = tagNameTuple.split(",");
                if (tagNameAttributes.length == 3) {
                    standardTags.remove(tagNameAttributes[0]);  //Use standard tag's saved settings instead of default settings
                    if (badTags.contains(tagNameAttributes[0])) {
                        tagNames.add(new TagNameDefinition(tagNameAttributes[0], tagNameAttributes[1], TagName.HTML_COLOR.valueOf(tagNameAttributes[2]), NOTABLE));
                    } else {
                        tagNames.add(new TagNameDefinition(tagNameAttributes[0], tagNameAttributes[1], TagName.HTML_COLOR.valueOf(tagNameAttributes[2]), "")); //add the default value for that tag 
                    }
                } else if (tagNameAttributes.length == 4) {
                    standardTags.remove(tagNameAttributes[0]);  //Use standard tag's saved settings instead of default settings
                    tagNames.add(new TagNameDefinition(tagNameAttributes[0], tagNameAttributes[1], TagName.HTML_COLOR.valueOf(tagNameAttributes[2]), tagNameAttributes[3]));
                }
            }
        }
        for (String standardTagName : standardTags) {
            if (STANDARD_NOTABLE_TAG_DISPLAY_NAMES.contains(standardTagName)) {
                tagNames.add(new TagNameDefinition(standardTagName, "", TagName.HTML_COLOR.NONE, NOTABLE));
            } else {
                tagNames.add(new TagNameDefinition(standardTagName, "", TagName.HTML_COLOR.NONE, "")); //add the default value for that tag 
            }
        }
        return tagNames;
    }

    /**
     * Sets the tag name definitions in the tag settings file.
     *
     * @param tagNames A set of tag name definition objects.
     */
    static synchronized void setTagNameDefinitions(Set<TagNameDefinition> tagNames) {
        StringBuilder setting = new StringBuilder();
        for (TagNameDefinition tagName : tagNames) {
            if (setting.length() != 0) {
                setting.append(";");
            }
            setting.append(tagName.toSettingsFormat());
        }
        ModuleSettings.setConfigSetting(TAGS_SETTINGS_NAME, TAG_NAMES_SETTING_KEY, setting.toString());
    }

}
