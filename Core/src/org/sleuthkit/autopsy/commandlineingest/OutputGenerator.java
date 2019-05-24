/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.commandlineingest;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.TimeStampUtils;

/**
 * Generates JSON output 
 */
class OutputGenerator {
    private static final Logger logger = Logger.getLogger(OutputGenerator.class.getName());

    private OutputGenerator() {
    }
    
    static void saveCreateCaseOutput(Case caseForJob, String outputDirPath) {
        // Create the JSON generator
        JsonFactory jsonGeneratorFactory = new JsonFactory();
        String reportOutputPath = outputDirPath + File.separator + "createCase_" + TimeStampUtils.createTimeStamp() + ".json";
        java.io.File reportFile = Paths.get(reportOutputPath).toFile();
        try {
            Files.createDirectories(Paths.get(reportFile.getParent()));
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Unable to create output file " + reportFile.toString() + " for 'Create Case' command", ex); //NON-NLS
            System.err.println("Unable to create output file " + reportFile.toString() + " for 'Create Case' command"); //NON-NLS
            return;
        }
        
        JsonGenerator jsonGenerator = null;        
        try {
            jsonGenerator = jsonGeneratorFactory.createGenerator(reportFile, JsonEncoding.UTF8);
            // instert \n after each field for more readable formatting
            jsonGenerator.setPrettyPrinter(new DefaultPrettyPrinter().withObjectIndenter(new DefaultIndenter("  ", "\n")));
            
            // save command output
            jsonGenerator.writeStartObject();
            jsonGenerator.writeStringField("@caseDir", caseForJob.getCaseDirectory());
            jsonGenerator.writeEndObject();            
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Failed to create JSON output for 'Create Case' command", ex); //NON-NLS
            System.err.println("Failed to create JSON output for 'Create Case' command"); //NON-NLS
        } finally {
            if (jsonGenerator != null) {
                try {
                    jsonGenerator.close();
                } catch (IOException ex) {
                    logger.log(Level.WARNING, "Failed to close JSON output file for 'Create Case' command", ex); //NON-NLS
                    System.err.println("Failed to close JSON output file for 'Create Case' command"); //NON-NLS
                }
            }
        }
        
    }
}
