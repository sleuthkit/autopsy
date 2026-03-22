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
import org.joda.time.DateTimeZone;
import org.joda.time.Interval;
import org.sleuthkit.datamodel.*;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
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
                Map.ofEntries(
                    Map.entry("nameContains",   param("string",  "Filter by filename substring (case-insensitive)")),
                    Map.entry("extension",      param("string",  "Filter by file extension without dot, e.g. exe, jpg, pdf (case-insensitive)")),
                    Map.entry("mimeType",       param("string",  "Filter by MIME type e.g. image/jpeg or image/*")),
                    Map.entry("minSize",        param("integer", "Minimum file size in bytes")),
                    Map.entry("maxSize",        param("integer", "Maximum file size in bytes")),
                    Map.entry("modifiedAfter",  param("string",  "ISO 8601 date — files with mtime after this date")),
                    Map.entry("modifiedBefore", param("string",  "ISO 8601 date — files with mtime before this date")),
                    Map.entry("createdAfter",   param("string",  "ISO 8601 date — files with crtime (birth time) after this date")),
                    Map.entry("createdBefore",  param("string",  "ISO 8601 date — files with crtime (birth time) before this date")),
                    Map.entry("pathContains",   param("string",  "Filter by parent path substring (case-insensitive)")),
                    Map.entry("isDirectory",    param("boolean", "true to return only directories, false for regular files only")),
                    Map.entry("allocated",      param("boolean", "true for allocated files only, false for unallocated only")),
                    Map.entry("knownState",     param("string",  "Filter by known status: UNKNOWN, KNOWN, or NOTABLE")),
                    Map.entry("md5",            param("string",  "Filter by exact MD5 hash (hex string)")),
                    Map.entry("sha256",         param("string",  "Filter by exact SHA-256 hash (hex string)")),
                    Map.entry("limit",          param("integer", "Max results, default 50, max 500")),
                    Map.entry("orderBy",        param("string",  "Sort order: size_desc, size_asc, name_asc, name_desc, modified_desc, modified_asc, created_desc, created_asc"))
                )),

            tool("query_data_artifacts",
                "Search for data artifacts in the current case. Data artifacts represent facts " +
                "extracted from the data — browser history, messages, contacts, installed programs, " +
                "GPS locations, etc. Use artifactType to filter by type. " +
                "Available types: " +
                "TSK_ACCOUNT (credit card or communications account; attr: TSK_ACCOUNT_TYPE, TSK_ID, TSK_CARD_NUMBER), " +
                "TSK_ASSOCIATED_OBJECT (back-link to referencing artifact; attr: TSK_ASSOCIATED_ARTIFACT), " +
                "TSK_BACKUP_EVENT (system/app/file backup; attr: TSK_DATETIME_START, TSK_DATETIME_END), " +
                "TSK_BLUETOOTH_ADAPTER (BT adapter; attr: TSK_MAC_ADDRESS, TSK_NAME, TSK_DATETIME, TSK_DEVICE_ID), " +
                "TSK_BLUETOOTH_PAIRING (BT pairing; attr: TSK_DEVICE_NAME, TSK_DATETIME, TSK_MAC_ADDRESS, TSK_DEVICE_ID, TSK_DATETIME_ACCESSED), " +
                "TSK_CALENDAR_ENTRY (calendar event; attr: TSK_CALENDAR_ENTRY_TYPE, TSK_DATETIME_START, TSK_DESCRIPTION, TSK_LOCATION, TSK_DATETIME_END), " +
                "TSK_CALLLOG (call record; attr: TSK_PHONE_NUMBER, TSK_PHONE_NUMBER_FROM, TSK_PHONE_NUMBER_TO, TSK_DATETIME_START, TSK_DATETIME_END, TSK_DIRECTION, TSK_NAME), " +
                "TSK_CLIPBOARD_CONTENT (clipboard data; attr: TSK_TEXT), " +
                "TSK_CONTACT (contact book entry; attr: TSK_NAME, TSK_EMAIL, TSK_PHONE_NUMBER, TSK_ORGANIZATION, TSK_URL), " +
                "TSK_DELETED_PROG (deleted program; attr: TSK_DATETIME, TSK_PROG_NAME, TSK_PATH), " +
                "TSK_DEVICE_ATTACHED (physically attached device e.g. USB; attr: TSK_DEVICE_ID, TSK_DATETIME, TSK_DEVICE_MAKE, TSK_DEVICE_MODEL, TSK_MAC_ADDRESS), " +
                "TSK_DEVICE_INFO (device identifiers; attr: TSK_IMEI, TSK_ICCID, TSK_IMSI), " +
                "TSK_EMAIL_MSG (email message; attr: TSK_EMAIL_FROM, TSK_EMAIL_TO, TSK_EMAIL_CC, TSK_EMAIL_BCC, TSK_SUBJECT, TSK_DATETIME_SENT, TSK_DATETIME_RCVD, TSK_EMAIL_CONTENT_PLAIN, TSK_HEADERS, TSK_MSG_ID, TSK_THREAD_ID), " +
                "TSK_EXTRACTED_TEXT (text extracted from content; attr: TSK_TEXT), " +
                "TSK_GEN_INFO (generic per-file info; attr: TSK_HASH_PHOTODNA), " +
                "TSK_GPS_AREA (GPS area outline; attr: TSK_GEO_WAYPOINTS, TSK_LOCATION, TSK_NAME, TSK_PROG_NAME), " +
                "TSK_GPS_BOOKMARK (saved GPS waypoint; attr: TSK_GEO_LATITUDE, TSK_GEO_LONGITUDE, TSK_GEO_ALTITUDE, TSK_DATETIME, TSK_LOCATION, TSK_NAME, TSK_PROG_NAME), " +
                "TSK_GPS_LAST_KNOWN_LOCATION (last known GPS location; attr: TSK_GEO_LATITUDE, TSK_GEO_LONGITUDE, TSK_GEO_ALTITUDE, TSK_DATETIME, TSK_LOCATION, TSK_NAME), " +
                "TSK_GPS_ROUTE (GPS route; attr: TSK_GEO_WAYPOINTS, TSK_DATETIME, TSK_LOCATION, TSK_NAME, TSK_PROG_NAME), " +
                "TSK_GPS_SEARCH (GPS location that was searched; attr: TSK_GEO_LATITUDE, TSK_GEO_LONGITUDE, TSK_GEO_ALTITUDE, TSK_DATETIME, TSK_LOCATION, TSK_NAME), " +
                "TSK_GPS_TRACK (GPS track path; attr: TSK_GEO_TRACKPOINTS, TSK_NAME, TSK_PROG_NAME), " +
                "TSK_INSTALLED_PROG (installed program; attr: TSK_PROG_NAME, TSK_DATETIME, TSK_PATH, TSK_VERSION, TSK_PERMISSIONS), " +
                "TSK_MESSAGE (chat/SMS message; attr: TSK_TEXT, TSK_MESSAGE_TYPE, TSK_DATETIME, TSK_DIRECTION, TSK_PHONE_NUMBER_FROM, TSK_PHONE_NUMBER_TO, TSK_READ_STATUS, TSK_SUBJECT, TSK_THREAD_ID), " +
                "TSK_METADATA (document metadata; attr: TSK_DATETIME_CREATED, TSK_DATETIME_MODIFIED, TSK_DESCRIPTION, TSK_OWNER, TSK_ORGANIZATION, TSK_PROG_NAME, TSK_VERSION, TSK_LAST_PRINTED_DATETIME, TSK_USER_ID), " +
                "TSK_OS_INFO (OS details; attr: TSK_PROG_NAME, TSK_VERSION, TSK_DATETIME, TSK_DOMAIN, TSK_OWNER, TSK_ORGANIZATION, TSK_PATH, TSK_PROCESSOR_ARCHITECTURE, TSK_NAME, TSK_PRODUCT_ID, TSK_TEMP_DIR), " +
                "TSK_PROG_NOTIFICATIONS (app notifications; attr: TSK_DATETIME, TSK_PROG_NAME, TSK_TITLE, TSK_VALUE), " +
                "TSK_PROG_RUN (program execution; attr: TSK_PROG_NAME, TSK_DATETIME, TSK_COUNT, TSK_USER_NAME, TSK_PATH), " +
                "TSK_RECENT_OBJECT (recently accessed item; attr: TSK_PATH, TSK_DATETIME_ACCESSED, TSK_PROG_NAME, TSK_NAME, TSK_VALUE), " +
                "TSK_REMOTE_DRIVE (remote/mapped drive; attr: TSK_REMOTE_PATH, TSK_LOCAL_PATH), " +
                "TSK_SCREEN_SHOTS (screenshot; attr: TSK_DATETIME, TSK_PROG_NAME, TSK_PATH), " +
                "TSK_SERVICE_ACCOUNT (app/web account; attr: TSK_PROG_NAME, TSK_USER_ID, TSK_USER_NAME, TSK_DOMAIN, TSK_URL, TSK_DATETIME_CREATED, TSK_CATEGORY, TSK_PASSWORD), " +
                "TSK_SIM_ATTACHED (SIM card; attr: TSK_ICCID, TSK_IMSI), " +
                "TSK_SPEED_DIAL_ENTRY (speed dial; attr: TSK_PHONE_NUMBER, TSK_NAME_PERSON, TSK_SHORTCUT), " +
                "TSK_TL_EVENT (timeline event; attr: TSK_TL_EVENT_TYPE, TSK_DATETIME, TSK_DESCRIPTION), " +
                "TSK_USER_DEVICE_EVENT (device activity e.g. lock/unlock; attr: TSK_DATETIME_START, TSK_ACTIVITY_TYPE, TSK_DATETIME_END, TSK_PROG_NAME), " +
                "TSK_WEB_BOOKMARK (browser bookmark; attr: TSK_URL, TSK_DOMAIN, TSK_PROG_NAME, TSK_NAME, TSK_TITLE, TSK_DATETIME_CREATED, TSK_USER_NAME, TSK_COMMENT), " +
                "TSK_WEB_CACHE (web cache entry; attr: TSK_URL, TSK_PATH, TSK_DOMAIN, TSK_DATETIME_CREATED, TSK_HEADERS), " +
                "TSK_WEB_COOKIE (web cookie; attr: TSK_URL, TSK_NAME, TSK_VALUE, TSK_DOMAIN, TSK_DATETIME_CREATED, TSK_DATETIME_ACCESSED, TSK_DATETIME_END, TSK_PROG_NAME, TSK_USER_NAME), " +
                "TSK_WEB_DOWNLOAD (web download; attr: TSK_URL, TSK_DOMAIN, TSK_PATH, TSK_DATETIME_ACCESSED, TSK_PROG_NAME), " +
                "TSK_WEB_FORM_ADDRESS (browser autofill address; attr: TSK_LOCATION, TSK_NAME_PERSON, TSK_EMAIL, TSK_PHONE_NUMBER, TSK_DATETIME_ACCESSED), " +
                "TSK_WEB_FORM_AUTOFILL (browser autofill field; attr: TSK_NAME, TSK_VALUE, TSK_DATETIME_CREATED, TSK_DATETIME_ACCESSED, TSK_PROG_NAME), " +
                "TSK_WEB_HISTORY (browser history; attr: TSK_URL, TSK_DOMAIN, TSK_TITLE, TSK_DATETIME_ACCESSED, TSK_REFERRER, TSK_URL_DECODED, TSK_USER_NAME, TSK_PROG_NAME), " +
                "TSK_WEB_SEARCH_QUERY (web search; attr: TSK_TEXT, TSK_DOMAIN, TSK_DATETIME_ACCESSED, TSK_PROG_NAME), " +
                "TSK_WIFI_NETWORK (WiFi network; attr: TSK_SSID, TSK_DATETIME, TSK_DEVICE_ID, TSK_MAC_ADDRESS), " +
                "TSK_WIFI_NETWORK_ADAPTER (WiFi adapter; attr: TSK_MAC_ADDRESS).",
                Map.of(
                    "artifactType",   param("string",  "TSK artifact type name e.g. TSK_WEB_HISTORY"),
                    "attributeType",  param("string",  "Filter by attribute type name e.g. TSK_URL"),
                    "attributeValue", param("string",  "Filter by attribute value substring"),
                    "dataSourceId",   param("integer", "Limit to a specific data source"),
                    "limit",          param("integer", "Max results, default 50, max 500")
                )),

            tool("query_analysis_results",
                "Search for analysis results in the current case. Analysis results are conclusions " +
                "drawn by ingest modules — hash hits, keyword hits, encryption detection, EXIF data, " +
                "etc. Each result includes a score (significance + priority) indicating how notable " +
                "the finding is. Significance values: NOTABLE, LIKELY_NOTABLE, LIKELY_NONE, NONE, UNKNOWN. " +
                "Use artifactType to filter by type. Available types: " +
                "TSK_DATA_SOURCE_USAGE (how data source was used e.g. OS Drive; attr: TSK_DESCRIPTION), " +
                "TSK_ENCRYPTION_DETECTED (encrypted content; attr: TSK_COMMENT), " +
                "TSK_ENCRYPTION_SUSPECTED (likely encrypted content; attr: TSK_COMMENT), " +
                "TSK_EXT_MISMATCH_DETECTED (file extension does not match MIME type), " +
                "TSK_FACE_DETECTED (human face detected in media), " +
                "TSK_HASHSET_HIT (MD5 matches a known hashset; attr: TSK_SET_NAME, TSK_COMMENT), " +
                "TSK_INTERESTING_ITEM (matches an interesting items rule; attr: TSK_SET_NAME, TSK_COMMENT, TSK_CATEGORY), " +
                "TSK_INTERESTING_ARTIFACT_HIT (artifact matches an interesting items rule; attr: TSK_SET_NAME, TSK_COMMENT), " +
                "TSK_INTERESTING_FILE_HIT (file matches an interesting items rule; attr: TSK_SET_NAME, TSK_COMMENT), " +
                "TSK_KEYWORD_HIT (keyword search match; attr: TSK_KEYWORD, TSK_KEYWORD_SEARCH_TYPE, TSK_SET_NAME, TSK_KEYWORD_PREVIEW), " +
                "TSK_MALWARE (malware detected), " +
                "TSK_METADATA_EXIF (EXIF metadata from image; attr: TSK_DATETIME_CREATED, TSK_DEVICE_MAKE, TSK_DEVICE_MODEL, TSK_GEO_LATITUDE, TSK_GEO_LONGITUDE, TSK_GEO_ALTITUDE), " +
                "TSK_OBJECT_DETECTED (object detected in media by CV; attr: TSK_COMMENT), " +
                "TSK_PREVIOUSLY_NOTABLE (previously tagged Notable in another case; attr: TSK_CORRELATION_TYPE, TSK_CORRELATION_VALUE, TSK_OTHER_CASES), " +
                "TSK_PREVIOUSLY_SEEN (seen in another case; attr: TSK_CORRELATION_TYPE, TSK_CORRELATION_VALUE, TSK_OTHER_CASES), " +
                "TSK_PREVIOUSLY_UNSEEN (not seen before; attr: TSK_CORRELATION_TYPE, TSK_CORRELATION_VALUE), " +
                "TSK_USER_CONTENT_SUSPECTED (likely user-generated content; attr: TSK_COMMENT), " +
                "TSK_VERIFICATION_FAILED (hash/integrity verification failed; attr: TSK_COMMENT), " +
                "TSK_WEB_ACCOUNT_TYPE (web account type classification; attr: TSK_DOMAIN, TSK_TEXT, TSK_URL), " +
                "TSK_WEB_CATEGORIZATION (web host category e.g. Web Email; attr: TSK_NAME, TSK_DOMAIN, TSK_HOST), " +
                "TSK_YARA_HIT (YARA rule match; attr: TSK_RULE, TSK_SET_NAME).",
                Map.of(
                    "artifactType",   param("string",  "TSK artifact type name e.g. TSK_KEYWORD_HIT"),
                    "attributeType",  param("string",  "Filter by attribute type name e.g. TSK_KEYWORD"),
                    "attributeValue", param("string",  "Filter by attribute value substring"),
                    "dataSourceId",   param("integer", "Limit to a specific data source"),
                    "limit",          param("integer", "Max results, default 50, max 500")
                )),

            tool("query_data_sources",
                "List all data sources (disk images, logical file sets) in the current case. " +
                "Returns id, name, type, size, timezone, and for disk images: image type, " +
                "sector size, file paths, and acquisition hashes (MD5/SHA-1/SHA-256).",
                Map.of()),

            tool("get_hosts",
                "List all hosts in the case. Each host groups one or more data sources " +
                "that belong to the same device or machine. Use this as the top of the " +
                "storage hierarchy before drilling into data sources.",
                Map.of()),

            tool("get_data_source_tree",
                "Returns the full storage hierarchy for one or all data sources: " +
                "Image → VolumeSystem → Volume → FileSystem. " +
                "Use this to understand how a disk image is partitioned and what file systems it contains. " +
                "Logical file set data sources (no partitions) appear as leaf nodes with no children. " +
                "Each FileSystem entry includes type (NTFS, FAT32, ext4, etc.), offset, block size, " +
                "block count, and inode range.",
                Map.of(
                    "dataSourceId", param("integer", "Object ID of the data source to inspect. Omit to return all data sources.")
                )),

            tool("query_tags",
                "Find files or artifacts that have been tagged by the examiner.",
                Map.of(
                    "tagName", param("string", "Filter by tag name e.g. \"Notable Item\"")
                )),

            tool("query_timeline",
                "Return timeline events in a time range, sorted by time. " +
                "Covers all event types in a single query: file system timestamps (modified, accessed, " +
                "changed, created) and artifact events (web history, downloads, searches, cookies, " +
                "bookmarks, installed programs, USB devices, etc.). " +
                "Much more efficient than querying individual artifact types when the question is " +
                "time-driven. Use summarize_timeline first to understand what categories of activity " +
                "exist before fetching detail. Omit startTime/endTime to span the entire case.",
                Map.ofEntries(
                    Map.entry("startTime",    param("string",  "ISO 8601 start of time range (inclusive)")),
                    Map.entry("endTime",      param("string",  "ISO 8601 end of time range (inclusive)")),
                    Map.entry("dataSourceId", param("integer", "Limit to a specific data source")),
                    Map.entry("textFilter",   param("string",  "Filter events whose description contains this substring")),
                    Map.entry("limit",        param("integer", "Max results, default 100, max 1000"))
                )),

            tool("summarize_timeline",
                "Return counts of timeline events grouped by category for a time range. " +
                "Categories are: File System (file timestamps), Web Activity (history, downloads, " +
                "cookies, bookmarks, searches), and Misc (installed programs, USB devices, etc.). " +
                "Use this before query_timeline to understand the shape of activity in a period " +
                "without fetching every event. Omit startTime/endTime to summarize the entire case.",
                Map.of(
                    "startTime",    param("string",  "ISO 8601 start of time range (inclusive)"),
                    "endTime",      param("string",  "ISO 8601 end of time range (inclusive)"),
                    "dataSourceId", param("integer", "Limit to a specific data source")
                )),

            tool("get_os_accounts",
                "List OS user accounts discovered in the case. Returns SID/UID, login name, " +
                "full name, account type, status, creation time, extended attributes " +
                "(e.g., home directory, login script, last login), and which data sources " +
                "the account appeared on with instance type (LAUNCHED, ACCESSED, or REFERENCED). " +
                "Useful for identifying users, admins, and service accounts on examined systems.",
                Map.of()),

            tool("get_communications_accounts",
                "List accounts found in communications data: email addresses, phone numbers, " +
                "Skype/Facebook/WhatsApp/Twitter/Instagram usernames, etc. Optionally filter " +
                "by account type. Available types: EMAIL, PHONE, SKYPE, FACEBOOK, WHATSAPP, " +
                "TWITTER, INSTAGRAM, MESSAGING_APP, WEBSITE_ACCOUNT, DEVICE. " +
                "Returns account type, identifier, device ID, and relationship count.",
                Map.of(
                    "accountType", param("string",  "Filter by account type e.g. EMAIL, PHONE, SKYPE"),
                    "limit",       param("integer", "Max results, default 100")
                )),

            tool("get_file_content",
                "Read the text content of a file by its object ID. Returns UTF-8 text with " +
                "undecodable bytes replaced by '?'. " +
                "Files larger than 65536 bytes require an explicit maxBytes parameter up to 1048576 (1 MB); " +
                "requests beyond that are rejected — use offset+maxBytes to page through larger files. " +
                "Returns the content string, actual bytes read, file size, and whether the content was truncated.",
                Map.of(
                    "fileId",   param("integer", "Object ID of the file (from query_files id field)"),
                    "offset",   param("integer", "Byte offset to start reading from, default 0"),
                    "maxBytes", param("integer", "Maximum bytes to read, default 65536, max 1048576")
                )),

            tool("get_account_relationships",
                "Get communications relationships for a specific account — who it communicated " +
                "with and how many messages/calls. Supply the accountType (e.g. EMAIL) and " +
                "accountId (the identifier, e.g. user@example.com or +15551234567). " +
                "Returns the matched account plus all related accounts with relationship counts.",
                Map.of(
                    "accountType", param("string", "Account type e.g. EMAIL, PHONE"),
                    "accountId",   param("string", "Type-specific identifier e.g. user@example.com or +15551234567")
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

        // --- orderBy ---
        String orderBy = textOrNull(args, "orderBy");
        String orderClause = switch (orderBy == null ? "" : orderBy.toLowerCase()) {
            case "size_desc"     -> " ORDER BY size DESC";
            case "size_asc"      -> " ORDER BY size ASC";
            case "name_asc"      -> " ORDER BY name ASC";
            case "name_desc"     -> " ORDER BY name DESC";
            case "modified_desc" -> " ORDER BY mtime DESC";
            case "modified_asc"  -> " ORDER BY mtime ASC";
            case "created_desc"  -> " ORDER BY crtime DESC";
            case "created_asc"   -> " ORDER BY crtime ASC";
            default              -> "";
        };

        String whereClause = (conditions.isEmpty() ? "1=1" : String.join(" AND ", conditions))
                + orderClause + " LIMIT " + limit;

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
    // query_data_artifacts
    // -------------------------------------------------------------------------

    List<Map<String, Object>> queryDataArtifacts(JsonNode args) throws TskCoreException {
        return queryArtifactsInternal(args, false);
    }

    // -------------------------------------------------------------------------
    // query_analysis_results
    // -------------------------------------------------------------------------

    List<Map<String, Object>> queryAnalysisResults(JsonNode args) throws TskCoreException {
        return queryArtifactsInternal(args, true);
    }

    /**
     * Shared implementation for both data artifact and analysis result queries.
     * Uses the Blackboard API to query the correct sub-table, then post-filters
     * by attribute type/value and applies the limit.
     *
     * @param args        JSON arguments from the MCP request
     * @param isAnalysis  true → query analysis results; false → query data artifacts
     */
    private List<Map<String, Object>> queryArtifactsInternal(JsonNode args, boolean isAnalysis)
            throws TskCoreException {
        int limit = args.path("limit").asInt(50);
        if (limit <= 0 || limit > 500) limit = 50;

        String artifactTypeName  = textOrNull(args, "artifactType");
        String attrTypeFilter    = textOrNull(args, "attributeType");
        String attrValueFilter   = textOrNull(args, "attributeValue");
        long   dataSourceId      = args.path("dataSourceId").isMissingNode() ? -1
                                 : args.path("dataSourceId").asLong();

        // Build WHERE clause for the Blackboard query
        StringBuilder where = new StringBuilder("1=1");
        if (artifactTypeName != null) {
            BlackboardArtifact.Type type;
            try {
                type = skCase.getBlackboard().getArtifactType(artifactTypeName);
            } catch (TskCoreException ex) {
                return Collections.emptyList(); // unknown type → no results
            }
            where.append(" AND artifacts.artifact_type_id = ").append(type.getTypeID());
        }
        if (dataSourceId >= 0) {
            where.append(" AND artifacts.data_source_obj_id = ").append(dataSourceId);
        }

        // Fetch from the correct sub-table
        List<? extends BlackboardArtifact> artifacts = isAnalysis
                ? skCase.getBlackboard().getAnalysisResultsWhere(where.toString())
                : skCase.getBlackboard().getDataArtifactsWhere(where.toString());

        // Post-filter by attribute and apply limit
        List<Map<String, Object>> result = new ArrayList<>();
        for (BlackboardArtifact artifact : artifacts) {
            if (result.size() >= limit) break;

            List<BlackboardAttribute> attrs = artifact.getAttributes();
            if (attrTypeFilter != null || attrValueFilter != null) {
                boolean matches = false;
                for (BlackboardAttribute attr : attrs) {
                    boolean typeMatch  = attrTypeFilter  == null
                            || attr.getAttributeType().getTypeName().equalsIgnoreCase(attrTypeFilter);
                    boolean valueMatch = attrValueFilter == null
                            || attrValueAsString(attr).toLowerCase().contains(attrValueFilter.toLowerCase());
                    if (typeMatch && valueMatch) { matches = true; break; }
                }
                if (!matches) continue;
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id",           artifact.getArtifactID());
            item.put("sourceFileId", artifact.getObjectID());
            item.put("artifactType", artifact.getArtifactTypeName());
            item.put("dataSourceId", artifact.getDataSourceObjectID());

            if (isAnalysis) {
                Score score = ((AnalysisResult) artifact).getScore();
                item.put("score", Map.of(
                    "significance", score.getSignificance().getName(),
                    "priority",     score.getPriority().getName()
                ));
            }

            List<Map<String, Object>> attrList = new ArrayList<>();
            for (BlackboardAttribute attr : attrs) {
                Map<String, Object> attrMap = new LinkedHashMap<>();
                attrMap.put("type",  attr.getAttributeType().getTypeName());
                attrMap.put("value", attrValueAsObject(attr));
                attrList.add(attrMap);
            }
            item.put("attributes", attrList);
            result.add(item);
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // get_hosts
    // -------------------------------------------------------------------------

    /**
     * Returns all active hosts with their associated data sources.
     * Hosts sit above data sources in the hierarchy: Host → DataSource → VolumeSystem → ...
     */
    List<Map<String, Object>> getHosts() throws TskCoreException {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Host host : skCase.getHostManager().getAllHosts()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", host.getHostId());
            item.put("name", host.getName());

            List<Map<String, Object>> dataSources = new ArrayList<>();
            for (DataSource ds : skCase.getHostManager().getDataSourcesForHost(host)) {
                dataSources.add(buildDataSourceItem((Content) ds));
            }
            item.put("dataSources", dataSources);
            result.add(item);
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // query_data_sources
    // -------------------------------------------------------------------------

    List<Map<String, Object>> queryDataSources() throws TskCoreException {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Content ds : skCase.getDataSources()) {
            result.add(buildDataSourceItem(ds));
        }
        return result;
    }

    /**
     * Builds a flat summary map for a data source. For Image, includes image
     * type, sector size, file paths, and acquisition hashes.
     */
    private Map<String, Object> buildDataSourceItem(Content ds) throws TskCoreException {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", ds.getId());
        item.put("name", ds.getName());
        item.put("type", ds.getClass().getSimpleName());
        item.put("size", ds.getSize());

        if (ds instanceof DataSource) {
            DataSource dataSource = (DataSource) ds;
            item.put("timezone", dataSource.getTimeZone());
            item.put("deviceId", dataSource.getDeviceId());
            // added_date_time is always stored as Java milliseconds via new Date().getTime()
            // (sole write site: SleuthkitCase.java INSERT_DATA_SOURCE_INFO) — divide by 1000 for epoch seconds
            try {
                Long addedMs = dataSource.getDateAdded();
                item.put("addedDate", addedMs != null && addedMs > 0 ? epochToIso(addedMs / 1000) : null);
            } catch (TskCoreException ex) {
                item.put("addedDate", null);
            }
        }

        if (ds instanceof Image) {
            Image image = (Image) ds;
            item.put("imageType", image.getType().name());
            item.put("sectorSize", image.getSsize());
            item.put("paths", List.of(image.getPaths()));
            try { item.put("md5", image.getMd5()); }
            catch (TskCoreException ex) { item.put("md5", null); }
            try { item.put("sha1", image.getSha1()); }
            catch (TskCoreException ex) { item.put("sha1", null); }
            try { item.put("sha256", image.getSha256()); }
            catch (TskCoreException ex) { item.put("sha256", null); }
            try { item.put("acquisitionDetails", image.getAcquisitionDetails()); }
            catch (TskCoreException ex) { item.put("acquisitionDetails", null); }
            try { item.put("acquisitionToolName", image.getAcquisitionToolName()); }
            catch (TskCoreException ex) { item.put("acquisitionToolName", null); }
            try { item.put("acquisitionToolVersion", image.getAcquisitionToolVersion()); }
            catch (TskCoreException ex) { item.put("acquisitionToolVersion", null); }
        }

        return item;
    }

    // -------------------------------------------------------------------------
    // get_data_source_tree
    // -------------------------------------------------------------------------

    /**
     * Returns the full storage hierarchy for one or all data sources.
     * Disk images: Image → VolumeSystem → Volume → FileSystem
     * Logical file sets: data source node only (no storage sub-structure).
     */
    Object getDataSourceTree(JsonNode args) throws TskCoreException {
        long filterDsId = args.path("dataSourceId").isMissingNode() ? -1
                        : args.path("dataSourceId").asLong();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Content ds : skCase.getDataSources()) {
            if (filterDsId >= 0 && ds.getId() != filterDsId) {
                continue;
            }
            result.add(buildDataSourceTreeNode(ds));
        }

        // If a specific ID was requested, unwrap the single result
        if (filterDsId >= 0 && result.size() == 1) {
            return result.get(0);
        }
        return result;
    }

    private Map<String, Object> buildDataSourceTreeNode(Content ds) throws TskCoreException {
        Map<String, Object> node = buildDataSourceItem(ds);

        if (ds instanceof Image) {
            Image image = (Image) ds;
            List<Map<String, Object>> vsNodes = new ArrayList<>();

            for (VolumeSystem vs : image.getVolumeSystems()) {
                vsNodes.add(buildVolumeSystemNode(vs));
            }

            // Images may also have file systems directly (no volume system layer)
            List<Map<String, Object>> directFsNodes = new ArrayList<>();
            for (FileSystem fs : image.getFileSystems()) {
                directFsNodes.add(buildFileSystemNode(fs));
            }

            node.put("volumeSystems", vsNodes);
            // Only include fileSystems at the image level when there is no volume system.
            // When volume systems exist, file systems are already nested inside their volumes.
            node.put("fileSystems", vsNodes.isEmpty() ? directFsNodes : Collections.emptyList());
        } else {
            // LocalFilesDataSource / other — no storage sub-structure
            node.put("volumeSystems", Collections.emptyList());
            node.put("fileSystems", Collections.emptyList());
        }

        return node;
    }

    private Map<String, Object> buildVolumeSystemNode(VolumeSystem vs) throws TskCoreException {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", vs.getId());
        node.put("type", "VolumeSystem");
        node.put("vsType", vs.getType().getName());
        node.put("offset", vs.getOffset());
        node.put("blockSize", vs.getBlockSize());

        List<Map<String, Object>> volNodes = new ArrayList<>();
        for (Volume vol : vs.getVolumes()) {
            volNodes.add(buildVolumeNode(vol));
        }
        node.put("volumes", volNodes);
        return node;
    }

    private Map<String, Object> buildVolumeNode(Volume vol) throws TskCoreException {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", vol.getId());
        node.put("type", "Volume");
        node.put("addr", vol.getAddr());
        node.put("description", vol.getDescription());
        node.put("startSector", vol.getStart());
        node.put("lengthSectors", vol.getLength());
        node.put("size", vol.getSize());
        node.put("flags", vol.getFlagsAsString());

        List<Map<String, Object>> fsNodes = new ArrayList<>();
        for (FileSystem fs : vol.getFileSystems()) {
            fsNodes.add(buildFileSystemNode(fs));
        }
        node.put("fileSystems", fsNodes);
        return node;
    }

    private Map<String, Object> buildFileSystemNode(FileSystem fs) throws TskCoreException {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", fs.getId());
        node.put("type", "FileSystem");
        node.put("fsType", fs.getFsType().getDisplayName());
        node.put("imageOffset", fs.getImageOffset());
        node.put("blockSize", fs.getBlock_size());
        node.put("blockCount", fs.getBlock_count());
        node.put("rootInum", fs.getRoot_inum());
        node.put("firstInum", fs.getFirst_inum());
        node.put("lastInum", fs.getLastInum());
        return node;
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
    // get_file_content
    // -------------------------------------------------------------------------

    Map<String, Object> getFileContent(JsonNode args) throws TskCoreException {
        long fileId  = args.path("fileId").asLong(-1);
        if (fileId < 0) {
            throw new TskCoreException("fileId is required");
        }

        long offset   = args.path("offset").asLong(0);
        int  maxBytes = args.path("maxBytes").asInt(65536);

        // Cap at 1 MB; default to 64 KB
        if (maxBytes <= 0 || maxBytes > 1_048_576) {
            maxBytes = 65536;
        }

        AbstractFile file = skCase.getAbstractFileById(fileId);
        long fileSize = file.getSize();

        if (offset < 0 || offset >= fileSize) {
            offset = 0;
        }

        long available = fileSize - offset;
        int  toRead    = (int) Math.min(maxBytes, available);
        boolean truncated = available > maxBytes;

        byte[] buf = new byte[toRead];
        int bytesRead = file.read(buf, offset, toRead);
        if (bytesRead < toRead) {
            buf = java.util.Arrays.copyOf(buf, Math.max(bytesRead, 0));
        }

        // Decode as UTF-8, replacing undecodable bytes with '?'
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        CharBuffer chars = decoder.decode(ByteBuffer.wrap(buf));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fileId",    fileId);
        result.put("fileName",  file.getName());
        result.put("fileSize",  fileSize);
        result.put("offset",    offset);
        result.put("bytesRead", buf.length);
        result.put("truncated", truncated);
        result.put("content",   chars.toString());
        return result;
    }

    // -------------------------------------------------------------------------
    // query_timeline
    // -------------------------------------------------------------------------

    List<Map<String, Object>> queryTimeline(JsonNode args) throws TskCoreException {
        int limit = args.path("limit").asInt(100);
        if (limit <= 0 || limit > 1000) {
            limit = 100;
        }

        TimelineManager tm = skCase.getTimelineManager();
        long startEpoch = parseTimeArg(args, "startTime", tm.getMinEventTime());
        long endEpoch   = parseTimeArg(args, "endTime",   tm.getMaxEventTime());

        Interval interval = new Interval(startEpoch * 1000L, (endEpoch + 1) * 1000L, DateTimeZone.UTC);
        TimelineFilter.RootFilter filter = buildTimelineFilter(args);

        List<TimelineEvent> events = tm.getEvents(interval, filter);

        List<Map<String, Object>> result = new ArrayList<>(Math.min(events.size(), limit));
        for (TimelineEvent e : events) {
            if (result.size() >= limit) break;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("time",         epochToIso(e.getTime()));
            item.put("eventType",    e.getEventType().getDisplayName());
            item.put("description",  e.getDescription(TimelineLevelOfDetail.HIGH));
            item.put("sourceFileId", e.getContentObjID());
            item.put("artifactId",   e.getArtifactID().orElse(null));
            item.put("dataSourceId", e.getDataSourceObjID());
            item.put("tagged",       e.eventSourceIsTagged());
            item.put("hashHit",      e.eventSourceHasHashHits());
            result.add(item);
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // summarize_timeline
    // -------------------------------------------------------------------------

    Map<String, Object> summarizeTimeline(JsonNode args) throws TskCoreException {
        TimelineManager tm = skCase.getTimelineManager();
        long startEpoch = parseTimeArg(args, "startTime", tm.getMinEventTime());
        long endEpoch   = parseTimeArg(args, "endTime",   tm.getMaxEventTime());

        TimelineFilter.RootFilter filter = buildTimelineFilter(args);

        Map<TimelineEventType, Long> counts = tm.countEventsByType(
                startEpoch, endEpoch, filter, TimelineEventType.HierarchyLevel.CATEGORY);

        long total = 0;
        Map<String, Long> byCategory = new LinkedHashMap<>();
        for (Map.Entry<TimelineEventType, Long> entry : counts.entrySet()) {
            byCategory.put(entry.getKey().getDisplayName(), entry.getValue());
            total += entry.getValue();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("startTime",   epochToIso(startEpoch));
        result.put("endTime",     epochToIso(endEpoch));
        result.put("totalEvents", total);
        result.put("byCategory",  byCategory);
        return result;
    }

    // -------------------------------------------------------------------------
    // get_os_accounts
    // -------------------------------------------------------------------------

    List<Map<String, Object>> getOsAccounts() throws TskCoreException {
        List<Map<String, Object>> result = new ArrayList<>();
        OsAccountManager oam = skCase.getOsAccountManager();
        for (OsAccount account : oam.getOsAccounts()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id",        account.getId());
            item.put("addr",      account.getAddr().orElse(null));        // SID or UID
            item.put("loginName", account.getLoginName().orElse(null));
            item.put("fullName",  account.getFullName().orElse(null));
            item.put("type",      account.getOsAccountType().map(t -> t.getName()).orElse(null));
            item.put("status",    account.getOsAccountStatus().map(s -> s.getName()).orElse(null));
            Long createdEpoch = account.getCreationTime().orElse(null);
            item.put("creationTime", createdEpoch != null && createdEpoch > 0
                    ? epochToIso(createdEpoch) : null);

            // Extended attributes: home dir, login script, last login, etc.
            List<Map<String, String>> extAttrs = new ArrayList<>();
            for (OsAccount.OsAccountAttribute attr : account.getExtendedOsAccountAttributes()) {
                Map<String, String> a = new LinkedHashMap<>();
                a.put("type",  attr.getAttributeType().getTypeName());
                a.put("value", attr.getDisplayString());
                extAttrs.add(a);
            }
            item.put("extendedAttributes", extAttrs);

            // Which data sources this account appeared on
            List<Map<String, Object>> instances = new ArrayList<>();
            for (OsAccountInstance inst : account.getOsAccountInstances()) {
                Map<String, Object> instMap = new LinkedHashMap<>();
                instMap.put("dataSourceId",  inst.getDataSource().getId());
                instMap.put("instanceType",  inst.getInstanceType().getName());
                instances.add(instMap);
            }
            item.put("instances", instances);
            result.add(item);
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // get_communications_accounts
    // -------------------------------------------------------------------------

    List<Map<String, Object>> getCommunicationsAccounts(JsonNode args) throws TskCoreException {
        int limit = args.path("limit").asInt(100);
        if (limit <= 0 || limit > 500) limit = 100;

        String accountTypeStr = textOrNull(args, "accountType");

        CommunicationsFilter filter = new CommunicationsFilter();
        if (accountTypeStr != null) {
            Account.Type type = lookupAccountType(accountTypeStr);
            if (type != null) {
                filter.addAndFilter(new CommunicationsFilter.AccountTypeFilter(
                        Collections.singleton(type)));
            }
        }

        CommunicationsManager cm = skCase.getCommunicationsManager();
        List<AccountDeviceInstance> adis = cm.getAccountDeviceInstancesWithRelationships(filter);

        List<Map<String, Object>> result = new ArrayList<>();
        for (AccountDeviceInstance adi : adis) {
            if (result.size() >= limit) break;
            Map<String, Object> item = new LinkedHashMap<>();
            Account account = adi.getAccount();
            item.put("accountType",        account.getAccountType().getDisplayName());
            item.put("accountId",          account.getTypeSpecificID());
            item.put("deviceId",           adi.getDeviceId());
            item.put("relationshipCount",  cm.getRelationshipSourcesCount(adi, new CommunicationsFilter()));
            result.add(item);
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // get_account_relationships
    // -------------------------------------------------------------------------

    Map<String, Object> getAccountRelationships(JsonNode args) throws TskCoreException {
        String accountTypeStr = textOrNull(args, "accountType");
        String accountId      = textOrNull(args, "accountId");

        CommunicationsManager cm = skCase.getCommunicationsManager();
        CommunicationsFilter filter = new CommunicationsFilter();

        // Find the target AccountDeviceInstance by account ID (and optionally type)
        AccountDeviceInstance targetAdi = null;
        for (AccountDeviceInstance adi : cm.getAccountDeviceInstancesWithRelationships(filter)) {
            Account account = adi.getAccount();
            if (accountId != null && !account.getTypeSpecificID().equalsIgnoreCase(accountId)) {
                continue;
            }
            if (accountTypeStr != null && !account.getAccountType().getTypeName()
                    .equalsIgnoreCase(accountTypeStr)) {
                continue;
            }
            targetAdi = adi;
            break;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        if (targetAdi == null) {
            result.put("error", "Account not found: " + accountId);
            return result;
        }

        // Get all accounts that communicated with this one
        List<AccountDeviceInstance> related = cm.getRelatedAccountDeviceInstances(targetAdi, filter);

        List<Map<String, Object>> relationships = new ArrayList<>();
        for (AccountDeviceInstance relAdi : related) {
            Map<String, Object> item = new LinkedHashMap<>();
            Account relAccount = relAdi.getAccount();
            item.put("accountType",       relAccount.getAccountType().getDisplayName());
            item.put("accountId",         relAccount.getTypeSpecificID());
            item.put("deviceId",          relAdi.getDeviceId());
            item.put("relationshipCount", cm.getRelationshipSourcesCount(relAdi, filter));
            relationships.add(item);
        }

        result.put("account",            targetAdi.getAccount().getTypeSpecificID());
        result.put("accountType",        targetAdi.getAccount().getAccountType().getDisplayName());
        result.put("deviceId",           targetAdi.getDeviceId());
        result.put("totalRelationships", cm.getRelationshipSourcesCount(targetAdi, filter));
        result.put("relatedAccounts",    relationships);
        return result;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Builds a RootFilter for timeline queries. Null sub-filters are stripped
     * by RootFilter itself, so passing null means "no restriction".
     */
    private TimelineFilter.RootFilter buildTimelineFilter(JsonNode args) throws TskCoreException {
        // DataSource filter (optional)
        TimelineFilter.DataSourcesFilter dsFilter = null;
        if (!args.path("dataSourceId").isMissingNode()) {
            long dsId = args.path("dataSourceId").asLong();
            dsFilter = new TimelineFilter.DataSourcesFilter();
            dsFilter.addSubFilter(new TimelineFilter.DataSourceFilter("", dsId));
        }

        // Text/description filter (optional)
        String text = textOrNull(args, "textFilter");
        TimelineFilter.TextFilter textFilter = (text != null)
                ? new TimelineFilter.TextFilter(text) : null;

        // Include all event types
        TimelineFilter.EventTypeFilter typeFilter =
                new TimelineFilter.EventTypeFilter(TimelineEventType.ROOT_EVENT_TYPE);

        return new TimelineFilter.RootFilter(
                null,                        // knownFilesFilter  — null = include known files too
                null,                        // tagsFilter
                null,                        // hashSetHitsFilter
                textFilter,
                typeFilter,
                dsFilter,
                null,                        // fileTypesFilter
                Collections.emptyList()
        );
    }

    /**
     * Parses an ISO 8601 timestamp argument, returning the fallback epoch value if absent.
     */
    private static long parseTimeArg(JsonNode args, String field, Long fallback) {
        String s = textOrNull(args, field);
        if (s == null) {
            return fallback != null ? fallback : 0L;
        }
        // Accept "2012-03-02" (date-only) by appending T00:00:00Z
        if (s.length() == 10) {
            s = s + "T00:00:00Z";
        }
        return Instant.parse(s).getEpochSecond();
    }

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
     * Maps an account type name string to the corresponding Account.Type constant.
     */
    private static Account.Type lookupAccountType(String name) {
        switch (name.toUpperCase()) {
            case "EMAIL":            return Account.Type.EMAIL;
            case "PHONE":            return Account.Type.PHONE;
            case "SKYPE":            return Account.Type.SKYPE;
            case "FACEBOOK":         return Account.Type.FACEBOOK;
            case "WHATSAPP":         return Account.Type.WHATSAPP;
            case "TWITTER":          return Account.Type.TWITTER;
            case "INSTAGRAM":        return Account.Type.INSTAGRAM;
            case "MESSAGING_APP":    return Account.Type.MESSAGING_APP;
            case "WEBSITE_ACCOUNT":  return Account.Type.WEBSITE_ACCOUNT;
            case "DEVICE":           return Account.Type.DEVICE;
            default:                 return null;
        }
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
