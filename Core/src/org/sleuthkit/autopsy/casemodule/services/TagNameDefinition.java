/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2024 Basis Technology Corp.
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
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import javax.annotation.concurrent.Immutable;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.centralrepository.CentralRepoSettings;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * A tag name definition consisting of a display name, description and color.
 */
@Immutable
final public class TagNameDefinition implements Comparable<TagNameDefinition> {

    private static final Logger LOGGER = Logger.getLogger(TagNameDefinition.class.getName());
    @Messages({
        "TagNameDefinition.predefTagNames.bookmark.text=Bookmark",
        "TagNameDefinition.predefTagNames.followUp.text=Follow Up",
        "TagNameDefinition.predefTagNames.notableItem.text=Notable Item",})

    private static final String TAGS_SETTINGS_NAME = "Tags"; //NON-NLS
    private static final String TAG_NAMES_SETTING_KEY = "TagNames"; //NON-NLS 
    private static final String TAG_SETTING_VERSION_KEY = "CustomTagNameVersion";
    private static final int TAG_SETTINGS_VERSION = 2; // Changed tag type from TskData.FileKnown to TskData.TagType

    private final String displayName;
    private final String description;
    private final TagName.HTML_COLOR color;
    private final TskData.TagType tagType;

    private static final List<TagNameDefinition> STANDARD_TAGS_DEFINITIONS = new ArrayList<>();
    private static final List<String> PROJECT_VIC_NAMES_NO_LONGER_USED = new ArrayList<>();

    static {
        STANDARD_TAGS_DEFINITIONS.add(new TagNameDefinition(Bundle.TagNameDefinition_predefTagNames_bookmark_text(), "", TagName.HTML_COLOR.NONE, TskData.TagType.SUSPICIOUS));
        STANDARD_TAGS_DEFINITIONS.add(new TagNameDefinition(Bundle.TagNameDefinition_predefTagNames_followUp_text(), "", TagName.HTML_COLOR.NONE, TskData.TagType.SUSPICIOUS));
        STANDARD_TAGS_DEFINITIONS.add(new TagNameDefinition(Bundle.TagNameDefinition_predefTagNames_notableItem_text(), "", TagName.HTML_COLOR.NONE, TskData.TagType.BAD));

        PROJECT_VIC_NAMES_NO_LONGER_USED.add("CAT-1: Child Exploitation (Illegal)");
        PROJECT_VIC_NAMES_NO_LONGER_USED.add("CAT-2: Child Exploitation (Non-Illegal/Age Difficult)");
        PROJECT_VIC_NAMES_NO_LONGER_USED.add("CAT-3: CGI/Animation (Child Exploitive)");
        PROJECT_VIC_NAMES_NO_LONGER_USED.add("CAT-4: Exemplar/Comparison (Internal Use Only)");
        PROJECT_VIC_NAMES_NO_LONGER_USED.add("CAT-5: Non-pertinent");
        PROJECT_VIC_NAMES_NO_LONGER_USED.add("CAT-0: Uncategorized");
    }

    /**
     * Constructs a tag name definition consisting of a display name,
     * description, color and tag type.
     *
     * @param displayName The display name for the tag name.
     * @param description The description for the tag name.
     * @param color       The color for the tag name.
     * @param status      The status denoted by the tag name.
     * @deprecated TagNameDefinition(String displayName, String description, TagName.HTML_COLOR color, TskData.TagType status) should be used instead.
     */
    @Deprecated
    public TagNameDefinition(String displayName, String description, TagName.HTML_COLOR color, TskData.FileKnown status) {
        this.displayName = displayName;
        this.description = description;
        this.color = color;
        this.tagType = TskData.TagType.convertFileKnownToTagType(status);
    }
    
    public TagNameDefinition(String displayName, String description, TagName.HTML_COLOR color, TskData.TagType status) {
        this.displayName = displayName;
        this.description = description;
        this.color = color;
        this.tagType = status;
    }

    static Collection<TagNameDefinition> getStandardTagNameDefinitions() {
        return Collections.unmodifiableCollection(STANDARD_TAGS_DEFINITIONS);
    }

