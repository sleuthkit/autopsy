/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMember;
import javassist.Modifier;

/**
 *
 * @author gregd
 */
public class APIDiff {

    private static final FileFilter JAR_FILTER
            = (File f) -> f.isFile() && (f.getName().toLowerCase().endsWith(".jar") || f.getName().toLowerCase().endsWith(".nbm"));

    static List<String> getCommonJars(File prevDir, File currDir) {
        Set<String> prevJars = getJars(prevDir);
        Set<String> currJars = getJars(currDir);

        Set<String> commonJars = new HashSet<>(prevJars);
        commonJars.retainAll(currJars);

        // TODO how to handle different
        return commonJars.stream().sorted().collect(Collectors.toList());
    }

    private static Set<String> getJars(File dir) {
        return Stream.of(dir.listFiles(JAR_FILTER))
                .map(f -> f.getName())
                .collect(Collectors.toSet());
    }

    private static Set<String> getPublicPackages(File jarFile) throws IOException, IllegalStateException {
        String publicPackageStr = ManifestLoader.loadFromJar(jarFile).getValue("OpenIDE-Module-Public-Packages");
        if (publicPackageStr == null) {
            throw new IllegalStateException(MessageFormat.format("Manifest for {0} does not have key of 'OpenIDE-Module-Public-Packages'", jarFile.getAbsolutePath()));
        } else {
            return Stream.of(publicPackageStr.split(","))
                    .map(String::trim)
                    .map(str -> str.endsWith(".*") ? str.substring(0, str.length() - 2) : str)
                    .collect(Collectors.toSet());
        }
    }

    // only fields, methods that are public or protected
    static boolean excludeMember(CtMember member) {
        return !Modifier.isPublic(member.getModifiers()) && !Modifier.isProtected(member.getModifiers());
    }

    static ComparisonRecord getComparison(String prevVersion, String curVersion, File prevJar, File curJar) throws IOException {
        // scope only to previous or current public packages
        Set<String> prevPublicApiPackages = getPublicPackages(prevJar);
        Set<String> curPublicApiPackages = getPublicPackages(curJar);

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
        comparatorOptions.getFilters().getExcludes().add((ClassFilter) (CtClass ctClass) -> !allPublicApiPackages.contains(ctClass.getPackageName()));
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
        options.setOutputOnlyModifications(true);

        StdoutOutputGenerator stdoutOutputGenerator = new StdoutOutputGenerator(options, jApiClasses);
        String humanReadableApiChange = stdoutOutputGenerator.generate();
        return new ComparisonRecord(prevVersion, curVersion, prevJar, curJar, humanReadableApiChange, changeType, onlyPrevApiPackages, onlyCurApiPackages, commonApiPackages);
    }

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
        };

        final PublicApiChangeType finalApiChangeType = apiChangeType;
        apiChangeRef.updateAndGet((refType) -> Comparators.max(refType, finalApiChangeType));
    }

     static PublicApiChangeType getChangeType(List<JApiClass> jApiClasses) {
        AtomicReference<PublicApiChangeType> apiChange = new AtomicReference<PublicApiChangeType>(PublicApiChangeType.NONE);

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

        public String getPrevVersion() {
            return prevVersion;
        }

        public String getCurVersion() {
            return curVersion;
        }

        public File getPrevJar() {
            return prevJar;
        }

        public File getCurJar() {
            return curJar;
        }

        public String getHumanReadableApiChange() {
            return humanReadableApiChange;
        }

        public PublicApiChangeType getChangeType() {
            return changeType;
        }

        public Set<String> getOnlyPrevApiPackages() {
            return onlyPrevApiPackages;
        }

        public Set<String> getOnlyCurrApiPackages() {
            return onlyCurrApiPackages;
        }

        public Set<String> getCommonApiPackages() {
            return commonApiPackages;
        }
        
        
    }

}
