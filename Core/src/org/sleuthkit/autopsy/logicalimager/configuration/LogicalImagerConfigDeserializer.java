/*
 * Autopsy
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.logicalimager.configuration;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.openide.util.NbBundle;

/**
 * Logical Imager Configuration JSON deserializer
 */
@NbBundle.Messages({
    "LogicalImagerConfigDeserializer.missingRuleSetException=Missing rule-set",
    "# {0} - key",
    "LogicalImagerConfigDeserializer.unsupportedKeyException=Unsupported key: {0}",
    "LogicalImagerConfigDeserializer.fullPathsException=A rule with full-paths cannot have other rule definitions",})
class LogicalImagerConfigDeserializer implements JsonDeserializer<LogicalImagerConfig> {

    @Override
    public LogicalImagerConfig deserialize(JsonElement je, Type type, JsonDeserializationContext jdc) throws JsonParseException {
        String version = LogicalImagerConfig.getCurrentVersion();
        boolean finalizeImageWriter = false;
        boolean promptBeforeExit = true;
        boolean createVHD = false;

        final JsonObject jsonObject = je.getAsJsonObject();
        final JsonElement jsonVersion = jsonObject.get("version"); // NON-NLS
        if (jsonVersion != null) {
            version = jsonVersion.getAsString();
        }

        final JsonElement jsonFinalizeImageWriter = jsonObject.get("finalize-image-writer"); // NON-NLS
        if (jsonFinalizeImageWriter != null) {
            finalizeImageWriter = jsonFinalizeImageWriter.getAsBoolean();
        }

        final JsonElement jsonPromptBeforeExit = jsonObject.get("prompt-before-exit"); // NON-NLS
        if (jsonPromptBeforeExit != null) {
            promptBeforeExit = jsonPromptBeforeExit.getAsBoolean();
        }

        final JsonElement jsonCreateVHD = jsonObject.get("create-VHD"); // NON-NLS
        if (jsonCreateVHD != null) {
            createVHD = jsonCreateVHD.getAsBoolean();
        }

        JsonArray asJsonArray = jsonObject.get("rule-sets").getAsJsonArray(); // NON-NLS
        if (asJsonArray == null) {
            throw new JsonParseException(Bundle.LogicalImagerConfigDeserializer_missingRuleSetException());
        }

        List<LogicalImagerRuleSet> ruleSets = new ArrayList<>();
        for (JsonElement element : asJsonArray) {
            String setName = null;
            List<LogicalImagerRule> rules = null;
            JsonObject asJsonObject = element.getAsJsonObject();
            JsonElement setNameElement = asJsonObject.get("set-name");
            setName = setNameElement.getAsString();
            JsonElement rulesElement = asJsonObject.get("rules");
            rules = parseRules(rulesElement.getAsJsonArray());
            LogicalImagerRuleSet ruleSet = new LogicalImagerRuleSet(setName, rules);
            ruleSets.add(ruleSet);
        }
        return new LogicalImagerConfig(version, finalizeImageWriter, promptBeforeExit, createVHD, ruleSets);
    }

