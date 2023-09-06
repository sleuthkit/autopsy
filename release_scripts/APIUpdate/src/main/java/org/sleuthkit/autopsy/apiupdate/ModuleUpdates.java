/*
 * Autopsy Forensic Browser
 *
 * Copyright 2023 Basis Technology Corp.
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
package org.sleuthkit.autopsy.apiupdate;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Responsible for gathering current module versions and updating current module
 * versions.
 *
 * NOTE: During update, this class gives preference to regex replacements as
 * opposed to loading and rewriting settings to minimize diffs.
 */
public class ModuleUpdates {

    private static final Logger LOGGER = Logger.getLogger(ModuleUpdates.class.getName());

    private static final Pattern SPEC_REGEX = Pattern.compile("^\\s*((?<major>\\d*)\\.)?(?<minor>\\d*)(\\.(?<patch>\\d*))?\\s*$");

    private static final Pattern PROJECT_PROPS_SPEC_REGEX = Pattern.compile("^\\s*spec\\.version\\.base\\s*=.*$", Pattern.MULTILINE);
    private static final String PROJECT_PROPS_SPEC_REPLACE_FMT = "spec.version.base={0}";

    private static final String MANIFEST_FIELD_REGEX_FMT = "^\\s*{0}\\s*:.*$";

    private static final String MANIFEST_IMPL_KEY = "OpenIDE-Module-Implementation-Version";
    private static final Pattern MANIFEST_IMPL_REGEX = Pattern.compile(MessageFormat.format(MANIFEST_FIELD_REGEX_FMT, MANIFEST_IMPL_KEY), Pattern.MULTILINE);
    private static final String MANIFEST_IMPL_REPLACE_FMT = MANIFEST_IMPL_KEY + ": {0}";

    private static final String MANIFEST_SPEC_KEY = "OpenIDE-Module-Specification-Version";
    private static final Pattern MANIFEST_SPEC_REGEX = Pattern.compile(MessageFormat.format(MANIFEST_FIELD_REGEX_FMT, MANIFEST_SPEC_KEY), Pattern.MULTILINE);
    private static final String MANIFEST_SPEC_REPLACE_FMT = MANIFEST_SPEC_KEY + ": {0}";

    private static final String MANIFEST_RELEASE_KEY = "OpenIDE-Module";
    private static final Pattern MANIFEST_RELEASE_REGEX = Pattern.compile(MessageFormat.format(MANIFEST_FIELD_REGEX_FMT, MANIFEST_RELEASE_KEY), Pattern.MULTILINE);
    private static final String MANIFEST_RELEASE_REPLACE_FMT = MANIFEST_RELEASE_KEY + ": {0}";

    private static final Pattern RELEASE_REGEX = Pattern.compile("^\\s*(?<releaseName>.+?)(/(?<releaseNum>\\d*))?\\s*$");

    private static final SemVer DEFAULT_SEMVER = new SemVer(1, 0, null);
    private static final int DEFAULT_VERS_VAL = 1;

    private static final String PROJECT_PROPS_REL_PATH = "nbproject/project.properties";
    private static final String PROJ_XML_REL_PATH = "nbproject/project.xml";
    private static final String MANIFEST_FILE_NAME = "manifest.mf";

    private static final String PROJ_XML_FMT_STR = "//project/configuration/data/module-dependencies/dependency[code-name-base[contains(text(), ''{0}'')]]/run-dependency";
    private static final String PROJ_XML_RELEASE_VERS_EL = "release-version";
    private static final String PROJ_XML_SPEC_VERS_EL = "specification-version";
    private static final String PROJ_XML_IMPL_VERS_EL = "implementation-version";

    private static final String XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";

    /**
     * Parses a SemVer (i.e. 1.23 or 1.23.01) from a string.
     *
     * @param semVerStr The SemVer string.
     * @param defaultSemVer The default SemVer if one cannot be parsed.
     * @param resourceForLogging A string identifier of this resource for
     * logging purposes.
     * @return The parsed SemVer or default.
     */
    private static SemVer parseSemVer(String semVerStr, SemVer defaultSemVer, String resourceForLogging) {
        if (StringUtils.isBlank(semVerStr)) {
            LOGGER.log(Level.WARNING, MessageFormat.format("Unable to parse semver for empty string in {0}", resourceForLogging));
            return defaultSemVer;
        }

        Matcher m = SPEC_REGEX.matcher(semVerStr);
        if (m.find()) {
            try {
                String majorStr = m.group("major");
                int major = StringUtils.isBlank(majorStr) ? 1 : Integer.parseInt(majorStr);
                int minor = Integer.parseInt(m.group("minor"));
                String patchStr = m.group("patch");
                Integer patch = StringUtils.isBlank(patchStr) ? null : Integer.valueOf(patchStr);
                return new SemVer(major, minor, patch);
            } catch (NullPointerException | NumberFormatException ex) {
                LOGGER.log(Level.SEVERE, MessageFormat.format("Unable to parse semver string {0} for {1}", semVerStr, resourceForLogging), ex);
            }
        } else {
            LOGGER.log(Level.WARNING, MessageFormat.format("Unable to parse semver string {0} for {1}", semVerStr, resourceForLogging));
        }

        return defaultSemVer;
    }

