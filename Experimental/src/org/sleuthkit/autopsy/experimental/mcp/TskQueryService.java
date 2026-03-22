/*
 * Autopsy Forensic Browser
 *
 * Copyright 2024 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import org.sleuthkit.datamodel.*;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * All TSK Java API calls live here. Nothing else touches SleuthkitCase directly.
 * Accepts strongly-typed or JSON-parsed parameters; returns plain Map/List results
 * that Jackson can serialize directly into the MCP JSON-RPC response.
 */
class TskQueryService {

    private final SleuthkitCase skCase;
    private final String caseName;

    TskQueryService(SleuthkitCase skCase, String caseName) {
        this.skCase = skCase;
        this.caseName = caseName;
    }

    // -------------------------------------------------------------------------
    // Tool definitions (what Claude sees)
    // -------------------------------------------------------------------------

    /**
     * Returns the list of MCP tool definitions Claude will see.
     */
    List<Map<String, Object>> listTools() {
        return List.of(
            tool("get_case_summary",
                "Get a summary of the currently open case including name, " +
                "data sources, file count, and artifact count. Call this first " +
                "for any general question about the case.",
                Map.of()),

            tool("query_files",
                "Search for files in the current case. All parameters optional. " +
                "Returns full file metadata matching Autopsy's UI columns: name, path, " +
                "timestamps (modified/changed/accessed/created), size, flags, known status, " +
                "MIME type, extension, hashes (MD5/SHA-256/SHA-1), and file attributes. " +
                "Limit defaults to 50.",
                Map.of(
                    "nameContains",    param("string",  "Filter by filename substring (case-insensitive)"),
                    "extension",       param("string",  "Filter by file extension without dot, e.g. exe, jpg, pdf (case-insensitive)"),
                    "mimeType",        param("string",  "Filter by MIME type e.g. image/jpeg or image/*"),
                    "minSize",         param("integer", "Minimum file size in bytes"),
                    "maxSize",         param("integer", "Maximum file size in bytes"),
                    "modifiedAfter",   param("string",  "ISO 8601 date — files with mtime after this date"),
                    "modifiedBefore",  param("string",  "ISO 8601 date — files with mtime before this date"),
                    "createdAfter",    param("string",  "ISO 8601 date — files with crtime (birth time) after this date"),
                    "createdBefore",   param("string",  "ISO 8601 date — files with crtime (birth time) before this date"),
                    "pathContains",    param("string",  "Filter by parent path substring (case-insensitive)"),
                    "isDirectory",     param("boolean", "true to return only directories, false for regular files only"),
                    "allocated",       param("boolean", "true for allocated files only, false for unallocated only"),
                    "knownState",      param("string",  "Filter by known status: UNKNOWN, KNOWN, or NOTABLE"),
                    "md5",             param("string",  "Filter by exact MD5 hash (hex string)"),
                    "sha256",          param("string",  "Filter by exact SHA-256 hash (hex string)"),
                    "limit",           param("integer", "Max results, default 50, max 500")
                )),

            tool("query_artifacts",
                "Search for artifacts (browser history, downloads, installed programs, " +
                "USB devices, etc.) in the current case.",
                Map.of(
                    "artifactType",   param("string",  "TSK artifact type name e.g. TSK_WEB_HISTORY"),
                    "attributeType",  param("string",  "Filter by attribute type name"),
                    "attributeValue", param("string",  "Filter by attribute value substring"),
                    "dataSourceId",   param("integer", "Limit to a specific data source"),
                    "limit",          param("integer", "Max results, default 50, max 500")
                )),

            tool("query_data_sources",
                "List all data sources (disk images, logical file sets) in the current case.",
                Map.of()),

            tool("query_tags",
                "Find files or artifacts that have been tagged by the examiner.",
                Map.of(
                    "tagName", param("string", "Filter by tag name e.g. \"Notable Item\"")
                ))
        );
    }

    // -------------------------------------------------------------------------
    // get_case_summary
    // -------------------------------------------------------------------------

