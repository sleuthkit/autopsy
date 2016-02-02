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

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.coreutils.XMLUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Provides access to interesting item definitions persisted to disk. Clients
 * receive copies of the most recent interesting item definitions via
 * synchronized methods, allowing the definitions to be safely published to
 * multiple threads.
 */
final class InterestingItemDefsManager extends Observable {

    private static final List<String> ILLEGAL_FILE_NAME_CHARS = Collections.unmodifiableList(new ArrayList<>(Arrays.asList("\\", "/", ":", "*", "?", "\"", "<", ">")));
    private static final List<String> ILLEGAL_FILE_PATH_CHARS = Collections.unmodifiableList(new ArrayList<>(Arrays.asList("\\", ":", "*", "?", "\"", "<", ">")));
    private static final String INTERESTING_FILES_SET_DEFS_FILE_NAME = "InterestingFilesSetDefs.xml"; //NON-NLS
    private static final String DEFAULT_FILE_SET_DEFS_PATH = PlatformUtil.getUserConfigDirectory() + File.separator + INTERESTING_FILES_SET_DEFS_FILE_NAME;
    private static InterestingItemDefsManager instance;

    /**
     * Gets the interesting item definitions manager singleton.
     */
    synchronized static InterestingItemDefsManager getInstance() {
        if (instance == null) {
            instance = new InterestingItemDefsManager();
        }
        return instance;
    }

    /**
     * Gets the set of chars deemed to be illegal in file names (Windows).
     *
     * @return A list of characters.
     */
    static List<String> getIllegalFileNameChars() {
        return InterestingItemDefsManager.ILLEGAL_FILE_NAME_CHARS;
    }

    /**
     * Gets the set of chars deemed to be illegal in file path
     * (SleuthKit/Windows).
     *
     * @return A list of characters.
     */
    static List<String> getIllegalFilePathChars() {
        return InterestingItemDefsManager.ILLEGAL_FILE_PATH_CHARS;
    }

    /**
     * Gets a copy of the current interesting files set definitions.
     *
     * @return A map of interesting files set names to interesting file sets,
     *         possibly empty.
     */
    synchronized Map<String, FilesSet> getInterestingFilesSets() {
        return FilesSetXML.readDefinitionsFile(DEFAULT_FILE_SET_DEFS_PATH);
    }

    /**
     * Sets the current interesting file sets definitions, replacing any
     * previous definitions.
     *
     * @param filesSets A mapping of interesting files set names to files sets,
     *                  used to enforce unique files set names.
     */
    synchronized void setInterestingFilesSets(Map<String, FilesSet> filesSets) {
        FilesSetXML.writeDefinitionsFile(DEFAULT_FILE_SET_DEFS_PATH, filesSets);
        this.setChanged();
        this.notifyObservers();
    }

    /**
     * Reads and writes interesting files set definitions to and from disk in
     * XML format.
     */
    private final static class FilesSetXML {

        private static final Logger logger = Logger.getLogger(FilesSetXML.class.getName());
        private static final String XML_ENCODING = "UTF-8"; //NON-NLS
        private static final List<String> illegalFileNameChars = InterestingItemDefsManager.getIllegalFileNameChars();

        // The following tags and attributes are identical to those used in the  
        // TSK Framework interesting files set definitions file schema.
        private static final String FILE_SETS_ROOT_TAG = "INTERESTING_FILE_SETS"; //NON-NLS
        private static final String FILE_SET_TAG = "INTERESTING_FILE_SET"; //NON-NLS
        private static final String NAME_RULE_TAG = "NAME"; //NON-NLS
        private static final String EXTENSION_RULE_TAG = "EXTENSION"; //NON-NLS
        private static final String NAME_ATTR = "name"; //NON-NLS
        private static final String RULE_UUID_ATTR = "ruleUUID"; //NON-NLS
        private static final String DESC_ATTR = "description"; //NON-NLS 
        private static final String IGNORE_KNOWN_FILES_ATTR = "ignoreKnown"; //NON-NLS
        private static final String TYPE_FILTER_ATTR = "typeFilter"; //NON-NLS
        private static final String PATH_FILTER_ATTR = "pathFilter"; //NON-NLS
        private static final String TYPE_FILTER_VALUE_FILES = "file"; //NON-NLS
        private static final String TYPE_FILTER_VALUE_DIRS = "dir"; //NON-NLS

