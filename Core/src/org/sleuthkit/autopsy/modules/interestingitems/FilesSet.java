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
package org.sleuthkit.autopsy.modules.interestingitems;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.openide.util.NbBundle;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskData;

/**
 * A collection of set membership rules that define an interesting files set.
 * The rules are independent, i.e., if any rule is satisfied by a file, the file
 * belongs to the set.
 *
 * Interesting files set definition objects are immutable, so they may be safely
 * published to multiple threads.
 */
public final class FilesSet implements Serializable {

    private static final long serialVersionUID = 1L;
    private final String name;
    private final String description;
    private final boolean ignoreKnownFiles;
    private final boolean ignoreUnallocatedSpace;
    private final Map<String, Rule> rules = new HashMap<>();

    /**
     * Constructs an interesting files set.
     *
     * @param name                   The name of the set.
     * @param description            A description of the set, may be null.
     * @param ignoreKnownFiles       Whether or not to exclude known files from
     *                               the set.
     * @param ignoreUnallocatedSpace Whether or not to exclude unallocated space
     *                               from the set.
     * @param rules                  The rules that define the set. May be null,
     *                               but a set with no rules is the empty set.
     */
    public FilesSet(String name, String description, boolean ignoreKnownFiles, boolean ignoreUnallocatedSpace, Map<String, Rule> rules) {
        if ((name == null) || (name.isEmpty())) {
            throw new IllegalArgumentException("Interesting files set name cannot be null or empty");
        }
        this.name = name;
        this.description = (description != null ? description : "");
        this.ignoreKnownFiles = ignoreKnownFiles;
        this.ignoreUnallocatedSpace = ignoreUnallocatedSpace;
        if (rules != null) {
            this.rules.putAll(rules);
        }
    }

    /**
     * Gets the name of this interesting files set.
     *
     * @return A name string.
     */
    public String getName() {
        return this.name;
    }

    /**
     * Gets the description of this interesting files set.
     *
     * @return A description string, possibly the empty string.
     */
    public String getDescription() {
        return this.description;
    }

    /**
     * Returns whether or not this interesting files set ignores known files,
     * i.e., files marked as known by a look up in a known files hash set such
     * as the National Software Reference Library (NSRL). Note that the
     * interesting files set does not do hash set look ups; it simply queries
     * the known status of the files when testing them for set membership.
     *
     * @return True if known files are ignored, false otherwise.
     */
    public boolean ignoresKnownFiles() {
        return this.ignoreKnownFiles;
    }

    /**
     * Returns whether or not this set of rules will process unallocated space.
     *
     * @return True if unallocated space should be processed, false if it should
     *         not be.
     */
    public boolean ingoresUnallocatedSpace() {
        return this.ignoreUnallocatedSpace;
    }

    /**
     * Gets a copy of the set membership rules of this interesting files set.
     *
     * @return A map of set membership rule names to rules, possibly empty.
     */
    public Map<String, Rule> getRules() {
        return new HashMap<>(this.rules);
    }

