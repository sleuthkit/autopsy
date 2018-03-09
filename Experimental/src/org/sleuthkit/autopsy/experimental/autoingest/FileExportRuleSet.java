/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.autoingest;

import java.io.Serializable;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;
import javax.annotation.concurrent.Immutable;
import org.apache.commons.codec.DecoderException;
import org.joda.time.DateTime;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.apache.commons.codec.binary.Hex;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;

/**
 * Uniquely named file export rules organized into uniquely named rule sets.
 */
final class FileExportRuleSet implements Serializable, Comparable<FileExportRuleSet> {

    private static final long serialVersionUID = 1L;
    private String name;
    private final TreeMap<String, Rule> rules;

    /**
     * Constructs an empty named set of uniquely named rules.
     *
     * @param name The name of the set.
     */
    FileExportRuleSet(String name) {
        this.name = name;
        rules = new TreeMap<>();
    }

    /**
     * Gets the name of the rule set.
     *
     * @return The rules set name.
     */
    String getName() {
        return name;
    }

    /**
     * Sets the name of the rule set.
     *
     * @param setName The name of the rule set
     */
    public void setName(String setName) {
        this.name = setName;
    }

    /**
     * Gets the uniquely named rules in the rule set.
     *
     * @return A map of rules with name keys, sorted by name.
     */
    NavigableMap<String, Rule> getRules() {
        return Collections.unmodifiableNavigableMap(rules);
    }

    /**
     * Gets a rule by name.
     *
     * @return A rule if found, null otherwise.
     */
    Rule getRule(String ruleName) {
        return rules.get(ruleName);
    }

    /**
     * Adds a rule to this set. If there is a rule in the set with the same
     * name, the existing rule is replaced by the new rule.
     *
     * @param rule The rule to be added to the set.
     */
    void addRule(Rule rule) {
        this.rules.put(rule.getName(), rule);
    }

    /**
     * Removes a rule from a set, if it is present.
     *
     * @param rule The rule to be removed from the set.
     */
    void removeRule(Rule rule) {
        this.rules.remove(rule.getName());
    }

    /**
     * Removes a rule from a set, if it is present.
     *
     * @param ruleName The rule to be removed from the set.
     */
    void removeRule(String ruleName) {
        this.rules.remove(ruleName);
    }