        // The following tags and attributes are currently specific to the 
        // Autopsy implementation of interesting files set definitions. Autopsy
        // definitions that use these will not be able to be used by TSK 
        // Framework. However, Autopsy can accept TSK Framework definitions:
        // 
        // 1. Rules do not have names in the TSK Framework schema, but rules do
        // have names in the Autopsy schema. Names will be synthesized as needed
        // to allow Autopsy to use TSK Framework interesting files set 
        // definitions.
        // 2. The TSK Framework has an interesting files module that supports
        // simple globbing with "*" characters. Name rules and path filters with 
        // "*" characters will be converted to regexes to allow Autopsy to use 
        // TSK Framework interesting files set definitions.
        // 3. Type filters are required by Autopsy, but not by TSK Frmaework.
        // Missing type filters will defualt to "files" filters.
        private static final String REGEX_ATTR = "regex"; //NON-NLS
        private static final String PATH_REGEX_ATTR = "pathRegex"; //NON-NLS
        private static final String TYPE_FILTER_VALUE_FILES_AND_DIRS = "files_and_dirs"; //NON-NLS
        private static final String UNNAMED_LEGACY_RULE_PREFIX = "Unnamed Rule "; // NON-NLS
        private static int unnamedLegacyRuleCounter;

        /**
         * Reads interesting file set definitions from an XML file.
         *
         * @param filePath Path of the set definitions file as a string.
         *
         * @return The set definitions in a map of set names to sets.
         */
        // Note: This method takes a file path to support the possibility of 
        // multiple intersting files set definition files, e.g., one for 
        // definitions that ship with Autopsy and one for user definitions.
        static Map<String, FilesSet> readDefinitionsFile(String filePath) {
            Map<String, FilesSet> filesSets = new HashMap<>();

            // Check if the file exists.
            File defsFile = new File(filePath);
            if (!defsFile.exists()) {
                return filesSets;
            }

            // Check if the file can be read.
            if (!defsFile.canRead()) {
                logger.log(Level.SEVERE, "Interesting file sets definition file at {0} exists, but cannot be read", filePath); // NON-NLS
                return filesSets;
            }

            // Parse the XML in the file.
            Document doc = XMLUtil.loadDoc(FilesSetXML.class, filePath);
            if (doc == null) {
                logger.log(Level.SEVERE, "Failed to parse interesting file sets definition file at {0}", filePath); // NON-NLS
                return filesSets;
            }

            // Get the root element.
            Element root = doc.getDocumentElement();
            if (root == null) {
                logger.log(Level.SEVERE, "Failed to get root {0} element tag of interesting file sets definition file at {1}", new Object[]{FilesSetXML.FILE_SETS_ROOT_TAG, filePath}); // NON-NLS
                return filesSets;
            }

            // Read in the files set definitions.
            NodeList setElems = root.getElementsByTagName(FILE_SET_TAG);
            for (int i = 0; i < setElems.getLength(); ++i) {
                readFilesSet((Element) setElems.item(i), filesSets, filePath);
            }

            return filesSets;
        }

