/*
 * Autopsy Forensic Browser
 *
 * Copyright 2026 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datamodel;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.TimeZoneUtils;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Data model item for the "Addl. Cyber Triage Data" top-level tree node.
 * Visible only when the current case database contains Cyber Triage tables
 * (detected by the presence of the ct_errors table).
 */
public class CyberTriageData implements AutopsyVisitableItem {

    private final SleuthkitCase skCase;

    public CyberTriageData(SleuthkitCase skCase) {
        this.skCase = skCase;
    }

    public SleuthkitCase getSleuthkitCase() {
        return skCase;
    }

    @Override
    public <T> T accept(AutopsyItemVisitor<T> visitor) {
        return visitor.visit(this);
    }

    /**
     * The type name of the Cyber Triage custom JSON attribute that carries
     * per-artifact extended data.
     */
    public static final String CT_JSON_ATTRIBUTE_TYPE_NAME = "CT_JSON_DATA_ATTRIBUTE";

    /**
     * Returns true if the given database contains Cyber Triage-specific tables.
     * Uses the presence of ct_errors as the detection heuristic.
     */
    public static boolean isCyberTriageDatabase(SleuthkitCase skCase) {
        try (SleuthkitCase.CaseDbQuery dbQuery = skCase.executeQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='ct_errors'")) {
            return dbQuery.getResultSet().next();
        } catch (TskCoreException | SQLException ex) {
            return false;
        }
    }

