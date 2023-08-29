/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */
package org.sleuthkit.autopsy.apiupdate;

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
import org.sleuthkit.autopsy.apiupdate.CLIProcessor.CLIArgs;
import org.sleuthkit.autopsy.apiupdate.ModuleUpdates.ModuleVersionNumbers;

/**
 *
 * @author gregd
 */
public class Main {

    public static void main(String[] args) {
        args = "-c C:\\Users\\gregd\\Documents\\Source\\autopsy\\build\\cluster\\modules -p C:\\Users\\gregd\\Desktop\\prevVers -cv 4.21.0 -pv 4.20.0 -s C:\\Users\\gregd\\Documents\\Source\\autopsy".split(" ");
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

        for (String commonJarFileName : APIDiff.getCommonJars(cliArgs.getPreviousVersPath(), cliArgs.getCurrentVersPath())) {
            try {
                ModuleVersionNumbers m = ModuleUpdates.getVersionsFromJar(cliArgs.getPreviousVersPath().toPath().resolve(commonJarFileName).toFile());
                System.out.println(MessageFormat.format("release: {0}, spec: {1}, implementation: {2}", m.getRelease().getFullReleaseStr(), m.getSpec().getSemVerStr(), m.getImplementation()));
                
            } catch (IOException ex) {
                Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            }
            
        }
        
        
        
//        for (String commonJarFileName : getCommonJars(cliArgs.getPreviousVersPath(), cliArgs.getCurrentVersPath())) {
////            getComparison(
////                    cliArgs.getPreviousVersion(),
////                    cliArgs.getCurrentVersion(),
////                    cliArgs.getPreviousVersPath().toPath().resolve(commonJarFileName).toFile(),
////                    cliArgs.getCurrentVersPath().toPath().resolve(commonJarFileName).toFile());
//            try {
//                Set<String> pubPackages = getPublicPackages(cliArgs.getPreviousVersPath().toPath().resolve(commonJarFileName).toFile());
//                System.out.println(pubPackages);
//            } catch (IOException ex) {
//                Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
//            } catch (IllegalStateException ex) {
//                Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
//            }
//        }

    }


    
    
    private static void mainRun() {

        // get public API diff's, for each jar
        // limit to public packages
        // one of the following:
        // generate text output of difference
        // update version numbers in manifest file/references accordingly
    }

}