    Map<String, Object> getCaseSummary() throws TskCoreException {
        long totalFiles = skCase.countFilesWhere("1=1");

        final long[] artifactCount = {0};
        skCase.getCaseDbAccessManager().select(
            "COUNT(*) AS cnt FROM blackboard_artifacts",
            rs -> {
                try {
                    if (rs.next()) {
                        artifactCount[0] = rs.getLong("cnt");
                    }
                } catch (java.sql.SQLException ex) {
                    // leave count at 0
                }
            });

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("caseName", caseName);
        summary.put("dataSources", skCase.getDataSources().size());
        summary.put("totalFiles", totalFiles);
        summary.put("totalArtifacts", artifactCount[0]);
        return summary;
    }

    // -------------------------------------------------------------------------
    // query_files
    // -------------------------------------------------------------------------

    /**
     * Query files with optional filters. Returns a rich map of all AbstractFile
     * fields matching the Autopsy UI columns plus extended file attributes.
     *
     * Bug fix vs. prior version: ctime = change time (not creation time);
     * crtime = creation/birth time.
     */
    List<Map<String, Object>> queryFiles(JsonNode args) throws TskCoreException {
        int limit = args.path("limit").asInt(50);
        if (limit <= 0 || limit > 500) {
            limit = 50;
        }

        List<String> conditions = new ArrayList<>();

        // --- name ---
        String nameContains = textOrNull(args, "nameContains");
        if (nameContains != null) {
            conditions.add("LOWER(name) LIKE '%" + escapeSql(nameContains.toLowerCase()) + "%'");
        }

        // --- extension ---
        String extension = textOrNull(args, "extension");
        if (extension != null) {
            conditions.add("LOWER(extension) = '" + escapeSql(extension.toLowerCase()) + "'");
        }

        // --- MIME type ---
        String mimeType = textOrNull(args, "mimeType");
        if (mimeType != null) {
            if (mimeType.endsWith("*")) {
                String prefix = mimeType.substring(0, mimeType.length() - 1);
                conditions.add("mime_type LIKE '" + escapeSql(prefix) + "%'");
            } else {
                conditions.add("mime_type = '" + escapeSql(mimeType) + "'");
            }
        }

        // --- size ---
        if (!args.path("minSize").isMissingNode()) {
            conditions.add("size >= " + args.path("minSize").asLong());
        }
        if (!args.path("maxSize").isMissingNode()) {
            conditions.add("size <= " + args.path("maxSize").asLong());
        }

        // --- mtime ---
        if (!args.path("modifiedAfter").isMissingNode()) {
            long epoch = Instant.parse(args.path("modifiedAfter").asText()).getEpochSecond();
            conditions.add("mtime >= " + epoch);
        }
        if (!args.path("modifiedBefore").isMissingNode()) {
            long epoch = Instant.parse(args.path("modifiedBefore").asText()).getEpochSecond();
            conditions.add("mtime <= " + epoch);
        }

        // --- crtime (birth/creation time) ---
        if (!args.path("createdAfter").isMissingNode()) {
            long epoch = Instant.parse(args.path("createdAfter").asText()).getEpochSecond();
            conditions.add("crtime >= " + epoch);
        }
        if (!args.path("createdBefore").isMissingNode()) {
            long epoch = Instant.parse(args.path("createdBefore").asText()).getEpochSecond();
            conditions.add("crtime <= " + epoch);
        }

        // --- parent path ---
        String pathContains = textOrNull(args, "pathContains");
        if (pathContains != null) {
            conditions.add("LOWER(parent_path) LIKE '%" + escapeSql(pathContains.toLowerCase()) + "%'");
        }

        // --- directory vs. regular file ---
        if (!args.path("isDirectory").isMissingNode()) {
            boolean isDir = args.path("isDirectory").asBoolean();
            conditions.add("dir_type = " + (isDir ? TskData.TSK_FS_NAME_TYPE_ENUM.DIR.getValue()
                                                   : TskData.TSK_FS_NAME_TYPE_ENUM.REG.getValue()));
        }

        // --- allocated / unallocated ---
        if (!args.path("allocated").isMissingNode()) {
            boolean alloc = args.path("allocated").asBoolean();
            conditions.add("dir_flags = " + (alloc ? TskData.TSK_FS_NAME_FLAG_ENUM.ALLOC.getValue()
                                                    : TskData.TSK_FS_NAME_FLAG_ENUM.UNALLOC.getValue()));
        }

        // --- known state ---
        String knownStateStr = textOrNull(args, "knownState");
        if (knownStateStr != null) {
            TskData.FileKnown knownVal = parseKnownState(knownStateStr);
            if (knownVal != null) {
                conditions.add("known = " + knownVal.getFileKnownValue());
            }
        }

        // --- MD5 hash ---
        String md5 = textOrNull(args, "md5");
        if (md5 != null) {
            conditions.add("LOWER(md5) = '" + escapeSql(md5.toLowerCase()) + "'");
        }

        // --- SHA-256 hash ---
        String sha256 = textOrNull(args, "sha256");
        if (sha256 != null) {
            conditions.add("LOWER(sha256) = '" + escapeSql(sha256.toLowerCase()) + "'");
        }

        String whereClause = (conditions.isEmpty() ? "1=1" : String.join(" AND ", conditions))
                + " LIMIT " + limit;

        List<AbstractFile> files = skCase.findAllFilesWhere(whereClause);

        List<Map<String, Object>> result = new ArrayList<>(files.size());
        for (AbstractFile f : files) {
            result.add(buildFileItem(f));
        }
        return result;
    }