    static List<String> getStandardTagNames() {
        List<String> strList = new ArrayList<>();

        for (TagNameDefinition def : STANDARD_TAGS_DEFINITIONS) {
            strList.add(def.getDisplayName());
        }

        return strList;
    }

    /**
     * Returns the bookmark tag display string.
     *
     * @return
     */
    static String getBookmarkTagDisplayName() {
        return Bundle.TagNameDefinition_predefTagNames_bookmark_text();
    }

    /**
     * Returns the Follow Up tag display string.
     *
     * @return
     */
    static String getFollowUpTagDisplayName() {
        return Bundle.TagNameDefinition_predefTagNames_followUp_text();
    }

    /**
     * Returns the Notable tag display string.
     *
     * @return
     */
    static String getNotableTagDisplayName() {
        return Bundle.TagNameDefinition_predefTagNames_notableItem_text();
    }

    /**
     * Gets the display name for the tag name.
     *
     * @return The display name.
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Gets the description for the tag name.
     *
     * @return The description.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Gets the color for the tag name.
     *
     * @return The color.
     */
    public TagName.HTML_COLOR getColor() {
        return color;
    }

    /**
     * The status which will be applied to items with this tag.
     *
     * @return a value of TskData.TagType which is associated with this tag
     */
    public TskData.TagType getTagType() {
        return tagType;
    }
    
    /**
     * The status which will be applied to items with this tag.
     *
     * @return a value of TskData.FileKnown which is associated with this tag
     * @deprecated getTagType() should be used instead.
     */
    @Deprecated
    public TskData.FileKnown getKnownStatus() {
        return TskData.TagType.convertTagTypeToFileKnown(tagType);
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
        boolean sameName = this.getDisplayName().equals(((TagNameDefinition) obj).getDisplayName());
        boolean sameStatus = this.getTagType().equals(((TagNameDefinition) obj).getTagType());
        return sameName && sameStatus;
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
        return displayName + "," + description + "," + color.name() + "," + tagType.toString();
    }

    TagName saveToCase(SleuthkitCase caseDb) {
        TagName tagName = null;
        try {
            tagName = caseDb.getTaggingManager().addOrUpdateTagName(displayName, description, color, tagType);
        } catch (TskCoreException ex) {
            LOGGER.log(Level.SEVERE, "Error saving tag name definition", ex);
        }
        return tagName;
    }

    /**
     * Gets tag name definitions from the tag settings file as well as the
     * default tag name definitions.
     *
     * The currently custom tags properties are stored in one string property
     * value separated by ;. The properties of an individual tag are comma
     * separated in the format of:
     * tag_name,tag_description,tag_color,tag_type
     *
     * In prior versions of autopsy the known_status was stored in the central
     * repository, therefore the properties file only had three values.
     *
     * @return A set of tag name definition objects.
     */
    static synchronized Set<TagNameDefinition> getTagNameDefinitions() {
        if (needsVersionUpdate()) {
            updatePropertyFile();
        }

        String tagsProperty = ModuleSettings.getConfigSetting(TAGS_SETTINGS_NAME, TAG_NAMES_SETTING_KEY);
        if (tagsProperty == null || tagsProperty.isEmpty()) {
            return new HashSet<>();
        }

        List<String> individualTags = Arrays.asList(tagsProperty.split(";"));

        if (individualTags == null || individualTags.isEmpty()) {
            return new HashSet<>();
        }

        Set<TagNameDefinition> definitions = new HashSet<>();
        for (String tagProps : individualTags) {
            String[] attributes = tagProps.split(",");

            definitions.add(new TagNameDefinition(attributes[0], attributes[1],
                    TagName.HTML_COLOR.valueOf(attributes[2]), TskData.TagType.valueOf(attributes[3])));
        }

        return definitions;
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
            try {
                SleuthkitCase caseDb = Case.getCurrentCaseThrows().getSleuthkitCase();
                tagName.saveToCase(caseDb);
            } catch (NoCurrentCaseException ex) {
                LOGGER.log(Level.SEVERE, "Exception while getting open case.", ex);
            }
        }