        /**
         * Reads in an interesting files set.
         *
         * @param setElem   An interesting files set XML element
         * @param filesSets A collection to which the set is to be added.
         * @param filePath  The source file, used for error reporting.
         */
        private static void readFilesSet(Element setElem, Map<String, FilesSet> filesSets, String filePath) {
            // The file set must have a unique name.
            String setName = setElem.getAttribute(FilesSetXML.NAME_ATTR);
            if (setName.isEmpty()) {
                logger.log(Level.SEVERE, "Found {0} element without required {1} attribute, ignoring malformed file set definition in interesting file sets definition file at {2}", new Object[]{FilesSetXML.FILE_SET_TAG, FilesSetXML.NAME_ATTR, filePath}); // NON-NLS
                return;
            }
            if (filesSets.containsKey(setName)) {
                logger.log(Level.SEVERE, "Found duplicate definition of set named {0} in interesting file sets definition file at {1}, discarding duplicate set", new Object[]{setName, filePath}); // NON-NLS
                return;
            }

            // The file set may have a description. The empty string is o.k. 
            String description = setElem.getAttribute(FilesSetXML.DESC_ATTR);

            // The file set may or may not ignore known files. The default behavior
            // is to not ignore them.
            String ignoreKnown = setElem.getAttribute(FilesSetXML.IGNORE_KNOWN_FILES_ATTR);
            boolean ignoreKnownFiles = false;
            if (!ignoreKnown.isEmpty()) {
                ignoreKnownFiles = Boolean.parseBoolean(ignoreKnown);
            }

            // Read file name set membership rules, if any.
            FilesSetXML.unnamedLegacyRuleCounter = 1;
            Map<String, FilesSet.Rule> rules = new HashMap<>();
            NodeList nameRuleElems = setElem.getElementsByTagName(FilesSetXML.NAME_RULE_TAG);
            for (int j = 0; j < nameRuleElems.getLength(); ++j) {
                Element elem = (Element) nameRuleElems.item(j);
                FilesSet.Rule rule = FilesSetXML.readFileNameRule(elem);
                if (rule != null) {
                    if (!rules.containsKey(rule.getUuid())) {
                        rules.put(rule.getUuid(), rule);
                    } else {
                        logger.log(Level.SEVERE, "Found duplicate rule {0} for set named {1} in interesting file sets definition file at {2}, discarding malformed set", new Object[]{rule.getUuid(), setName, filePath}); // NON-NLS
                        return;
                    }
                } else {
                    logger.log(Level.SEVERE, "Found malformed rule for set named {0} in interesting file sets definition file at {1}, discarding malformed set", new Object[]{setName, filePath}); // NON-NLS
                    return;
                }
            }

            // Read file extension set membership rules, if any.
            NodeList extRuleElems = setElem.getElementsByTagName(FilesSetXML.EXTENSION_RULE_TAG);
            for (int j = 0; j < extRuleElems.getLength(); ++j) {
                Element elem = (Element) extRuleElems.item(j);
                FilesSet.Rule rule = FilesSetXML.readFileExtensionRule(elem);
                if (rule != null) {
                    if (!rules.containsKey(rule.getUuid())) {
                        rules.put(rule.getUuid(), rule);
                    } else {
                        logger.log(Level.SEVERE, "Found duplicate rule {0} for set named {1} in interesting file sets definition file at {2}, discarding malformed set", new Object[]{rule.getUuid(), setName, filePath}); //NOI18N NON-NLS
                        return;
                    }
                } else {
                    logger.log(Level.SEVERE, "Found malformed rule for set named {0} in interesting file sets definition file at {1}, discarding malformed set", new Object[]{setName, filePath}); //NOI18N NON-NLS
                    return;
                }
            }

            // Make the files set. Note that degenerate sets with no rules are
            // allowed to facilitate the separation of set definition and rule
            // definitions. A set without rules is simply the empty set.
            FilesSet set = new FilesSet(setName, description, ignoreKnownFiles, rules);
            filesSets.put(set.getName(), set);
        }

