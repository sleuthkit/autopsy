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

import com.google.common.collect.Comparators;
import japicmp.cmp.JApiCmpArchive;
import japicmp.cmp.JarArchiveComparator;
import japicmp.cmp.JarArchiveComparatorOptions;
import japicmp.config.Options;
import japicmp.filter.BehaviorFilter;
import japicmp.filter.ClassFilter;
import japicmp.filter.FieldFilter;
import japicmp.model.JApiClass;
import japicmp.model.JApiSemanticVersionLevel;
import static japicmp.model.JApiSemanticVersionLevel.MAJOR;
import static japicmp.model.JApiSemanticVersionLevel.PATCH;
import japicmp.output.semver.SemverOut;
import japicmp.output.stdout.StdoutOutputGenerator;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMember;
import javassist.Modifier;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Handles diffing the public API between two jar files.
 */
public class APIDiff {

    private static final Logger LOGGER = Logger.getLogger(APIDiff.class.getName());

    // filters to a jar or nbm file
    private static final FileFilter JAR_FILTER
            = (File f) -> f.isFile() && (f.getName().toLowerCase().endsWith(".jar") || f.getName().toLowerCase().endsWith(".nbm"));

    /**
     * Identifies common jar files between two directories. Only files listed in
     * the directory are considered. This method does not recurse.
     *
     * @param prev The previous version directory.
     * @param curr The current version directory.
     * @return The jar file names.
     */
    static List<Pair<File, File>> getCommonJars(File prev, File curr) {
        if (prev.isFile() && curr.isFile()) {
            return Arrays.asList(Pair.of(prev, curr));
        }

        Map<String, File> prevJars = getJars(prev);
        Map<String, File> currJars = getJars(curr);
        Set<String> combined = new HashSet<>(prevJars.keySet());
        combined.addAll(currJars.keySet());

        List<Pair<File, File>> retMapping = new ArrayList<>();

        for (String prevKey : (Iterable<String>) combined.stream().sorted(StringUtils::compareIgnoreCase)::iterator) {
            File prevFile = prevJars.get(prevKey);
            File curFile = currJars.get(prevKey);
            retMapping.add(Pair.of(prevFile, curFile));
        }

        return retMapping;
    }

    /**
     * Returns all jar files listed in directory (does not recurse).
     *
     * @param dir The directory.
     * @return The jar file names.
     */
    private static Map<String, File> getJars(File dir) {
        File[] files = dir.isDirectory() ? dir.listFiles(JAR_FILTER) : new File[]{dir};
        files = files == null ? new File[0] : files;
        return Stream.of(files).collect(Collectors.toMap(f -> f.getName(), f -> f, (f1, f2) -> f1));
    }

    /**
     * Uses manfest.mf specification of "OpenIDE-Module-Public-Packages" to
     * determine public API packages.
     *
     * @param jarFile The jar file.
     * @return The set of package names.
     * @throws IOException
     * @throws IllegalStateException
     */
    private static Set<String> getPublicPackages(File jarFile) throws IOException, IllegalStateException {
        String publicPackageStr = ManifestLoader.loadFromJar(jarFile).getValue("OpenIDE-Module-Public-Packages");
        if (publicPackageStr == null) {
            LOGGER.log(Level.WARNING, MessageFormat.format("Manifest for {0} does not have key of 'OpenIDE-Module-Public-Packages'", jarFile.getAbsolutePath()));
            return null;
        } else {
            return Stream.of(publicPackageStr.split(","))
                    .map(String::trim)
                    .map(str -> str.endsWith(".*") ? str.substring(0, str.length() - 2) : str)
                    .collect(Collectors.toSet());
        }
    }

    /**
     * Filter to identify non-public, non-protected members for exclusion.
     *
     * @param member The CtMember (field/method).
     * @return True if should be excluded (private/package private).
     */
    static boolean excludeMember(CtMember member) {
        return !Modifier.isPublic(member.getModifiers()) && !Modifier.isProtected(member.getModifiers());
    }

