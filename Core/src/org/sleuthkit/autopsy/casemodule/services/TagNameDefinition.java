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
package org.sleuthkit.autopsy.casemodule.services;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import javax.annotation.concurrent.Immutable;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
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
final class TagNameDefinition implements Comparable<TagNameDefinition> {

    private static final Logger LOGGER = Logger.getLogger(TagNameDefinition.class.getName());
    @NbBundle.Messages({"TagNameDefinition.predefTagNames.bookmark.text=Bookmark",
        "TagNameDefinition.predefTagNames.followUp.text=Follow Up",
        "TagNameDefinition.predefTagNames.notableItem.text=Notable Item",
        "Category.one=CAT-1: Child Exploitation (Illegal)",
        "Category.two=CAT-2: Child Exploitation (Non-Illegal/Age Difficult)",
        "Category.three=CAT-3: CGI/Animation (Child Exploitive)",
        "Category.four=CAT-4: Exemplar/Comparison (Internal Use Only)",
        "Category.five=CAT-5: Non-pertinent",
        "Category.zero=CAT-0: Uncategorized"})

    private static final String TAGS_SETTINGS_NAME = "Tags"; //NON-NLS
    private static final String TAG_NAMES_SETTING_KEY = "TagNames"; //NON-NLS 
    private static final String TAG_SETTING_VERSION_KEY = "CustomTagNameVersion";
    private static final String TAG_SETTINGS_VERSION = "1";

    private final String displayName;
    private final String description;
    private final TagName.HTML_COLOR color;
    private final TskData.FileKnown knownStatus;

    private static final Map<String, TagNameDefinition> STANDARD_TAGS_DEFINITIONS = new HashMap<>();
    private static final Map<String, TagNameDefinition> PROJECT_VIC_TAG_DEFINITIONS = new HashMap<>();

    static {
        STANDARD_TAGS_DEFINITIONS.put(Bundle.TagNameDefinition_predefTagNames_bookmark_text(), new TagNameDefinition(Bundle.TagNameDefinition_predefTagNames_bookmark_text(), "", TagName.HTML_COLOR.NONE, TskData.FileKnown.UNKNOWN));
        STANDARD_TAGS_DEFINITIONS.put(Bundle.TagNameDefinition_predefTagNames_followUp_text(), new TagNameDefinition(Bundle.TagNameDefinition_predefTagNames_followUp_text(), "", TagName.HTML_COLOR.NONE, TskData.FileKnown.UNKNOWN));
        STANDARD_TAGS_DEFINITIONS.put(Bundle.TagNameDefinition_predefTagNames_notableItem_text(), new TagNameDefinition(Bundle.TagNameDefinition_predefTagNames_notableItem_text(), "", TagName.HTML_COLOR.NONE, TskData.FileKnown.BAD));

        PROJECT_VIC_TAG_DEFINITIONS.put(Bundle.Category_one(), new TagNameDefinition(Bundle.Category_one(), "", TagName.HTML_COLOR.RED, TskData.FileKnown.BAD));
        PROJECT_VIC_TAG_DEFINITIONS.put(Bundle.Category_two(), new TagNameDefinition(Bundle.Category_two(), "", TagName.HTML_COLOR.LIME, TskData.FileKnown.BAD));
        PROJECT_VIC_TAG_DEFINITIONS.put(Bundle.Category_three(), new TagNameDefinition(Bundle.Category_three(), "", TagName.HTML_COLOR.YELLOW, TskData.FileKnown.BAD));
        PROJECT_VIC_TAG_DEFINITIONS.put(Bundle.Category_four(), new TagNameDefinition(Bundle.Category_four(), "", TagName.HTML_COLOR.PURPLE, TskData.FileKnown.UNKNOWN));
        PROJECT_VIC_TAG_DEFINITIONS.put(Bundle.Category_five(), new TagNameDefinition(Bundle.Category_five(), "", TagName.HTML_COLOR.SILVER, TskData.FileKnown.UNKNOWN));
    }

    /**
     * Constructs a tag name definition consisting of a display name,
     * description, color and knownStatus.
     *
     * @param displayName The display name for the tag name.
     * @param description The description for the tag name.
     * @param color       The color for the tag name.
     * @param status      The status denoted by the tag name.
     */
    TagNameDefinition(String displayName, String description, TagName.HTML_COLOR color, TskData.FileKnown status) {
        this.displayName = displayName;
        this.description = description;
        this.color = color;
        this.knownStatus = status;
    }

    static Collection<TagNameDefinition> getStandardTagNameDefinitions() {
        return STANDARD_TAGS_DEFINITIONS.values();
    }

    static Collection<TagNameDefinition> getProjectVICDefaultDefinitions() {
        return PROJECT_VIC_TAG_DEFINITIONS.values();
    }

