/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */
package org.sleuthkit.autopsy.apiupdate;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.cli.ParseException;
import org.sleuthkit.autopsy.apiupdate.CLIProcessor.CLIArgs;

/**
 *
 * @author gregd
 */
public class Main {

    public static void main(String[] args) {
        args = "-c C:\\Users\\gregd\\Desktop\\apidiff\\new -p C:\\Users\\gregd\\Desktop\\apidiff\\old -cv 4.21.0 -pv 4.20.0 -s C:\\Users\\gregd\\Documents\\Source\\autopsy".split(" ");
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
        
//        Map<String, ModuleVersionNumbers> versNums = Stream.of(
//                new ModuleVersionNumbers(
//                        "org.sleuthkit.autopsy.core", 
//                        new ModuleUpdates.SemVer(1,2,3),
//                        4,
//                        new ReleaseVal("org.sleuthkit.autopsy.core", 5)),
//                new ModuleVersionNumbers(
//                        "org.sleuthkit.autopsy.corelibs", 
//                        new ModuleUpdates.SemVer(6,7,8),
//                        9,
//                        new ReleaseVal("org.sleuthkit.autopsy.corelibs", 10)))
//                .collect(Collectors.toMap(v -> v.getModuleName(), v -> v, (v1, v2) -> v1));
//        
//        ModuleUpdates.setVersions(cliArgs.getSrcPath(), versNums);

        for (String commonJarFileName : APIDiff.getCommonJars(cliArgs.getPreviousVersPath(), cliArgs.getCurrentVersPath())) {
            try {
//                ModuleVersionNumbers m = ModuleUpdates.getVersionsFromJar(cliArgs.getPreviousVersPath().toPath().resolve(commonJarFileName).toFile());
//                System.out.println(MessageFormat.format("release: {0}, spec: {1}, implementation: {2}", m.getRelease().getFullReleaseStr(), m.getSpec().getSemVerStr(), m.getImplementation()));
                APIDiff.getComparison(
                        cliArgs.getPreviousVersion(), 
                        cliArgs.getCurrentVersion(), 
                        cliArgs.getPreviousVersPath().toPath().resolve(commonJarFileName).toFile(),
                        cliArgs.getCurrentVersPath().toPath().resolve(commonJarFileName).toFile());
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