    /**
     * Compares two jar files.
     *
     * @param prevVersion The name of the previous version.
     * @param curVersion The name of the current version.
     * @param prevJar The previous version jar file.
     * @param curJar The current version jar file.
     * @return A record describing the comparison in public API.
     * @throws IOException
     */
    static ComparisonRecord getComparison(String prevVersion, String curVersion, File prevJar, File curJar) throws IOException {
        // scope only to previous or current public packages if jars have public packages
        Set<String> prevPublicApiPackages = getPublicPackages(prevJar);
        Set<String> curPublicApiPackages = getPublicPackages(curJar);

        Set<String> onlyPrevApiPackages = new HashSet<>();
        Set<String> onlyCurApiPackages = new HashSet<>();
        Set<String> commonApiPackages = new HashSet<>();

        Optional<Set<String>> allPublicApiPackagesFilter = Optional.empty();
        if (prevPublicApiPackages != null && curPublicApiPackages != null) {
            Set<String> allPublicApiPackages = new HashSet<>();
            allPublicApiPackages.addAll(prevPublicApiPackages);
            allPublicApiPackages.addAll(curPublicApiPackages);
            allPublicApiPackagesFilter.of(allPublicApiPackages);
            
            for (String apiPackage : allPublicApiPackages) {
                boolean inPrev = prevPublicApiPackages.contains(apiPackage);
                boolean inCur = curPublicApiPackages.contains(apiPackage);
                if (inPrev && !inCur) {
                    onlyPrevApiPackages.add(apiPackage);
                } else if (!inPrev && inCur) {
                    onlyCurApiPackages.add(apiPackage);
                } else {
                    commonApiPackages.add(apiPackage);
                }
            }
        }

        // get classes diff for public api changes
        List<JApiClass> jApiClasses = getClassDiff(true, allPublicApiPackagesFilter, prevJar, prevVersion, curJar, curVersion);

        Options options = Options.newDefault();
        options.setOldArchives(Arrays.asList(new JApiCmpArchive(prevJar, prevVersion)));
        options.setNewArchives(Arrays.asList(new JApiCmpArchive(curJar, curVersion)));
        options.setOutputOnlyModifications(true);

        PublicApiChangeType changeType = getChangeType(options, jApiClasses);
        
        // if the change type is none, check for any internal changes
        if (changeType == PublicApiChangeType.NONE) {
            List<JApiClass> alljApiClasses = getClassDiff(false,Optional.empty(), prevJar, prevVersion, curJar, curVersion);
            PublicApiChangeType allClassChangeType = getChangeType(options, jApiClasses);
            changeType = allClassChangeType == PublicApiChangeType.NONE ? PublicApiChangeType.NONE : allClassChangeType.INTERNAL_CHANGE;
        }
        
        StdoutOutputGenerator stdoutOutputGenerator = new StdoutOutputGenerator(options, jApiClasses);
        String humanReadableApiChange = stdoutOutputGenerator.generate();
        return new ComparisonRecord(prevVersion, curVersion, prevJar, curJar, humanReadableApiChange, changeType, onlyPrevApiPackages, onlyCurApiPackages, commonApiPackages);
    }

    private static List<JApiClass> getClassDiff(boolean filter, Optional<Set<String>> publicPackagesFilter, File prevJar, String prevVersion, File curJar, String curVersion) {
        JarArchiveComparatorOptions comparatorOptions = new JarArchiveComparatorOptions();
        if (filter) {
            // only classes in prev or current public api
            if (publicPackagesFilter.isPresent()) {
                final Set<String> publicPackages = publicPackagesFilter.get();
                comparatorOptions.getFilters().getExcludes().add((ClassFilter) (CtClass ctClass) -> !publicPackages.contains(ctClass.getPackageName()));
            }
            // only public classes
            comparatorOptions.getFilters().getExcludes().add((ClassFilter) (CtClass ctClass) -> !Modifier.isPublic(ctClass.getModifiers()));
            // only fields, methods that are public or protected and class is not final
            comparatorOptions.getFilters().getExcludes().add((FieldFilter) (CtField ctField) -> excludeMember(ctField));
            comparatorOptions.getFilters().getExcludes().add((BehaviorFilter) (CtBehavior ctBehavior) -> excludeMember(ctBehavior));
        }
        comparatorOptions.getIgnoreMissingClasses().setIgnoreAllMissingClasses(true);
        JarArchiveComparator jarArchiveComparator = new JarArchiveComparator(comparatorOptions);
        List<JApiClass> jApiClasses = jarArchiveComparator.compare(
                new JApiCmpArchive(prevJar, prevVersion),
                new JApiCmpArchive(curJar, curVersion)
        );
        return jApiClasses;
    }