    /**
     * @inheritDoc
     */
    @Override
    public boolean equals(Object that) {
        if (this == that) {
            return true;
        } else if (!(that instanceof FileExportRuleSet)) {
            return false;
        } else {
            FileExportRuleSet thatSet = (FileExportRuleSet) that;
            return this.name.equals(thatSet.getName());
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public int hashCode() {
        return this.name.hashCode();
    }

    /**
     * @inheritDoc
     */
    @Override
    public int compareTo(FileExportRuleSet that) {
        return this.name.compareTo(that.getName());
    }

    /**
     * A named file export rule consisting of zero to many conditions.
     */
    static final class Rule implements Serializable, Comparable<Rule> {

        private static final long serialVersionUID = 1L;
        private final String name;
        private FileMIMETypeCondition fileTypeCondition;
        private final List<FileSizeCondition> fileSizeConditions;
        private final List<ArtifactCondition> artifactConditions;

        /**
         * Constructs a named file export rule consisting of zero to many
         * conditions.
         *
         * @param name The name of the rule.
         */
        Rule(String name) {
            this.name = name;
            this.fileSizeConditions = new ArrayList<>();
            this.artifactConditions = new ArrayList<>();
        }

        /**
         * Gets the name of the rule.
         *
         * @return The rule name.
         */
        String getName() {
            return this.name;
        }

        /**
         * Adds a file MIME type condition to the rule. If the rule already has
         * a file MIME type condition, the existing condition is replaced by the
         * new condition.
         *
         * @param condition The new file MIME type condition.
         */
        void addFileMIMETypeCondition(FileMIMETypeCondition condition) {
            this.fileTypeCondition = condition;
        }

        /**
         * Removes a file MIME type condition from the rule.
         *
         * @param condition The new file MIME type condition.
         */
        void removeFileMIMETypeCondition() {
            this.fileTypeCondition = null;
        }

        /**
         * Gets the file MIME type condition of a rule.
         *
         * @return The file MIME type condition, possibly null.
         */
        FileMIMETypeCondition getFileMIMETypeCondition() {
            return this.fileTypeCondition;
        }

        /**
         * Adds a file size condition to the rule. If the rule already has a
         * file size or file size range condition, the existing condition is
         * replaced by the new condition.
         *
         * A rule may have either a file size condition or a file size range
         * condition, but not both.
         *
         * @param condition The new file size condition.
         */
        void addFileSizeCondition(FileSizeCondition condition) {
            this.fileSizeConditions.clear();
            this.fileSizeConditions.add(condition);
        }

        /**
         * Removes a file size condition from the rule A rule may have either a
         * file size condition or a file size range condition, but not both.
         *
         */
        void removeFileSizeCondition() {
            this.fileSizeConditions.clear();
        }

        /**
         * Adds a file size range condition to the rule. If the rule already has
         * a file size or file size range condition, the existing condition is
         * replaced by the new condition.
         *
         * The file size conditions that make up the file size range condition
         * are not validated.
         *
         * A rule may have either a file size condition or a file size range
         * condtion, but not both.
         *
         * @param conditionOne One part of the new size range condition.
         * @param conditionTwo The other part of the new size range conditon.
         */
        void addFileSizeRangeCondition(FileSizeCondition conditionOne, FileSizeCondition conditionTwo) {
            this.fileSizeConditions.clear();
            this.fileSizeConditions.add(conditionOne);
            this.fileSizeConditions.add(conditionTwo);
        }

        /**
         * Gets the file size conditions of a rule.
         *
         * @return A list of zero to two file size conditions.
         */
        List<FileSizeCondition> getFileSizeConditions() {
            return Collections.unmodifiableList(this.fileSizeConditions);
        }

        /**
         * Adds a condition that requires a file to have an artifact of a given
         * type with an attribute of a given type with a value comparable to a
         * specified value.
         *
         * @param condition The new artifact condition.
         */
        void addArtfactCondition(ArtifactCondition condition) {
            for (ArtifactCondition ac : artifactConditions) {
                if (ac.equals(condition)) {
                    // already exists, do not re-add
                    return;
                }
            }
            this.artifactConditions.add(condition);
        }

        /**
         * Removes a condition that requires a file to have an artifact of a
         * given type with an attribute of a given type with a value comparable
         * to a specified value.
         *
         * @param condition The new artifact condition.
         */
        void removeArtifactCondition(ArtifactCondition condition) {
            this.artifactConditions.remove(condition);
        }

        /**
         * Removes all artifact condition that requires a file to have an
         * artifact of a given type with an attribute of a given type with a
         * value comparable to a specified value.
         *
         */
        void removeArtifactConditions() {
            this.artifactConditions.clear();
        }

        /**
         * Gets the artifact conditions of a rule.
         *
         * @return A list of artifact conditions, possibly empty.
         */
        List<ArtifactCondition> getArtifactConditions() {
            return Collections.unmodifiableList(this.artifactConditions);
        }

        /**
         * @inheritDoc
         */
        @Override
        public boolean equals(Object that) {
            if (this == that) {
                return true;
            } else if (!(that instanceof Rule)) {
                return false;
            } else {
                Rule thatRule = (Rule) that;
                return this.name.equals(thatRule.getName())
                        && conditionsAreEqual(thatRule);
            }
        }

        boolean conditionsAreEqual(Rule that) {
            if (!Objects.equals(this.fileTypeCondition, that.getFileMIMETypeCondition())) {
                return false;
            }
            this.fileSizeConditions.sort(null);
            that.fileSizeConditions.sort(null);
            if (!this.fileSizeConditions.equals(that.getFileSizeConditions())) {
                return false;
            }
            this.artifactConditions.sort(null);
            that.artifactConditions.sort(null);
            return this.artifactConditions.equals(that.getArtifactConditions());
        }

        /**
         * @inheritDoc
         */
        @Override
        public int hashCode() {
            return this.name.hashCode();
        }

        /**
         * @inheritDoc
         */
        @Override
        public int compareTo(Rule that) {
            return this.name.compareTo(that.getName());
        }

        /**
         * Evaluates a rule to determine if there are any files that satisfy the
         * rule.
         *
         * @param dataSourceId The data source id of the files.
         *
         * @return A list of file ids, possibly empty.
         *
         * @throws
         * org.sleuthkit.autopsy.autoingest.fileexporter.ExportRuleSet.ExportRulesException
         */
        List<Long> evaluate(long dataSourceId) throws ExportRulesException {
            try {
                SleuthkitCase db = Case.getOpenCase().getSleuthkitCase();
                try (SleuthkitCase.CaseDbQuery queryResult = db.executeQuery(getQuery(dataSourceId))) {
                    ResultSet resultSet = queryResult.getResultSet();
                    List<Long> fileIds = new ArrayList<>();
                    while (resultSet.next()) {
                        fileIds.add(resultSet.getLong("obj_id"));
                    }
                    return fileIds;
                }
            } catch (NoCurrentCaseException ex) {
                throw new ExportRulesException("No current case", ex);
            } catch (TskCoreException ex) {
                throw new ExportRulesException("Error querying case database", ex);
            } catch (SQLException ex) {
                throw new ExportRulesException("Error processing result set", ex);
            }
        }

        /**
         * Gets an SQL query statement that returns the object ids (column name
         * is files.obj_id) of the files that satisfy the rule.
         *
         * @param dataSourceId The data source id of the files.
         *
         * @return The SQL query.
         *
         * @throws ExportRulesException If the artifact type or attribute type
         *                              for a condition does not exist.
         */
        private String getQuery(long dataSourceId) throws ExportRulesException {
            String query = "SELECT DISTINCT files.obj_id FROM tsk_files AS files";
            if (!this.artifactConditions.isEmpty()) {
                for (int i = 0; i < this.artifactConditions.size(); ++i) {
                    query += String.format(", blackboard_artifacts AS arts%d, blackboard_attributes AS attrs%d", i, i);
                }
            }
            query += (" WHERE meta_type=1 AND mime_type IS NOT NULL AND md5 IS NOT NULL AND files.data_source_obj_id = " + dataSourceId);

            List<String> conditions = this.getConditionClauses();
            if (!conditions.isEmpty()) {
                for (int i = 0; i < conditions.size(); ++i) {
                    query += " AND " + conditions.get(i);
                }
            }
            return query;
        }

        /**
         * Gets the SQL condition clauses for all the conditions.
         *
         * @return A collection of SQL condition clauses.
         *
         * @throws ExportRulesException If the artifact type or attribute type
         *                              for a condition does not exist.
         */
        private List<String> getConditionClauses() throws ExportRulesException {
            List<String> conditions = new ArrayList<>();
            if (null != this.fileTypeCondition) {
                conditions.add(fileTypeCondition.getConditionClause());
            }
            if (!this.fileSizeConditions.isEmpty()) {
                for (FileSizeCondition condition : this.fileSizeConditions) {
                    conditions.add(condition.getConditionClause());
                }
            }
            if (!this.artifactConditions.isEmpty()) {
                for (int i = 0; i < this.artifactConditions.size(); ++i) {
                    conditions.add(this.artifactConditions.get(i).getConditionClause(i));
                }
            }
            return conditions;
        }

        /**
         * Relational operators that can be used to define rule conditions.
         */
        enum RelationalOp {

            Equals("="),
            LessThanEquals("<="),
            LessThan("<"),
            GreaterThanEquals(">="),
            GreaterThan(">"),
            NotEquals("!=");

            private String symbol;
            private static final Map<String, RelationalOp> symbolToEnum = new HashMap<>();

            static {
                for (RelationalOp op : RelationalOp.values()) {
                    symbolToEnum.put(op.getSymbol(), op);
                }
            }

            /**
             * Constructs a relational operator enum member that can are used to
             * define rule conditions.
             *
             * @param symbol The symbolic form of the operator.
             */
            private RelationalOp(String symbol) {
                this.symbol = symbol;
            }

            /**
             * Gets the symbolic form of the operator.
             *
             * @return The operator symbol.
             */
            String getSymbol() {
                return this.symbol;
            }

            /**
             * Looks up the relational operator with a given symbol.
             *
             * @return The relational operator or null if there is no operator
             *         for the symbol.
             */
            static RelationalOp fromSymbol(String symbol) {
                return symbolToEnum.get(symbol);
            }

        }

        /**
         * A condition that requires a file to be of a specified MIME type.
         */
        @Immutable
        static final class FileMIMETypeCondition implements Serializable, Comparable<FileMIMETypeCondition> {

            private static final long serialVersionUID = 1L;
            private final String mimeType;
            private final RelationalOp operator;

            /**
             * Constructs a condition that requires a file to be of a specified
             * MIME type.
             *
             * @param mimeType The MIME type.
             */
            FileMIMETypeCondition(String mimeType, RelationalOp operator) {
                this.mimeType = mimeType;
                this.operator = operator;
            }

            /**
             * Gets the MIME type required by the condition.
             *
             * @return The MIME type.
             */
            String getMIMEType() {
                return mimeType;
            }

            /**
             * Gets the operator required by the condition.
             *
             * @return the operator.
             */
            public RelationalOp getRelationalOp() {
                return operator;
            }

            /**
             * @inheritDoc
             */
            @Override
            public boolean equals(Object that) {
                if (this == that) {
                    return true;
                } else if (!(that instanceof FileMIMETypeCondition)) {
                    return false;
                } else {
                    FileMIMETypeCondition thatCondition = (FileMIMETypeCondition) that;
                    return ((this.mimeType.equals(thatCondition.getMIMEType()))
                            && (this.operator == thatCondition.getRelationalOp()));
                }
            }

            /**
             * @inheritDoc
             */
            @Override
            public int hashCode() {
                return this.mimeType.hashCode();
            }

            @Override
            public int compareTo(FileMIMETypeCondition that) {
                return this.mimeType.compareTo(that.getMIMEType());
            }

            /**
             * Gets an SQL condition clause for the condition.
             *
             * @return The SQL condition clause.
             */
            private String getConditionClause() {
                return String.format("files.mime_type = '%s'", this.mimeType);
            }

        }

        /**
         * A condition that requires a file to have a size in bytes comparable
         * to a specified size.
         */
        @Immutable
        static final class FileSizeCondition implements Serializable, Comparable<FileSizeCondition> {

            private static final long serialVersionUID = 1L;
            private final int size;
            private final SizeUnit unit;
            private final Rule.RelationalOp op;

            /**
             * Constructs a condition that requires a file to have a size in
             * bytes comparable to a specified size.
             *
             * @param sizeinBytes The specified size.
             * @param op          The relational operator for the comparison.
             */
            FileSizeCondition(int size, SizeUnit unit, Rule.RelationalOp op) {
                this.size = size;
                this.unit = unit;
                this.op = op;
            }

            /**
             * Gets the size required by the condition.
             *
             * @return The size.
             */
            int getSize() {
                return size;
            }

            /**
             * Gets the size unit for the size required by the condition.
             *
             * @return The size unit.
             */
            SizeUnit getUnit() {
                return unit;
            }

            /**
             * Gets the relational operator for the condition.
             *
             * @return The operator.
             */
            RelationalOp getRelationalOperator() {
                return this.op;
            }

            /**
             * @inheritDoc
             */
            @Override
            public boolean equals(Object that) {
                if (this == that) {
                    return true;
                } else if (!(that instanceof FileSizeCondition)) {
                    return false;
                } else {
                    FileSizeCondition thatCondition = (FileSizeCondition) that;
                    return this.size == thatCondition.getSize()
                            && this.unit == thatCondition.getUnit()
                            && this.op == thatCondition.getRelationalOperator();
                }
            }

            /**
             * @inheritDoc
             */
            @Override
            public int hashCode() {
                int hash = 7;
                hash = 9 * hash + this.size;
                hash = 11 * hash + this.unit.hashCode();
                hash = 13 * hash + this.op.hashCode();
                return hash;
            }

            @Override
            public int compareTo(FileSizeCondition that) {
                int retVal = this.unit.compareTo(that.getUnit());
                if (0 != retVal) {
                    return retVal;
                }
                retVal = new Long(this.size).compareTo(new Long(that.getSize()));
                if (0 != retVal) {
                    return retVal;
                }
                return this.op.compareTo(that.getRelationalOperator());
            }

            /**
             * Gets an SQL condition clause for the condition.
             *
             * @return The SQL condition clause.
             */
            private String getConditionClause() {
                return String.format("files.size %s %d", op.getSymbol(), size * unit.getMultiplier());
            }

            /**
             * Size units used to define file size conditions.
             */
            enum SizeUnit {

                Bytes(1L),
                Kilobytes(1024L),
                Megabytes(1024L * 1024),
                Gigabytes(1024L * 1024 * 1024),
                Terabytes(1024L * 1024 * 1024 * 1024),
                Petabytes(1024L * 1024 * 1024 * 1024 * 1024);
                private final long multiplier;

                /**
                 * Constructs a member of this enum.
                 *
                 * @param multiplier A multiplier for the size field of a file
                 *                   size condition.
                 */
                private SizeUnit(long multiplier) {
                    this.multiplier = multiplier;
                }

                /**
                 * Gets the multiplier for the size field of a file size
                 * condition.
                 *
                 * @return The multiplier.
                 */
                long getMultiplier() {
                    return this.multiplier;
                }
            }
        }

        /**
         * A condition that requires a file to have an artifact of a given type
         * with an attribute of a given type with a value comparable to a
         * specified value.
         */
        @Immutable
        static final class ArtifactCondition implements Serializable, Comparable<ArtifactCondition> {

            private static final long serialVersionUID = 1L;
            private final String artifactTypeName;
            private final String attributeTypeName;
            private final BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE attributeValueType;
            private Integer intValue;
            private Long longValue;
            private Double doubleValue;
            private String stringValue;
            private DateTime dateTimeValue;
            private byte[] byteValue;
            private final RelationalOp op;
            private String treeDisplayName;

            /**
             * Constructs a condition that requires a file to have an artifact
             * of a given type.
             *
             * @param treeDisplayName    The name to display in the tree
             * @param artifactTypeName   The name of the artifact type.
             * @param attributeTypeName  The name of the attribute type.
             * @param value              The String representation of the value.
             * @param attributeValueType The type of the value being passed in.
             * @param op                 The relational operator for the
             *                           comparison.
             */
            ArtifactCondition(String artifactTypeName, String attributeTypeName, String value,
                    BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE attributeValueType, RelationalOp op) throws IllegalArgumentException {
                this.artifactTypeName = artifactTypeName;
                this.attributeTypeName = attributeTypeName;
                this.attributeValueType = attributeValueType;
                this.treeDisplayName = artifactTypeName;
                this.intValue = null;
                this.longValue = null;
                this.doubleValue = null;
                this.stringValue = null;
                this.byteValue = null;
                this.op = op;
                try {
                    switch (this.attributeValueType) {
                        case STRING:
                            this.stringValue = value;
                            break;
                        case INTEGER:
                            this.intValue = Integer.parseInt(value);
                            break;
                        case LONG:
                            this.longValue = Long.parseLong(value);
                            break;
                        case DOUBLE:
                            this.doubleValue = Double.parseDouble(value);
                            break;
                        case BYTE:
                            try {
                                this.byteValue = Hex.decodeHex(value.toCharArray());
                            } catch (DecoderException ex) {
                                this.byteValue = null;
                                throw new IllegalArgumentException("Bad hex decode"); //NON-NLS
                            }
                            break;
                        case DATETIME:
                            long result = Long.parseLong(value);
                            this.dateTimeValue = new DateTime(result);
                            break;
                        default:
                            throw new NumberFormatException("Bad type chosen"); //NON-NLS
                    }
                } catch (NumberFormatException ex) {
                    this.intValue = null;
                    this.longValue = null;
                    this.doubleValue = null;
                    this.stringValue = null;
                    this.byteValue = null;
                    this.dateTimeValue = null;
                    throw new IllegalArgumentException(ex);
                }
            }

            /**
             * Gets the artifact type name for this condition.
             *
             * @return The type name.
             */
            String getArtifactTypeName() {
                return this.artifactTypeName;
            }

            /**
             * Gets the tree display name for this condition.
             *
             * @return The tree display name for this condition.
             */
            String getTreeDisplayName() {
                return this.treeDisplayName;
            }

            /**
             * Sets the tree display name for this condition.
             *
             * @param name The tree display name for this condition.
             */
            void setTreeDisplayName(String name) {
                this.treeDisplayName = name;
            }

            /**
             * Gets the attribute type name for this condition.
             *
             * @return The type name.
             */
            String getAttributeTypeName() {
                return this.attributeTypeName;
            }

            /**
             * Gets the value type for this condition.
             *
             * @return The value type.
             */
            BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE getAttributeValueType() {
                return this.attributeValueType;
            }

            /**
             * Gets the integer value for this condition.
             *
             * @return The value, may be null.
             */
            Integer getIntegerValue() {
                return this.intValue;
            }

            /**
             * Gets the long value for this condition.
             *
             * @return The value, may be null.
             */
            Long getLongValue() {
                return this.longValue;
            }

            /**
             * Gets the double value for this condition.
             *
             * @return The value, may be null.
             */
            Double getDoubleValue() {
                return this.doubleValue;
            }

            /**
             * Gets the string value for this condition.
             *
             * @return The value, may be null.
             */
            String getStringValue() {
                return this.stringValue;
            }

            /**
             * Gets the byte value for this condition.
             *
             * @return The value, may be null.
             */
            byte[] getByteValue() {
                return this.byteValue;
            }

            /**
             * Gets the DateTime value for this condition.
             *
             * @return The value, may be null.
             */
            DateTime getDateTimeValue() {
                return this.dateTimeValue;
            }

            /**
             * Gets the string representation of the value, regardless of the
             * data type
             *
             * @return The value, may be null.
             */
            String getStringRepresentationOfValue() {
                String valueText = "";
                switch (this.attributeValueType) {
                    case BYTE:
                        valueText = new String(Hex.encodeHex(getByteValue()));
                        break;
                    case DATETIME:
                        valueText = "";
                        break;
                    case DOUBLE:
                        valueText = getDoubleValue().toString();
                        break;
                    case INTEGER:
                        valueText = getIntegerValue().toString();
                        break;
                    case LONG:
                        valueText = getLongValue().toString();
                        break;
                    case STRING:
                        valueText = getStringValue();
                        break;
                    default:
                        valueText = "Undefined";
                        break;
                }
                return valueText;
            }

            /**
             * Gets the relational operator for the condition.
             *
             * @return The operator.
             */
            RelationalOp getRelationalOperator() {
                return this.op;
            }

            /**
             * @inheritDoc
             */
            @Override
            public boolean equals(Object that) {
                if (this == that) {
                    return true;
                } else if (!(that instanceof ArtifactCondition)) {
                    return false;
                } else {
                    ArtifactCondition thatCondition = (ArtifactCondition) that;
                    return this.artifactTypeName.equals(thatCondition.getArtifactTypeName())
                            && this.attributeTypeName.equals(thatCondition.getAttributeTypeName())
                            && this.attributeValueType == thatCondition.getAttributeValueType()
                            && this.op == thatCondition.getRelationalOperator()
                            && Objects.equals(this.intValue, thatCondition.getIntegerValue())
                            && Objects.equals(this.longValue, thatCondition.getLongValue())
                            && Objects.equals(this.doubleValue, thatCondition.getDoubleValue())
                            && Objects.equals(this.stringValue, thatCondition.getStringValue())
                            && Arrays.equals(this.byteValue, thatCondition.getByteValue())
                            && Objects.equals(this.dateTimeValue, thatCondition.getDateTimeValue());
                }
            }

            /**
             * @inheritDoc
             */
            @Override
            public int hashCode() {
                int hash = 7;
                hash = 9 * hash + this.artifactTypeName.hashCode();
                hash = 13 * hash + this.attributeTypeName.hashCode();
                hash = 11 * hash + this.attributeValueType.hashCode();
                hash = 13 * hash + this.op.hashCode();
                hash = 15 * hash + Objects.hashCode(this.intValue);
                hash = 7 * hash + Objects.hashCode(this.longValue);
                hash = 17 * hash + Objects.hashCode(this.doubleValue);
                hash = 8 * hash + Objects.hashCode(this.stringValue);
                hash = 27 * hash + Objects.hashCode(this.byteValue);
                hash = 3 * hash + Objects.hashCode(this.dateTimeValue);
                return hash;
            }

            /**
             * @inheritDoc
             */
            @Override
            public int compareTo(ArtifactCondition that) {
                int retVal = this.artifactTypeName.compareTo(that.getArtifactTypeName());
                if (0 != retVal) {
                    return retVal;
                }
                retVal = this.attributeTypeName.compareTo(that.getAttributeTypeName());
                if (0 != retVal) {
                    return retVal;
                }
                retVal = this.attributeValueType.compareTo(that.getAttributeValueType());
                if (0 != retVal) {
                    return retVal;
                } else {
                    switch (this.attributeValueType) {
                        case STRING:
                            retVal = this.stringValue.compareTo(that.getStringValue());
                            if (0 != retVal) {
                                return retVal;
                            }
                            break;
                        case INTEGER:
                            retVal = this.intValue.compareTo(that.getIntegerValue());
                            if (0 != retVal) {
                                return retVal;
                            }
                            break;
                        case LONG:
                            retVal = this.longValue.compareTo(that.getLongValue());
                            if (0 != retVal) {
                                return retVal;
                            }
                            break;
                        case DOUBLE:
                            retVal = this.doubleValue.compareTo(that.getDoubleValue());
                            if (0 != retVal) {
                                return retVal;
                            }
                            break;
                        case BYTE:
                            if (Arrays.equals(this.byteValue, that.getByteValue())) {
                                return 0;
                            } else {
                                return 1;
                            }
                        case DATETIME:
                            retVal = this.dateTimeValue.compareTo(that.getDateTimeValue());
                            if (0 != retVal) {
                                return retVal;
                            }
                            break;
                    }
                }
                return this.op.compareTo(that.getRelationalOperator());
            }

            /**
             * Gets the SQL condition clause for the condition.
             *
             * @param index The index of the condition within the collection of
             *              conditions that make up a rule. It is used for table
             *              name aliasing.
             *
             * @return The SQL clause as a string, without leading or trailing
             *         spaces.
             *
             * @throws ExportRulesException If the artifact type or attribute
             *                              type for the condition does not
             *                              exist.
             */
            private String getConditionClause(int index) throws ExportRulesException {
                Case currentCase;
                try {
                    currentCase = Case.getOpenCase();
                } catch (NoCurrentCaseException ex) {
                    throw new ExportRulesException("Exception while getting open case.", ex);
                }
                SleuthkitCase caseDb = currentCase.getSleuthkitCase();
                BlackboardArtifact.Type artifactType;
                BlackboardAttribute.Type attributeType;
                try {
                    artifactType = caseDb.getArtifactType(artifactTypeName);
                } catch (TskCoreException ex) {
                    throw new ExportRulesException(String.format("The specified %s artifact type does not exist in case database for %s", artifactTypeName, currentCase.getCaseDirectory()), ex);
                }
                try {
                    attributeType = caseDb.getAttributeType(attributeTypeName);
                } catch (TskCoreException ex) {
                    throw new ExportRulesException(String.format("The specified %s attribute type does not exist in case database for %s", attributeTypeName, currentCase.getCaseDirectory()), ex);
                }
                                                
                String clause = String.format("files.obj_id = arts%d.obj_id AND arts%d.artifact_type_id = %d AND attrs%d.artifact_id = arts%d.artifact_id AND attrs%d.attribute_type_id = %d AND ",
                        index, index, artifactType.getTypeID(), index, index, index, attributeType.getTypeID());                
                switch (this.attributeValueType) {
                    case INTEGER:
                        clause += String.format("attrs%d.value_int32 %s %d", index, this.op.getSymbol(), this.intValue);
                        break;
                    case LONG:
                        clause += String.format("attrs%d.value_int64 %s %d", index, this.op.getSymbol(), this.longValue);
                        break;
                    case DOUBLE:
                        clause += String.format("attrs%d.value_double %s %f", index, this.op.getSymbol(), this.doubleValue);
                        break;
                    case STRING:
                        clause += String.format("attrs%d.value_text %s '%s'", index, this.op.getSymbol(), this.stringValue);
                        break;
                    case BYTE:
                        clause += String.format("attrs%d.value_byte %s decode('%s', 'hex')", index, this.op.getSymbol(), new String(Hex.encodeHex(getByteValue())));
                        break;
                    case DATETIME:
                        clause += String.format("attrs%d.value_int64 %s '%s'", index, this.op.getSymbol(), this.dateTimeValue.getMillis()/1000);
                        break;
                }
                return clause;
            }

        }

    }

    /**
     * Exception type thrown by the export rules class.
     */
    public final static class ExportRulesException extends Exception {

        private static final long serialVersionUID = 1L;

        /**
         * Constructs an exception.
         *
         * @param message The exception message.
         */
        private ExportRulesException(String message) {
            super(message);
        }

        /**
         * Constructs an exception.
         *
         * @param message The exception message.
         * @param cause   The exception cause.
         */
        private ExportRulesException(String message, Throwable cause) {
            super(message, cause);
        }
    }

}
