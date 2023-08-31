/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.sleuthkit.autopsy.apiupdate;

import japicmp.cmp.JApiCmpArchive;
import japicmp.cmp.JarArchiveComparator;
import japicmp.cmp.JarArchiveComparatorOptions;
import japicmp.config.Options;
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

    static void getComparison(String prevVersion, String curVersion, File prevJar, File curJar) throws IOException {
        JarArchiveComparatorOptions comparatorOptions = new JarArchiveComparatorOptions();
        //comparatorOptions.setAccessModifier(AccessModifier.);
        comparatorOptions.getIgnoreMissingClasses().setIgnoreAllMissingClasses(true);
        JarArchiveComparator jarArchiveComparator = new JarArchiveComparator(comparatorOptions);
        List<JApiClass> jApiClasses = jarArchiveComparator.compare(
                new JApiCmpArchive(prevJar, prevVersion),
                new JApiCmpArchive(curJar, curVersion)
        );

        Set<String> prevPublicApiPackages = getPublicPackages(prevJar);
        Set<String> curPublicApiPackages = getPublicPackages(curJar);

        // TODO handle diff in this list
        Set<String> allPublicApiPackages = new HashSet<>();
        allPublicApiPackages.addAll(prevPublicApiPackages);
        allPublicApiPackages.addAll(curPublicApiPackages);
        jApiClasses = jApiClasses.stream()
                .filter(cls -> allPublicApiPackages.contains(cls.getNewClass().or(cls.getOldClass()).get().getPackageName()))
                .collect(Collectors.toList());

        Options options = Options.newDefault();
        options.setOutputOnlyModifications(true);

        System.out.println("Comparing " + prevJar.getName());
        ChangeOutputGenerator stdoutOutputGenerator = new ChangeOutputGenerator(options, jApiClasses);
        String output = stdoutOutputGenerator.generate();
        System.out.println(output);
    }

    private static void generateOutput(Options options, List<JApiClass> jApiClasses, JarArchiveComparator jarArchiveComparator) {
//        for (JApiClass cls: jApiClasses) {
//            cls.is
//        }
//        
        
//        if (options.isSemanticVersioning()) {
//            SemverOut semverOut = new SemverOut(options, jApiClasses);
//            String output = semverOut.generate();
//            System.out.println(output);
//            return;
//        }
//        if (options.getXmlOutputFile().isPresent() || options.getHtmlOutputFile().isPresent()) {
//            SemverOut semverOut = new SemverOut(options, jApiClasses);
//            XmlOutputGeneratorOptions xmlOutputGeneratorOptions = new XmlOutputGeneratorOptions();
//            xmlOutputGeneratorOptions.setCreateSchemaFile(true);
//            xmlOutputGeneratorOptions.setSemanticVersioningInformation(semverOut.generate());
//            XmlOutputGenerator xmlGenerator = new XmlOutputGenerator(jApiClasses, options, xmlOutputGeneratorOptions);
//            try (XmlOutput xmlOutput = xmlGenerator.generate()) {
//                XmlOutputGenerator.writeToFiles(options, xmlOutput);
//            } catch (Exception e) {
//                throw new JApiCmpException(JApiCmpException.Reason.IoException, "Could not close output streams: " + e.getMessage(), e);
//            }
//        }
//        StdoutOutputGenerator stdoutOutputGenerator = new StdoutOutputGenerator(options, jApiClasses);
//        String output = stdoutOutputGenerator.generate();
//        System.out.println(output);

//        if (options.isErrorOnBinaryIncompatibility()
//                || options.isErrorOnSourceIncompatibility()
//                || options.isErrorOnExclusionIncompatibility()
//                || options.isErrorOnModifications()
//                || options.isErrorOnSemanticIncompatibility()) {
//            IncompatibleErrorOutput errorOutput = new IncompatibleErrorOutput(options, jApiClasses, jarArchiveComparator);
//            errorOutput.generate();
//        }
    }

    
    
//    enum ChangeType { CHANGE, ADD, REMOVE }
//    
//    public class ClassChangeDTO {
//        private final String packageStr;
//        private final String fullyQualifiedClassName;
//        private final ChangeType changeType;
//        
//        private final ClassDataDTO prevClassRecord;
//        private final ClassDataDTO currClassRecord;
//        
//        
//    }
//    
//    public class ClassDataDTO {
//        private final String packageStr;
//        private final String fullyQualifiedClassName;
//        private final AccessModifier accessModifier;
//    }
//    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
}
