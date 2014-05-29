/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this fileOnDisk except in compliance with the License.
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
package org.sleuthkit.autopsy.externalresults;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleContentEvent;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Mechanism to import blackboard items, derived files, etc. It is decoupled
 * from the actual parsing/interfacing with external data.
 */
public class ExternalResultsImporter {

    private static final Logger logger = Logger.getLogger(ExternalResultsImporter.class.getName());
    private static final String EVENT_STRING = "External Results";

    /**
     * Import results for a data source from an XML fileOnDisk (see
     * org.sleuthkit.autopsy.externalresults.autopsy_external_results.xsd).
     *
     * @param dataSource A data source.
     * @param resultsXmlPath Path to an XML fileOnDisk containing results (e.g.,
     * blackboard artifacts, derived files, reports) from the data source.
     */
    public static void importResultsFromXML(Content dataSource, String resultsXmlPath) {
        ExternalResults results = new ExternalResultsXMLParser(dataSource, resultsXmlPath).parse();
        generateDerivedFiles(dataSource, results);
        importArtifacts(dataSource, results);
        importReports(results);
    }

    /**
     * Add derived files. This should be called before importArtifacts() in case
     * any of the new blackboard artifacts refer to expected derived files.
     *
     * @param results
     * @param dataSource
     */
    private static void generateDerivedFiles(Content dataSource, ExternalResults results) {
        try {
            FileManager fileManager = Case.getCurrentCase().getServices().getFileManager();
            for (ExternalResults.DerivedFile derivedFileResult : results.getDerivedFiles()) {
                String path = derivedFileResult.getLocalPath();
                File fileOnDisk = new File(path);
                if (fileOnDisk.exists()) {
                    // Get a parent object.
                    AbstractFile parentFile;
                    if (!derivedFileResult.getParentPath().isEmpty()) {
                        parentFile = findFileInDatabase(derivedFileResult.getParentPath());
                    } else {
                        // RJCTODO: Create a virtual folder for the data source, if necessary. Make that
                        // folder the parent.
//                        List<AbstractFile> files = Case.getCurrentCase().getSleuthkitCase().findFiles(dataSource, "");
//                        parentFile = files.get(0);
                        parentFile = null;
                    }

                    if (parentFile != null) {
                        // Try to get a relative local path
                        String relPath = path;
                        Path pathTo = Paths.get(path);
                        if (pathTo.isAbsolute()) {
                            Path pathBase = Paths.get(Case.getCurrentCase().getCaseDirectory());
                            try {
                                Path pathRelative = pathBase.relativize(pathTo);
                                relPath = pathRelative.toString();
                            } catch (IllegalArgumentException ex) {
                                // RJCTODO: Fix
//                                logger.log(Level.WARNING, "Derived file {0} path may be incorrect. The derived file object will still be added to the database.", fileName);
                            }
                        }

                        DerivedFile derivedFile = fileManager.addDerivedFile(fileOnDisk.getName(), relPath, fileOnDisk.length(),
                                0, 0, 0, 0, // Do not currently have fileOnDisk times for derived files.
                                true, parentFile, "", EVENT_STRING, "", "");

                        if (derivedFile != null) {
                            IngestServices.getInstance().fireModuleContentEvent(new ModuleContentEvent(derivedFile));
                        }
                    } else {
                        // RJCTODO: Emit a warning or get rid of the condition.
                    }
                }
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, ex.getLocalizedMessage());
        }
    }

