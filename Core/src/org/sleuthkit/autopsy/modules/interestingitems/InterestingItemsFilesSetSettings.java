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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.lang.StringUtils;
import static org.openide.util.NbBundle.Messages;
import org.openide.util.io.NbObjectInputStream;
import org.openide.util.io.NbObjectOutputStream;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.coreutils.XMLUtil;
import org.sleuthkit.autopsy.modules.interestingitems.FilesSet.Rule.FileNameCondition;
import org.sleuthkit.autopsy.modules.interestingitems.FilesSet.Rule.FileSizeCondition;
import org.sleuthkit.autopsy.modules.interestingitems.FilesSet.Rule.MetaTypeCondition;
import org.sleuthkit.autopsy.modules.interestingitems.FilesSet.Rule.MimeTypeCondition;
import org.sleuthkit.autopsy.modules.interestingitems.FilesSet.Rule.ParentPathCondition;
import org.sleuthkit.autopsy.modules.interestingitems.FilesSet.Rule.DateCondition;
import org.sleuthkit.autopsy.modules.interestingitems.FilesSetsManager.FilesSetsManagerException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import java.util.Comparator;
import java.util.function.Function;
import java.util.stream.Collectors;

class InterestingItemsFilesSetSettings implements Serializable {

    private static final long serialVersionUID = 1L;
    // The following tags and attributes are identical to those used in the
    // TSK Framework FilesSet definitions file schema.
    private static final String FILE_SETS_ROOT_TAG = "INTERESTING_FILE_SETS"; //NON-NLS
    private static final String DESC_ATTR = "description"; //NON-NLS
    private static final String IGNORE_KNOWN_FILES_ATTR = "ignoreKnown"; //NON-NLS
    private static final String PATH_REGEX_ATTR = "pathRegex"; //NON-NLS
    private static final String TYPE_FILTER_VALUE_ALL = "all";
    private static final String TYPE_FILTER_VALUE_FILES_AND_DIRS = "files_and_dirs"; //NON-NLS
    private static final String IGNORE_UNALLOCATED_SPACE = "ingoreUnallocated"; //NON-NLS
    private static final String PATH_FILTER_ATTR = "pathFilter"; //NON-NLS
    private static final String TYPE_FILTER_VALUE_DIRS = "dir"; //NON-NLS
    private static final String REGEX_ATTR = "regex"; //NON-NLS
    private static final List<String> illegalFileNameChars = FilesSetsManager.getIllegalFileNameChars();
    private static final String FILE_SET_TAG = "INTERESTING_FILE_SET"; //NON-NLS
    private static final String NAME_RULE_TAG = "NAME"; //NON-NLS
    private static final String NAME_ATTR = "name"; //NON-NLS
    private static final String DAYS_INCLUDED_ATTR = "daysIncluded";
    private static final String MIME_ATTR = "mimeType";
    private static final String FS_COMPARATOR_ATTR = "comparatorSymbol";
    private static final String FS_SIZE_ATTR = "sizeValue";
    private static final String FS_UNITS_ATTR = "sizeUnits";
    private static final String EXCLUSIVE_ATTR = "isExclusive";
    private static final String TYPE_FILTER_VALUE_FILES = "file"; //NON-NLS
    private static final String XML_ENCODING = "UTF-8"; //NON-NLS
    private static final Logger logger = Logger.getLogger(InterestingItemsFilesSetSettings.class.getName());
    private static final String TYPE_FILTER_ATTR = "typeFilter"; //NON-NLS
    private static final String EXTENSION_RULE_TAG = "EXTENSION"; //NON-NLS
    private static final String STANDARD_SET = "standardSet";
    private static final String VERSION_NUMBER = "versionNumber";

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
        String ruleName = elem.getAttribute(NAME_ATTR);
        return ruleName;
    }

    /**
     * Reads the definitions from the serialization file
     * @param basePath       The base output directory.
     * @param serialFileName Name of the set definitions file as a string.
     * @return the map representing settings saved to serialization file, empty
     *         set if the file does not exist.
     *
     * @throws FilesSetsManagerException if file could not be read
     */
    @Messages({
        "# {0} - filePathStr",
        "InterestingItemsFilesSetSettings.readSerializedDefinitions.failedReadSettings=Failed to read settings from ''{0}''"
    })
    private static Map<String, FilesSet> readSerializedDefinitions(String basePath, String serialFileName) throws FilesSetsManager.FilesSetsManagerException {
        Path filePath = Paths.get(basePath, serialFileName);
        File fileSetFile = filePath.toFile();
        String filePathStr = filePath.toString();
        if (fileSetFile.exists()) {
            try {
                try (final NbObjectInputStream in = new NbObjectInputStream(new FileInputStream(filePathStr))) {
                    InterestingItemsFilesSetSettings filesSetsSettings = (InterestingItemsFilesSetSettings) in.readObject();
                    return filesSetsSettings.getFilesSets();
                }
            } catch (IOException | ClassNotFoundException ex) {

                throw new FilesSetsManager.FilesSetsManagerException(
                        Bundle.InterestingItemsFilesSetSettings_readSerializedDefinitions_failedReadSettings(filePathStr),
                        ex);
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
     *
     * @throws
     * org.sleuthkit.autopsy.modules.interestingitems.FilesSetsManager.FilesSetsManagerException
     */
    @Messages({
        "# {0} - regex",
        "InterestingItemsFilesSetSettings.readPathCondition.failedCompiledRegex=Error compiling ''{0}'' regex",
        "# {0} - ruleName",
        "InterestingItemsFilesSetSettings.readPathCondition.pathConditionCreationError=Error creating path condition for rule ''{0}''"
    })
    private static ParentPathCondition readPathCondition(Element ruleElement) throws FilesSetsManager.FilesSetsManagerException {
        // Read in the optional path condition. Null is o.k., but if the attribute
        // is there, be sure it is not malformed.
        ParentPathCondition pathCondition = null;
        if (!ruleElement.getAttribute(PATH_FILTER_ATTR).isEmpty() || !ruleElement.getAttribute(PATH_REGEX_ATTR).isEmpty()) {
            String path = ruleElement.getAttribute(PATH_FILTER_ATTR);
            String pathRegex = ruleElement.getAttribute(PATH_REGEX_ATTR);
            if (!pathRegex.isEmpty() && path.isEmpty()) {
                try {
                    Pattern pattern = Pattern.compile(pathRegex);
                    pathCondition = new ParentPathCondition(pattern);
                } catch (PatternSyntaxException ex) {
                    logger.log(Level.SEVERE, "Error compiling " + PATH_REGEX_ATTR + " regex, ignoring malformed path condition definition", ex); // NON-NLS
                    throw new FilesSetsManager.FilesSetsManagerException(
                            Bundle.InterestingItemsFilesSetSettings_readPathCondition_failedCompiledRegex(PATH_REGEX_ATTR),
                            ex);
                }
            } else if (!path.isEmpty() && pathRegex.isEmpty()) {
                pathCondition = new ParentPathCondition(path);
            }
            if (pathCondition == null) {
                // Malformed attribute.
                throw new FilesSetsManager.FilesSetsManagerException(
                        Bundle.InterestingItemsFilesSetSettings_readPathCondition_pathConditionCreationError(readRuleName(ruleElement)));
            }
        }
        return pathCondition;
    }

    /**
     * Construct a date condition for a FilesSet membership rule from data in an
     * XML element.
     *
     * @param ruleElement The XML element.
     *
     * @return The date condition, or null if there is an error (logged).
     *
     * @throws
     * org.sleuthkit.autopsy.modules.interestingitems.FilesSetsManager.FilesSetsManagerException
     */
    @Messages({
        "# {0} - regex",
        "InterestingItemsFilesSetSettings.readDateCondition.failedCompiledRegex=Error determining ''{0}'' number",})
    private static DateCondition readDateCondition(Element ruleElement) throws FilesSetsManager.FilesSetsManagerException {
        // Read in the optional path condition. Null is o.k., but if the attribute
        // is there, be sure it is not malformed.
        DateCondition dateCondition = null;
        if (!ruleElement.getAttribute(DAYS_INCLUDED_ATTR).isEmpty()) {
            String daysIncluded = ruleElement.getAttribute(DAYS_INCLUDED_ATTR);
            if (!daysIncluded.isEmpty()) {
                try {
                    dateCondition = new DateCondition(Integer.parseInt(daysIncluded));
                } catch (NumberFormatException ex) {
                    logger.log(Level.SEVERE, "Error creating condition for " + daysIncluded + ", ignoring malformed date condition definition", ex); // NON-NLS

                    throw new FilesSetsManager.FilesSetsManagerException(
                            Bundle.InterestingItemsFilesSetSettings_readDateCondition_failedCompiledRegex(DAYS_INCLUDED_ATTR),
                            ex);
                }
            }
        }
        return dateCondition;
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
     * Construct a fileset membership rule from the data in an xml element for
     * use in a FilesSet.
     *
     * @param elem The XML element.
     *
     * @return A file set constructed from the conditions available in the XML
     *         element
     *
     * @throws
     * org.sleuthkit.autopsy.modules.interestingitems.FilesSetsManager.FilesSetsManagerException
     */
    @Messages({
        "# {0} - ruleName",
        "InterestingItemsFilesSetSettings.readRule.missingNecessary=Invalid rule in files set, missing necessary conditions for ''{0}''",})
    private static FilesSet.Rule readRule(Element elem) throws FilesSetsManager.FilesSetsManagerException {
        String ruleName = readRuleName(elem);
        FileNameCondition nameCondition = readNameCondition(elem);
        MetaTypeCondition metaCondition = readMetaTypeCondition(elem);
        ParentPathCondition pathCondition = readPathCondition(elem);
        MimeTypeCondition mimeCondition = readMimeCondition(elem);
        FileSizeCondition sizeCondition = readSizeCondition(elem);
        DateCondition dateCondition = readDateCondition(elem); //if meta type condition or all four types of conditions the user can create are all null then don't make the rule
        Boolean isExclusive = readExclusive(elem);
        if (metaCondition == null || (nameCondition == null && pathCondition == null && mimeCondition == null && sizeCondition == null && dateCondition == null)) {
            logger.log(Level.WARNING, "Error Reading Rule, " + ruleName + " was either missing a meta condition or contained only a meta condition. No rule was imported."); // NON-NLS

            throw new FilesSetsManager.FilesSetsManagerException(
                    Bundle.InterestingItemsFilesSetSettings_readRule_missingNecessary(ruleName));
        }
        return new FilesSet.Rule(ruleName, nameCondition, metaCondition, pathCondition, mimeCondition, sizeCondition, dateCondition, isExclusive);
    }

    /**
     * Construct a file name condition for a FilesSet membership rule from data
     * in an XML element.
     *
     * @param ruleElement The XML element.
     *
     * @return The file name condition, or null if none existed
     *
     * @throws
     * org.sleuthkit.autopsy.modules.interestingitems.FilesSetsManager.FilesSetsManagerException
     */
    @Messages({
        "# {0} - tagName",
        "# {1} - ruleName",
        "InterestingItemsFilesSetSettings.readNameCondition.invalidTag=Name condition has invalid tag name of ''{0}'' for rule ''{1}''",
        "# {0} - regex",
        "# {1} - rule",
        "InterestingItemsFilesSetSettings.readNameCondition.errorCompilingRegex=Error compiling ''{0}'' regex in rule ''{1}''",
        "# {0} - character",
        "# {1} - rule",
        "InterestingItemsFilesSetSettings.readNameCondition.illegalChar=File name has illegal character of ''{0}'' in rule ''{1}''",})
    private static FileNameCondition readNameCondition(Element elem) throws FilesSetsManager.FilesSetsManagerException {
        FileNameCondition nameCondition = null;
        String content = elem.getTextContent();
        String regex = elem.getAttribute(REGEX_ATTR);
        if (content != null && !content.isEmpty()) {  //if there isn't content this is not a valid name condition
            if ((!regex.isEmpty() && regex.equalsIgnoreCase("true")) || content.contains("*")) { // NON-NLS
                Pattern pattern = compileRegex(content);
                if (pattern != null) {
                    if (elem.getTagName().equals(NAME_RULE_TAG)) {
                        nameCondition = new FilesSet.Rule.FullNameCondition(pattern);
                    } else if (elem.getTagName().equals(EXTENSION_RULE_TAG)) {
                        nameCondition = new FilesSet.Rule.ExtensionCondition(pattern);
                    } else {
                        throw new FilesSetsManager.FilesSetsManagerException(
                                Bundle.InterestingItemsFilesSetSettings_readNameCondition_invalidTag(elem.getTagName(), readRuleName(elem)));
                    }
                } else {
                    logger.log(Level.SEVERE, "Error compiling " + elem.getTagName() + " regex, ignoring malformed ''{0}'' rule definition", readRuleName(elem)); // NON-NLS
                    throw new FilesSetsManager.FilesSetsManagerException(
                            Bundle.InterestingItemsFilesSetSettings_readNameCondition_errorCompilingRegex(REGEX_ATTR, readRuleName(elem)));
                }
            } else {
                for (String illegalChar : illegalFileNameChars) {
                    if (content.contains(illegalChar)) {
                        logger.log(Level.SEVERE, elem.getTagName() + " content has illegal chars, ignoring malformed ''{0}'' rule definition", new Object[]{elem.getTagName(), readRuleName(elem)}); // NON-NLS

                        throw new FilesSetsManager.FilesSetsManagerException(
                                Bundle.InterestingItemsFilesSetSettings_readNameCondition_illegalChar(illegalChar, readRuleName(elem)));
                    }
                }
                if (elem.getTagName().equals(NAME_RULE_TAG)) {
                    nameCondition = new FilesSet.Rule.FullNameCondition(content);
                } else if (elem.getTagName().equals(EXTENSION_RULE_TAG)) {
                    nameCondition = new FilesSet.Rule.ExtensionCondition(Arrays.asList(content.split(",")));
                }
            }
        }
        return nameCondition;
    }
    
    /**
     * Construct a MIME type condition for a FilesSet membership rule from data
     * in an XML element.
     *
     * @param ruleElement The XML element.
     *
     * @return The mime TYPE condition, or null if none existed
     */
    private static Boolean readExclusive(Element elem) {
        Boolean isExclusive = null;
        if (!elem.getAttribute(EXCLUSIVE_ATTR).isEmpty()) {
            isExclusive = Boolean.parseBoolean(elem.getAttribute(EXCLUSIVE_ATTR));
        }
        return isExclusive;
    }

    /**
     * Construct a MIME type condition for a FilesSet membership rule from data
     * in an XML element.
     *
     * @param ruleElement The XML element.
     *
     * @return The mime TYPE condition, or null if none existed
     */
    private static MimeTypeCondition readMimeCondition(Element elem) {
        MimeTypeCondition mimeCondition = null;
        if (!elem.getAttribute(MIME_ATTR).isEmpty()) {
            mimeCondition = new MimeTypeCondition(elem.getAttribute(MIME_ATTR));
            //no checks on mime type here which means
            //if they import a rule with a custom MIME type they don't have
            //the rule will not get any hits
        }
        return mimeCondition;
    }

    /**
     * Construct a file size condition for a FilesSet membership rule from data
     * in an XML element.
     *
     * @param ruleElement The XML element.
     *
     * @return The file size condition, or null if none existed
     *
     * @throws
     * org.sleuthkit.autopsy.modules.interestingitems.FilesSetsManager.FilesSetsManagerException
     */
    @Messages({
        "# {0} - rule",
        "InterestingItemsFilesSetSettings.readSizeCondition.notIntegerValue=Non integer size in files set for rule ''{0}''",
        "# {0} - rule",
        "InterestingItemsFilesSetSettings.readSizeCondition.invalidComparator=Invalid comparator or size unit in files set for rule ''{0}''",
        "# {0} - rule",
        "InterestingItemsFilesSetSettings.readSizeCondition.malformedXml=Files set is malformed missing at least one 'fileSize' attribute for rule ''{0}''",})
    private static FileSizeCondition readSizeCondition(Element elem) throws FilesSetsManager.FilesSetsManagerException {
        FileSizeCondition sizeCondition = null;
        if (!elem.getAttribute(FS_COMPARATOR_ATTR).isEmpty() && !elem.getAttribute(FS_SIZE_ATTR).isEmpty() && !elem.getAttribute(FS_UNITS_ATTR).isEmpty()) {
            try {  //incase they modified the xml manually to invalid comparator, size unit, or non integer string for size
                FileSizeCondition.COMPARATOR comparator = FileSizeCondition.COMPARATOR.fromSymbol(elem.getAttribute(FS_COMPARATOR_ATTR));
                FileSizeCondition.SIZE_UNIT sizeUnit = FileSizeCondition.SIZE_UNIT.fromName(elem.getAttribute(FS_UNITS_ATTR));
                int size = Integer.parseInt(elem.getAttribute(FS_SIZE_ATTR));
                sizeCondition = new FileSizeCondition(comparator, sizeUnit, size);
            } catch (NumberFormatException nfEx) {
                logger.log(Level.SEVERE, "Value in file size attribute was not an integer, unable to create FileSizeCondition for rule: " + readRuleName(elem), nfEx);
                throw new FilesSetsManager.FilesSetsManagerException(
                        Bundle.InterestingItemsFilesSetSettings_readSizeCondition_notIntegerValue(readRuleName(elem)),
                        nfEx);
            } catch (IllegalArgumentException iaEx) {
                logger.log(Level.SEVERE, "Invalid Comparator symbol or Size Unit set in FilesSet xml, unable to create FileSizeCondition for rule: " + readRuleName(elem), iaEx);
                throw new FilesSetsManager.FilesSetsManagerException(
                        Bundle.InterestingItemsFilesSetSettings_readSizeCondition_invalidComparator(readRuleName(elem)),
                        iaEx);
            }
        } //if all of them aren't populated but some of them are this is a malformed xml
        else if (!elem.getAttribute(FS_COMPARATOR_ATTR).isEmpty() || !elem.getAttribute(FS_SIZE_ATTR).isEmpty() || !elem.getAttribute(FS_UNITS_ATTR).isEmpty()) {
            logger.log(Level.SEVERE, "Invalid Comparator symbol or Size Unit set in FilesSet xml, unable to create FileSizeCondition for rule: " + readRuleName(elem));
            throw new FilesSetsManager.FilesSetsManagerException(
                    Bundle.InterestingItemsFilesSetSettings_readSizeCondition_malformedXml(readRuleName(elem)));
        }
        return sizeCondition;
    }

    /**
     * Reads in a FilesSet.
     *
     * @param setElem   A FilesSet XML element
     * @param filesSets A collection to which the set is to be added.
     * @param filePath  The source file, used for error reporting.
     *
     * @throws
     * org.sleuthkit.autopsy.modules.interestingitems.FilesSetsManager.FilesSetsManagerException
     */
    private static void readFilesSet(Element setElem, Map<String, FilesSet> filesSets, String filePath) throws FilesSetsManager.FilesSetsManagerException {
        // The file set must have a unique name.
        String setName = setElem.getAttribute(NAME_ATTR);
        if (setName.isEmpty()) {
            logger.log(Level.SEVERE, "Found {0} element without required {1} attribute, ignoring malformed file set definition in FilesSet definition file at {2}", new Object[]{FILE_SET_TAG, NAME_ATTR, filePath}); // NON-NLS
            return;
        }
        if (filesSets.containsKey(setName)) {
            logger.log(Level.SEVERE, "Found duplicate definition of set named {0} in FilesSet definition file at {1}, discarding duplicate set", new Object[]{setName, filePath}); // NON-NLS
            return;
        }
        // The file set may have a description. The empty string is o.k.
        String description = setElem.getAttribute(DESC_ATTR);
        // The file set may or may not ignore known files. The default behavior
        // is to not ignore them.
        String ignoreKnown = setElem.getAttribute(IGNORE_KNOWN_FILES_ATTR);
        boolean ignoreKnownFiles = false;
        if (!ignoreKnown.isEmpty()) {
            ignoreKnownFiles = Boolean.parseBoolean(ignoreKnown);
        }
        // The file set may or may not skip unallocated space. The default behavior
        // is not to skip it.
        String ignoreUnallocated = setElem.getAttribute(IGNORE_UNALLOCATED_SPACE);
        boolean ignoreUnallocatedSpace = false;
        if (!ignoreUnallocated.isEmpty()) {
            ignoreUnallocatedSpace = Boolean.parseBoolean(ignoreUnallocated);
        }

        String isStandardSetString = setElem.getAttribute(STANDARD_SET);
        boolean isStandardSet = false;
        if (StringUtils.isNotBlank(isStandardSetString)) {
            isStandardSet = Boolean.parseBoolean(isStandardSetString);
        }

        String versionNumberString = setElem.getAttribute(VERSION_NUMBER);
        int versionNumber = 0;
        if (StringUtils.isNotBlank(versionNumberString)) {
            try {
                versionNumber = Integer.parseInt(versionNumberString);
            } catch (NumberFormatException ex) {
                logger.log(Level.WARNING,
                        String.format("Unable to parse version number for files set named: %s with provided input: '%s'", setName, versionNumberString),
                        ex);
            }
        }

        // Read the set membership rules, if any.
        Map<String, FilesSet.Rule> rules = new HashMap<>();
        NodeList allRuleElems = setElem.getChildNodes();
        for (int j = 0; j < allRuleElems.getLength(); ++j) {
            if (allRuleElems.item(j) instanceof Element) {  //All the children we need to parse here are elements
                Element elem = (Element) allRuleElems.item(j);
                FilesSet.Rule rule = readRule(elem);
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
        }
        // Make the files set. Note that degenerate sets with no rules are
        // allowed to facilitate the separation of set definition and rule
        // definitions. A set without rules is simply the empty set.
        FilesSet set = new FilesSet(setName, description, ignoreKnownFiles, ignoreUnallocatedSpace, rules, isStandardSet, versionNumber);
        filesSets.put(set.getName(), set);
    }
    // Note: This method takes a file path to support the possibility of
    // multiple intersting files set definition files, e.g., one for
    // definitions that ship with Autopsy and one for user definitions.

    /**
     * Reads FilesSet definitions from Serialized file or XML file.
     * @param basePath       The base output directory.
     * @param fileName       The name of the file which is expected to store the
     *                       serialized definitions
     * @param legacyFileName Name of the xml set definitions file as a string.
     *
     * @return The set definitions in a map of set names to sets.
     *
     * @throws
     * org.sleuthkit.autopsy.modules.interestingitems.FilesSetsManager.FilesSetsManagerException
     */
    static Map<String, FilesSet> readDefinitionsFile(String basePath, String fileName, String legacyFileName) throws FilesSetsManager.FilesSetsManagerException {
        Map<String, FilesSet> filesSets = readSerializedDefinitions(basePath, fileName);
        if (!filesSets.isEmpty()) {
            return filesSets;
        }
        // Check if the legacy xml file exists.
        if (!legacyFileName.isEmpty()) {
            return readDefinitionsXML(Paths.get(basePath, legacyFileName).toFile());
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
        if (!xmlFile.exists()) {
            return new HashMap<>();
        }
        // Check if the file can be read.
        if (!xmlFile.canRead()) {
            logger.log(Level.SEVERE, "FilesSet definition file at {0} exists, but cannot be read", xmlFile.getPath()); // NON-NLS
            return new HashMap<>();
        }

        Document doc = XMLUtil.loadDoc(InterestingItemsFilesSetSettings.class, xmlFile.getPath());
        return readDefinitionsXML(doc, xmlFile.getPath());
    }

    /**
     * Reads an XML file and returns a map of fileSets. Allows for legacy XML
     * support as well as importing of file sets to XMLs.
     *
     *
     * @param doc The xml document.
     *
     * @return fileSets - a Map<String, Filesset> of the definition(s) found in
     *         the xml file for logging purposes (can provide null).
     *
     * @throws
     * org.sleuthkit.autopsy.modules.interestingitems.FilesSetsManager.FilesSetsManagerException
     */
    static Map<String, FilesSet> readDefinitionsXML(Document doc, String resourceName) throws FilesSetsManager.FilesSetsManagerException {
        // Parse the XML in the file.
        Map<String, FilesSet> filesSets = new HashMap<>();

        if (doc == null) {
            logger.log(Level.SEVERE, "FilesSet definition file at {0}", resourceName); // NON-NLS
            return filesSets;
        }
        // Get the root element.
        Element root = doc.getDocumentElement();
        if (root == null) {
            logger.log(Level.SEVERE, "Failed to get root {0} element tag of FilesSet definition file at {1}",
                    new Object[]{FILE_SETS_ROOT_TAG, resourceName}); // NON-NLS
            return filesSets;
        }
        // Read in the files set definitions.
        NodeList setElems = root.getElementsByTagName(FILE_SET_TAG);
        for (int i = 0; i < setElems.getLength(); ++i) {
            readFilesSet((Element) setElems.item(i), filesSets, resourceName);
        }
        return filesSets;
    }

    // Note: This method takes a file path to support the possibility of
    // multiple intersting files set definition files, e.g., one for
    // definitions that ship with Autopsy and one for user definitions.
    /**
     * Writes FilesSet definitions to disk as an XML file, logging any errors.
     * @param basePath       The base output directory.
     * @param fileName Name of the set definitions file as a string.
     *
     * @returns True if the definitions are written to disk, false otherwise.
     */
    static boolean writeDefinitionsFile(String basePath, String fileName, Map<String, FilesSet> interestingFilesSets) throws FilesSetsManager.FilesSetsManagerException {
        File outputFile = Paths.get(basePath, fileName).toFile();
        outputFile.getParentFile().mkdirs();
        try (final NbObjectOutputStream out = new NbObjectOutputStream(new FileOutputStream(outputFile))) {
            out.writeObject(new InterestingItemsFilesSetSettings(interestingFilesSets));
        } catch (IOException ex) {
            throw new FilesSetsManager.FilesSetsManagerException(String.format("Failed to write settings to %s", fileName), ex);
        }
        return true;
    }
    

    /**
     * Generates an alphabetically sorted list based on the provided collection and Function to retrieve a string field from each object.
     * @param itemsToSort The items to be sorted into the newly generated list.
     * @param getName The method to retrieve the given field from the object.
     * @return The newly generated list sorted alphabetically by the given field.
     */
    private static <T> List<T> sortOnField(Collection<T> itemsToSort, final Function<T, String> getName) {
        Comparator<T> comparator = (a,b) -> {
            String aName = getName.apply(a);
            String bName = getName.apply(b);
            if (aName == null) {
                aName = "";
            }
            
            if (bName == null) {
                bName = "";
            }
            
            return aName.compareToIgnoreCase(bName);
        };
        
        return itemsToSort.stream()
                .sorted(comparator)
                .collect(Collectors.toList());
    }
    

    /**
     * Write the FilesSets to a file as an xml.
     *
     * @param xmlFile              the file you will be writing the FilesSets to
     * @param interestingFilesSets a map of the file sets you wish to write to
     *                             the xml file
     *
     * @return true for successfully writing, false for a failure
     */
    static boolean exportXmlDefinitionsFile(File xmlFile, List<FilesSet> interestingFilesSets) {
        DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
        try {
            // Create the new XML document.
            DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
            Document doc = docBuilder.newDocument();
            Element rootElement = doc.createElement(FILE_SETS_ROOT_TAG);
            doc.appendChild(rootElement);
            // Add the interesting files sets to the document.

            List<FilesSet> sortedFilesSets = sortOnField(
                    interestingFilesSets, 
                    filesSet -> filesSet == null ? null : filesSet.getName());
            
            for (FilesSet set : sortedFilesSets) {
                // Add the files set element and its attributes.
                Element setElement = doc.createElement(FILE_SET_TAG);
                setElement.setAttribute(NAME_ATTR, set.getName());
                setElement.setAttribute(DESC_ATTR, set.getDescription());
                setElement.setAttribute(IGNORE_KNOWN_FILES_ATTR, Boolean.toString(set.ignoresKnownFiles()));
                setElement.setAttribute(STANDARD_SET, Boolean.toString(set.isStandardSet()));
                setElement.setAttribute(VERSION_NUMBER, Integer.toString(set.getVersionNumber()));
                // Add the child elements for the set membership rules.
                // All conditions of a rule will be written as a single element in the xml
                
                List<FilesSet.Rule> sortedRules = sortOnField(
                        set.getRules().values(), 
                        rule -> rule == null ? null : rule.getName());
                
                for (FilesSet.Rule rule : sortedRules) {
                    // Add a rule element with the appropriate name Condition 
                    // type tag.
                    Element ruleElement;

                    FileNameCondition nameCondition = rule.getFileNameCondition();
                    //The element type is just being used as another attribute for 
                    //the name condition in legacy xmls.
                    //For rules which don't contain a name condition it doesn't matter
                    //what type of element it is 
                    if (nameCondition instanceof FilesSet.Rule.FullNameCondition) {
                        ruleElement = doc.createElement(NAME_RULE_TAG);
                    } else {
                        ruleElement = doc.createElement(EXTENSION_RULE_TAG);
                    }
                    // Add the optional rule name attribute.
                    ruleElement.setAttribute(NAME_ATTR, rule.getName());
                    if (nameCondition != null) {
                        // Add the name Condition regex attribute
                        ruleElement.setAttribute(REGEX_ATTR, Boolean.toString(nameCondition.isRegex()));
                        // Add the name Condition text as the rule element content.
                        ruleElement.setTextContent(nameCondition.getTextToMatch());
                    }
                    // Add the type Condition attribute.
                    MetaTypeCondition typeCondition = rule.getMetaTypeCondition();
                    switch (typeCondition.getMetaType()) {
                        case FILES:
                            ruleElement.setAttribute(TYPE_FILTER_ATTR, TYPE_FILTER_VALUE_FILES);
                            break;
                        case DIRECTORIES:
                            ruleElement.setAttribute(TYPE_FILTER_ATTR, TYPE_FILTER_VALUE_DIRS);
                            break;
                        default:
                            ruleElement.setAttribute(TYPE_FILTER_ATTR, TYPE_FILTER_VALUE_ALL);
                            break;
                    }
                    // Add the optional path Condition.
                    ParentPathCondition pathCondition = rule.getPathCondition();
                    if (pathCondition != null) {
                        if (pathCondition.isRegex()) {
                            ruleElement.setAttribute(PATH_REGEX_ATTR, pathCondition.getTextToMatch());
                        } else {
                            ruleElement.setAttribute(PATH_FILTER_ATTR, pathCondition.getTextToMatch());
                        }
                    }
                    //Add the optional MIME type condition
                    MimeTypeCondition mimeCondition = rule.getMimeTypeCondition();
                    if (mimeCondition != null) {
                        ruleElement.setAttribute(MIME_ATTR, mimeCondition.getMimeType());
                    }
                    //Add the optional file size condition
                    FileSizeCondition sizeCondition = rule.getFileSizeCondition();
                    if (sizeCondition != null) {
                        ruleElement.setAttribute(FS_COMPARATOR_ATTR, sizeCondition.getComparator().getSymbol());
                        ruleElement.setAttribute(FS_SIZE_ATTR, Integer.toString(sizeCondition.getSizeValue()));
                        ruleElement.setAttribute(FS_UNITS_ATTR, sizeCondition.getUnit().getName());
                    }

                    //Add the optional date condition
                    DateCondition dateCondition = rule.getDateCondition();
                    if (dateCondition != null) {
                        ruleElement.setAttribute(DAYS_INCLUDED_ATTR, Integer.toString(dateCondition.getDaysIncluded()));
                    }
                    
                    ruleElement.setAttribute(EXCLUSIVE_ATTR, Boolean.toString(rule.isExclusive()));

                    setElement.appendChild(ruleElement);
                }
                rootElement.appendChild(setElement);
            }
            // Overwrite the previous definitions file. Note that the utility 
            // method logs an error on failure.
            return XMLUtil.saveDoc(InterestingItemsFilesSetSettings.class, xmlFile.getPath(), XML_ENCODING, doc);
        } catch (ParserConfigurationException ex) {
            logger.log(Level.SEVERE, "Error writing interesting files definition file to " + xmlFile.getPath(), ex); // NON-NLS
            return false;
        }
    }

    /**
     * Construct a meta-type condition for a FilesSet membership rule from data
     * in an XML element.
     *
     * @param ruleElement The XML element.
     *
     * @return The meta-type condition, or null if there is an error (logged).
     *
     * @throws
     * org.sleuthkit.autopsy.modules.interestingitems.FilesSetsManager.FilesSetsManagerException
     */
    @Messages({
        "# {0} - condition",
        "# {1} - rule",
        "InterestingItemsFilesSetSettings.readMetaTypeCondition.malformedXml=Files set is malformed for metatype condition, ''{0}'', in rule ''{1}''"
    })
    private static MetaTypeCondition readMetaTypeCondition(Element ruleElement) throws FilesSetsManager.FilesSetsManagerException {
        MetaTypeCondition metaCondition = null;
        // The rule must have a meta-type condition, unless a TSK Framework
        // definitions file is being read.
        if (!ruleElement.getAttribute(TYPE_FILTER_ATTR).isEmpty()) {
            String conditionAttribute = ruleElement.getAttribute(TYPE_FILTER_ATTR);
            if (!conditionAttribute.isEmpty()) {
                switch (conditionAttribute) {
                    case TYPE_FILTER_VALUE_FILES:
                        metaCondition = new MetaTypeCondition(MetaTypeCondition.Type.FILES);
                        break;
                    case TYPE_FILTER_VALUE_DIRS:
                        metaCondition = new MetaTypeCondition(MetaTypeCondition.Type.DIRECTORIES);
                        break;
                    case TYPE_FILTER_VALUE_ALL:
                    case TYPE_FILTER_VALUE_FILES_AND_DIRS:  //converts legacy xmls to current metaCondition terms
                        metaCondition = new MetaTypeCondition(MetaTypeCondition.Type.ALL);
                        break;
                    default:
                        logger.log(Level.SEVERE, "Found {0} " + TYPE_FILTER_ATTR + " attribute with unrecognized value ''{0}'', ignoring malformed rule definition", conditionAttribute); // NON-NLS
                        // Malformed attribute.

                        throw new FilesSetsManager.FilesSetsManagerException(
                                Bundle.InterestingItemsFilesSetSettings_readMetaTypeCondition_malformedXml(
                                        conditionAttribute, readRuleName(ruleElement)));
                }
            }
        }
        if (metaCondition == null) {
            // Accept TSK Framework FilesSet definitions,
            // default to files.
            metaCondition = new MetaTypeCondition(MetaTypeCondition.Type.FILES);
        }
        return metaCondition;
    }
}