        /**
         * Construct an interesting files set file name rule from the data in an
         * XML element.
         *
         * @param filePath The path of the definitions file.
         * @param setName  The name of the files set.
         * @param elem     The file name rule XML element.
         *
         * @return A file name rule, or null if there is an error (the error is
         *         logged).
         */
        private static FilesSet.Rule readFileNameRule(Element elem) {
            String ruleName = FilesSetXML.readRuleName(elem);

            // The content of the rule tag is a file name filter. It may be a  
            // regex, or it may be from a TSK Framework rule definition with a 
            // "*" globbing char, or it may be simple text.
            String content = elem.getTextContent();
            FilesSet.Rule.FullNameFilter nameFilter;
            String regex = elem.getAttribute(FilesSetXML.REGEX_ATTR);
            if ((!regex.isEmpty() && regex.equalsIgnoreCase("true")) || content.contains("*")) { // NON-NLS
                Pattern pattern = compileRegex(content);
                if (pattern != null) {
                    nameFilter = new FilesSet.Rule.FullNameFilter(pattern);
                } else {
                    logger.log(Level.SEVERE, "Error compiling " + FilesSetXML.NAME_RULE_TAG + " regex, ignoring malformed '{0}' rule definition", ruleName); // NON-NLS
                    return null;
                }
            } else {
                for (String illegalChar : illegalFileNameChars) {
                    if (content.contains(illegalChar)) {
                        logger.log(Level.SEVERE, FilesSetXML.NAME_RULE_TAG + " content has illegal chars, ignoring malformed '{0}' rule definition", new Object[]{FilesSetXML.NAME_RULE_TAG, ruleName}); // NON-NLS
                        return null;
                    }
                }
                nameFilter = new FilesSet.Rule.FullNameFilter(content);
            }

            // Read in the type filter.
            FilesSet.Rule.MetaTypeFilter metaTypeFilter = FilesSetXML.readMetaTypeFilter(elem);
            if (metaTypeFilter == null) {
                // Malformed attribute.
                return null;
            }

            // Read in the optional path filter. Null is o.k., but if the attribute
            // is there, be sure it is not malformed.
            FilesSet.Rule.ParentPathFilter pathFilter = null;
            if (!elem.getAttribute(FilesSetXML.PATH_FILTER_ATTR).isEmpty()
                    || !elem.getAttribute(FilesSetXML.PATH_REGEX_ATTR).isEmpty()) {
                pathFilter = FilesSetXML.readPathFilter(elem);
                if (pathFilter == null) {
                    // Malformed attribute.
                    return null;
                }
            }

            return new FilesSet.Rule(ruleName, nameFilter, metaTypeFilter, pathFilter);
        }

        /**
         * Construct an interesting files set file name extension rule from the
         * data in an XML element.
         *
         * @param elem The file name extension rule XML element.
         *
         * @return A file name extension rule, or null if there is an error (the
         *         error is logged).
         */
        private static FilesSet.Rule readFileExtensionRule(Element elem) {
            String ruleName = FilesSetXML.readRuleName(elem);

            // The content of the rule tag is a file name extension filter. It may 
            // be a regex, or it may be from a TSK Framework rule definition 
            // with a "*" globbing char.
            String content = elem.getTextContent();
            FilesSet.Rule.ExtensionFilter extFilter;
            String regex = elem.getAttribute(FilesSetXML.REGEX_ATTR);
            if ((!regex.isEmpty() && regex.equalsIgnoreCase("true")) || content.contains("*")) { // NON-NLS
                Pattern pattern = compileRegex(content);
                if (pattern != null) {
                    extFilter = new FilesSet.Rule.ExtensionFilter(pattern);
                } else {
                    logger.log(Level.SEVERE, "Error compiling " + FilesSetXML.EXTENSION_RULE_TAG + " regex, ignoring malformed {0} rule definition", ruleName); // NON-NLS
                    return null;
                }
            } else {
                for (String illegalChar : illegalFileNameChars) {
                    if (content.contains(illegalChar)) {
                        logger.log(Level.SEVERE, "{0} content has illegal chars, ignoring malformed {1} rule definition", ruleName); // NON-NLS
                        return null;
                    }
                }
                extFilter = new FilesSet.Rule.ExtensionFilter(content);
            }

            // The rule must have a meta-type filter, unless a TSK Framework
            // definitions file is being read.
            FilesSet.Rule.MetaTypeFilter metaTypeFilter = null;
            if (!elem.getAttribute(FilesSetXML.TYPE_FILTER_ATTR).isEmpty()) {
                metaTypeFilter = FilesSetXML.readMetaTypeFilter(elem);
                if (metaTypeFilter == null) {
                    // Malformed attribute.
                    return null;
                }
            } else {
                metaTypeFilter = new FilesSet.Rule.MetaTypeFilter(FilesSet.Rule.MetaTypeFilter.Type.FILES);
            }

            // The rule may have a path filter. Null is o.k., but if the attribute
            // is there, it must not be malformed.
            FilesSet.Rule.ParentPathFilter pathFilter = null;
            if (!elem.getAttribute(FilesSetXML.PATH_FILTER_ATTR).isEmpty()
                    || !elem.getAttribute(FilesSetXML.PATH_REGEX_ATTR).isEmpty()) {
                pathFilter = FilesSetXML.readPathFilter(elem);
                if (pathFilter == null) {
                    // Malformed attribute.
                    return null;
                }
            }

            return new FilesSet.Rule(ruleName, extFilter, metaTypeFilter, pathFilter);
        }