    /**
     * Builds the full file result map from an AbstractFile, covering all fields
     * shown in the Autopsy UI and all AbstractFile get* methods.
     */
    private Map<String, Object> buildFileItem(AbstractFile f) {
        Map<String, Object> item = new LinkedHashMap<>();

        // Identity
        item.put("id", f.getId());
        item.put("name", f.getName());
        try {
            item.put("path", f.getUniquePath());
        } catch (TskCoreException ex) {
            item.put("path", f.getParentPath() + f.getName());
        }
        item.put("parentPath", f.getParentPath());
        item.put("extension", f.getNameExtension());
        item.put("dataSourceId", f.getDataSourceObjectId());

        // Size
        item.put("size", f.getSize());

        // Timestamps (MACB)
        item.put("modifiedTime", epochToIso(f.getMtime()));   // mtime
        item.put("accessedTime", epochToIso(f.getAtime()));   // atime
        item.put("changeTime",   epochToIso(f.getCtime()));   // ctime  (metadata change)
        item.put("createdTime",  epochToIso(f.getCrtime()));  // crtime (birth/creation)

        // Type and flags
        item.put("fileType",  f.getType().name());            // FS, CARVED, DERIVED, etc.
        item.put("dirType",   f.getDirTypeAsString());        // REG, DIR, etc.
        item.put("metaType",  f.getMetaTypeAsString());       // regular, directory, etc.
        item.put("dirFlag",   f.getDirFlagAsString());        // Flags (Directory): ALLOC/UNALLOC
        item.put("metaFlags", f.getMetaFlagsAsString());      // Flags (Metadata): ALLOC, UNALLOC, USED, etc.
        item.put("modes",     f.getModesAsString());          // Unix permissions string e.g. "-rw-r--r--"

        // Classification
        item.put("knownState", f.getKnown().name());          // UNKNOWN, KNOWN, BAD (NOTABLE)

        // Content identifiers
        item.put("mimeType",   f.getMIMEType());
        item.put("md5Hash",    f.getMd5Hash());
        item.put("sha256Hash", f.getSha256Hash());
        item.put("sha1Hash",   f.getSha1Hash());

        // Ownership (Unix uid/gid and Windows owner SID)
        item.put("uid", f.getUid());
        item.put("gid", f.getGid());
        item.put("ownerUid", f.getOwnerUid().orElse(null));

        // Low-level metadata
        item.put("metaAddr", f.getMetaAddr());
        item.put("metaSeq",  f.getMetaSeq());

        // Extended file attributes (blackboard attributes attached to this file)
        try {
            List<Attribute> attrs = f.getAttributes();
            if (!attrs.isEmpty()) {
                List<Map<String, Object>> attrList = new ArrayList<>(attrs.size());
                for (Attribute attr : attrs) {
                    Map<String, Object> attrMap = new LinkedHashMap<>();
                    attrMap.put("type", attr.getAttributeType().getTypeName());
                    attrMap.put("value", fileAttrValueAsObject(attr));
                    attrList.add(attrMap);
                }
                item.put("attributes", attrList);
            } else {
                item.put("attributes", Collections.emptyList());
            }
        } catch (TskCoreException ex) {
            item.put("attributes", Collections.emptyList());
        }

        return item;
    }

