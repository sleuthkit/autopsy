/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.sleuthkit.autopsy.apiupdate;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
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
 *
 * @author gregd
 */
public class ModuleUpdates {

    private static final Logger LOGGER = Logger.getLogger(ModuleUpdates.class.getName());

    static {
        LOGGER.addHandler(new StreamHandler(System.out, new SimpleFormatter()));
    }

    private static final Pattern SPEC_REGEX = Pattern.compile("^\\s*((?<major>\\d*)\\.)?(?<minor>\\d*)(\\.(?<patch>\\d*))?\\s*$");
    private static final String SPEC_KEY = "OpenIDE-Module-Specification-Version";
    private static final String SPEC_PROPS_KEY = "spec.version.base";
    private static final String IMPL_KEY = "OpenIDE-Module-Implementation-Version";
    private static final String RELEASE_KEY = "OpenIDE-Module";

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

    private static SemVer parseSemVer(String semVerStr, SemVer defaultSemVer, String resourceForLogging) {
        if (StringUtils.isBlank(semVerStr)) {
            LOGGER.log(Level.SEVERE, MessageFormat.format("Unable to parse semver for empty string in {0}", resourceForLogging));
            return defaultSemVer;
        }

        Matcher m = SPEC_REGEX.matcher(semVerStr);
        if (m.find()) {
            try {
                String majorStr = m.group("major");
                int major = StringUtils.isBlank(majorStr) ? 1 : Integer.parseInt(majorStr);
                int minor = Integer.parseInt(m.group("minor"));
                String patchStr = m.group("patch");
                Integer patch = StringUtils.isBlank(patchStr) ? null : Integer.parseInt(patchStr);
                return new SemVer(major, minor, patch);
            } catch (NullPointerException | NumberFormatException ex) {
                LOGGER.log(Level.SEVERE, MessageFormat.format("Unable to parse semver string {0} for {1}", semVerStr, resourceForLogging), ex);
            }
        } else {
            LOGGER.log(Level.SEVERE, MessageFormat.format("Unable to parse semver string {0} for {1}", semVerStr, resourceForLogging));
        }

        return defaultSemVer;
    }

    private static ReleaseVal parseReleaseVers(String releaseStr, String resourceForLogging) {
        Matcher m = RELEASE_REGEX.matcher(StringUtils.defaultString(releaseStr));
        if (StringUtils.isNotBlank(releaseStr) && m.find()) {
            String releaseName = m.group("releaseName");
            Integer releaseNum = null;
            try {
                String releaseNumStr = m.group("releaseNum");
                releaseNum = StringUtils.isBlank(releaseNumStr) ? null : Integer.parseInt(releaseNumStr);
            } catch (NullPointerException | NumberFormatException ex) {
                LOGGER.log(Level.SEVERE, MessageFormat.format("Unable to parse release version string {0} for {1}", releaseStr, resourceForLogging), ex);
            }
            return new ReleaseVal(releaseName, releaseNum);
        } else {
            LOGGER.log(Level.SEVERE, MessageFormat.format("Unable to parse release version string {0} for {1}", releaseStr, resourceForLogging));
        }

        return new ReleaseVal("", null);

    }

    private static int tryParse(String str, int defaultVal, String resourceForLogging) {
        try {
            return Integer.parseInt(str);
        } catch (NullPointerException | NumberFormatException ex) {
            LOGGER.log(Level.SEVERE, MessageFormat.format("Unable to parse version string {0} for {1}", str, resourceForLogging), ex);
            return defaultVal;
        }
    }

    public static ModuleVersionNumbers getVersionsFromJar(File jarFile) throws IOException {
        Attributes manifest = ManifestLoader.loadFromJar(jarFile);
        String spec = manifest.getValue(SPEC_KEY);
        SemVer specSemVer = parseSemVer(spec, DEFAULT_SEMVER,
                MessageFormat.format("{0} in manifest for {1}", SPEC_KEY, jarFile));

        int implementation = tryParse(manifest.getValue(IMPL_KEY), DEFAULT_VERS_VAL,
                MessageFormat.format("{0} in manifest for {1}", IMPL_KEY, jarFile));

        ReleaseVal release = parseReleaseVers(manifest.getValue(RELEASE_KEY),
                MessageFormat.format("{0} in manifest for {1}", RELEASE_KEY, jarFile));

        return new ModuleVersionNumbers(jarFile.getName(), specSemVer, implementation, release);
    }

    private static void updateVersions() {
//        [specification major/minor/patch, implementation, release]
//        assumed defaults???
//        NON_COMPATIBLE:
//            specification.major += 1
//            implementation += 1
//            release += 1
//        COMPATIBLE:
//            specification.minor += 1
//            implementation += 1
//        NO_CHANGES:
//            implementation += 1
    }

    private static Map<String, File> getModuleDirs(File srcDir) throws IOException {
        Map<String, File> moduleDirMapping = new HashMap<>();
        for (File dir : srcDir.listFiles((File f) -> f.isDirectory())) {
            File manifestFile = dir.toPath().resolve(MANIFEST_FILE_NAME).toFile();
            if (manifestFile.isFile()) {
                try (FileInputStream manifestIs = new FileInputStream(manifestFile)) {
                    Attributes manifestAttrs = ManifestLoader.loadInputStream(manifestIs);
                    ReleaseVal releaseVal = parseReleaseVers(manifestAttrs.getValue(RELEASE_KEY), manifestFile.getAbsolutePath());
                    moduleDirMapping.put(releaseVal.getModuleName(), dir);
                }
            }
        }
        return moduleDirMapping;
    }