    /**
     * Parses the ReleaseVal object from a release string (i.e. "module/num").
     *
     * @param releaseStr The release string in format of "module/num".
     * @param resourceForLogging A string identifier of this resource for
     * logging purposes.
     * @return The parsed release val. Release version may be null.
     */
    private static ReleaseVal parseReleaseVers(String releaseStr, String resourceForLogging) {
        Matcher m = RELEASE_REGEX.matcher(StringUtils.defaultString(releaseStr));
        if (StringUtils.isNotBlank(releaseStr) && m.find()) {
            String releaseName = m.group("releaseName");
            Integer releaseNum = null;
            try {
                String releaseNumStr = m.group("releaseNum");
                releaseNum = StringUtils.isBlank(releaseNumStr) ? null : Integer.valueOf(releaseNumStr);
            } catch (NullPointerException | NumberFormatException ex) {
                LOGGER.log(Level.SEVERE, MessageFormat.format("Unable to parse release version string {0} for {1}", releaseStr, resourceForLogging), ex);
            }
            return new ReleaseVal(releaseName, releaseNum);
        } else {
            LOGGER.log(Level.WARNING, MessageFormat.format("Unable to parse release version string {0} for {1}", releaseStr, resourceForLogging));
        }

        return new ReleaseVal("", null);

    }

    /**
     * Attempts to parse an integer value from a string returning the default
     * value if the string cannot be parsed.
     *
     * @param str The string.
     * @param defaultVal The default value.
     * @param resourceForLogging A string identifier of this resource for
     * logging purposes.
     * @return The parsed value or the default value.
     */
    private static int tryParse(String str, int defaultVal, String resourceForLogging) {
        try {
            return Integer.parseInt(str);
        } catch (NullPointerException | NumberFormatException ex) {
            LOGGER.log(Level.WARNING, MessageFormat.format("Unable to parse version string {0} for {1}", str, resourceForLogging), ex);
            return defaultVal;
        }
    }

    /**
     * Parses version numbers from a jar file.
     *
     * @param jarFile The jar file.
     * @return The module version numbers.
     * @throws IOException
     */
    public static ModuleVersionNumbers getVersionsFromJar(File jarFile) throws IOException {       
        Attributes manifest = ManifestLoader.loadFromJar(jarFile);
        String spec = manifest.getValue(MANIFEST_SPEC_KEY);
        SemVer specSemVer = parseSemVer(spec, DEFAULT_SEMVER,
                MessageFormat.format("{0} in manifest for {1}", MANIFEST_SPEC_KEY, jarFile));

        String implStr = manifest.getValue(MANIFEST_IMPL_KEY);
        int implementation;
        if (StringUtils.isBlank(implStr)) {
            LOGGER.log(Level.WARNING, MessageFormat.format("No {0} for implementation found in {1}.  Using default of {2}.", MANIFEST_IMPL_KEY, jarFile.getName(), DEFAULT_VERS_VAL));
            implementation = DEFAULT_VERS_VAL;
        } else {
            implementation = tryParse(implStr, DEFAULT_VERS_VAL,
                    MessageFormat.format("{0} in manifest for {1}", MANIFEST_IMPL_KEY, jarFile));
        }

        ReleaseVal release = parseReleaseVers(manifest.getValue(MANIFEST_RELEASE_KEY),
                MessageFormat.format("{0} in manifest for {1}", MANIFEST_RELEASE_KEY, jarFile));

        return new ModuleVersionNumbers(jarFile.getName(), specSemVer, implementation, release);
    }