    private List<LogicalImagerRule> parseRules(JsonArray asJsonArray) {
        List<LogicalImagerRule> rules = new ArrayList<>();

        for (JsonElement element : asJsonArray) {
            String key1;
            Boolean shouldSave = false;
            Boolean shouldAlert = true;
            String name = null;
            String description = null;
            List<String> extensions = null;
            List<String> paths = null;
            List<String> fullPaths = null;
            List<String> filenames = null;
            Long minFileSize = null;
            Long maxFileSize = null;
            Integer minDays = null;
            Integer minDate = null;
            Integer maxDate = null;

            Set<Map.Entry<String, JsonElement>> entrySet = element.getAsJsonObject().entrySet();

            for (Map.Entry<String, JsonElement> entry1 : entrySet) {
                key1 = entry1.getKey();
                switch (key1) {
                    case "shouldAlert": // NON-NLS
                        shouldAlert = entry1.getValue().getAsBoolean();
                        break;
                    case "shouldSave": // NON-NLS
                        shouldSave = entry1.getValue().getAsBoolean();
                        break;
                    case "name": // NON-NLS
                        name = entry1.getValue().getAsString();
                        break;
                    case "description": // NON-NLS
                        description = entry1.getValue().getAsString();
                        break;
                    case "extensions": // NON-NLS
                        JsonArray extensionsArray = entry1.getValue().getAsJsonArray();
                        extensions = new ArrayList<>();
                        for (JsonElement e : extensionsArray) {
                            extensions.add(e.getAsString());
                        }
                        break;
                    case "folder-names": // NON-NLS
                        JsonArray pathsArray = entry1.getValue().getAsJsonArray();
                        paths = new ArrayList<>();
                        for (JsonElement e : pathsArray) {
                            paths.add(e.getAsString());
                        }
                        break;
                    case "file-names": // NON-NLS
                        JsonArray filenamesArray = entry1.getValue().getAsJsonArray();
                        filenames = new ArrayList<>();
                        for (JsonElement e : filenamesArray) {
                            filenames.add(e.getAsString());
                        }
                        break;
                    case "full-paths": // NON-NLS
                        JsonArray fullPathsArray = entry1.getValue().getAsJsonArray();
                        fullPaths = new ArrayList<>();
                        for (JsonElement e : fullPathsArray) {
                            fullPaths.add(e.getAsString());
                        }
                        break;
                    case "size-range": // NON-NLS
                        JsonObject sizeRangeObject = entry1.getValue().getAsJsonObject();
                        Set<Map.Entry<String, JsonElement>> entrySet1 = sizeRangeObject.entrySet();
                        for (Map.Entry<String, JsonElement> entry2 : entrySet1) {
                            String sizeKey = entry2.getKey();
                            switch (sizeKey) {
                                case "min": // NON-NLS
                                    minFileSize = entry2.getValue().getAsLong();
                                    break;
                                case "max": // NON-NLS
                                    maxFileSize = entry2.getValue().getAsLong();
                                    break;
                                default:
                                    throw new JsonParseException(Bundle.LogicalImagerConfigDeserializer_unsupportedKeyException(sizeKey));
                            }
                        }
                        break;
                    case "date-range": // NON-NLS
                        JsonObject dateRangeObject = entry1.getValue().getAsJsonObject();
                        Set<Map.Entry<String, JsonElement>> entrySet2 = dateRangeObject.entrySet();
                        for (Map.Entry<String, JsonElement> entry2 : entrySet2) {
                            String dateKey = entry2.getKey();
                            switch (dateKey) {
                                case "min": // NON-NLS
                                    minDate = entry2.getValue().getAsInt();
                                    break;
                                case "max": // NON-NLS
                                    maxDate = entry2.getValue().getAsInt();
                                    break;
                                case "min-days": // NON-NLS
                                    minDays = entry2.getValue().getAsInt();
                                    break;
                                default:
                                    throw new JsonParseException(Bundle.LogicalImagerConfigDeserializer_unsupportedKeyException(dateKey));
                            }
                        }
                        break;
                    default:
                        throw new JsonParseException(Bundle.LogicalImagerConfigDeserializer_unsupportedKeyException(key1));
                }
            }

            // A rule with full-paths cannot have other rule definitions
            if ((fullPaths != null && !fullPaths.isEmpty()) && ((extensions != null && !extensions.isEmpty())
                    || (paths != null && !paths.isEmpty())
                    || (filenames != null && !filenames.isEmpty()))) {
                throw new JsonParseException(Bundle.LogicalImagerConfigDeserializer_fullPathsException());
            }

            LogicalImagerRule rule = new LogicalImagerRule.Builder()
                    .getShouldAlert(shouldAlert)
                    .getShouldSave(shouldSave)
                    .getName(name)
                    .getDescription(description)
                    .getExtensions(extensions)
                    .getPaths(paths)
                    .getFullPaths(fullPaths)
                    .getFilenames(filenames)
                    .getMinFileSize(minFileSize)
                    .getMaxFileSize(maxFileSize)
                    .getMinDays(minDays)
                    .getMinDate(minDate)
                    .getMaxDate(maxDate)
                    .build();
            rules.add(rule);
        } // for

        return rules;
    }
}