        /**
         * Read a rule name attribute from a rule element.
         *
         * @param elem A rule element.
         *
         * @return A rule name.
         */
        private static String readRuleName(Element elem) {
            // The rule must have a name. 
            String ruleName = elem.getAttribute(FilesSetXML.NAME_ATTR);
            return ruleName;
        }

        /**
         * Attempts to compile a regular expression.
         *
         * @param regex The regular expression.
         *
         * @return A pattern object, or null if the compilation fails.
         */
        private static Pattern compileRegex(String regex) {
            try {
                return Pattern.compile(regex);
            } catch (PatternSyntaxException ex) {
                logger.log(Level.SEVERE, "Error compiling rule regex: " + ex.getMessage(), ex); // NON-NLS
                return null;
            }
        }

        /**
         * Construct a meta-type filter for an interesting files set membership
         * rule from data in an XML element.
         *
         * @param ruleElement The XML element.
         *
         * @return The meta-type filter, or null if there is an error (logged).
         */
        private static FilesSet.Rule.MetaTypeFilter readMetaTypeFilter(Element ruleElement) {
            FilesSet.Rule.MetaTypeFilter filter = null;
            String filterAttribute = ruleElement.getAttribute(FilesSetXML.TYPE_FILTER_ATTR);
            if (!filterAttribute.isEmpty()) {
                switch (filterAttribute) {
                    case FilesSetXML.TYPE_FILTER_VALUE_FILES:
                        filter = new FilesSet.Rule.MetaTypeFilter(FilesSet.Rule.MetaTypeFilter.Type.FILES);
                        break;
                    case FilesSetXML.TYPE_FILTER_VALUE_DIRS:
                        filter = new FilesSet.Rule.MetaTypeFilter(FilesSet.Rule.MetaTypeFilter.Type.DIRECTORIES);
                        break;
                    case FilesSetXML.TYPE_FILTER_VALUE_FILES_AND_DIRS:
                        filter = new FilesSet.Rule.MetaTypeFilter(FilesSet.Rule.MetaTypeFilter.Type.FILES_AND_DIRECTORIES);
                        break;
                    default:
                        logger.log(Level.SEVERE, "Found {0} " + FilesSetXML.TYPE_FILTER_ATTR + " attribute with unrecognized value ''{0}'', ignoring malformed rule definition", filterAttribute); // NON-NLS
                        break;
                }
            } else {
                // Accept TSK Framework interesting files set definitions, 
                // default to files.
                filter = new FilesSet.Rule.MetaTypeFilter(FilesSet.Rule.MetaTypeFilter.Type.FILES);
            }
            return filter;
        }

        /**
         * Construct a path filter for an interesting files set membership rule
         * from data in an XML element.
         *
         * @param ruleElement The XML element.
         *
         * @return The path filter, or null if there is an error (logged).
         */
        private static FilesSet.Rule.ParentPathFilter readPathFilter(Element ruleElement) {
            FilesSet.Rule.ParentPathFilter filter = null;
            String path = ruleElement.getAttribute(FilesSetXML.PATH_FILTER_ATTR);
            String pathRegex = ruleElement.getAttribute(FilesSetXML.PATH_REGEX_ATTR);
            if (!pathRegex.isEmpty() && path.isEmpty()) {
                try {
                    Pattern pattern = Pattern.compile(pathRegex);
                    filter = new FilesSet.Rule.ParentPathFilter(pattern);
                } catch (PatternSyntaxException ex) {
                    logger.log(Level.SEVERE, "Error compiling " + FilesSetXML.PATH_REGEX_ATTR + " regex, ignoring malformed path filter definition", ex); // NON-NLS
                }
            } else if (!path.isEmpty() && pathRegex.isEmpty()) {
                filter = new FilesSet.Rule.ParentPathFilter(path);
            }
            return filter;
        }

