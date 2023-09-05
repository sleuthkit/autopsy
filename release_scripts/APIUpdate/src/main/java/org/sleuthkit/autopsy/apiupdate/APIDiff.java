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
import japicmp.model.JApiAnnotation;
import japicmp.model.JApiClass;
import japicmp.model.JApiConstructor;
import japicmp.model.JApiField;
import japicmp.model.JApiHasChangeStatus;
import japicmp.model.JApiImplementedInterface;
import japicmp.model.JApiMethod;
import japicmp.model.JApiSuperclass;
import japicmp.output.Filter;
import japicmp.output.stdout.StdoutOutputGenerator;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
        
        List<Pair<File, File>> retMapping = new ArrayList<>();

        for (String prevKey: (Iterable<String>) prevJars.keySet().stream().sorted(StringUtils::compareIgnoreCase)::iterator) {
            File prevFile = prevJars.get(prevKey);
            File curFile = currJars.get(prevKey);
            if (prevFile != null && curFile != null) {
                retMapping.add(Pair.of(prevFile, curFile));
            }
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
        // scope only to previous or current public packages
        Set<String> prevPublicApiPackages = getPublicPackages(prevJar);
        Set<String> curPublicApiPackages = getPublicPackages(curJar);
        
        boolean filterToPublicPackages = (prevPublicApiPackages == null && curPublicApiPackages == null) ? false : true;
        prevPublicApiPackages = prevPublicApiPackages == null ? Collections.emptySet() : prevPublicApiPackages;
        curPublicApiPackages = curPublicApiPackages == null ? Collections.emptySet() : curPublicApiPackages;
        
        Set<String> allPublicApiPackages = new HashSet<>();
        allPublicApiPackages.addAll(prevPublicApiPackages);
        allPublicApiPackages.addAll(curPublicApiPackages);

        Set<String> onlyPrevApiPackages = new HashSet<>();
        Set<String> onlyCurApiPackages = new HashSet<>();
        Set<String> commonApiPackages = new HashSet<>();
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

        JarArchiveComparatorOptions comparatorOptions = new JarArchiveComparatorOptions();
        // only classes in prev or current public api
        if (filterToPublicPackages) {
            comparatorOptions.getFilters().getExcludes().add((ClassFilter) (CtClass ctClass) -> !allPublicApiPackages.contains(ctClass.getPackageName()));    
        }
        
        // only public classes
        comparatorOptions.getFilters().getExcludes().add((ClassFilter) (CtClass ctClass) -> !Modifier.isPublic(ctClass.getModifiers()));
        // only fields, methods that are public or protected and class is not final
        comparatorOptions.getFilters().getExcludes().add((FieldFilter) (CtField ctField) -> excludeMember(ctField));
        comparatorOptions.getFilters().getExcludes().add((BehaviorFilter) (CtBehavior ctBehavior) -> excludeMember(ctBehavior));

        comparatorOptions.getIgnoreMissingClasses().setIgnoreAllMissingClasses(true);

        JarArchiveComparator jarArchiveComparator = new JarArchiveComparator(comparatorOptions);
        List<JApiClass> jApiClasses = jarArchiveComparator.compare(
                new JApiCmpArchive(prevJar, prevVersion),
                new JApiCmpArchive(curJar, curVersion)
        );

        PublicApiChangeType changeType = getChangeType(jApiClasses);

        Options options = Options.newDefault();
        options.setOldArchives(Arrays.asList(new JApiCmpArchive(prevJar, prevVersion)));
        options.setNewArchives(Arrays.asList(new JApiCmpArchive(curJar, curVersion)));
        options.setOutputOnlyModifications(true);

        StdoutOutputGenerator stdoutOutputGenerator = new StdoutOutputGenerator(options, jApiClasses);
        String humanReadableApiChange = stdoutOutputGenerator.generate();
        return new ComparisonRecord(prevVersion, curVersion, prevJar, curJar, humanReadableApiChange, changeType, onlyPrevApiPackages, onlyCurApiPackages, commonApiPackages);
    }

    /**
     * Updates an atomic ref to the public api change type to the maximum change
     * (where no change is min and incompatible change is max).
     *
     * @param apiChangeRef The atomic ref to a public api change type.
     * @param tp The possibly new change type.
     */
    private static void updateToMax(AtomicReference<PublicApiChangeType> apiChangeRef, JApiHasChangeStatus tp) {
        PublicApiChangeType apiChangeType;
        switch (tp.getChangeStatus()) {
            case UNCHANGED:
                apiChangeType = PublicApiChangeType.NONE;
                break;
            case NEW:
                apiChangeType = PublicApiChangeType.COMPATIBLE_CHANGE;
                break;
            case MODIFIED:
            case REMOVED:
            default:
                apiChangeType = PublicApiChangeType.INCOMPATIBLE_CHANGE;
                break;
        }

        final PublicApiChangeType finalApiChangeType = apiChangeType;
        apiChangeRef.updateAndGet((refType) -> Comparators.max(refType, finalApiChangeType));
    }

    /**
     * Determines the public api change type for the given classes.
     *
     * @param jApiClasses The classes.
     * @return The public API change type.
     */
    static PublicApiChangeType getChangeType(List<JApiClass> jApiClasses) {
        AtomicReference<PublicApiChangeType> apiChange = new AtomicReference<>(PublicApiChangeType.NONE);

        Filter.filter(jApiClasses, new Filter.FilterVisitor() {
            @Override
            public void visit(Iterator<JApiClass> itrtr, JApiClass jac) {
                updateToMax(apiChange, jac);
            }

            @Override
            public void visit(Iterator<JApiMethod> itrtr, JApiMethod jam) {
                updateToMax(apiChange, jam);
            }

            @Override
            public void visit(Iterator<JApiConstructor> itrtr, JApiConstructor jac) {
                updateToMax(apiChange, jac);
            }

            @Override
            public void visit(Iterator<JApiImplementedInterface> itrtr, JApiImplementedInterface jaii) {
                updateToMax(apiChange, jaii);
            }

            @Override
            public void visit(Iterator<JApiField> itrtr, JApiField jaf) {
                updateToMax(apiChange, jaf);
            }

            @Override
            public void visit(Iterator<JApiAnnotation> itrtr, JApiAnnotation jaa) {
                updateToMax(apiChange, jaa);
            }

            @Override
            public void visit(JApiSuperclass jas) {
                updateToMax(apiChange, jas);
            }
        });

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