    static List<String> getStandardTagNames() {
        List<String> strList = new ArrayList<>();
        strList.addAll(STANDARD_TAGS_DEFINITIONS.keySet());
        strList.addAll(PROJECT_VIC_TAG_DEFINITIONS.keySet());

        return strList;
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
     * The status which will be applied to items with this tag.
     *
     * @return a value of TskData.FileKnown which is associated with this tag
     */
    TskData.FileKnown getKnownStatus() {
        return knownStatus;
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
        boolean sameStatus = this.getKnownStatus().equals(((TagNameDefinition) obj).getKnownStatus());
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
        return displayName + "," + description + "," + color.name() + "," + knownStatus.toString();
    }

    TagName saveToCase(SleuthkitCase caseDb) {
        TagName tagName = null;
        try {
            tagName = caseDb.addOrUpdateTagName(displayName, description, color, knownStatus);
        } catch (TskCoreException ex) {
            LOGGER.log(Level.SEVERE, "Error updating non-file object ", ex);
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
     * tag_name,tag_description,tag_color,known_status
     *
     * In prior versions of autopsy the known_status was stored in the central
     * repository, therefore the properties file only had three values.
     *
     * @return A set of tag name definition objects.
     */
    static synchronized Set<TagNameDefinition> getTagNameDefinitions() {
        Set<TagNameDefinition> tagNames = new LinkedHashSet<>();
        String customTagsList = ModuleSettings.getConfigSetting(TAGS_SETTINGS_NAME, TAG_NAMES_SETTING_KEY);
        if (null != customTagsList && !customTagsList.isEmpty()) {
            List<String> customTagDefinitions = Arrays.asList(customTagsList.split(";"));
            int numberOfAttributes = 0;
            List<TagNameDefinition> tagDefinitions = new ArrayList<>();
            if (!customTagDefinitions.isEmpty()) {
                // Use the first entry in the list to figure out if there are 3 
                // or four tag attributes in the file.
                numberOfAttributes = customTagDefinitions.get(0).split(",").length;
            }
            if (numberOfAttributes == 3) {
                // Get the known status from the Central Repository
                String crTagKnownProp = ModuleSettings.getConfigSetting("CentralRepository", "db.badTags"); // NON-NLS
                List<String> knownTagNameList = new ArrayList<>();
                if (crTagKnownProp != null && !crTagKnownProp.isEmpty()) {
                    knownTagNameList.addAll(Arrays.asList(crTagKnownProp.split(",")));
                }

                tagDefinitions = buildTagNameDefinitions(customTagDefinitions, knownTagNameList);
            } else if (numberOfAttributes == 4) {
                tagDefinitions = buildTagNameDefinitions(customTagDefinitions);
            }

            // Remove the standard and project vic tags.
            List<String> standardTagNames = getStandardTagNames();
            for (TagNameDefinition def : tagDefinitions) {
                if (!standardTagNames.contains(def.getDisplayName())) {
                    tagNames.add(def);
                }
            }
        }
        return tagNames;
    }

    /**
     * Returns a list of TagNameDefinitons created by merging the tag data from
     * the properties file and the known status information from the central
     * repository.
     *
     * @param tagProperties          List of description strings.
     * @param centralRepoNotableTags List of known tag names.
     *
     * @return A list of TagNameDefinitions.
     */
    private static List<TagNameDefinition> buildTagNameDefinitions(List<String> tagProperties, List<String> centralRepoNotableTags) {
        List<TagNameDefinition> tagNameDefinitions = new ArrayList<>();

        for (String propertyString : tagProperties) {
            // Split the property into attributes
            String[] attributes = propertyString.split(","); //get the attributes
            String tagName = attributes[0];
            TskData.FileKnown knownStatus = TskData.FileKnown.UNKNOWN;

            if (centralRepoNotableTags.contains(tagName)) {
                knownStatus = TskData.FileKnown.BAD;
            }

            tagNameDefinitions.add(new TagNameDefinition(tagName, attributes[1],
                    TagName.HTML_COLOR.valueOf(attributes[2]), knownStatus));
        }

        return tagNameDefinitions;
    }

    /**
     * Read the Tags.properties file to get the TagNameDefinitions that are
     * preserved across cases.
     *
     * @param tagProperties           List of description strings.
     *
     * @param standardTagsToBeCreated the list of standard tags which have yet
     *                                to be created
     *
     * @return tagNames a list of TagNameDefinitions
     */
    private static List<TagNameDefinition> buildTagNameDefinitions(List<String> tagProperties) {
        List<TagNameDefinition> tagNameDefinitions = new ArrayList<>();
        for (String tagNameTuple : tagProperties) {
            String[] tagNameAttributes = tagNameTuple.split(","); //get the attributes
            tagNameDefinitions.add(new TagNameDefinition(tagNameAttributes[0], tagNameAttributes[1],
                    TagName.HTML_COLOR.valueOf(tagNameAttributes[2]), TskData.FileKnown.valueOf(tagNameAttributes[3])));
        }
        return tagNameDefinitions;
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

        ModuleSettings.setConfigSetting(TAGS_SETTINGS_NAME, TAG_SETTING_VERSION_KEY, TAG_SETTINGS_VERSION);
        ModuleSettings.setConfigSetting(TAGS_SETTINGS_NAME, TAG_NAMES_SETTING_KEY, setting.toString());
    }

}
