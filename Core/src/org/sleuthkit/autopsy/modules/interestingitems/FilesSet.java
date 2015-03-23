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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
final class FilesSet {

    private final String name;
    private final String description;
    private final boolean ignoreKnownFiles;
    private final Map<String, Rule> rules = new HashMap<>();

    /**
     * Constructs an interesting files set.
     *
     * @param name The name of the set.
     * @param description A description of the set, may be null.
     * @param ignoreKnownFiles Whether or not to exclude known files from the
     * set.
     * @param rules The rules that define the set. May be null, but a set with
     * no rules is the empty set.
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
     * @return The name of the first set membership rule satisfied by the file,
     * will be null if the file does not belong to the set.
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
    static class Rule {

        private final String ruleName;
        private final FileNameFilter fileNameFilter;
        private final MetaTypeFilter metaTypeFilter;
        private final ParentPathFilter pathFilter;
        private final List<FileAttributeFilter> filters = new ArrayList<>();

        /**
         * Construct an interesting files set membership rule.
         *
         * @param ruleName The name of the rule.
         * @param fileNameFilter A file name filter.
         * @param metaTypeFilter A file meta-type filter.
         * @param pathFilter A file path filter, may be null.
         */
        Rule(String ruleName, FileNameFilter fileNameFilter, MetaTypeFilter metaTypeFilter, ParentPathFilter pathFilter) {
            if ((ruleName == null) || (ruleName.isEmpty())) {
                throw new IllegalArgumentException("Interesting files set rule name cannot be null or empty");
            }
            if (fileNameFilter == null) {
                throw new IllegalArgumentException("Interesting files set rule file name filter cannot be null");
            }
            if (metaTypeFilter == null) {
                throw new IllegalArgumentException("Interesting files set rule meta-type filter cannot be null");
            }
            this.ruleName = ruleName;
            this.fileNameFilter = fileNameFilter;
            this.filters.add(fileNameFilter);
            this.metaTypeFilter = metaTypeFilter;
            this.filters.add(this.metaTypeFilter);
            this.pathFilter = pathFilter;
            if (this.pathFilter != null) {
                this.filters.add(this.pathFilter);
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
         * Get the file name filter for the rule.
         *
         * @return A file name filter.
         */
        FileNameFilter getFileNameFilter() {
            return this.fileNameFilter;
        }

        /**
         * Get the meta-type filter for the rule.
         *
         * @return A meta-type filter.
         */
        MetaTypeFilter getMetaTypeFilter() {
            return this.metaTypeFilter;
        }

        /**
         * Get the path filter for the rule.
         *
         * @return A path filter, may be null.
         */
        ParentPathFilter getPathFilter() {
            return this.pathFilter;
        }

        /**
         * Determines whether or not a file satisfies the rule.
         *
         * @param file The file to test.
         * @return True if the rule is satisfied, false otherwise.
         */
        boolean isSatisfied(AbstractFile file) {
            for (FileAttributeFilter filter : filters) {
                if (!filter.passes(file)) {
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
            return this.ruleName + " (" + fileNameFilter.getTextToMatch() + ")";
        }

        /**
         * An interface for the file attribute filters of which interesting
         * files set membership rules are composed.
         */
        static interface FileAttributeFilter {

            /**
             * Tests whether or not a file satisfies the conditions of a filter.
             *
             * @param file The file to test.
             * @return True if the file passes the test, false otherwise.
             */
            boolean passes(AbstractFile file);
        }

        /**
         * A file meta-type filter for an interesting files set membership rule.
         * The immutability of a meta-type filter object allows it to be safely
         * published to multiple threads.
         */
        static final class MetaTypeFilter implements FileAttributeFilter {

            enum Type {

                FILES,
                DIRECTORIES,
                FILES_AND_DIRECTORIES
            }

            private final Type type;

            /**
             * Construct a meta-type filter.
             *
             * @param metaType The meta-type to match, must.
             */
            MetaTypeFilter(Type type) {
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
             * Gets the meta-type the filter matches.
             *
             * @return A member of the MetaTypeFilter.Type enumeration.
             */
            Type getMetaType() {
                return this.type;
            }
        }

        /**
         * An interface for file attribute filters that do textual matching.
         */
        static interface TextFilter extends FileAttributeFilter {

            /**
             * Gets the text the filter matches.
             *
             * @return The text.
             */
            String getTextToMatch();

            /**
             * Queries whether or not the text the filter matches is a regular
             * expression.
             *
             * @return True if the text to be matched is a regular expression,
             * false otherwise.
             */
            boolean isRegex();

            /**
             * Determines whether a string of text matches the filter.
             *
             * @param textToMatch The text string.
             * @return True if the text matches, false otherwise.
             */
            boolean textMatches(String textToMatch);

        }

        /**
         * An abstract base class for file attribute filters that do textual
         * matching.
         */
        private static abstract class AbstractTextFilter implements TextFilter {

            private final TextMatcher textMatcher;

            /**
             * Construct a case-insensitive text filter.
             *
             * @param text The text to be matched.
             */
            AbstractTextFilter(String text) {
                this.textMatcher = new FilesSet.Rule.CaseInsensitiveStringComparisionMatcher(text);
            }

            /**
             * Construct a regular expression text filter.
             *
             * @param regex The regular expression to be matched.
             */
            AbstractTextFilter(Pattern regex) {
                this.textMatcher = new FilesSet.Rule.RegexMatcher(regex);
            }

            /**
             * Get the text the filter matches.
             *
             * @return The text.
             */
            @Override
            public String getTextToMatch() {
                return this.textMatcher.getTextToMatch();
            }

            /**
             * Queries whether or not the text the filter matches is a regular
             * expression.
             *
             * @return True if the text to be matched is a regular expression,
             * false otherwise.
             */
            @Override
            public boolean isRegex() {
                return this.textMatcher.isRegex();
            }

            /**
             * Determines whether a string of text matches the filter.
             *
             * @param textToMatch The text string.
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
         * A file path filter for an interesting files set membership rule. The
         * immutability of a path filter object allows it to be safely published
         * to multiple threads.
         */
        static final class ParentPathFilter extends AbstractTextFilter {

            /**
             * Construct a case-insensitive file path filter.
             *
             * @param path The path to be matched.
             */
            ParentPathFilter(String path) {
                super(path);
            }

            /**
             * Construct a file path regular expression filter.
             *
             * @param path The path regular expression to be matched.
             */
            ParentPathFilter(Pattern path) {
                super(path);
            }

            /**
             * @inheritDoc
             */
            @Override
            public boolean passes(AbstractFile file) {
                return this.textMatches(file.getParentPath());
            }

        }

        /**
         * A "tagging" interface to group name and extension filters separately
         * from path filters for type safety when constructing rules.
         */
        static interface FileNameFilter extends TextFilter {
        }

        /**
         * A file name filter for an interesting files set membership rule. The
         * immutability of a file name filter object allows it to be safely
         * published to multiple threads.
         */
        static final class FullNameFilter extends AbstractTextFilter implements FileNameFilter {

            /**
             * Construct a case-insensitive full file name filter.
             *
             * @param name The file name to be matched.
             */
            FullNameFilter(String name) {
                super(name);
            }

            /**
             * Construct a full file name regular expression filter.
             *
             * @param name The file name regular expression to be matched.
             */
            FullNameFilter(Pattern name) {
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
         * A file name extension filter for an interesting files set membership
         * rule. The immutability of a file name extension filter object allows
         * it to be safely published to multiple threads.
         */
        static final class ExtensionFilter extends AbstractTextFilter implements FileNameFilter {

            /**
             * Construct a case-insensitive file name extension filter.
             *
             * @param extension The file name extension to be matched.
             */
            ExtensionFilter(String extension) {
                // If there is a leading ".", strip it since 
                // AbstractFile.getFileNameExtension() returns just the 
                // extension chars and not the dot.
                super(extension.startsWith(".") ? extension.substring(1) : extension);
            }

            /**
             * Construct a file name extension regular expression filter.
             *
             * @param extension The file name extension regular expression to be
             * matched.
             */
            ExtensionFilter(Pattern extension) {
                super(extension.pattern());
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
         * text filter.
         */
        private static interface TextMatcher {

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
             * false otherwise.
             */
            boolean isRegex();

            /**
             * Determines whether a string of text is matched.
             *
             * @param textToMatch The text string.
             * @return True if the text matches, false otherwise.
             */
            boolean textMatches(String subject);

        }

        /**
         * A text matcher that does a case-insensitive string comparison.
         */
        private static class CaseInsensitiveStringComparisionMatcher implements TextMatcher {

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
         * A text matcher that does regular expression matching.
         */
        private static class RegexMatcher implements TextMatcher {

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