    static void setVersions(File srcDir, Map<String, ModuleVersionNumbers> versNums) {
        // TODO parse from repo/DIR/manifest.mf release version
        Map<String, File> moduleDirs;
        try {
            moduleDirs = getModuleDirs(srcDir);
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "There was an error getting module directories from source dir: " + srcDir.getAbsolutePath(), ex);
            return;
        }

        for (Entry<String, File> moduleNameDir : moduleDirs.entrySet()) {
            String moduleName = moduleNameDir.getKey();
            File moduleDir = moduleNameDir.getValue();
            ModuleVersionNumbers thisVersNums = versNums.get(moduleName);

            try {
                LOGGER.log(Level.INFO, "Updating for module name: " + moduleName);
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

    private static void updateProjProperties(File moduleDir, ModuleVersionNumbers thisVersNums) throws IOException {
        File projectPropsFile = moduleDir.toPath().resolve(PROJECT_PROPS_REL_PATH).toFile();
        if (!projectPropsFile.isFile()) {
            LOGGER.log(Level.SEVERE, "No project properties found at " + projectPropsFile.getAbsolutePath());
            return;
        }

        Properties projectProps = new Properties();
        try (FileInputStream propsIs = new FileInputStream(projectPropsFile)) {
            projectProps.load(propsIs);
        }

        String specVal = projectProps.getProperty(SPEC_PROPS_KEY);
        if (StringUtils.isNotBlank(specVal)) {
            projectProps.setProperty(SPEC_PROPS_KEY, thisVersNums.getSpec().getSemVerStr());

            try (FileOutputStream propsOut = new FileOutputStream(projectPropsFile)) {
                projectProps.store(propsOut, null);
            }
        }
    }

    private static void updateManifest(File moduleDir, ModuleVersionNumbers thisVersNums) throws IOException {
        File manifestFile = moduleDir.toPath().resolve(MANIFEST_FILE_NAME).toFile();
        if (!manifestFile.isFile()) {
            LOGGER.log(Level.SEVERE, "No manifest file found at " + manifestFile.getAbsolutePath());
            return;
        }

        Manifest manifest;
        try (FileInputStream manifestIs = new FileInputStream(manifestFile)) {
            manifest = ManifestLoader.loadManifest(manifestIs);
        }
        Attributes attributes = manifest.getMainAttributes();

        boolean updated = updateAttr(attributes, IMPL_KEY, Integer.toString(thisVersNums.getImplementation()), true);
        updated = updateAttr(attributes, SPEC_KEY, thisVersNums.getSpec().getSemVerStr(), true) || updated;
        updated = updateAttr(attributes, RELEASE_KEY, thisVersNums.getRelease().getFullReleaseStr(), true) || updated;
        if (updated) {
            try (FileOutputStream manifestOut = new FileOutputStream(manifestFile)) {
                manifest.write(manifestOut);
            }
        }
    }

    private static boolean updateAttr(Attributes attributes, String key, String val, boolean updateOnlyIfPresent) {
        if (updateOnlyIfPresent && attributes.getValue(key) == null) {
            return false;
        }

        attributes.putValue(key, val);
        return true;
    }

    private static void updateProjXml(File moduleDir, Map<String, ModuleVersionNumbers> versNums)
            throws IOException, ParserConfigurationException, SAXException, XPathExpressionException, TransformerException {

        File projXmlFile = moduleDir.toPath().resolve(PROJ_XML_REL_PATH).toFile();
        if (!projXmlFile.isFile()) {
            LOGGER.log(Level.SEVERE, "No project.xml file found at " + projXmlFile.getAbsolutePath());
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
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();

            // pretty print XML
            //transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            DOMSource source = new DOMSource(projectXmlDoc);
            try (FileOutputStream xmlOut = new FileOutputStream(projXmlFile)) {
                StreamResult result = new StreamResult(xmlOut);
                transformer.transform(source, result);
            }
        }

    }

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

    //        // Spec
//            // project.properties
//                // spec.version.base
//            // manifest
//                // OpenIDE-Module-Specification-Version
//        // Implementation
//            // manifest
//                // OpenIDE-Module-Implementation-Version
//        // Release
//            // manifest
//                // OpenIDE-Module (/number)
//                
//        // Dependency specification
//            // project.xml
//                // project.configuration.data.module-dependencies.dependency.run-dependency:
//                    // specification-version
//                    // release-version
    public static class ReleaseVal {

        private final String moduleName;
        private final Integer releaseVersion;

        public ReleaseVal(String moduleName, Integer releaseVersion) {
            this.moduleName = moduleName;
            this.releaseVersion = releaseVersion;
        }

        public String getModuleName() {
            return moduleName;
        }

        public Integer getReleaseVersion() {
            return releaseVersion;
        }

        public String getFullReleaseStr() {
            return this.releaseVersion == null
                    ? moduleName
                    : MessageFormat.format("{0}/{1,number,#}", moduleName, releaseVersion);
        }
    }

    public static class SemVer {

        private final int major;
        private final int minor;
        private final Integer patch;

        public SemVer(int major, int minor, Integer patch) {
            this.major = major;
            this.minor = minor;
            this.patch = patch;
        }

        public int getMajor() {
            return major;
        }

        public int getMinor() {
            return minor;
        }

        public Integer getPatch() {
            return patch;
        }

        public String getSemVerStr() {
            return (patch == null)
                    ? MessageFormat.format("{0,number,#}.{1,number,#}", major, minor)
                    : MessageFormat.format("{0,number,#}.{1,number,#}.{2,number,#}", major, minor, patch);
        }
    }

    public static class ModuleVersionNumbers {

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

        public String getModuleName() {
            return moduleName;
        }

        public SemVer getSpec() {
            return spec;
        }

        public int getImplementation() {
            return implementation;
        }

        public ReleaseVal getRelease() {
            return release;
        }

    }
}