    /**
     * Parses the CT_JSON_DATA_ATTRIBUTE JSON string and inserts each top-level
     * field into {@code map} with a "CT " prefix on the key name. Nested
     * objects and arrays are stored as their string representation. Null JSON
     * values are skipped.
     *
     * @param map  The property map to populate (same map used by
     *             BlackboardArtifactNode.fillPropertyMap).
     * @param json The raw JSON string from the attribute.
     */
    public static void addCtJsonProperties(Map<String, Object> map, String json) {
        if (json == null || json.isEmpty()) {
            return;
        }
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                JsonElement value = entry.getValue();
                if (value.isJsonNull()) {
                    continue;
                }
                String key = "CT " + entry.getKey();
                if (value.isJsonPrimitive()) {
                    JsonPrimitive primitive = value.getAsJsonPrimitive();
                    if (primitive.isNumber()) {
                        String lowerName = entry.getKey().toLowerCase();
                        if (lowerName.contains("date") || lowerName.contains("time")) {
                            long numVal = primitive.getAsLong();
                            // CT stores some timestamps in milliseconds; values > 10^10
                            // are ms and must be converted to seconds for TimeZoneUtils.
                            long seconds = (numVal > 10_000_000_000L) ? numVal / 1000 : numVal;
                            map.put(key, TimeZoneUtils.getFormattedTime(seconds));
                        } else {
                            map.put(key, primitive.getAsNumber());
                        }
                    } else if (primitive.isBoolean()) {
                        map.put(key, primitive.getAsBoolean());
                    } else {
                        map.put(key, primitive.getAsString());
                    }
                } else {
                    // Nested object or array — store as string
                    map.put(key, value.toString());
                }
            }
        } catch (JsonParseException | IllegalStateException ex) {
            Logger.getLogger(CyberTriageData.class.getName())
                    .log(Level.WARNING, "Failed to parse CT_JSON_DATA_ATTRIBUTE value", ex);
        }
    }

    // -------------------------------------------------------------------------
    // Root node
    // -------------------------------------------------------------------------

    /**
     * The "Addl. Cyber Triage Data" root node shown in the directory tree.
     */
    public static class RootNode extends DisplayableItemNode {

        private static final String DISPLAY_NAME = "Addl. Cyber Triage Data";
        private static final String ICON_PATH = "org/sleuthkit/autopsy/images/extracted_content.png";

        public RootNode(SleuthkitCase skCase) {
            super(Children.create(new RootChildFactory(skCase), true),
                    Lookups.singleton(DISPLAY_NAME));
            setName(DISPLAY_NAME);
            setDisplayName(DISPLAY_NAME);
            setIconBaseWithExtension(ICON_PATH);
        }

        @Override
        public boolean isLeafTypeNode() {
            return false;
        }

        @Override
        public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
            return visitor.visit(this);
        }

        @Override
        public String getItemType() {
            return getClass().getName();
        }
    }

    /**
     * Factory for the children of RootNode. Each child represents a category
     * of Cyber Triage-specific data (currently: Errors).
     */
    private static class RootChildFactory extends ChildFactory<String> {

        private final SleuthkitCase skCase;

        RootChildFactory(SleuthkitCase skCase) {
            this.skCase = skCase;
        }

        @Override
        protected boolean createKeys(List<String> list) {
            list.add("ERRORS");
            return true;
        }

        @Override
        protected Node createNodeForKey(String key) {
            if ("ERRORS".equals(key)) {
                return new ErrorsNode(skCase);
            }
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Errors node
    // -------------------------------------------------------------------------

    /**
     * A node representing the collection of rows from ct_errors.
     */
    public static class ErrorsNode extends DisplayableItemNode {

        private static final String DISPLAY_NAME = "Errors";
        private static final String ICON_PATH = "org/sleuthkit/autopsy/images/error-icon-16.png";

        public ErrorsNode(SleuthkitCase skCase) {
            super(Children.create(new ErrorsChildFactory(skCase), true),
                    Lookups.singleton(DISPLAY_NAME));
            setName(DISPLAY_NAME);
            setDisplayName(DISPLAY_NAME);
            setIconBaseWithExtension(ICON_PATH);
        }

        @Override
        public boolean isLeafTypeNode() {
            return false;
        }

        @Override
        public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
            return visitor.visit(this);
        }

        @Override
        public String getItemType() {
            return getClass().getName();
        }
    }

    // -------------------------------------------------------------------------
    // Error data and leaf nodes
    // -------------------------------------------------------------------------

    /**
     * Represents a single row from the ct_errors table.
     */
    public static class CtError {

        public final long id;
        public final String title;
        public final String description;
        public final String stackTrace;
        public final long timestamp;
        public final String severity;

        CtError(long id, String title, String description,
                String stackTrace, long timestamp, String severity) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.stackTrace = stackTrace;
            this.timestamp = timestamp;
            this.severity = severity;
        }
    }

    /**
     * Queries ct_errors and produces one CtError key per row.
     */
    private static class ErrorsChildFactory extends ChildFactory<CtError> {

        private static final Logger logger = Logger.getLogger(ErrorsChildFactory.class.getName());
        private final SleuthkitCase skCase;

        ErrorsChildFactory(SleuthkitCase skCase) {
            this.skCase = skCase;
        }

        @Override
        protected boolean createKeys(List<CtError> list) {
            String query = "SELECT id, title, description, stack_trace, time_stamp, severity "
                    + "FROM ct_errors ORDER BY time_stamp DESC";
            try (SleuthkitCase.CaseDbQuery dbQuery = skCase.executeQuery(query)) {
                ResultSet rs = dbQuery.getResultSet();
                while (rs.next()) {
                    list.add(new CtError(
                            rs.getLong("id"),
                            rs.getString("title"),
                            rs.getString("description"),
                            rs.getString("stack_trace"),
                            rs.getLong("time_stamp"),
                            rs.getString("severity")));
                }
            } catch (TskCoreException | SQLException ex) {
                logger.log(Level.WARNING, "Failed to query ct_errors table", ex);
            }
            return true;
        }

        @Override
        protected Node createNodeForKey(CtError error) {
            return new ErrorNode(error);
        }
    }

    /**
     * A leaf node representing a single ct_errors row.
     */
    public static class ErrorNode extends DisplayableItemNode {

        private static final String ICON_PATH = "org/sleuthkit/autopsy/images/warning-icon-16.png";
        private static final DateTimeFormatter DATE_FORMAT
                = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

        private final CtError error;

        ErrorNode(CtError error) {
            super(Children.LEAF, Lookups.singleton(error));
            this.error = error;
            setName(Long.toString(error.id));
            setDisplayName(error.title);
            setIconBaseWithExtension(ICON_PATH);
        }

        @Override
        public boolean isLeafTypeNode() {
            return true;
        }

        @Override
        public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
            return visitor.visit(this);
        }

        @Override
        public String getItemType() {
            return getClass().getName();
        }

        @Override
        protected Sheet createSheet() {
            Sheet sheet = super.createSheet();
            Sheet.Set props = sheet.get(Sheet.PROPERTIES);
            if (props == null) {
                props = Sheet.createPropertiesSet();
                sheet.put(props);
            }
            props.put(new NodeProperty<>("Severity", "Severity", "Severity of the error",
                    error.severity != null ? error.severity : ""));
            props.put(new NodeProperty<>("Title", "Title", "Error title",
                    error.title != null ? error.title : ""));
            props.put(new NodeProperty<>("Description", "Description", "Error description",
                    error.description != null ? error.description : ""));
            props.put(new NodeProperty<>("Timestamp", "Timestamp", "When the error occurred",
                    DATE_FORMAT.format(Instant.ofEpochMilli(error.timestamp))));
            props.put(new NodeProperty<>("StackTrace", "Stack Trace", "Error stack trace",
                    error.stackTrace != null ? error.stackTrace : ""));
            return sheet;
        }
    }
}
