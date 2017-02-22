/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.openide.util.io.NbObjectInputStream;
import org.openide.util.io.NbObjectOutputStream;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.coreutils.XMLUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 *
 * @author oliver
 */
class InterestingItemsFilesSetSettings implements Serializable {

    private static final long serialVersionUID = 1L;
    // The following tags and attributes are identical to those used in the
    // TSK Framework FilesSet definitions file schema.
    private static final String FILE_SETS_ROOT_TAG = "INTERESTING_FILE_SETS"; //NON-NLS
    private static final String DESC_ATTR = "description"; //NON-NLS
    private static final String RULE_UUID_ATTR = "ruleUUID"; //NON-NLS
    private static final String IGNORE_KNOWN_FILES_ATTR = "ignoreKnown"; //NON-NLS
    private static final String PATH_REGEX_ATTR = "pathRegex"; //NON-NLS
    private static final String TYPE_FILTER_VALUE_FILES_AND_DIRS = "files_and_dirs"; //NON-NLS
    private static final String IGNORE_UNALLOCATED_SPACE = "ingoreUnallocated"; //NON-NLS
    private static final String PATH_FILTER_ATTR = "pathFilter"; //NON-NLS
    private static final String TYPE_FILTER_VALUE_DIRS = "dir"; //NON-NLS
    private static final String REGEX_ATTR = "regex"; //NON-NLS
    private static final List<String> illegalFileNameChars = FilesSetsManager.getIllegalFileNameChars();
    private static final String FILE_SET_TAG = "INTERESTING_FILE_SET"; //NON-NLS
    private static final String NAME_RULE_TAG = "NAME"; //NON-NLS
    private static final String UNNAMED_LEGACY_RULE_PREFIX = "Unnamed Rule "; // NON-NLS
    private static final String NAME_ATTR = "name"; //NON-NLS
    private static final String TYPE_FILTER_VALUE_FILES = "file"; //NON-NLS
    private static final String XML_ENCODING = "UTF-8"; //NON-NLS
    private static final Logger logger = Logger.getLogger(InterestingItemsFilesSetSettings.class.getName());
    private static int unnamedLegacyRuleCounter;
    private static final String TYPE_FILTER_ATTR = "typeFilter"; //NON-NLS
    private static final String EXTENSION_RULE_TAG = "EXTENSION"; //NON-NLS

    private Map<String, FilesSet> filesSets;

    InterestingItemsFilesSetSettings(Map<String, FilesSet> filesSets) {
        this.filesSets = filesSets;
    }

    /**
     * @return the filesSets
     */
    Map<String, FilesSet> getFilesSets() {
        return filesSets;
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
        String ruleName = elem.getAttribute(InterestingItemsFilesSetSettings.NAME_ATTR);
        return ruleName;
    }

    /**
     * Reads the definitions from the serialization file
     *
     * @return the map representing settings saved to serialization file, empty
     *         set if the file does not exist.
     *
     * @throws FilesSetsManagerException if file could not be read
     */
    private static Map<String, FilesSet> readSerializedDefinitions(String serialFileName) throws FilesSetsManager.FilesSetsManagerException {
        Path filePath = Paths.get(PlatformUtil.getUserConfigDirectory(), serialFileName);
        File fileSetFile = filePath.toFile();
        String filePathStr = filePath.toString();
        if (fileSetFile.exists()) {
            try {
                try (final NbObjectInputStream in = new NbObjectInputStream(new FileInputStream(filePathStr))) {
                    InterestingItemsFilesSetSettings filesSetsSettings = (InterestingItemsFilesSetSettings) in.readObject();
                    return filesSetsSettings.getFilesSets();
                }
            } catch (IOException | ClassNotFoundException ex) {
                throw new FilesSetsManager.FilesSetsManagerException(String.format("Failed to read settings from %s", filePathStr), ex);
            }
        } else {
            return new HashMap<>();
        }
    }