    // -------------------------------------------------------------------------
    // query_artifacts
    // -------------------------------------------------------------------------

    List<Map<String, Object>> queryArtifacts(JsonNode args) throws TskCoreException {
        int limit = args.path("limit").asInt(50);
        if (limit <= 0 || limit > 500) {
            limit = 50;
        }

        String artifactTypeName    = textOrNull(args, "artifactType");
        String attributeTypeFilter = textOrNull(args, "attributeType");
        String attributeValueFilter = textOrNull(args, "attributeValue");
        long   dataSourceId = args.path("dataSourceId").isMissingNode() ? -1
                            : args.path("dataSourceId").asLong();

        List<BlackboardArtifact.Type> typesToQuery = new ArrayList<>();
        for (BlackboardArtifact.Type t : skCase.getArtifactTypesInUse()) {
            if (artifactTypeName == null || t.getTypeName().equalsIgnoreCase(artifactTypeName)) {
                typesToQuery.add(t);
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        outer:
        for (BlackboardArtifact.Type type : typesToQuery) {
            for (BlackboardArtifact artifact : skCase.getBlackboardArtifacts(type.getTypeID())) {
                if (result.size() >= limit) {
                    break outer;
                }
                if (dataSourceId >= 0 && artifact.getDataSourceObjectID() != dataSourceId) {
                    continue;
                }
                List<BlackboardAttribute> attrs = artifact.getAttributes();
                if (attributeTypeFilter != null || attributeValueFilter != null) {
                    boolean matches = false;
                    for (BlackboardAttribute attr : attrs) {
                        boolean typeMatch = attributeTypeFilter == null
                                || attr.getAttributeType().getTypeName().equalsIgnoreCase(attributeTypeFilter);
                        boolean valueMatch = attributeValueFilter == null
                                || attrValueAsString(attr).toLowerCase().contains(attributeValueFilter.toLowerCase());
                        if (typeMatch && valueMatch) {
                            matches = true;
                            break;
                        }
                    }
                    if (!matches) {
                        continue;
                    }
                }

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", artifact.getArtifactID());
                item.put("artifactType", artifact.getArtifactTypeName());
                item.put("dataSourceId", artifact.getDataSourceObjectID());

                List<Map<String, Object>> attrList = new ArrayList<>();
                for (BlackboardAttribute attr : attrs) {
                    Map<String, Object> attrMap = new LinkedHashMap<>();
                    attrMap.put("type", attr.getAttributeType().getTypeName());
                    attrMap.put("value", attrValueAsObject(attr));
                    attrList.add(attrMap);
                }
                item.put("attributes", attrList);
                result.add(item);
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // query_data_sources
    // -------------------------------------------------------------------------

    List<Map<String, Object>> queryDataSources() throws TskCoreException {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Content ds : skCase.getDataSources()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", ds.getId());
            item.put("name", ds.getName());
            item.put("type", ds.getClass().getSimpleName());
            item.put("size", ds.getSize());
            if (ds instanceof DataSource) {
                DataSource dataSource = (DataSource) ds;
                item.put("timezone", dataSource.getTimeZone());
                item.put("addedDate", epochToIso(dataSource.getDateAdded()));
            } else {
                item.put("timezone", null);
                item.put("addedDate", null);
            }
            result.add(item);
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // query_tags
    // -------------------------------------------------------------------------

    List<Map<String, Object>> queryTags(JsonNode args) throws TskCoreException {
        String tagNameFilter = textOrNull(args, "tagName");

        List<TagName> tagNames = new ArrayList<>();
        for (TagName tn : skCase.getTagNamesInUse()) {
            if (tagNameFilter == null || tn.getDisplayName().equalsIgnoreCase(tagNameFilter)) {
                tagNames.add(tn);
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (TagName tagName : tagNames) {
            for (ContentTag tag : skCase.getContentTagsByTagName(tagName)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("tagId", tag.getId());
                item.put("tagName", tagName.getDisplayName());
                item.put("comment", tag.getComment());
                item.put("itemType", "file");
                Content content = tag.getContent();
                item.put("itemId", content.getId());
                item.put("itemName", content.getName());
                result.add(item);
            }
            for (BlackboardArtifactTag tag : skCase.getBlackboardArtifactTagsByTagName(tagName)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("tagId", tag.getId());
                item.put("tagName", tagName.getDisplayName());
                item.put("comment", tag.getComment());
                item.put("itemType", "artifact");
                BlackboardArtifact artifact = tag.getArtifact();
                item.put("itemId", artifact.getArtifactID());
                item.put("itemName", artifact.getArtifactTypeName());
                result.add(item);
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Map<String, Object> tool(String name, String description, Map<String, Object> properties) {
        return Map.of(
            "name", name,
            "description", description,
            "inputSchema", Map.of(
                "type", "object",
                "properties", properties
            )
        );
    }

    private Map<String, Object> param(String type, String description) {
        return Map.of("type", type, "description", description);
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode child = node.path(field);
        if (child.isMissingNode() || child.isNull()) {
            return null;
        }
        String text = child.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private static String escapeSql(String value) {
        return value.replace("'", "''");
    }

    private static String epochToIso(long epochSeconds) {
        if (epochSeconds <= 0) {
            return null;
        }
        return Instant.ofEpochSecond(epochSeconds)
                .atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    /**
     * Maps "UNKNOWN", "KNOWN", "NOTABLE" (or "BAD") to FileKnown enum values.
     */
    private static TskData.FileKnown parseKnownState(String value) {
        switch (value.toUpperCase()) {
            case "KNOWN":   return TskData.FileKnown.KNOWN;
            case "NOTABLE":
            case "BAD":     return TskData.FileKnown.BAD;
            case "UNKNOWN": return TskData.FileKnown.UNKNOWN;
            default:        return null;
        }
    }

    /**
     * Returns the value of a BlackboardAttribute as a Java object suitable for
     * JSON serialization. DATETIME values become ISO 8601 strings.
     */
    private static Object attrValueAsObject(BlackboardAttribute attr) {
        switch (attr.getValueType()) {
            case STRING:
            case JSON:
                return attr.getValueString();
            case DATETIME:
                return epochToIso(attr.getValueLong());
            case INTEGER:
                return attr.getValueInt();
            case LONG:
                return attr.getValueLong();
            case DOUBLE:
                return attr.getValueDouble();
            default:
                return null;
        }
    }

    /**
     * Returns the value of a file Attribute (from AbstractFile.getAttributes())
     * as a Java object suitable for JSON serialization.
     */
    private static Object fileAttrValueAsObject(Attribute attr) {
        BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE valueType =
                attr.getAttributeType().getValueType();
        switch (valueType) {
            case STRING:
            case JSON:
                return attr.getValueString();
            case DATETIME:
                return epochToIso(attr.getValueLong());
            case INTEGER:
                return attr.getValueInt();
            case LONG:
                return attr.getValueLong();
            case DOUBLE:
                return attr.getValueDouble();
            default:
                return null;
        }
    }

    /**
     * Returns the value of a BlackboardAttribute as a String for substring
     * matching. DATETIME values are ISO 8601.
     */
    private static String attrValueAsString(BlackboardAttribute attr) {
        switch (attr.getValueType()) {
            case STRING:
            case JSON:
                String s = attr.getValueString();
                return s != null ? s : "";
            case DATETIME:
                String iso = epochToIso(attr.getValueLong());
                return iso != null ? iso : "";
            case INTEGER:
                return Integer.toString(attr.getValueInt());
            case LONG:
                return Long.toString(attr.getValueLong());
            case DOUBLE:
                return Double.toString(attr.getValueDouble());
            default:
                return "";
        }
    }
}
