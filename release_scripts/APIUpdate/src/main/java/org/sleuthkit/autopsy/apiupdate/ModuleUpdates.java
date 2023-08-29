/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.sleuthkit.autopsy.apiupdate;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author gregd
 */
public class ModuleUpdates {

    private static final Logger LOGGER = Logger.getLogger(ModuleUpdates.class.getName());

    private static final Pattern SPEC_REGEX = Pattern.compile("^\\s*((?<major>\\d*)\\.)?(?<minor>\\d*)(\\.(?<patch>\\d*))?\\s*$");
    private static final String SPEC_KEY = "OpenIDE-Module-Specification-Version";
    private static final String IMPL_KEY = "OpenIDE-Module-Implementation-Version";
    private static final String RELEASE_KEY = "OpenIDE-Module";
    

    private static final Pattern RELEASE_REGEX = Pattern.compile("^\\s*(?<releaseName>.+?)(/(?<releaseNum>\\d*))?\\s*$");

    private static final SemVer DEFAULT_SEMVER = new SemVer(1, 0, null);
    private static final int DEFAULT_VERS_VAL = 1;

    private static final String AUTOPSY_PREFIX = "org-sleuthkit-autopsy-";
    
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

    private static void setVersions(File srcDir, Map<String, ModuleVersionNumbers> versNums) {
        // TODO parse from repo/DIR/manifest.mf release version
        Map<String, File> moduleDirs = Stream.of(srcDir.listFiles((File f) -> f.isDirectory()))
                .filter(f -> versNums.containsKey((AUTOPSY_PREFIX + f.getName()).toLowerCase()))
                .collect(Collectors.toMap(f -> (AUTOPSY_PREFIX + f.getName()).toLowerCase(), f -> f, (f1, f2) -> f1);
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
    }

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

        private final String jarName;
        private final SemVer spec;
        private final int implementation;
        private final ReleaseVal release;

        public ModuleVersionNumbers(String jarName, SemVer spec, int implementation, ReleaseVal release) {
            this.jarName = jarName;
            this.spec = spec;
            this.implementation = implementation;
            this.release = release;
        }

        public String getJarName() {
            return jarName;
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
