/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2019 Basis Technology Corp.
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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.TimeStampUtils;
import org.sleuthkit.autopsy.datasourceprocessors.AutoIngestDataSource;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Generates JSON output
 */
class OutputGenerator {

    private static final Logger logger = Logger.getLogger(OutputGenerator.class.getName());

    private OutputGenerator() {
    }

    static void saveCreateCaseOutput(Case caseForJob, String outputDirPath) {
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

    static void saveAddDataSourceOutput(Case caseForJob, AutoIngestDataSource dataSource, String outputDirPath) {

        List<Content> contentObjects = dataSource.getContent();
        if (contentObjects == null || contentObjects.isEmpty()) {
            logger.log(Level.SEVERE, "No content objects for 'Add Data Source' command"); //NON-NLS
            System.err.println("No content objects for 'Add Data Source' command"); //NON-NLS
            return;
        }

        JsonFactory jsonGeneratorFactory = new JsonFactory();
        String reportOutputPath = outputDirPath + File.separator + "addDataSource_" + TimeStampUtils.createTimeStamp() + ".json";
        java.io.File reportFile = Paths.get(reportOutputPath).toFile();
        try {
            Files.createDirectories(Paths.get(reportFile.getParent()));
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Unable to create output file " + reportFile.toString() + " for 'Add Data Source' command", ex); //NON-NLS
            System.err.println("Unable to create output file " + reportFile.toString() + " for 'Add Data Source' command"); //NON-NLS
            return;
        }

        JsonGenerator jsonGenerator = null;
        try {
            jsonGenerator = jsonGeneratorFactory.createGenerator(reportFile, JsonEncoding.UTF8);
            // instert \n after each field for more readable formatting
            jsonGenerator.setPrettyPrinter(new DefaultPrettyPrinter().withObjectIndenter(new DefaultIndenter("  ", "\n")));

            // save command output
            for (Content content : contentObjects) {
                String dataSourceName;
                if (content.getDataSource() instanceof Image) {
                    // image data source. Need to get display name
                    dataSourceName = getImageDisplayName(caseForJob, content.getId());
                    if (dataSourceName == null) {
                        // soma data sources do not have "display_name" set, use data source name instead
                        dataSourceName = content.getName();
                    }
                } else {
                    // logical data source
                    dataSourceName = content.getName();
                }

                // save the JSON output
                jsonGenerator.writeStartObject();
                jsonGenerator.writeStringField("@dataSourceName", dataSourceName);
                jsonGenerator.writeStringField("@dataSourceObjectId", String.valueOf(content.getId()));
                jsonGenerator.writeEndObject();
            }
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Failed to create JSON output for 'Add Data Source' command", ex); //NON-NLS
            System.err.println("Failed to create JSON output for 'Add Data Source' command"); //NON-NLS
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Failed to get data source info for 'Add Data Source' command output", ex); //NON-NLS
            System.err.println("Failed to get data source info for 'Add Data Source' command output"); //NON-NLS
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "Failed to get data source display name for 'Add Data Source' command output", ex); //NON-NLS
            System.err.println("Failed to get data source display name for 'Add Data Source' command output"); //NON-NLS
        } finally {
            if (jsonGenerator != null) {
                try {
                    jsonGenerator.close();
                } catch (IOException ex) {
                    logger.log(Level.WARNING, "Failed to close JSON output file for 'Add Data Source' command", ex); //NON-NLS
                    System.err.println("Failed to close JSON output file for 'Add Data Source' command"); //NON-NLS
                }
            }
        }
    }

    /**
     * Gets display_name from tsk_image_info table for Image data sources
     *
     * @param caseForJob Case object
     * @param dataSourceId object ID of the data source
     * @return
     * @throws TskCoreException
     * @throws SQLException
     */
    private static String getImageDisplayName(Case caseForJob, Long dataSourceId) throws TskCoreException, SQLException {
        String getImageDataSourceQuery = "select display_name from tsk_image_info where obj_id = " + dataSourceId;
        try (SleuthkitCase.CaseDbQuery queryResult = caseForJob.getSleuthkitCase().executeQuery(getImageDataSourceQuery)) {
            ResultSet resultSet = queryResult.getResultSet();
            // check if we got a result
            while (resultSet.next()) {
                // we got a result so the data source was an image data source
                return resultSet.getString(1);
            }
        }

        return null;
    }
}
