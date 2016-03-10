/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
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
final class FilesSet implements Serializable {

    private static final long serialVersionUID = 1L;
    private final String name;
    private final String description;
    private final boolean ignoreKnownFiles;
    private final Map<String, Rule> rules = new HashMap<>();

    /**
     * Constructs an interesting files set.
     *
     * @param name             The name of the set.
     * @param description      A description of the set, may be null.
     * @param ignoreKnownFiles Whether or not to exclude known files from the
     *                         set.
     * @param rules            The rules that define the set. May be null, but a
     *                         set with no rules is the empty set.
     */
    FilesSet(String name, String description, boolean ignoreKnownFiles, Map<String, Rule> rules) {
        if ((name == null) || (name.isEmpty())) {
            throw new IllegalArgumentException("Interesting files set name cannot be null or empty");
        }
        this.name = name;
        this.description = (description != null ? description : "");
        this.ignoreKnownFiles = ignoreKnownFiles;
        if (rules != null) {
            this.rules.putAll(rules);
        }
    }

    /**
     * Gets the name of this interesting files set.
     *
     * @return A name string.
     */
    String getName() {
        return this.name;
    }

    /**
     * Gets the description of this interesting files set.
     *
     * @return A description string, possibly the empty string.
     */
    String getDescription() {
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
    boolean ignoresKnownFiles() {
        return this.ignoreKnownFiles;
    }

    /**
     * Gets a copy of the set membership rules of this interesting files set.
     *
     * @return A map of set membership rule names to rules, possibly empty.
     */
    Map<String, Rule> getRules() {
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
    String fileIsMemberOf(AbstractFile file) {
        if ((this.ignoreKnownFiles) && (file.getKnown() == TskData.FileKnown.KNOWN)) {
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
    static class Rule implements Serializable {

        private static final long serialVersionUID = 1L;
        private final String uuid;
        private final String ruleName;
        private final FileNameCondition fileNameCondition;
        private final MetaTypeCondition metaTypeCondition;
        private final ParentPathCondition pathCondition;
        private final MimeTypeCondition mimeTypeCondition;
        private final FileSizeCondition fileSizeCondition;
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
         */
        Rule(String ruleName, FileNameCondition fileNameCondition, MetaTypeCondition metaTypeCondition, ParentPathCondition pathCondition, MimeTypeCondition mimeTypeCondition, FileSizeCondition fileSizeCondition) {
            // since ruleName is optional, ruleUUID can be used to uniquely identify a rule.
            this.uuid = UUID.randomUUID().toString();
            if (metaTypeCondition == null) {
                throw new IllegalArgumentException("Interesting files set rule meta-type condition cannot be null");
            }
            if (pathCondition == null && fileNameCondition == null && mimeTypeCondition == null && fileSizeCondition == null) {
                throw new IllegalArgumentException("Must have at least one condition on rule.");
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
        }

        /**
         * Get the name of the rule.
         *
         * @return A name string.
         */
        String getName() {
            return ruleName;
        }

        /**
         * Get the file name condition for the rule.
         *
         * @return A file name condition. Can be null.
         */
        FileNameCondition getFileNameCondition() {
            return this.fileNameCondition;
        }

        /**
         * Get the meta-type condition for the rule.
         *
         * @return A meta-type condition. Can be null.
         */
        MetaTypeCondition getMetaTypeCondition() {
            return this.metaTypeCondition;
        }

        /**
         * Get the path condition for the rule.
         *
         * @return A path condition, may be null.
         */
        ParentPathCondition getPathCondition() {
            return this.pathCondition;
        }

        /**
         * Determines whether or not a file satisfies the rule.
         *
         * @param file The file to test.
         *
         * @return True if the rule is satisfied, false otherwise.
         */
        boolean isSatisfied(AbstractFile file) {
            for (FileAttributeCondition condition : conditions) {
                if (!condition.passes(file)) {
                    return false;
                }
            }
            return true;
        }

        /**
         * @inheritDoc
         */
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
        MimeTypeCondition getMimeTypeCondition() {
            return mimeTypeCondition;
        }

        /**
         * @return the file size condition. Can be null.
         */
        FileSizeCondition getFileSizeCondition() {
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
        static final class MimeTypeCondition implements FileAttributeCondition {

            private static final long serialVersionUID = 1L;
            private final String mimeType;

            /**
             * Constructs a MimeTypeCondition
             *
             * @param mimeType The mime type to condition for
             */
            MimeTypeCondition(String mimeType) {
                this.mimeType = mimeType;
            }

            /**
             * @inheritDoc
             */
            @Override
            public boolean passes(AbstractFile file) {
                return this.mimeType.equals(file.getMIMEType());
            }

            /**
             * Gets the mime type that is being checked
             *
             * @return the mime type
             */
            String getMimeType() {
                return this.mimeType;
            }

        }

        /**
         * A class for checking whether a file's size is within the
         * specifications given (i.e. < N Bytes).
         */
        static final class FileSizeCondition implements FileAttributeCondition {

            private static final long serialVersionUID = 1L;

            
            /**
             * Represents a comparison item for file size
             */
            static enum COMPARATOR {

                LESS_THAN("<"),
                LESS_THAN_EQUAL("≤"),
                EQUAL("="),
                GREATER_THAN(">"),
                GREATER_THAN_EQUAL("≥");

                private String symbol;

                COMPARATOR(String symbol) {
                    this.symbol = symbol;
                }

                public static COMPARATOR fromSymbol(String symbol) {
                    if (symbol.equals("<=") || symbol.equals("≤")) {
                        return LESS_THAN_EQUAL;
                    } else if (symbol.equals("<")) {
                        return LESS_THAN;
                    } else if (symbol.equals("==") || symbol.equals("=")) {
                        return EQUAL;
                    } else if (symbol.equals(">")) {
                        return GREATER_THAN;
                    } else if (symbol.equals(">=") || symbol.equals("≥")) {
                        return GREATER_THAN_EQUAL;
                    } else {
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
            static enum SIZE_UNIT {

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

            FileSizeCondition(COMPARATOR comparator, SIZE_UNIT unit, int sizeValue) {
                this.comparator = comparator;
                this.unit = unit;
                this.sizeValue = sizeValue;
            }
            
            /**
             * Gets the comparator of this condition
             *
             * @return the comparator
             */
            COMPARATOR getComparator() {
                return comparator;
            }

            /**
             * Gets the unit for the size of this condition
             *
             * @return the unit
             */
            SIZE_UNIT getUnit() {
                return unit;
            }

            /**
             * Gets the size value of this condition
             *
             * @return the size value
             */
            int getSizeValue() {
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
        static final class MetaTypeCondition implements FileAttributeCondition {

            private static final long serialVersionUID = 1L;

            enum Type {

                FILES,
                DIRECTORIES,
                FILES_AND_DIRECTORIES
            }

            private final Type type;

            /**
             * Construct a meta-type condition.
             *
             * @param metaType The meta-type to match, must.
             */
            MetaTypeCondition(Type type) {
                this.type = type;
            }

            /**
             * @inheritDoc
             */
            @Override
            public boolean passes(AbstractFile file) {
                switch (this.type) {
                    case FILES:
                        return file.getMetaType() == TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_REG;
                    case DIRECTORIES:
                        return file.getMetaType() == TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_DIR;
                    default:
                        return file.getMetaType() == TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_REG
                                || file.getMetaType() == TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_DIR;
                }
            }

            /**
             * Gets the meta-type the condition matches.
             *
             * @return A member of the MetaTypeCondition.Type enumeration.
             */
            Type getMetaType() {
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

            /**
             * @inheritDoc
             */
            @Override
            public abstract boolean passes(AbstractFile file);

        }

        /**
         * A file path condition for an interesting files set membership rule.
         * The immutability of a path condition object allows it to be safely
         * published to multiple threads.
         */
        static final class ParentPathCondition extends AbstractTextCondition {

            private static final long serialVersionUID = 1L;

            /**
             * Construct a case-insensitive file path condition.
             *
             * @param path The path to be matched.
             */
            ParentPathCondition(String path) {
                super(path, true);
            }

            /**
             * Construct a file path regular expression condition.
             *
             * @param path The path regular expression to be matched.
             */
            ParentPathCondition(Pattern path) {
                super(path);
            }

            /**
             * @inheritDoc
             */
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
        static final class FullNameCondition extends AbstractTextCondition implements FileNameCondition {

            private static final long serialVersionUID = 1L;

            /**
             * Construct a case-insensitive full file name condition.
             *
             * @param name The file name to be matched.
             */
            FullNameCondition(String name) {
                super(name, false);
            }

            /**
             * Construct a full file name regular expression condition.
             *
             * @param name The file name regular expression to be matched.
             */
            FullNameCondition(Pattern name) {
                super(name);
            }

            /**
             * @inheritDoc
             */
            @Override
            public boolean passes(AbstractFile file) {
                return this.textMatches(file.getName());
            }

        }

        /**
         * A file name extension condition for an interesting files set
         * membership rule. The immutability of a file name extension condition
         * object allows it to be safely published to multiple threads.
         */
        static final class ExtensionCondition extends AbstractTextCondition implements FileNameCondition {

            private static final long serialVersionUID = 1L;

            /**
             * Construct a case-insensitive file name extension condition.
             *
             * @param extension The file name extension to be matched.
             */
            ExtensionCondition(String extension) {
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
            ExtensionCondition(Pattern extension) {
                super(extension.pattern(), false);
            }

            /**
             * @inheritDoc
             */
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
             * @param textToMatch The text string.
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

            /**
             * @inheritDoc
             */
            @Override
            public String getTextToMatch() {
                return this.textToMatch;
            }

            /**
             * @inheritDoc
             */
            @Override
            public boolean isRegex() {
                return false;
            }

            /**
             * @inheritDoc
             */
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

            /**
             * @inheritDoc
             */
            @Override
            public String getTextToMatch() {
                return this.textToMatch;
            }

            /**
             * @inheritDoc
             */
            @Override
            public boolean isRegex() {
                return false;
            }

            /**
             * @inheritDoc
             */
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

            /**
             * @inheritDoc
             */
            @Override
            public String getTextToMatch() {
                return this.regex.pattern();
            }

            /**
             * @inheritDoc
             */
            @Override
            public boolean isRegex() {
                return true;
            }

            /**
             * @inheritDoc
             */
            @Override
            public boolean textMatches(String subject) {
                // A single match is sufficient.
                return this.regex.matcher(subject).find();
            }

        }

    }

}