        /**
         * Writes interesting files set definitions to disk as an XML file,
         * logging any errors.
         *
         * @param filePath Path of the set definitions file as a string.
         *
         * @returns True if the definitions are written to disk, false
         * otherwise.
         */
        // Note: This method takes a file path to support the possibility of 
        // multiple intersting files set definition files, e.g., one for 
        // definitions that ship with Autopsy and one for user definitions.
        static boolean writeDefinitionsFile(String filePath, Map<String, FilesSet> interestingFilesSets) {
            DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
            try {
                // Create the new XML document.
                DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
                Document doc = docBuilder.newDocument();
                Element rootElement = doc.createElement(FilesSetXML.FILE_SETS_ROOT_TAG);
                doc.appendChild(rootElement);

                // Add the interesting files sets to the document.
                for (FilesSet set : interestingFilesSets.values()) {
                    // Add the files set element and its attributes.
                    Element setElement = doc.createElement(FilesSetXML.FILE_SET_TAG);
                    setElement.setAttribute(FilesSetXML.NAME_ATTR, set.getName());
                    setElement.setAttribute(FilesSetXML.DESC_ATTR, set.getDescription());
                    setElement.setAttribute(FilesSetXML.IGNORE_KNOWN_FILES_ATTR, Boolean.toString(set.ignoresKnownFiles()));

                    // Add the child elements for the set membership rules.
                    for (FilesSet.Rule rule : set.getRules().values()) {
                        // Add a rule element with the appropriate name filter 
                        // type tag.
                        FilesSet.Rule.FileNameFilter nameFilter = rule.getFileNameFilter();
                        Element ruleElement;
                        if (nameFilter instanceof FilesSet.Rule.FullNameFilter) {
                            ruleElement = doc.createElement(FilesSetXML.NAME_RULE_TAG);
                        } else {
                            ruleElement = doc.createElement(FilesSetXML.EXTENSION_RULE_TAG);
                        }

                        // Add the rule name attribute.
                        ruleElement.setAttribute(FilesSetXML.NAME_ATTR, rule.getName());

                        // Add the name filter regex attribute
                        ruleElement.setAttribute(FilesSetXML.REGEX_ATTR, Boolean.toString(nameFilter.isRegex()));

                        // Add the type filter attribute.
                        FilesSet.Rule.MetaTypeFilter typeFilter = rule.getMetaTypeFilter();
                        switch (typeFilter.getMetaType()) {
                            case FILES:
                                ruleElement.setAttribute(FilesSetXML.TYPE_FILTER_ATTR, FilesSetXML.TYPE_FILTER_VALUE_FILES);
                                break;
                            case DIRECTORIES:
                                ruleElement.setAttribute(FilesSetXML.TYPE_FILTER_ATTR, FilesSetXML.TYPE_FILTER_VALUE_DIRS);
                                break;
                            default:
                                ruleElement.setAttribute(FilesSetXML.TYPE_FILTER_ATTR, FilesSetXML.TYPE_FILTER_VALUE_FILES_AND_DIRS);
                                break;
                        }

                        // Add the optional path filter.
                        FilesSet.Rule.ParentPathFilter pathFilter = rule.getPathFilter();
                        if (pathFilter != null) {
                            if (pathFilter.isRegex()) {
                                ruleElement.setAttribute(FilesSetXML.PATH_REGEX_ATTR, pathFilter.getTextToMatch());
                            } else {
                                ruleElement.setAttribute(FilesSetXML.PATH_FILTER_ATTR, pathFilter.getTextToMatch());
                            }
                        }

                        // Add the name filter text as the rule element content.
                        ruleElement.setTextContent(nameFilter.getTextToMatch());

                        setElement.appendChild(ruleElement);
                    }

                    rootElement.appendChild(setElement);
                }

                // Overwrite the previous definitions file. Note that the utility 
                // method logs an error on failure.
                return XMLUtil.saveDoc(FilesSetXML.class, filePath, XML_ENCODING, doc);

            } catch (ParserConfigurationException ex) {
                logger.log(Level.SEVERE, "Error writing interesting files definition file to " + filePath, ex); // NON-NLS
                return false;
            }

        }

    }

}
