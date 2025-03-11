/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.casemodule.services;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import java.lang.reflect.Type;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.concurrent.Immutable;
import java.io.FileFilter;
import java.io.FileReader;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskData;

/**
 * Definition of a tag set.
 */
@Immutable
final public class TagSetDefinition {
    
    private static final Logger LOGGER = Logger.getLogger(TagSetDefinition.class.getName());

    private final static String FILE_NAME_TEMPLATE = "%s-tag-set.json";
    private final static Path TAGS_USER_CONFIG_DIR = Paths.get(PlatformUtil.getUserConfigDirectory(), "tags");

    private final String name;
    private final List<TagNameDefinition> tagNameDefinitionList;

    public TagSetDefinition(String name, List<TagNameDefinition> tagNameDefinitionList) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Invalid parameter passed to TagSetDefinition constructor. TagSet name was null or empty.");
        }

        if (tagNameDefinitionList == null || tagNameDefinitionList.isEmpty()) {
            throw new IllegalArgumentException("Invalid parameter passed to TagSetDefinition constructor. TagNameDefinition list was null or empty.");
        }

        this.name = name;
        this.tagNameDefinitionList = tagNameDefinitionList;
    }

    /**
     * Returns the name of the TagSet.
     *
     * @return The name of the tag set.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the set's list of TagNameDefinitions.
     *
     * @return List of TagNameDefinition objects
     */
    public List<TagNameDefinition> getTagNameDefinitions() {
        return Collections.unmodifiableList(tagNameDefinitionList);
    }

    /**
     * Writes the given TagSetDefinition to a JSON file. If a JSON file for the
     * given TagSet already exists it will be replaced with the new definition.
     *
     * @param tagSetDefinition TagSet to write to a JSON file.
     *
     * @throws IOException
     */
    static synchronized void writeTagSetDefinition(TagSetDefinition tagSetDefinition) throws IOException {
        // Create the tags directory if it doesn't exist.
        File dir = TAGS_USER_CONFIG_DIR.toFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }

        File file = Paths.get(TAGS_USER_CONFIG_DIR.toString(), tagSetDefinition.getFileName()).toFile();
        if (file.exists()) {
            file.delete();
        }

        try (FileWriter writer = new FileWriter(file)) {
            (new Gson()).toJson(tagSetDefinition, writer);
        }
    }

    /**
     * Returns a list of configured TagSets (from the user's config folder)
     *
     * @return A list of TagSetDefinition objects or empty list if none were
     *         found.
     */
    static synchronized List<TagSetDefinition> readTagSetDefinitions() throws IOException {
        List<TagSetDefinition> tagSetList = new ArrayList<>();
        File dir = TAGS_USER_CONFIG_DIR.toFile();

        if (!dir.exists()) {
            return tagSetList;
        }

        File[] fileList = dir.listFiles(new TagSetJsonFileFilter());
        if (fileList == null) {
            return tagSetList;
        }
        
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(TagSetDefinition.class, new TagSetDefinitionDeserializer())  // Use custom deserializer
                .create();

        for (File file : fileList) {
            try (FileReader reader = new FileReader(file)) {
                TagSetDefinition tagSet = gson.fromJson(reader, TagSetDefinition.class);
                if (tagSet != null) {
                    tagSetList.add(tagSet);
                }
            } catch (JsonSyntaxException e) {
                LOGGER.log(Level.SEVERE, "Skipping invalid JSON file: " + file.getName() + " - " + e.getMessage());
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Error reading file: " + file.getName() + " - " + e.getMessage());
            }
        }

        return tagSetList;
    }

    /**
     * Returns the JSON file name for this tag set definition.
     *
     * @return The file name.
     */
    private String getFileName() {
        return String.format(FILE_NAME_TEMPLATE, name.replace(" ", "-"));
    }

    /**
     * A FileFilter for TagSet JSON files.
     */
    private static final class TagSetJsonFileFilter implements FileFilter {

        @Override
        public boolean accept(File file) {
            return file.getName().endsWith("tag-set.json");
        }

    }
    
    // Custom JSON Deserializer for TagSetDefinition to support legacy user tags and tag set JSON files.
    // In TSK release 4.13.0 and Autopsy release 4.22.0 we:
    // 1) renamed "TskData.KnownStatus" to "TskData.TagType"
    // 2) renamed "TagSetDefinition.knownStatus" to "TagSetDefinition.tagType"
    // 3) "TskData.KnownStatus" of "unknown" used to carry a score of "suspicious". 
    //      Now "TskData.TagType" of "unknown" carries a score of "unknown".
    //      
    private static class TagSetDefinitionDeserializer implements JsonDeserializer<TagSetDefinition> {

        @Override
        public TagSetDefinition deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonObject = json.getAsJsonObject();

            String name = jsonObject.has("name") ? jsonObject.get("name").getAsString() : null;
            JsonArray tagArray = jsonObject.has("tagNameDefinitionList") ? jsonObject.getAsJsonArray("tagNameDefinitionList") : new JsonArray();
            List<TagNameDefinition> tagNameDefinitions = new ArrayList<>();

            for (JsonElement element : tagArray) {
                JsonObject tagObject = element.getAsJsonObject();

                String displayName = tagObject.has("displayName") ? tagObject.get("displayName").getAsString() : null;
                String description = tagObject.has("description") ? tagObject.get("description").getAsString() : null;
                TagName.HTML_COLOR color = context.deserialize(tagObject.get("color"), TagName.HTML_COLOR.class);

                TskData.TagType tagType = null;
                // Handle tagType vs knownStatus
                if (tagObject.has("tagType") && !tagObject.get("tagType").isJsonNull()) {
                    tagType = context.deserialize(tagObject.get("tagType"), TskData.TagType.class);
                } else if (tagObject.has("knownStatus") && !tagObject.get("knownStatus").isJsonNull()) {
                    TskData.TagType legacyStatus = context.deserialize(tagObject.get("knownStatus"), TskData.TagType.class);

                    // "UNKNOWN" tag type used to carry an automatic "SUSPICIOUS" score.
                    // If knownStatus was "UNKNOWN", use "SUSPICIOUS" instead
                    if (legacyStatus == TskData.TagType.UNKNOWN) {
                        tagType = TskData.TagType.SUSPICIOUS;
                    } else {
                        tagType = legacyStatus;
                    }
                }
                
                if (tagType == null) {
                    LOGGER.log(Level.SEVERE, "Failed to initialize tagType for tag: {0}. Skipping entry.", displayName);
                    continue;
                }

                tagNameDefinitions.add(new TagNameDefinition(displayName, description, color, tagType));
            }

            return new TagSetDefinition(name, tagNameDefinitions);
        }
    }
}