    /**
     * Determines whether a file is a member of this interesting files set.
     *
     * @param file A file to test for set membership.
     *
     * @return The name of the first set membership rule satisfied by the file,
     *         will be null if the file does not belong to the set.
     */
    public String fileIsMemberOf(AbstractFile file) {
        if ((this.ignoreKnownFiles) && (file.getKnown() == TskData.FileKnown.KNOWN)) {
            return null;
        }

        if ((this.ignoreUnallocatedSpace)
                && (file.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS)
                || file.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.SLACK)
                || file.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.UNUSED_BLOCKS))) {
            return null;
        }

        for (Rule rule : rules.values()) {
            if (rule.isSatisfied(file)) {
                return rule.getName();
            }
        }
        return null;
    }

    @Override
    public String toString() {
        // This override is designed to provide a display name for use with 
        // javax.swing.DefaultListModel<E>.
        return this.name;
    }

    /**
     * A set membership rule for an interesting files set. The immutability of a
     * rule object allows it to be safely published to multiple threads.
     */
    public final static class Rule implements Serializable {

        private static final long serialVersionUID = 1L;
        private final String uuid;
        private final String ruleName;
        private final FileNameCondition fileNameCondition;
        private final MetaTypeCondition metaTypeCondition;
        private final ParentPathCondition pathCondition;
        private final MimeTypeCondition mimeTypeCondition;
        private final FileSizeCondition fileSizeCondition;
        private final DateCondition dateCondition;
        private final List<FileAttributeCondition> conditions = new ArrayList<>();

        /**
         * Construct an interesting files set membership rule.
         *
         * @param ruleName          The name of the rule. Can be empty string.
         * @param fileNameCondition A file name condition, may be null.
         * @param metaTypeCondition A file meta-type condition.
         * @param pathCondition     A file path condition, may be null.
         * @param mimeTypeCondition A file mime type condition, may be null.
         * @param fileSizeCondition A file size condition, may be null.
         * @param dateCondition     A file date created or modified condition,
         *                          may be null
         */
        public Rule(String ruleName, FileNameCondition fileNameCondition, MetaTypeCondition metaTypeCondition, ParentPathCondition pathCondition, MimeTypeCondition mimeTypeCondition, FileSizeCondition fileSizeCondition, DateCondition dateCondition) {
            // since ruleName is optional, ruleUUID can be used to uniquely identify a rule.
            this.uuid = UUID.randomUUID().toString();
            if (metaTypeCondition == null) {
                throw new IllegalArgumentException("Interesting files set rule meta-type condition cannot be null");
            }

            this.ruleName = ruleName;

            /*
             * The rules are evaluated in the order added. MetaType check is
             * fastest, so do it first
             */
            this.metaTypeCondition = metaTypeCondition;
            this.conditions.add(this.metaTypeCondition);

            this.fileSizeCondition = fileSizeCondition;
            if (this.fileSizeCondition != null) {
                this.conditions.add(this.fileSizeCondition);
            }

            this.fileNameCondition = fileNameCondition;
            if (this.fileNameCondition != null) {
                this.conditions.add(fileNameCondition);
            }

            this.mimeTypeCondition = mimeTypeCondition;
            if (this.mimeTypeCondition != null) {
                this.conditions.add(mimeTypeCondition);
            }

            this.pathCondition = pathCondition;
            if (this.pathCondition != null) {
                this.conditions.add(this.pathCondition);
            }
            this.dateCondition = dateCondition;
            if (this.dateCondition != null) {
                this.conditions.add(this.dateCondition);
            }
        }

        /**
         * Get the name of the rule.
         *
         * @return A name string.
         */
        public String getName() {
            return ruleName;
        }

        /**
         * Get the file name condition for the rule.
         *
         * @return A file name condition. Can be null.
         */
        public FileNameCondition getFileNameCondition() {
            return this.fileNameCondition;
        }

        /**
         * Get the meta-type condition for the rule.
         *
         * @return A meta-type condition. Can be null.
         */
        public MetaTypeCondition getMetaTypeCondition() {
            return this.metaTypeCondition;
        }

        /**
         * Get the path condition for the rule.
         *
         * @return A path condition, may be null.
         */
        public ParentPathCondition getPathCondition() {
            return this.pathCondition;
        }

        public DateCondition getDateCondition() {
            return this.dateCondition;
        }

        /**
         * Determines whether or not a file satisfies the rule.
         *
         * @param file The file to test.
         *
         * @return True if the rule is satisfied, false otherwise.
         */
        public boolean isSatisfied(AbstractFile file) {
            for (FileAttributeCondition condition : conditions) {
                if (!condition.passes(file)) {
                    return false;
                }
            }
            return true;
        }

        @NbBundle.Messages({
            "# {0} - daysIncluded",
            "FilesSet.rule.dateRule.toString=(modified within {0} day(s))"
        })
        @Override
        public String toString() {
            // This override is designed to provide a display name for use with 
            // javax.swing.DefaultListModel<E>.
            if (fileNameCondition != null) {
                return this.ruleName + " (" + fileNameCondition.getTextToMatch() + ")";
            } else if (this.pathCondition != null) {
                return this.ruleName + " (" + pathCondition.getTextToMatch() + ")";
            } else if (this.mimeTypeCondition != null) {
                return this.ruleName + " (" + mimeTypeCondition.getMimeType() + ")";
            } else if (this.fileSizeCondition != null) {
                return this.ruleName + " (" + fileSizeCondition.getComparator().getSymbol() + " " + fileSizeCondition.getSizeValue()
                        + " " + fileSizeCondition.getUnit().getName() + ")";
            } else if (this.dateCondition != null) {
                return this.ruleName + Bundle.FilesSet_rule_dateRule_toString(dateCondition.getDaysIncluded());
            } else {
                return this.ruleName + " ()";
            }

        }

        /**
         * @return the ruleUUID
         */
        public String getUuid() {
            return this.uuid;
        }

        /**
         * @return the mime type condition. Can be null.
         */
        public MimeTypeCondition getMimeTypeCondition() {
            return mimeTypeCondition;
        }

        /**
         * @return the file size condition. Can be null.
         */
        public FileSizeCondition getFileSizeCondition() {
            return fileSizeCondition;
        }

        /**
         * An interface for the file attribute conditions of which interesting
         * files set membership rules are composed.
         */
        static interface FileAttributeCondition extends Serializable {

            /**
             * Tests whether or not a file satisfies the condition.
             *
             * @param file The file to test.
             *
             * @return True if the file passes the test, false otherwise.
             */
            boolean passes(AbstractFile file);
        }

        /**
         * A class for checking files based upon their MIME types.
         */
        public static final class MimeTypeCondition implements FileAttributeCondition {

            private static final long serialVersionUID = 1L;
            private final String mimeType;

            /**
             * Constructs a MimeTypeCondition
             *
             * @param mimeType The mime type to condition for
             */
            public MimeTypeCondition(String mimeType) {
                this.mimeType = mimeType;
            }

            @Override
            public boolean passes(AbstractFile file) {
                return this.mimeType.equals(file.getMIMEType());
            }

            /**
             * Gets the mime type that is being checked
             *
             * @return the mime type
             */
            public String getMimeType() {
                return this.mimeType;
            }

        }

        /**
         * A class for checking whether a file's size is within the
         * specifications given (i.e. < N Bytes).
         */
        public static final class FileSizeCondition implements FileAttributeCondition {

            private static final long serialVersionUID = 1L;

            /**
             * Represents a comparison item for file size
             */
            public static enum COMPARATOR {

                LESS_THAN("<"),
                LESS_THAN_EQUAL("≤"),
                EQUAL("="),
                GREATER_THAN(">"),
                GREATER_THAN_EQUAL("≥");

                private String symbol;

                private COMPARATOR(String symbol) {
                    this.symbol = symbol;
                }

                public static COMPARATOR fromSymbol(String symbol) {
                    switch (symbol) {
                        case "<=":
                        case "≤":
                            return LESS_THAN_EQUAL;
                        case "<":
                            return LESS_THAN;
                        case "==":
                        case "=":
                            return EQUAL;
                        case ">":
                            return GREATER_THAN;
                        case ">=":
                        case "≥":
                            return GREATER_THAN_EQUAL;
                        default:
                            throw new IllegalArgumentException("Invalid symbol");
                    }
                }

                /**
                 * @return the symbol
                 */
                public String getSymbol() {
                    return symbol;
                }
            }

            /**
             * Represents the units of size
             */
            public static enum SIZE_UNIT {

                BYTE(1, "Bytes"),
                KILOBYTE(1024, "Kilobytes"),
                MEGABYTE(1024 * 1024, "Megabytes"),
                GIGABYTE(1024 * 1024 * 1024, "Gigabytes");
                private long size;
                private String name;

                private SIZE_UNIT(long size, String name) {
                    this.size = size;
                    this.name = name;
                }

                public long getSize() {
                    return this.size;
                }

                public static SIZE_UNIT fromName(String name) {
                    for (SIZE_UNIT unit : SIZE_UNIT.values()) {
                        if (unit.getName().equals(name)) {
                            return unit;
                        }
                    }
                    throw new IllegalArgumentException("Invalid name for size unit.");
                }

                /**
                 * @return the name
                 */
                public String getName() {
                    return name;
                }
            }
            private final COMPARATOR comparator;
            private final SIZE_UNIT unit;
            private final int sizeValue;

            public FileSizeCondition(COMPARATOR comparator, SIZE_UNIT unit, int sizeValue) {
                this.comparator = comparator;
                this.unit = unit;
                this.sizeValue = sizeValue;
            }

            /**
             * Gets the comparator of this condition
             *
             * @return the comparator
             */
            public COMPARATOR getComparator() {
                return comparator;
            }

            /**
             * Gets the unit for the size of this condition
             *
             * @return the unit
             */
            public SIZE_UNIT getUnit() {
                return unit;
            }

            /**
             * Gets the size value of this condition
             *
             * @return the size value
             */
            public int getSizeValue() {
                return sizeValue;
            }

            @Override
            public boolean passes(AbstractFile file) {
                long fileSize = file.getSize();
                long conditionSize = this.getUnit().getSize() * this.getSizeValue();
                switch (this.getComparator()) {
                    case GREATER_THAN:
                        return fileSize > conditionSize;
                    case GREATER_THAN_EQUAL:
                        return fileSize >= conditionSize;
                    case LESS_THAN_EQUAL:
                        return fileSize <= conditionSize;
                    case LESS_THAN:
                        return fileSize < conditionSize;
                    default:
                        return fileSize == conditionSize;

                }
            }

        }

        /**
         * A file meta-type condition for an interesting files set membership
         * rule. The immutability of a meta-type condition object allows it to
         * be safely published to multiple threads.
         */
        public static final class MetaTypeCondition implements FileAttributeCondition {

            private static final long serialVersionUID = 1L;

            public enum Type {

                FILES,
                DIRECTORIES,
                FILES_AND_DIRECTORIES,
                ALL
            }

            private final Type type;

            /**
             * Construct a meta-type condition.
             *
             * @param metaType The meta-type to match, must.
             */
            public MetaTypeCondition(Type type) {
                this.type = type;
            }

            @Override
            public boolean passes(AbstractFile file) {
                switch (this.type) {
                    case FILES:
                        return file.isFile();
                    case DIRECTORIES:
                        return file.getMetaType() == TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_DIR
                                || file.getMetaType() == TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_VIRT_DIR;
                    case FILES_AND_DIRECTORIES:
                        return file.getMetaType() == TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_REG
                                || file.getMetaType() == TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_DIR
                                || file.getMetaType() == TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_VIRT_DIR;
                    case ALL:
                        return true;  //Effectively ignores the metatype condition when All is selected.
                    default:
                        return true;
                }
            }

            /**
             * Gets the meta-type the condition matches.
             *
             * @return A member of the MetaTypeCondition.Type enumeration.
             */
            public Type getMetaType() {
                return this.type;
            }
        }

        /**
         * An interface for file attribute conditions that do textual matching.
         */
        static interface TextCondition extends FileAttributeCondition {

            /**
             * Gets the text the condition matches.
             *
             * @return The text.
             */
            String getTextToMatch();

            /**
             * Queries whether or not the text the condition matches is a
             * regular expression.
             *
             * @return True if the text to be matched is a regular expression,
             *         false otherwise.
             */
            boolean isRegex();

            /**
             * Determines whether a string of text matches the condition.
             *
             * @param textToMatch The text string.
             *
             * @return True if the text matches, false otherwise.
             */
            boolean textMatches(String textToMatch);

        }

        /**
         * An abstract base class for file attribute conditions that do textual
         * matching.
         */
        private static abstract class AbstractTextCondition implements TextCondition {

            private static final long serialVersionUID = 1L;
            private final TextMatcher textMatcher;

            /**
             * Construct a case-insensitive text condition.
             *
             * @param text The text to be matched.
             */
            AbstractTextCondition(String text, Boolean partialMatch) {
                if (partialMatch) {
                    this.textMatcher = new FilesSet.Rule.CaseInsensitivePartialStringComparisionMatcher(text);
                } else {
                    this.textMatcher = new FilesSet.Rule.CaseInsensitiveStringComparisionMatcher(text);
                }
            }

            /**
             * Construct a regular expression text condition.
             *
             * @param regex The regular expression to be matched.
             */
            AbstractTextCondition(Pattern regex) {
                this.textMatcher = new FilesSet.Rule.RegexMatcher(regex);
            }

            /**
             * Get the text the condition matches.
             *
             * @return The text.
             */
            @Override
            public String getTextToMatch() {
                return this.textMatcher.getTextToMatch();
            }

            /**
             * Queries whether or not the text the condition matches is a
             * regular expression.
             *
             * @return True if the text to be matched is a regular expression,
             *         false otherwise.
             */
            @Override
            public boolean isRegex() {
                return this.textMatcher.isRegex();
            }

            /**
             * Determines whether a string of text matches the condition.
             *
             * @param textToMatch The text string.
             *
             * @return True if the text matches, false otherwise.
             */
            @Override
            public boolean textMatches(String textToMatch) {
                return this.textMatcher.textMatches(textToMatch);
            }

            @Override
            public abstract boolean passes(AbstractFile file);

        }

        /**
         * A file path condition for an interesting files set membership rule.
         * The immutability of a path condition object allows it to be safely
         * published to multiple threads.
         */
        public static final class ParentPathCondition extends AbstractTextCondition {

            private static final long serialVersionUID = 1L;

            /**
             * Construct a case-insensitive file path condition.
             *
             * @param path The path to be matched.
             */
            public ParentPathCondition(String path) {
                super(path, true);
            }

            /**
             * Construct a file path regular expression condition.
             *
             * @param path The path regular expression to be matched.
             */
            public ParentPathCondition(Pattern path) {
                super(path);
            }

            @Override
            public boolean passes(AbstractFile file) {
                return this.textMatches(file.getParentPath() + "/");
            }

        }

        /**
         * A "tagging" interface to group name and extension conditions
         * separately from path conditions for type safety when constructing
         * rules.
         */
        static interface FileNameCondition extends TextCondition {
        }

        /**
         * A file name condition for an interesting files set membership rule.
         * The immutability of a file name condition object allows it to be
         * safely published to multiple threads.
         */
        public static final class FullNameCondition extends AbstractTextCondition implements FileNameCondition {

            private static final long serialVersionUID = 1L;

            /**
             * Construct a case-insensitive full file name condition.
             *
             * @param name The file name to be matched.
             */
            public FullNameCondition(String name) {
                super(name, false);
            }

            /**
             * Construct a full file name regular expression condition.
             *
             * @param name The file name regular expression to be matched.
             */
            public FullNameCondition(Pattern name) {
                super(name);
            }

            @Override
            public boolean passes(AbstractFile file) {
                return this.textMatches(file.getName());
            }

        }

        /**
         * A class for checking whether a file's creation or modification
         * occured in a specific range of time
         */
        public static final class DateCondition implements FileAttributeCondition {

            private static final long serialVersionUID = 1L;
            private final static long SECS_PER_DAY = 60 * 60 * 24;

            private int daysIncluded;

            /**
             * Construct a new DateCondition
             *
             * @param days - files created or modified more recently than this
             *             number of days will pass
             */
            public DateCondition(int days) {
                daysIncluded = days;
            }

            /**
             * Get the number of days which this condition allows to pass
             *
             * @return integer value of the number days which will pass
             */
            public int getDaysIncluded() {
                return daysIncluded;
            }

            @Override
            public boolean passes(AbstractFile file) {
                long dateThreshold = System.currentTimeMillis() / 1000 - daysIncluded * SECS_PER_DAY;
                return file.getCrtime() > dateThreshold || file.getMtime() > dateThreshold;
            }

        }

        /**
         * A file name extension condition for an interesting files set
         * membership rule. The immutability of a file name extension condition
         * object allows it to be safely published to multiple threads.
         */
        public static final class ExtensionCondition extends AbstractTextCondition implements FileNameCondition {

            private static final long serialVersionUID = 1L;

            /**
             * Construct a case-insensitive file name extension condition.
             *
             * @param extension The file name extension to be matched.
             */
            public ExtensionCondition(String extension) {
                // If there is a leading ".", strip it since 
                // AbstractFile.getFileNameExtension() returns just the 
                // extension chars and not the dot.
                super(extension.startsWith(".") ? extension.substring(1) : extension, false);
            }

            /**
             * Construct a file name extension regular expression condition.
             *
             * @param extension The file name extension regular expression to be
             *                  matched.
             */
            public ExtensionCondition(Pattern extension) {
                super(extension);
            }

            @Override
            public boolean passes(AbstractFile file) {
                return this.textMatches(file.getNameExtension());
            }

        }

        /**
         * An interface for objects that do textual matches, used to compose a
         * text condition.
         */
        private static interface TextMatcher extends Serializable {

            /**
             * Get the text the matcher examines.
             *
             * @return The text.
             */
            String getTextToMatch();

            /**
             * Queries whether or not the text the matcher examines is a regular
             * expression.
             *
             * @return True if the text to be matched is a regular expression,
             *         false otherwise.
             */
            boolean isRegex();

            /**
             * Determines whether a string of text is matched.
             *
             * @param subject The text string.
             *
             * @return True if the text matches, false otherwise.
             */
            boolean textMatches(String subject);

        }

        /**
         * A text matcher that does a case-insensitive string comparison.
         */
        private static class CaseInsensitiveStringComparisionMatcher implements TextMatcher {

            private static final long serialVersionUID = 1L;
            private final String textToMatch;

            /**
             * Construct a text matcher that does a case-insensitive string
             * comparison.
             *
             * @param textToMatch The text to match.
             */
            CaseInsensitiveStringComparisionMatcher(String textToMatch) {
                this.textToMatch = textToMatch;
            }

            @Override
            public String getTextToMatch() {
                return this.textToMatch;
            }

            @Override
            public boolean isRegex() {
                return false;
            }

            @Override
            public boolean textMatches(String subject) {
                return subject.equalsIgnoreCase(textToMatch);
            }

        }

        /**
         * A text matcher that does a case-insensitive string comparison.
         */
        private static class CaseInsensitivePartialStringComparisionMatcher implements TextMatcher {

            private static final long serialVersionUID = 1L;
            private final String textToMatch;
            private final Pattern pattern;

            /**
             * Construct a text matcher that does a case-insensitive string
             * comparison.
             *
             * @param textToMatch The text to match.
             */
            CaseInsensitivePartialStringComparisionMatcher(String textToMatch) {
                this.textToMatch = textToMatch;
                this.pattern = Pattern.compile(Pattern.quote(textToMatch), Pattern.CASE_INSENSITIVE);
            }

            @Override
            public String getTextToMatch() {
                return this.textToMatch;
            }

            @Override
            public boolean isRegex() {
                return false;
            }

            @Override
            public boolean textMatches(String subject) {
                return pattern.matcher(subject).find();
            }
        }

        /**
         * A text matcher that does regular expression matching.
         */
        private static class RegexMatcher implements TextMatcher {

            private static final long serialVersionUID = 1L;
            private final Pattern regex;

            /**
             * Construct a text matcher that does a regular expression
             * comparison.
             *
             * @param regex The regular expression to match.
             */
            RegexMatcher(Pattern regex) {
                this.regex = regex;
            }

            @Override
            public String getTextToMatch() {
                return this.regex.pattern();
            }

            @Override
            public boolean isRegex() {
                return true;
            }

            @Override
            public boolean textMatches(String subject) {
                // A single match is sufficient.
                return this.regex.matcher(subject).find();
            }

        }

    }

}