    /**
     * Calculates new module version numbers based on previous module version
     * numbers and the type of API change.
     *
     * @param prev The previous version numbers.
     * @param apiChangeType The public api change type.
     * @return The calculated version numbers.
     */
    public static ModuleVersionNumbers getModuleVersionUpdate(ModuleVersionNumbers prev, PublicApiChangeType apiChangeType) {
        switch (apiChangeType) {
            case NONE -> {
                return new ModuleVersionNumbers(
                        prev.getModuleName(),
                        prev.getSpec(),
                        prev.getImplementation(),
                        prev.getRelease()
                );
            }
            case INTERNAL_CHANGE -> {
                return new ModuleVersionNumbers(
                        prev.getModuleName(),
                        prev.getSpec(),
                        prev.getImplementation() + 1,
                        prev.getRelease()
                );
            }
            case COMPATIBLE_CHANGE -> {
                return new ModuleVersionNumbers(
                        prev.getModuleName(),
                        new SemVer(
                                prev.getSpec().getMajor(),
                                prev.getSpec().getMinor() + 1,
                                prev.getSpec().getPatch()
                        ),
                        prev.getImplementation() + 1,
                        prev.getRelease()
                );
            }
            case INCOMPATIBLE_CHANGE -> {
                return new ModuleVersionNumbers(
                        prev.getModuleName(),
                        new SemVer(
                                prev.getSpec().getMajor() + 1,
                                0,
                                null
                        ),
                        prev.getImplementation() + 1,
                        new ReleaseVal(
                                prev.getRelease().getModuleName(),
                                prev.getRelease().getReleaseVersion() == null ? null : prev.getRelease().getReleaseVersion() + 1
                        )
                );
            }
            default ->
                throw new IllegalArgumentException("Unknown api change type: " + apiChangeType);
        }
    }

    /**
     * Maps release version name (i.e. `org.sleuthkit.autopsy.core`) to a file
     * for the directory (i.e. <autopsy repo>/Core).
     *
     * @param srcDir The autopsy source root directory.
     * @return The mapping of release names to directories.
     * @throws IOException
     */
    private static Map<String, File> getModuleDirs(File srcDir) throws IOException {
        Map<String, File> moduleDirMapping = new HashMap<>();
        for (File dir : srcDir.listFiles((File f) -> f.isDirectory())) {
            File manifestFile = dir.toPath().resolve(MANIFEST_FILE_NAME).toFile();
            if (manifestFile.isFile()) {
                try (FileInputStream manifestIs = new FileInputStream(manifestFile)) {
                    Attributes manifestAttrs = ManifestLoader.loadManifestAttributes(manifestIs);
                    ReleaseVal releaseVal = parseReleaseVers(manifestAttrs.getValue(MANIFEST_RELEASE_KEY), manifestFile.getAbsolutePath());
                    moduleDirMapping.put(releaseVal.getModuleName(), dir);
                }
            }
        }
        return moduleDirMapping;
    }

    /**
     * Updates version numbers in autopsy source. Updates nbm specified versions
     * as well as dependencies.
     *
     * @param srcDir The autopsy source root directory.
     * @param versNums The mapping of release name (i.e.
     * `org.sleuthkit.autopsy.core` to the new module version numbers).
     */
    static void setVersions(File srcDir, Map<String, ModuleVersionNumbers> versNums) {
        // TODO parse from repo/DIR/manifest.mf release version
        Map<String, File> moduleDirs;
        try {
            moduleDirs = getModuleDirs(srcDir);
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "There was an error getting module directories from source dir: " + srcDir.getAbsolutePath(), ex);
            return;
        }

        Set<String> notFoundModules = new HashSet<>(versNums.keySet());
        notFoundModules.removeAll(moduleDirs.keySet());
        if (!notFoundModules.isEmpty()) {
            LOGGER.log(Level.SEVERE, MessageFormat.format("The following modules were not found in {0}: {1}.  Aborting...", srcDir, notFoundModules));
            return;
        }

