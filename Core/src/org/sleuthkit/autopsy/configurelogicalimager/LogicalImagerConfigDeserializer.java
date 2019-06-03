/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.configurelogicalimager;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LogicalImagerConfigDeserializer implements JsonDeserializer<LogicalImagerConfig> {

    @Override
    public LogicalImagerConfig deserialize(JsonElement je, Type type, JsonDeserializationContext jdc) throws JsonParseException {
        boolean finalizeImageWriter = false;
        Map<String, LogicalImagerRule> ruleSet = new HashMap<>();

        final JsonObject jsonObject = je.getAsJsonObject();
        final JsonElement jsonFinalizeImageWriter = jsonObject.get("finalize-image-writer");
        if (jsonFinalizeImageWriter != null) {
            finalizeImageWriter = jsonFinalizeImageWriter.getAsBoolean();
        }

        final JsonObject jsonRuleSet = jsonObject.get("rule-set").getAsJsonObject();
        if (jsonRuleSet == null) {
            throw new JsonParseException("Missing rule-set");
        }
        for (Map.Entry<String, JsonElement> entry : jsonRuleSet.entrySet()) {
            String key = entry.getKey();
            JsonElement element = entry.getValue();
            Set<Map.Entry<String, JsonElement>> entrySet = element.getAsJsonObject().entrySet();
            String key1;
            Boolean shouldSave = false;
            Boolean shouldAlert = true;
            String description = null;
            List<String> extensions = null;
            List<String> paths = null;
            List<String> fullPaths = null;
            List<String> filenames = null;
            Integer minFileSize = null;
            Integer maxFileSize = null;
            Integer minDays = null;
            String minDate = null;
            String maxDate = null;
            
            for (Map.Entry<String, JsonElement> entry1 : entrySet) {
                key1 = entry1.getKey();
                switch (key1) {
                    case "shouldAlert":
                        shouldAlert = entry1.getValue().getAsBoolean();
                        break;
                    case "shouldSave":
                        shouldSave = entry1.getValue().getAsBoolean();
                        break;
                    case "description":
                        description = entry1.getValue().getAsString();
                        break;
                    case "extensions":
                        JsonArray extensionsArray = entry1.getValue().getAsJsonArray();
                        extensions = new ArrayList<>();
                        for (JsonElement e : extensionsArray) {
                            extensions.add(e.getAsString());
                        }
                        break;
                    case "folder-names":
                        JsonArray pathsArray = entry1.getValue().getAsJsonArray();
                        paths = new ArrayList<>();
                        for (JsonElement e : pathsArray) {
                            paths.add(e.getAsString());
                        }
                        break;
                    case "file-names":
                        JsonArray filenamesArray = entry1.getValue().getAsJsonArray();
                        filenames = new ArrayList<>();
                        for (JsonElement e : filenamesArray) {
                            filenames.add(e.getAsString());
                        }
                        break;
                    case "full-paths":
                        JsonArray fullPathsArray = entry1.getValue().getAsJsonArray();
                        fullPaths = new ArrayList<>();
                        for (JsonElement e : fullPathsArray) {
                            fullPaths.add(e.getAsString());
                        }
                        break;
                    case "size-range":
                        JsonObject sizeRangeObject = entry1.getValue().getAsJsonObject();
                        Set<Map.Entry<String, JsonElement>> entrySet1 = sizeRangeObject.entrySet();
                        for (Map.Entry<String, JsonElement> entry2 : entrySet1) {
                            String sizeKey = entry2.getKey();
                            switch (sizeKey) {
                                case "min":
                                    minFileSize = entry2.getValue().getAsInt();
                                    break;
                                case "max":
                                    maxFileSize = entry2.getValue().getAsInt();
                                    break;  
                                default:
                                    throw new JsonParseException("Unsupported key: " + sizeKey);
                            }
                        };
                        break;
                    case "date-range":
                        JsonObject dateRangeObject = entry1.getValue().getAsJsonObject();
                        Set<Map.Entry<String, JsonElement>> entrySet2 = dateRangeObject.entrySet();
                        for (Map.Entry<String, JsonElement> entry2 : entrySet2) {
                            String dateKey = entry2.getKey();
                            switch (dateKey) {
                                case "min":
                                    minDate = entry2.getValue().getAsString();
                                    break;
                                case "max":
                                    maxDate = entry2.getValue().getAsString();
                                    break;
                                case "min-days":
                                    minDays = entry2.getValue().getAsInt();  
                                    break;
                                default:
                                    throw new JsonParseException("Unsupported key: " + dateKey);
                            }
                        };
                        break;
                    default:
                        throw new JsonParseException("Unsupported key: " + key1);
                }
            }
            
            // A rule with full-paths cannot have other rule definitions
            if ((fullPaths != null && !fullPaths.isEmpty()) && ((extensions != null && !extensions.isEmpty())
                    || (paths != null && !paths.isEmpty())
                    || (filenames != null && !filenames.isEmpty()))) {
                throw new JsonParseException("A rule with full-paths cannot have other rule definitions");
            }
            
            LogicalImagerRule rule = new LogicalImagerRule.Builder()
                    .shouldAlert(shouldAlert)
                    .shouldSave(shouldSave)
                    .description(description)
                    .extensions(extensions)
                    .paths(paths)
                    .fullPaths(fullPaths)
                    .filenames(filenames)
                    .minFileSize(minFileSize)
                    .maxFileSize(maxFileSize)
                    .minDays(minDays)
                    .minDate(minDate)
                    .maxDate(maxDate)
                    .build();
            ruleSet.put(key, rule);
        }
        LogicalImagerConfig config = new LogicalImagerConfig(finalizeImageWriter, ruleSet);
        return config;
    }
}