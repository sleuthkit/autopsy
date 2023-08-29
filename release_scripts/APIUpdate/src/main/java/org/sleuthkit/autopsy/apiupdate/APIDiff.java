/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.sleuthkit.autopsy.apiupdate;

import japicmp.cmp.JApiCmpArchive;
import japicmp.cmp.JarArchiveComparator;
import japicmp.cmp.JarArchiveComparatorOptions;
import japicmp.model.JApiClass;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    static Set<String> getPublicPackages(File jarFile) throws IOException, IllegalStateException {
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

    private static void getComparison(String prevVersion, String curVersion, File prevJar, File curJar) {
        JarArchiveComparatorOptions comparatorOptions = new JarArchiveComparatorOptions();
        comparatorOptions.getIgnoreMissingClasses().setIgnoreAllMissingClasses(true);
        JarArchiveComparator jarArchiveComparator = new JarArchiveComparator(comparatorOptions);
        List<JApiClass> jApiClasses = jarArchiveComparator.compare(
                new JApiCmpArchive(prevJar, prevVersion),
                new JApiCmpArchive(curJar, curVersion)
        );
        System.out.println("Comparing " + prevJar.getName());
        System.out.println(jApiClasses);
    }
}