    /**
     * Create and add new blackboard artifacts, attributes, and types
     *
     * @param results
     * @param dataSource
     */
    private static void importArtifacts(Content dataSource, ExternalResults results) {
        for (ExternalResults.Artifact art : results.getArtifacts()) {
            try {
                // Get the artifact type id if defined, or create a new 
                // user-defined artifact type.
                int artifactTypeId;
                BlackboardArtifact.ARTIFACT_TYPE stdArtType = isStandardArtifactType(art.getType());
                if (stdArtType != null) {
                    artifactTypeId = stdArtType.getTypeID();
                } else {
                    artifactTypeId = Case.getCurrentCase().getSleuthkitCase().addArtifactType(art.getType(), art.getType());
                }

                Collection<BlackboardAttribute> bbAttributes = new ArrayList<>();
                for (ExternalResults.ArtifactAttribute attr : art.getAttributes()) {
                    int bbAttrTypeId;
                    BlackboardAttribute.ATTRIBUTE_TYPE stdAttrType = isStandardAttributeType(attr.getType());
                    if (stdAttrType != null) {
                        bbAttrTypeId = stdAttrType.getTypeID();
                    } else {
                        // assume it's user defined RJCTODO fix
                        bbAttrTypeId = Case.getCurrentCase().getSleuthkitCase().addAttrType(attr.getType(), attr.getType());
                    }

                    BlackboardAttribute bbAttr = null;
                    switch (attr.getValueType()) {
                        case "text": //NON-NLS
                            bbAttr = new BlackboardAttribute(bbAttrTypeId, attr.getSourceModule(), attr.getValue());
                            break;
                        case "int32": //NON-NLS
                            int intValue = Integer.parseInt(attr.getValue());
                            bbAttr = new BlackboardAttribute(bbAttrTypeId, attr.getSourceModule(), intValue);
                            break;
                        case "int64": //NON-NLS
                            long longValue = Long.parseLong(attr.getValue());
                            bbAttr = new BlackboardAttribute(bbAttrTypeId, attr.getSourceModule(), longValue);
                            break;
                        case "double": //NON-NLS
                            double doubleValue = Double.parseDouble(attr.getValue());
                            bbAttr = new BlackboardAttribute(bbAttrTypeId, attr.getSourceModule(), doubleValue);
                            break;
                        default:
                            logger.log(Level.WARNING, "Ignoring invalid attribute value type {0}", attr.getValueType());
                            break;
                    }
                    if (bbAttr != null) {
                        bbAttributes.add(bbAttr);
                    }
                }

                // Get associated fileOnDisk (if any) to use as the content obj to attach the artifact to
                Content currContent = null;
                if (art.getSourceFilePath().isEmpty()) {
                    currContent = findFileInDatabase(art.getSourceFilePath());
                }

                // If no associated fileOnDisk, use current data source itself
                if (currContent == null) {
                    currContent = dataSource;
                }

                BlackboardArtifact bbArt = currContent.newArtifact(artifactTypeId);
                bbArt.addAttributes(bbAttributes);
                if (stdArtType != null) {
                    IngestServices.getInstance().fireModuleDataEvent(new ModuleDataEvent(EVENT_STRING, stdArtType)); //NON-NLS
                }
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, ex.getLocalizedMessage());
            }
        }
    }

    private static void importReports(ExternalResults results) {
        for (ExternalResults.Report report : results.getReports()) {
            try {
                String reportPath = report.getLocalPath();
                File reportFile = new File(reportPath);
                if (reportFile.exists()) {
                    // Try to get a relative local path
                    String relPath = reportPath;
                    Path pathTo = Paths.get(reportPath);
                    if (pathTo.isAbsolute()) {
                        Path pathBase = Paths.get(Case.getCurrentCase().getCaseDirectory());
                        try {
                            Path pathRelative = pathBase.relativize(pathTo);
                            relPath = pathRelative.toString();
                        } catch (IllegalArgumentException ex) {
                            logger.log(Level.WARNING, "Report file {0} path may be incorrect. The report record will still be added to the database.", reportPath);
                        }
                    }

                    if (!relPath.isEmpty()) {
                        Case.getCurrentCase().getSleuthkitCase().addReport(relPath, report.getDisplayName());
                    }
                }
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, ex.getLocalizedMessage());
            }
        }
    }

    /**
     *
     * @param artTypeStr
     * @return valid artifact type or null if the type is not a standard TSK one
     */
    private static BlackboardArtifact.ARTIFACT_TYPE isStandardArtifactType(String artTypeStr) {
        BlackboardArtifact.ARTIFACT_TYPE[] stdArts = BlackboardArtifact.ARTIFACT_TYPE.values();
        for (BlackboardArtifact.ARTIFACT_TYPE art : stdArts) {
            if (art.getLabel().equals(artTypeStr)) {
                return art;
            }
        }
        return null;
    }

    /**
     *
     * @param attrTypeStr
     * @return valid attribute type or null if the type is not a standard TSK
     * one
     */
    private static BlackboardAttribute.ATTRIBUTE_TYPE isStandardAttributeType(String attrTypeStr) {
        BlackboardAttribute.ATTRIBUTE_TYPE[] stdAttrs = BlackboardAttribute.ATTRIBUTE_TYPE.values();
        for (BlackboardAttribute.ATTRIBUTE_TYPE attr : stdAttrs) {
            if (attr.getLabel().equals(attrTypeStr)) {
                return attr;
            }
        }
        return null;
    }

    /**
     * util function
     *
     * @param filePath full path including fileOnDisk or dir name
     * @return AbstractFile
     * @throws TskCoreException
     */
    private static AbstractFile findFileInDatabase(String filePath) throws TskCoreException {
        AbstractFile abstractFile = null;
        String fileName = filePath;
        String parentPath = "";
        int charPos = filePath.lastIndexOf("/");
        if (charPos >= 0) {
            fileName = filePath.substring(charPos + 1);
            parentPath = filePath.substring(0, charPos + 1);
        }
        String whereQuery = "name='" + fileName + "' AND parent_path='" + parentPath + "'"; //NON-NLS
        List<AbstractFile> files = Case.getCurrentCase().getSleuthkitCase().findAllFilesWhere(whereQuery);
        if (files.size() > 0) {
            abstractFile = files.get(0);
            if (files.size() > 1) {
                logger.log(Level.WARNING, "Ignoring extra files found for path {0}", filePath);
            }
        }
        return abstractFile;
    }
}
