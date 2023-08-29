/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */
package org.sleuthkit.autopsy.classpathsimplication.apiupdate;

import japicmp.cmp.JApiCmpArchive;
import japicmp.cmp.JarArchiveComparator;
import japicmp.cmp.JarArchiveComparatorOptions;
import japicmp.model.JApiClass;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.apache.commons.cli.ParseException;
import org.sleuthkit.autopsy.classpathsimplication.apiupdate.CLIProcessor.CLIArgs;

/**
 *
 * @author gregd
 */
public class APIUpdate {

    public static void main(String[] args) {
        args = "-c C:\\Users\\gregd\\Documents\\Source\\autopsy\\build\\cluster\\modules -p C:\\Users\\gregd\\Desktop\\prevVers -cv 4.21.0 -pv 4.20.0".split(" ");
        CLIArgs cliArgs;
        try {
            cliArgs = CLIProcessor.parseCli(args);
            if (cliArgs.isIsHelp()) {
                CLIProcessor.printHelp(null);
                System.exit(0);
            }
        } catch (ParseException ex) {
            CLIProcessor.printHelp(ex);
            System.exit(-1);
            return;
        }

        for (String commonJarFileName : getCommonJars(cliArgs.getPreviousVersPath(), cliArgs.getCurrentVersPath())) {
//            getComparison(
//                    cliArgs.getPreviousVersion(),
//                    cliArgs.getCurrentVersion(),
//                    cliArgs.getPreviousVersPath().toPath().resolve(commonJarFileName).toFile(),
//                    cliArgs.getCurrentVersPath().toPath().resolve(commonJarFileName).toFile());
            try {
                Set<String> pubPackages = getPublicPackages(cliArgs.getPreviousVersPath().toPath().resolve(commonJarFileName).toFile());
                System.out.println(pubPackages);
            } catch (IOException ex) {
                Logger.getLogger(APIUpdate.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IllegalStateException ex) {
                Logger.getLogger(APIUpdate.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

    }

    private static final FileFilter JAR_FILTER
            = (File f) -> f.isFile() && (f.getName().toLowerCase().endsWith(".jar") || f.getName().toLowerCase().endsWith(".nbm"));

    private static List<String> getCommonJars(File prevDir, File currDir) {
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
        ZipFile zipFile = new ZipFile(jarFile);

        Enumeration<? extends ZipEntry> entries = zipFile.entries();

        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if ("META-INF/MANIFEST.MF".equalsIgnoreCase(entry.getName())) {
                InputStream stream = zipFile.getInputStream(entry);
                Manifest manifest = new Manifest(stream);
                Attributes attributes = manifest.getMainAttributes();
                String publicPackageStr = attributes.getValue("OpenIDE-Module-Public-Packages");
                if (publicPackageStr == null) {
                    throw new IllegalStateException(MessageFormat.format("Manifest for {0} does not have key of 'OpenIDE-Module-Public-Packages'", jarFile.getAbsolutePath()));
                } else {
                    return Stream.of(publicPackageStr.split(","))
                            .map(String::trim)
                            .map(str -> str.endsWith(".*") ? str.substring(0, str.length() - 2) : str)
                            .collect(Collectors.toSet());
                }
            }
        }

        throw new FileNotFoundException("Could not find MANIFEST.MF in " + jarFile.getAbsolutePath());
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

    private static void mainRun() {

        // get public API diff's, for each jar
        // limit to public packages
        // one of the following:
        // generate text output of difference
        // update version numbers in manifest file/references accordingly
    }

}
