/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.casemodule.services;

import com.google.gson.Gson;
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
import org.sleuthkit.autopsy.coreutils.PlatformUtil;

/**
 * Definition of a tag set.
 */
@Immutable
final public class TagSetDefinition {

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
        Gson gson = new Gson();
        for (File file : fileList) {
            try (FileReader reader = new FileReader(file)) {
                tagSetList.add(gson.fromJson(reader, TagSetDefinition.class));
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
}