    /**
     * Updates an atomic ref to the public api change type to the maximum change
     * (where no change is min and incompatible change is max).
     *
     * @param apiChangeRef The atomic ref to a public api change type.
     * @param versionLevel The semantic version level of the current change.
     */
    private static void updateToMax(AtomicReference<PublicApiChangeType> apiChangeRef, JApiSemanticVersionLevel versionLevel) {
        PublicApiChangeType apiChangeType;
        if (versionLevel == null) {
            return;
        }

        switch (versionLevel) {
            case PATCH:
                apiChangeType = PublicApiChangeType.INTERNAL_CHANGE;
                break;
            case MINOR:
                apiChangeType = PublicApiChangeType.COMPATIBLE_CHANGE;
                break;
            case MAJOR:
                apiChangeType = PublicApiChangeType.INCOMPATIBLE_CHANGE;
                break;
            default:
                LOGGER.log(Level.WARNING, "Unknown sem ver type: " + versionLevel.name());
                apiChangeType = PublicApiChangeType.INCOMPATIBLE_CHANGE;
                break;
        }

        final PublicApiChangeType finalApiChangeType = apiChangeType;
        apiChangeRef.updateAndGet((refType) -> Comparators.max(refType, finalApiChangeType));
    }

    /**
     * Determines the public api change type for the given classes.
     *
     * @param options The options for output.
     * @param jApiClasses The classes.
     * @return The public API change type.
     */
    static PublicApiChangeType getChangeType(Options options, List<JApiClass> jApiClasses) {
        AtomicReference<PublicApiChangeType> apiChange = new AtomicReference<>(PublicApiChangeType.NONE);
        new SemverOut(options, jApiClasses, (change, semanticVersionLevel) -> updateToMax(apiChange, semanticVersionLevel)).generate();
        return apiChange.get();
    }

    /**
     * A record describing the public API comparison of a previous and current
     * version.
     */
    public static class ComparisonRecord {

        private final String prevVersion;
        private final String curVersion;
        private final File prevJar;
        private final File curJar;
        private final String humanReadableApiChange;
        private final PublicApiChangeType changeType;
        private final Set<String> onlyPrevApiPackages;
        private final Set<String> onlyCurrApiPackages;
        private final Set<String> commonApiPackages;

        public ComparisonRecord(String prevVersion, String curVersion, File prevJar, File curJar, String humanReadableApiChange, PublicApiChangeType changeType, Set<String> onlyPrevApiPackages, Set<String> onlyCurrApiPackages, Set<String> commonApiPackages) {
            this.prevVersion = prevVersion;
            this.curVersion = curVersion;
            this.prevJar = prevJar;
            this.curJar = curJar;
            this.humanReadableApiChange = humanReadableApiChange;
            this.changeType = changeType;
            this.onlyPrevApiPackages = onlyPrevApiPackages;
            this.onlyCurrApiPackages = onlyCurrApiPackages;
            this.commonApiPackages = commonApiPackages;
        }

        /**
         * @return The previous version name.
         */
        public String getPrevVersion() {
            return prevVersion;
        }

        /**
         * @return The current version name.
         */
        public String getCurVersion() {
            return curVersion;
        }

        /**
         * @return The previous version jar file.
         */
        public File getPrevJar() {
            return prevJar;
        }

        /**
         * @return The current version jar file.
         */
        public File getCurJar() {
            return curJar;
        }

        /**
         * @return The human readable output describing the api changes.
         */
        public String getHumanReadableApiChange() {
            return humanReadableApiChange;
        }

        /**
         * @return The public api change type.
         */
        public PublicApiChangeType getChangeType() {
            return changeType;
        }

        /**
         * @return Names of packages only in previous public API.
         */
        public Set<String> getOnlyPrevApiPackages() {
            return onlyPrevApiPackages;
        }

        /**
         * @return Names of packages only in current public API.
         */
        public Set<String> getOnlyCurrApiPackages() {
            return onlyCurrApiPackages;
        }

        /**
         * @return Names of packages in common between previous and current
         * public API.
         */
        public Set<String> getCommonApiPackages() {
            return commonApiPackages;
        }

    }

}