    /**
     * Construct a path condition for a FilesSet membership rule from data in an
     * XML element.
     *
     * @param ruleElement The XML element.
     *
     * @return The path condition, or null if there is an error (logged).
     */
    private static FilesSet.Rule.ParentPathCondition readPathCondition(Element ruleElement) {
        FilesSet.Rule.ParentPathCondition condition = null;
        String path = ruleElement.getAttribute(InterestingItemsFilesSetSettings.PATH_FILTER_ATTR);
        String pathRegex = ruleElement.getAttribute(InterestingItemsFilesSetSettings.PATH_REGEX_ATTR);
        if (!pathRegex.isEmpty() && path.isEmpty()) {
            try {
                Pattern pattern = Pattern.compile(pathRegex);
                condition = new FilesSet.Rule.ParentPathCondition(pattern);
            } catch (PatternSyntaxException ex) {
                logger.log(Level.SEVERE, "Error compiling " + InterestingItemsFilesSetSettings.PATH_REGEX_ATTR + " regex, ignoring malformed path condition definition", ex); // NON-NLS
            }
        } else if (!path.isEmpty() && pathRegex.isEmpty()) {
            condition = new FilesSet.Rule.ParentPathCondition(path);
        }
        return condition;
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
     * Construct a FilesSet file name extension rule from the data in an XML
     * element.
     *
     * @param elem The file name extension rule XML element.
     *
     * @return A file name extension rule, or null if there is an error (the
     *         error is logged).
     */
    private static FilesSet.Rule readFileExtensionRule(Element elem) {
        String ruleName = InterestingItemsFilesSetSettings.readRuleName(elem);
        // The content of the rule tag is a file name extension condition. It may
        // be a regex, or it may be from a TSK Framework rule definition
        // with a "*" globbing char.
        String content = elem.getTextContent();
        FilesSet.Rule.ExtensionCondition extCondition;
        String regex = elem.getAttribute(InterestingItemsFilesSetSettings.REGEX_ATTR);
        if ((!regex.isEmpty() && regex.equalsIgnoreCase("true")) || content.contains("*")) {
            // NON-NLS
            Pattern pattern = compileRegex(content);
            if (pattern != null) {
                extCondition = new FilesSet.Rule.ExtensionCondition(pattern);
            } else {
                logger.log(Level.SEVERE, "Error compiling " + InterestingItemsFilesSetSettings.EXTENSION_RULE_TAG + " regex, ignoring malformed {0} rule definition", ruleName); // NON-NLS
                return null;
            }
        } else {
            for (String illegalChar : illegalFileNameChars) {
                if (content.contains(illegalChar)) {
                    logger.log(Level.SEVERE, "{0} content has illegal chars, ignoring malformed {1} rule definition", ruleName); // NON-NLS
                    return null;
                }
            }
            extCondition = new FilesSet.Rule.ExtensionCondition(content);
        }
        // The rule must have a meta-type condition, unless a TSK Framework
        // definitions file is being read.
        FilesSet.Rule.MetaTypeCondition metaTypeCondition = null;
        if (!elem.getAttribute(InterestingItemsFilesSetSettings.TYPE_FILTER_ATTR).isEmpty()) {
            metaTypeCondition = InterestingItemsFilesSetSettings.readMetaTypeCondition(elem);
            if (metaTypeCondition == null) {
                // Malformed attribute.
                return null;
            }
        } else {
            metaTypeCondition = new FilesSet.Rule.MetaTypeCondition(FilesSet.Rule.MetaTypeCondition.Type.FILES);
        }
        // The rule may have a path condition. Null is o.k., but if the attribute
        // is there, it must not be malformed.
        FilesSet.Rule.ParentPathCondition pathCondition = null;
        if (!elem.getAttribute(InterestingItemsFilesSetSettings.PATH_FILTER_ATTR).isEmpty() || !elem.getAttribute(InterestingItemsFilesSetSettings.PATH_REGEX_ATTR).isEmpty()) {
            pathCondition = InterestingItemsFilesSetSettings.readPathCondition(elem);
            if (pathCondition == null) {
                // Malformed attribute.
                return null;
            }
        }
        return new FilesSet.Rule(ruleName, extCondition, metaTypeCondition, pathCondition, null, null);
    }

    /**
     * Construct a FilesSet file name rule from the data in an XML element.
     *
     * @param elem The file name rule XML element.
     *
     * @return A file name rule, or null if there is an error (the error is
     *         logged).
     */
    private static FilesSet.Rule readFileNameRule(Element elem) {
        String ruleName = InterestingItemsFilesSetSettings.readRuleName(elem);
        // The content of the rule tag is a file name condition. It may be a
        // regex, or it may be from a TSK Framework rule definition with a
        // "*" globbing char, or it may be simple text.
        String content = elem.getTextContent();
        FilesSet.Rule.FullNameCondition nameCondition;
        String regex = elem.getAttribute(InterestingItemsFilesSetSettings.REGEX_ATTR);
        if ((!regex.isEmpty() && regex.equalsIgnoreCase("true")) || content.contains("*")) {
            // NON-NLS
            Pattern pattern = compileRegex(content);
            if (pattern != null) {
                nameCondition = new FilesSet.Rule.FullNameCondition(pattern);
            } else {
                logger.log(Level.SEVERE, "Error compiling " + InterestingItemsFilesSetSettings.NAME_RULE_TAG + " regex, ignoring malformed '{0}' rule definition", ruleName); // NON-NLS
                return null;
            }
        } else {
            for (String illegalChar : illegalFileNameChars) {
                if (content.contains(illegalChar)) {
                    logger.log(Level.SEVERE, InterestingItemsFilesSetSettings.NAME_RULE_TAG + " content has illegal chars, ignoring malformed '{0}' rule definition", new Object[]{InterestingItemsFilesSetSettings.NAME_RULE_TAG, ruleName}); // NON-NLS
                    return null;
                }
            }
            nameCondition = new FilesSet.Rule.FullNameCondition(content);
        }
        // Read in the type condition.
        FilesSet.Rule.MetaTypeCondition metaTypeCondition = InterestingItemsFilesSetSettings.readMetaTypeCondition(elem);
        if (metaTypeCondition == null) {
            // Malformed attribute.
            return null;
        }
        // Read in the optional path condition. Null is o.k., but if the attribute
        // is there, be sure it is not malformed.
        FilesSet.Rule.ParentPathCondition pathCondition = null;
        if (!elem.getAttribute(InterestingItemsFilesSetSettings.PATH_FILTER_ATTR).isEmpty() || !elem.getAttribute(InterestingItemsFilesSetSettings.PATH_REGEX_ATTR).isEmpty()) {
            pathCondition = InterestingItemsFilesSetSettings.readPathCondition(elem);
            if (pathCondition == null) {
                // Malformed attribute.
                return null;
            }
        }
        return new FilesSet.Rule(ruleName, nameCondition, metaTypeCondition, pathCondition, null, null);
    }

    /**
     * Reads in a FilesSet.
     *
     * @param setElem   A FilesSet XML element
     * @param filesSets A collection to which the set is to be added.
     * @param filePath  The source file, used for error reporting.
     */
    private static void readFilesSet(Element setElem, Map<String, FilesSet> filesSets, String filePath) {
        // The file set must have a unique name.
        String setName = setElem.getAttribute(InterestingItemsFilesSetSettings.NAME_ATTR);
        if (setName.isEmpty()) {
            logger.log(Level.SEVERE, "Found {0} element without required {1} attribute, ignoring malformed file set definition in FilesSet definition file at {2}", new Object[]{InterestingItemsFilesSetSettings.FILE_SET_TAG, InterestingItemsFilesSetSettings.NAME_ATTR, filePath}); // NON-NLS
            return;
        }
        if (filesSets.containsKey(setName)) {
            logger.log(Level.SEVERE, "Found duplicate definition of set named {0} in FilesSet definition file at {1}, discarding duplicate set", new Object[]{setName, filePath}); // NON-NLS
            return;
        }
        // The file set may have a description. The empty string is o.k.
        String description = setElem.getAttribute(InterestingItemsFilesSetSettings.DESC_ATTR);
        // The file set may or may not ignore known files. The default behavior
        // is to not ignore them.
        String ignoreKnown = setElem.getAttribute(InterestingItemsFilesSetSettings.IGNORE_KNOWN_FILES_ATTR);
        boolean ignoreKnownFiles = false;
        if (!ignoreKnown.isEmpty()) {
            ignoreKnownFiles = Boolean.parseBoolean(ignoreKnown);
        }
        // The file set may or may not skip unallocated space. The default behavior
        // is not to skip it.
        String ignoreUnallocated = setElem.getAttribute(InterestingItemsFilesSetSettings.IGNORE_UNALLOCATED_SPACE);
        boolean ignoreUnallocatedSpace = false;
        if (!ignoreUnallocated.isEmpty()) {
            ignoreUnallocatedSpace = Boolean.parseBoolean(ignoreUnallocated);
        }
        // Read file name set membership rules, if any.
        InterestingItemsFilesSetSettings.unnamedLegacyRuleCounter = 1;
        Map<String, FilesSet.Rule> rules = new HashMap<>();
        NodeList nameRuleElems = setElem.getElementsByTagName(InterestingItemsFilesSetSettings.NAME_RULE_TAG);
        for (int j = 0; j < nameRuleElems.getLength(); ++j) {
            Element elem = (Element) nameRuleElems.item(j);
            FilesSet.Rule rule = InterestingItemsFilesSetSettings.readFileNameRule(elem);
            if (rule != null) {
                if (!rules.containsKey(rule.getUuid())) {
                    rules.put(rule.getUuid(), rule);
                } else {
                    logger.log(Level.SEVERE, "Found duplicate rule {0} for set named {1} in FilesSet definition file at {2}, discarding malformed set", new Object[]{rule.getUuid(), setName, filePath}); // NON-NLS
                    return;
                }
            } else {
                logger.log(Level.SEVERE, "Found malformed rule for set named {0} in FilesSet definition file at {1}, discarding malformed set", new Object[]{setName, filePath}); // NON-NLS
                return;
            }
        }
        // Read file extension set membership rules, if any.
        NodeList extRuleElems = setElem.getElementsByTagName(InterestingItemsFilesSetSettings.EXTENSION_RULE_TAG);
        for (int j = 0; j < extRuleElems.getLength(); ++j) {
            Element elem = (Element) extRuleElems.item(j);
            FilesSet.Rule rule = InterestingItemsFilesSetSettings.readFileExtensionRule(elem);
            if (rule != null) {
                if (!rules.containsKey(rule.getUuid())) {
                    rules.put(rule.getUuid(), rule);
                } else {
                    logger.log(Level.SEVERE, "Found duplicate rule {0} for set named {1} in FilesSet definition file at {2}, discarding malformed set", new Object[]{rule.getUuid(), setName, filePath}); //NOI18N NON-NLS
                    return;
                }
            } else {
                logger.log(Level.SEVERE, "Found malformed rule for set named {0} in FilesSet definition file at {1}, discarding malformed set", new Object[]{setName, filePath}); //NOI18N NON-NLS
                return;
            }
        }
        // Make the files set. Note that degenerate sets with no rules are
        // allowed to facilitate the separation of set definition and rule
        // definitions. A set without rules is simply the empty set.
        FilesSet set = new FilesSet(setName, description, ignoreKnownFiles, ignoreUnallocatedSpace, rules);
        filesSets.put(set.getName(), set);
    }

    // Note: This method takes a file path to support the possibility of
    // multiple intersting files set definition files, e.g., one for
    // definitions that ship with Autopsy and one for user definitions.
    /**
     * Reads FilesSet definitions from Serialized file or XML file.
     *
     * @param fileName       The name of the file which is expected to store the
     *                       serialized definitions
     * @param legacyFileName Name of the xml set definitions file as a string.
     *
     * @return The set definitions in a map of set names to sets.
     */
    static Map<String, FilesSet> readDefinitionsFile(String fileName, String legacyFileName) throws FilesSetsManager.FilesSetsManagerException {
        Map<String, FilesSet> filesSets = readSerializedDefinitions(fileName);
        if (!filesSets.isEmpty()) {
            return filesSets;
        }
        // Check if the legacy xml file exists.
        if (!legacyFileName.isEmpty()) {
            return readDefinitionsXML(Paths.get(PlatformUtil.getUserConfigDirectory(), legacyFileName).toFile());
        }
        return filesSets;
    }

    /**
     * Reads an XML file and returns a map of fileSets. Allows for legacy XML
     * support as well as importing of file sets to XMLs.
     *
     * @param xmlFilePath - The Path to the xml file containing the
     *                    definition(s).
     *
     * @return fileSets - a Map<String, Filesset> of the definition(s) found in
     *         the xml file.
     *
     * @throws
     * org.sleuthkit.autopsy.modules.interestingitems.FilesSetsManager.FilesSetsManagerException
     */
    static Map<String, FilesSet> readDefinitionsXML(File xmlFile) throws FilesSetsManager.FilesSetsManagerException {
        Map<String, FilesSet> filesSets = new HashMap<>();
        if (!xmlFile.exists()) {
            return filesSets;
        }
        // Check if the file can be read.
        if (!xmlFile.canRead()) {
            logger.log(Level.SEVERE, "FilesSet definition file at {0} exists, but cannot be read", xmlFile.getPath()); // NON-NLS
            return filesSets;
        }
        // Parse the XML in the file.
        Document doc = XMLUtil.loadDoc(InterestingItemsFilesSetSettings.class, xmlFile.getPath());
        if (doc == null) {
            logger.log(Level.SEVERE, "FilesSet definition file at {0}", xmlFile.getPath()); // NON-NLS
            return filesSets;
        }
        // Get the root element.
        Element root = doc.getDocumentElement();
        if (root == null) {
            logger.log(Level.SEVERE, "Failed to get root {0} element tag of FilesSet definition file at {1}", new Object[]{InterestingItemsFilesSetSettings.FILE_SETS_ROOT_TAG, xmlFile.getPath()}); // NON-NLS
            return filesSets;
        }
        // Read in the files set definitions.
        NodeList setElems = root.getElementsByTagName(FILE_SET_TAG);
        for (int i = 0; i < setElems.getLength(); ++i) {
            readFilesSet((Element) setElems.item(i), filesSets, xmlFile.getPath());
        }
        return filesSets;
    }

    // Note: This method takes a file path to support the possibility of
    // multiple intersting files set definition files, e.g., one for
    // definitions that ship with Autopsy and one for user definitions.
    /**
     * Writes FilesSet definitions to disk as an XML file, logging any errors.
     *
     * @param fileName Name of the set definitions file as a string.
     *
     * @returns True if the definitions are written to disk, false otherwise.
     */
    static boolean writeDefinitionsFile(String fileName, Map<String, FilesSet> interestingFilesSets) throws FilesSetsManager.FilesSetsManagerException {
        try (final NbObjectOutputStream out = new NbObjectOutputStream(new FileOutputStream(Paths.get(PlatformUtil.getUserConfigDirectory(), fileName).toString()))) {
            out.writeObject(new InterestingItemsFilesSetSettings(interestingFilesSets));
        } catch (IOException ex) {
            throw new FilesSetsManager.FilesSetsManagerException(String.format("Failed to write settings to %s", fileName), ex);
        }
        return true;
    }

    /**
     * Construct a meta-type condition for a FilesSet membership rule from data
     * in an XML element.
     *
     * @param ruleElement The XML element.
     *
     * @return The meta-type condition, or null if there is an error (logged).
     */
    private static FilesSet.Rule.MetaTypeCondition readMetaTypeCondition(Element ruleElement) {
        FilesSet.Rule.MetaTypeCondition condition = null;
        String conditionAttribute = ruleElement.getAttribute(InterestingItemsFilesSetSettings.TYPE_FILTER_ATTR);
        if (!conditionAttribute.isEmpty()) {
            switch (conditionAttribute) {
                case InterestingItemsFilesSetSettings.TYPE_FILTER_VALUE_FILES:
                    condition = new FilesSet.Rule.MetaTypeCondition(FilesSet.Rule.MetaTypeCondition.Type.FILES);
                    break;
                case InterestingItemsFilesSetSettings.TYPE_FILTER_VALUE_DIRS:
                    condition = new FilesSet.Rule.MetaTypeCondition(FilesSet.Rule.MetaTypeCondition.Type.DIRECTORIES);
                    break;
                case InterestingItemsFilesSetSettings.TYPE_FILTER_VALUE_FILES_AND_DIRS:
                    condition = new FilesSet.Rule.MetaTypeCondition(FilesSet.Rule.MetaTypeCondition.Type.FILES_AND_DIRECTORIES);
                    break;
                default:
                    logger.log(Level.SEVERE, "Found {0} " + InterestingItemsFilesSetSettings.TYPE_FILTER_ATTR + " attribute with unrecognized value ''{0}'', ignoring malformed rule definition", conditionAttribute); // NON-NLS
                    break;
            }
        } else {
            // Accept TSK Framework FilesSet definitions,
            // default to files.
            condition = new FilesSet.Rule.MetaTypeCondition(FilesSet.Rule.MetaTypeCondition.Type.FILES);
        }
        return condition;
    }
}