        ModuleSettings.setConfigSetting(TAGS_SETTINGS_NAME, TAG_SETTING_VERSION_KEY, Integer.toString(TAG_SETTINGS_VERSION));
        ModuleSettings.setConfigSetting(TAGS_SETTINGS_NAME, TAG_NAMES_SETTING_KEY, setting.toString());
    }

    /**
     * Updates the Tag Definition file to the current format.
     */
    private static void updatePropertyFile() {
        Integer version = getPropertyFileVersion();
        List<TagNameDefinition> definitions = new ArrayList<>();

        if (version == null || version == 1) {
            String tagsProperty = ModuleSettings.getConfigSetting(TAGS_SETTINGS_NAME, TAG_NAMES_SETTING_KEY);
            if (tagsProperty == null || tagsProperty.isEmpty()) {
                ModuleSettings.setConfigSetting(TAGS_SETTINGS_NAME, TAG_SETTING_VERSION_KEY, Integer.toString(TAG_SETTINGS_VERSION));
                return;
            }

            List<String> individualTags = Arrays.asList(tagsProperty.split(";"));

            if (individualTags == null || individualTags.isEmpty()) {
                return;
            }

            List<String> notableTagList = null;
            for (String tagProps : individualTags) {
                String[] attributes = tagProps.split(",");
                TskData.TagType tagType = TskData.TagType.SUSPICIOUS;
                if (attributes.length == 3) {
                    // If notableTagList is null load it from the CR.
                    if (notableTagList == null) {
                        notableTagList = getCRNotableList();
                    } else {
                        if (notableTagList.contains(attributes[0])) {
                            tagType = TskData.TagType.BAD;
                        }
                    }
                } else {
                    if (version == 1) {
                        // handle backwards compatibility
                        tagType = TskData.TagType.convertFileKnownToTagType(TskData.FileKnown.valueOf(attributes[3]));
                    } else {
                        tagType = TskData.TagType.valueOf(attributes[3]);
                    }
                }

                definitions.add(new TagNameDefinition(attributes[0], attributes[1],
                        TagName.HTML_COLOR.valueOf(attributes[2]), tagType));
            }
        }

        if (definitions.isEmpty()) {
            return;
        }

        // Remove the standard and Project VIC tags from the list
        List<String> tagStringsToKeep = new ArrayList<>();
        List<String> standardTags = getStandardTagNames();
        for (TagNameDefinition def : definitions) {
            if (!standardTags.contains(def.getDisplayName())
                    && !PROJECT_VIC_NAMES_NO_LONGER_USED.contains(def.getDisplayName())) {
                tagStringsToKeep.add(def.toSettingsFormat());
            }
        }

        // Write out the version and the new tag list.
        ModuleSettings.setConfigSetting(TAGS_SETTINGS_NAME, TAG_SETTING_VERSION_KEY, Integer.toString(TAG_SETTINGS_VERSION));
        ModuleSettings.setConfigSetting(TAGS_SETTINGS_NAME, TAG_NAMES_SETTING_KEY, String.join(";", tagStringsToKeep));
    }

    /**
     * Returns a list notable tag names from the CR bagTag list.
     *
     * @return A list of tag names, or empty list if none were found.
     */
    private static List<String> getCRNotableList() {
        String notableTagsProp = ModuleSettings.getConfigSetting(CentralRepoSettings.getInstance().getModuleSettingsKey(), "db.badTags"); // NON-NLS
        if (notableTagsProp != null && !notableTagsProp.isEmpty()) {
            return Arrays.asList(notableTagsProp.split(","));
        }

        return new ArrayList<>();
    }

    /**
     * Based on the version in the Tags property file, returns whether or not
     * the file needs updating.
     *
     * @return
     */
    private static boolean needsVersionUpdate() {
        Integer version = getPropertyFileVersion();
        return version == null || version < TAG_SETTINGS_VERSION;
    }

    /**
     * Returns the Tags property file version.
     *
     * @return The current version of tags property file, or null if no version
     *         was found.
     */
    private static Integer getPropertyFileVersion() {
        String version = ModuleSettings.getConfigSetting(TAGS_SETTINGS_NAME, TAG_SETTING_VERSION_KEY);
        if (version == null || version.isEmpty()) {
            return null;
        }

        try {
            return Integer.parseInt(version);
        } catch (NumberFormatException ex) {
            // The version is not an integer
            return null;
        }
    }

}