        for (Entry<String, File> moduleNameDir : moduleDirs.entrySet()) {
            String moduleName = moduleNameDir.getKey();
            File moduleDir = moduleNameDir.getValue();
            ModuleVersionNumbers thisVersNums = versNums.get(moduleName);

            try {
                LOGGER.log(Level.INFO, "Updating for module name: {0}", moduleName);
                updateProjXml(moduleDir, versNums);

                if (thisVersNums != null) {
                    updateProjProperties(moduleDir, thisVersNums);
                    updateManifest(moduleDir, thisVersNums);
                }
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "There was an error updating " + moduleDir.getAbsolutePath(), ex);
            }
        }
    }

    private static String regexUpdate(Pattern pattern, String text, String replacement) {
        return pattern.matcher(text).replaceAll(replacement);
    }

    /**
     * Escapes special characters in a regex replace pattern (i.e. '\' and '$').
     *
     * @param orig The original string value.
     * @return The escaped string.
     */
    private static String replaceEscape(String orig) {
        return orig.replaceAll("\\\\", "\\\\").replaceAll("\\$", "\\$");
    }

    /**
     * Updates project.properties version numbers.
     *
     * @param moduleDir The module directory.
     * @param thisVersNums The version numbers to set.
     * @throws IOException
     */
    private static void updateProjProperties(File moduleDir, ModuleVersionNumbers thisVersNums) throws IOException {
        File projectPropsFile = moduleDir.toPath().resolve(PROJECT_PROPS_REL_PATH).toFile();
        if (!projectPropsFile.isFile()) {
            LOGGER.log(Level.SEVERE, "No project properties found at {0}", projectPropsFile.getAbsolutePath());
            return;
        }

        String projectPropsText = Files.readString(projectPropsFile.toPath());
        String newText = regexUpdate(PROJECT_PROPS_SPEC_REGEX,
                projectPropsText,
                MessageFormat.format(
                        PROJECT_PROPS_SPEC_REPLACE_FMT,
                        replaceEscape(thisVersNums.getSpec().getSemVerStr())));

        if (!newText.equals(projectPropsText)) {
            Files.writeString(projectPropsFile.toPath(), newText);
        }
    }

    /**
     * Updates the manifest.mf file in source to the new module version numbers.
     *
     * @param moduleDir The module directory.
     * @param thisVersNums The new module version numbers.
     * @throws IOException
     */
    private static void updateManifest(File moduleDir, ModuleVersionNumbers thisVersNums) throws IOException {
        File manifestFile = moduleDir.toPath().resolve(MANIFEST_FILE_NAME).toFile();
        if (!manifestFile.isFile()) {
            LOGGER.log(Level.SEVERE, "No manifest file found at {0}", manifestFile.getAbsolutePath());
            return;
        }

        String manifestFileText = Files.readString(manifestFile.toPath());
        String newManifestText = regexUpdate(
                MANIFEST_IMPL_REGEX,
                manifestFileText,
                MessageFormat.format(
                        MANIFEST_IMPL_REPLACE_FMT,
                        replaceEscape(Integer.toString(thisVersNums.getImplementation()))));

        newManifestText = regexUpdate(
                MANIFEST_SPEC_REGEX,
                newManifestText,
                MessageFormat.format(
                        MANIFEST_SPEC_REPLACE_FMT,
                        replaceEscape(thisVersNums.getSpec().getSemVerStr())));

        newManifestText = regexUpdate(
                MANIFEST_RELEASE_REGEX,
                newManifestText,
                MessageFormat.format(
                        MANIFEST_RELEASE_REPLACE_FMT,
                        replaceEscape(thisVersNums.getRelease().getFullReleaseStr())));

        if (!newManifestText.equals(manifestFileText)) {
            Files.writeString(manifestFile.toPath(), newManifestText);
        }
    }

    /**
     * Updates project.xml to new version numbers. This method uses xml
     * parsing/writing instead of regex replacements to avoid xml errors.
     *
     * @param moduleDir The module directory.
     * @param versNums The new version numbers.
     * @throws IOException
     * @throws ParserConfigurationException
     * @throws SAXException
     * @throws XPathExpressionException
     * @throws TransformerException
     */
    private static void updateProjXml(File moduleDir, Map<String, ModuleVersionNumbers> versNums)
            throws IOException, ParserConfigurationException, SAXException, XPathExpressionException, TransformerException {

        File projXmlFile = moduleDir.toPath().resolve(PROJ_XML_REL_PATH).toFile();
        if (!projXmlFile.isFile()) {
            LOGGER.log(Level.SEVERE, "No project.xml file found at {0}", projXmlFile.getAbsolutePath());
            return;
        }

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document projectXmlDoc = db.parse(projXmlFile);

        XPath xPath = XPathFactory.newInstance().newXPath();

        boolean updated = false;
        for (Entry<String, ModuleVersionNumbers> updatedModule : versNums.entrySet()) {
            String moduleName = updatedModule.getKey();
            ModuleVersionNumbers newVers = updatedModule.getValue();
            Node node = (Node) xPath.compile(MessageFormat.format(PROJ_XML_FMT_STR, moduleName))
                    .evaluate(projectXmlDoc, XPathConstants.NODE);

            if (node != null) {
                Map<String, String> childElText = new HashMap<>() {
                    {
                        put(PROJ_XML_RELEASE_VERS_EL, newVers.getRelease().getReleaseVersion() == null ? "" : newVers.getRelease().getReleaseVersion().toString());
                        put(PROJ_XML_IMPL_VERS_EL, Integer.toString(newVers.getImplementation()));
                        put(PROJ_XML_SPEC_VERS_EL, newVers.getSpec().getSemVerStr());
                    }
                };
                updated = updateXmlChildrenIfPresent(node, childElText) || updated;
            }
        }

        if (updated) {
            StringWriter outputXmlStringWriter = new StringWriter();
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.transform(new DOMSource(projectXmlDoc), new StreamResult(outputXmlStringWriter));

            String xmlContent = outputXmlStringWriter.toString();
            xmlContent = XML_HEADER + System.lineSeparator() + xmlContent + System.lineSeparator();

            Files.writeString(projXmlFile.toPath(), xmlContent);
        }
    }

    /**
     * Update xml children if present.
     *
     * @param parentNode The parent node.
     * @param childElText The mapping of child element names to new text
     * content.
     * @return True if a change occurred.
     */
    private static boolean updateXmlChildrenIfPresent(Node parentNode, Map<String, String> childElText) {
        NodeList childNodeList = parentNode.getChildNodes();
        boolean changed = false;
        for (int i = 0; i < childNodeList.getLength(); i++) {
            Node childNode = childNodeList.item(i);
            String childNodeEl = childNode.getNodeName();
            String childNodeText = childNode.getTextContent();

            String newChildNodeText = childElText.get(childNodeEl);
            if (newChildNodeText != null && StringUtils.isNotBlank(childNodeText)) {
                childNode.setTextContent(newChildNodeText);
                changed = true;
            }
        }

        return changed;
    }

    /**
     * The release value originally in format like
     * `org.sleuthkit.autopsy.core/5`.
     */
    public static class ReleaseVal {

        private final String moduleName;
        private final Integer releaseVersion;

        public ReleaseVal(String moduleName, Integer releaseVersion) {
            this.moduleName = moduleName;
            this.releaseVersion = releaseVersion;
        }

        /**
         * @return The module name (i.e. `org.sleuthkit.autopsy.core`).
         */
        public String getModuleName() {
            return moduleName;
        }

        /**
         * @return The release version if one specified else null.
         */
        public Integer getReleaseVersion() {
            return releaseVersion;
        }

        /**
         * @return The full release string like `org.sleuthkit.autopsy.core/5`.
         */
        public String getFullReleaseStr() {
            return this.releaseVersion == null
                    ? moduleName
                    : MessageFormat.format("{0}/{1,number,#}", moduleName, releaseVersion);
        }
    }

    /**
     * SemVer object (i.e. 1.3.5).
     */
    public static class SemVer {

        private final int major;
        private final int minor;
        private final Integer patch;

        public SemVer(int major, int minor, Integer patch) {
            this.major = major;
            this.minor = minor;
            this.patch = patch;
        }

        /**
         * @return Major version.
         */
        public int getMajor() {
            return major;
        }

        /**
         * @return Minor version.
         */
        public int getMinor() {
            return minor;
        }

        /**
         * @return Patch version (can be null).
         */
        public Integer getPatch() {
            return patch;
        }

        /**
         * @return The SemVer string (i.e. `1.5` or `1.5.3`).
         */
        public String getSemVerStr() {
            return (patch == null)
                    ? MessageFormat.format("{0,number,#}.{1,number,#}", major, minor)
                    : MessageFormat.format("{0,number,#}.{1,number,#}.{2,number,#}", major, minor, patch);
        }
    }

    /**
     * Module version numbers record.
     */
    public static class ModuleVersionNumbers {
        
        /**
         * Returns ModuleVersionNumbers for a brand new module.
         * @param moduleName The module name.
         * @return  The module version numbers
         */
        public static ModuleVersionNumbers getNewModule(String moduleName) {
            return new ModuleVersionNumbers(
                    moduleName, 
                    new SemVer(1, 0, null), 
                    1,
                    new ReleaseVal(moduleName.replaceAll("-", "."), 1));
        }

        private final String moduleName;
        private final SemVer spec;
        private final int implementation;
        private final ReleaseVal release;

        public ModuleVersionNumbers(String moduleName, SemVer spec, int implementation, ReleaseVal release) {
            this.moduleName = moduleName;
            this.spec = spec;
            this.implementation = implementation;
            this.release = release;
        }

        /**
         * @return The name of the module (i.e. org-sleuthkit-autopsy-core)
         */
        public String getModuleName() {
            return moduleName;
        }

        /**
         * @return The specification SemVer.
         */
        public SemVer getSpec() {
            return spec;
        }

        /**
         * @return The implementation number.
         */
        public int getImplementation() {
            return implementation;
        }

        /**
         * @return The release name/version.
         */
        public ReleaseVal getRelease() {
            return release;
        }

    }
}
