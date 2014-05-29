/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
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
package org.sleuthkit.autopsy.externalresults;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.sleuthkit.autopsy.coreutils.XMLUtil;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Parses an XML representation of externally generated results (artifacts,
 * derived files, and reports).
 */
final class ExternalResultsXMLParser {

    private static final Logger logger = Logger.getLogger(ExternalResultsXMLParser.class.getName());
    private static final String XSD_FILE = "autopsy_external_results.xsd"; //NON-NLS
    private final Content dataSource;
    private final String resultsFilePath;
    private final ExternalResults externalResults;

    /**
     * Constructor.
     *
     * @param importFilePath Full path of the results file to be parsed.
     */
    ExternalResultsXMLParser(Content dataSource, String resultsFilePath) {
        this.dataSource = dataSource;
        this.resultsFilePath = resultsFilePath;
        externalResults = new ExternalResults();
    }

    /**
     * Parses artifacts, derived files, and reports represented in the XML file
     * passed to the constructor.
     *
     * @return A possibly empty collection of result objects (artifacts, derived
     * files, and reports).
     */
    ExternalResults parse() {
        try {
            // Try loading and parsing the results file - note that 
            // XMLUtil.loadDoc() only logs a warning if the file does not 
            // conform to the XSD. Validation is still required!
            final Document doc = XMLUtil.loadDoc(ExternalResultsXMLParser.class, resultsFilePath, XSD_FILE);
            if (doc != null) {
                Element rootElem = doc.getDocumentElement();
                if (rootElem != null) {
                    if (rootElem.getNodeName().equals(ExternalResultsXML.ROOT_ELEM.toString())) {
                        parseArtifacts(rootElem);
                        parseReports(rootElem);
                        parseDerivedFiles(rootElem);
                    } else {
                        logger.log(Level.SEVERE, "Error loading XML file {0} : root element must be " + ExternalResultsXML.ROOT_ELEM.toString(), resultsFilePath); //NON-NLS
                    }
                } else {
                    logger.log(Level.SEVERE, "Error loading XML file {0} : missing root element", resultsFilePath); //NON-NLS
                }
            } else {
                logger.log(Level.SEVERE, "Error loading XML file {0}", resultsFilePath); //NON-NLS
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error loading XML file " + resultsFilePath, e); //NON-NLS
        }
        return externalResults;
    }

    private void parseArtifacts(Element root) {
        NodeList artifactsListNodes = root.getElementsByTagName(ExternalResultsXML.ARTIFACTS_LIST_ELEM.toString());
        for (int i = 0; i < artifactsListNodes.getLength(); ++i) {
            Element artifactsListElem = (Element) artifactsListNodes.item(i);
            NodeList artifactNodes = artifactsListElem.getElementsByTagName(ExternalResultsXML.ARTIFACT_ELEM.toString());
            for (int j = 0; j < artifactNodes.getLength(); ++j) {
                Element artifactElem = (Element) artifactNodes.item(j);
                final String type = artifactElem.getAttribute(ExternalResultsXML.TYPE_ATTR.toString());
                if (type.isEmpty()) {
                    logger.log(Level.WARNING, "Ignoring invalid {0} element, missing {1} attribute ({2})", new Object[]{
                        ExternalResultsXML.ARTIFACT_ELEM.toString(),
                        ExternalResultsXML.TYPE_ATTR.toString(),
                        resultsFilePath});
                    continue;
                }

                String sourceFilePath = "";
                NodeList sourceFileNodes = artifactElem.getElementsByTagName(ExternalResultsXML.SOURCE_FILE_ELEM.toString());
                if (sourceFileNodes.getLength() > 0) {
                    if (sourceFileNodes.getLength() > 1) {
                        logger.log(Level.WARNING, "Found multiple {0} elements for a single {1} element, ignoring excess elements", new Object[]{ExternalResultsXML.SOURCE_FILE_ELEM.toString(), ExternalResultsXML.ARTIFACT_ELEM.toString()});
                    }
                    Element sourceFileElem = (Element) sourceFileNodes.item(0);
                    NodeList pathNodes = sourceFileElem.getElementsByTagName(ExternalResultsXML.PATH_ELEM.toString());
                    if (pathNodes.getLength() > 0) {
                        if (pathNodes.getLength() > 1) {
                            logger.log(Level.WARNING, "Found multiple {0} elements for a single {1} element, ignoring excess elements", new Object[]{ExternalResultsXML.PATH_ELEM.toString(), ExternalResultsXML.SOURCE_FILE_ELEM.toString()});
                        }
                        Element pathElem = (Element) pathNodes.item(0);
                        sourceFilePath = pathElem.getTextContent();
                        if (sourceFilePath.isEmpty()) {
                            logger.log(Level.WARNING, "Found empty {0} element, will use data source as artifact source file", ExternalResultsXML.PATH_ELEM.toString());
                        }
                    } else {
                        logger.log(Level.WARNING, "Found {0} element missing {1} element, will use data source as artifact source file", new Object[]{ExternalResultsXML.SOURCE_FILE_ELEM.toString(), ExternalResultsXML.PATH_ELEM.toString()});
                    }
                }
                if (sourceFilePath.isEmpty()) {
                    // Default to data source as the source file for the artifact.
                    try {
                        sourceFilePath = dataSource.getImage().getPaths()[0];
                    } catch (TskCoreException ex) {
                        // RJCTODO: Can do better than this, get rid of artifact...need to do better?
                        logger.log(Level.SEVERE, "Failed to get data source path to use as default artifact source file, artifact will have no source file", ex); //NON-NLS
                    }
                }

                ExternalResults.Artifact artifact = externalResults.addArtifact(type, sourceFilePath); 
                parseArtifactAttributes(artifactElem, artifact);
            }
        }
    }

    private void parseArtifactAttributes(Element artifactElem, ExternalResults.Artifact artifact) {
        NodeList attributeNodesList = artifactElem.getElementsByTagName(ExternalResultsXML.ATTRIBUTE_ELEM.toString());
        for (int i = 0; i < attributeNodesList.getLength(); ++i) {
            // Make sure the artifact attribute element has a type attribute.
            Element attributeElem = (Element) attributeNodesList.item(i);
            final String type = attributeElem.getAttribute(ExternalResultsXML.TYPE_ATTR.toString());
            if (type.isEmpty()) {
                logger.log(Level.WARNING, "Ignoring invalid {0} element, missing {1} attribute", new Object[]{ExternalResultsXML.ATTRIBUTE_ELEM.toString(), ExternalResultsXML.TYPE_ATTR.toString()});
                continue;
            }

            // Make sure the artifact attribute element has a value element.
            NodeList valueNodesList = attributeElem.getElementsByTagName(ExternalResultsXML.VALUE_ELEM.toString());
            if (valueNodesList.getLength() < 1) {
                logger.log(Level.WARNING, "Ignoring invalid {0} element, missing {1} element", new Object[]{ExternalResultsXML.ATTRIBUTE_ELEM.toString(), ExternalResultsXML.VALUE_ELEM.toString()});
                continue;
            }

            if (valueNodesList.getLength() > 1) {
                logger.log(Level.WARNING, "Found multiple {0} elements for a single {1} element, ignoring excess elements", new Object[]{ExternalResultsXML.VALUE_ELEM.toString(), ExternalResultsXML.ATTRIBUTE_ELEM.toString()});
            }

            // Make sure the value element is not empty.
            Element valueElem = (Element) valueNodesList.item(0);
            final String value = valueElem.getTextContent();
            if (value.isEmpty()) {
                logger.log(Level.WARNING, "Ignoring invalid {0} element, {1} element has no content", new Object[]{ExternalResultsXML.ATTRIBUTE_ELEM.toString(), ExternalResultsXML.VALUE_ELEM.toString()});
                continue;
            }

            // It's ok if the value element does not have a type attribute - the
            // default is ExternalResultsXML.VALUE_TYPE_TEXT.
            String valueType = valueElem.getAttribute(ExternalResultsXML.TYPE_ATTR.toString());
            if (valueType.isEmpty()) {
                valueType = ExternalResultsXML.VALUE_TYPE_TEXT.toString();
            }

            // Get the optional source module element.
            String srcModule = "";
            NodeList sourceModuleNodes = attributeElem.getElementsByTagName(ExternalResultsXML.SOURCE_MODULE_ELEM.toString());
            if (sourceModuleNodes.getLength() > 0) {
                if (sourceModuleNodes.getLength() > 1) {
                    logger.log(Level.WARNING, "Found multiple {0} elements for a single {1} element, ignoring excess elements", new Object[]{ExternalResultsXML.SOURCE_MODULE_ELEM.toString(), ExternalResultsXML.ATTRIBUTE_ELEM.toString()});
                }
                Element srcModuleElem = (Element) sourceModuleNodes.item(0);
                srcModule = srcModuleElem.getTextContent();
            }

            artifact.addAttribute(type, value, valueType, valueType);
        }
    }

    private void parseReports(Element root) {
        NodeList reportsListNodes = root.getElementsByTagName(ExternalResultsXML.REPORTS_LIST_ELEM.toString());
        for (int i = 0; i < reportsListNodes.getLength(); ++i) {
            Element reportsListElem = (Element) reportsListNodes.item(i);
            NodeList reportNodes = reportsListElem.getElementsByTagName(ExternalResultsXML.REPORT_ELEM.toString());
            for (int j = 0; j < reportNodes.getLength(); ++j) {
                Element reportElem = (Element) reportNodes.item(j);
                String displayName = getRequiredChildElementContent(reportElem, ExternalResultsXML.DISPLAY_NAME_ELEM.toString());
                if (displayName.isEmpty()) {
                    continue;
                }
                String path = getRequiredChildElementContent(reportElem, ExternalResultsXML.LOCAL_PATH_ELEM.toString());
                if (displayName.isEmpty()) {
                    continue;
                }
                externalResults.addReport(displayName, path);
            }
        }
    }

    private void parseDerivedFiles(Element rootElement) {
        NodeList derivedFilesListNodes = rootElement.getElementsByTagName(ExternalResultsXML.DERIVED_FILES_LIST_ELEM.toString());
        for (int i = 0; i < derivedFilesListNodes.getLength(); ++i) {
            Element derivedFilesListElem = (Element) derivedFilesListNodes.item(i);
            NodeList derivedFileNodes = derivedFilesListElem.getElementsByTagName(ExternalResultsXML.DERIVED_FILE_ELEM.toString());
            for (int j = 0; j < derivedFileNodes.getLength(); ++j) {
                Element derivedFileElem = (Element) derivedFileNodes.item(j);
                String path = getRequiredChildElementContent(derivedFileElem, ExternalResultsXML.LOCAL_PATH_ELEM.toString());
                if (path.isEmpty()) {
                    continue;
                }
                String parentPath = getRequiredChildElementContent((Element) derivedFileNodes.item(j), ExternalResultsXML.PARENT_PATH_ELEM.toString());
                if (parentPath.isEmpty()) {
                    continue;
                }
                externalResults.addDerivedFile(path, parentPath);
            }
        }
    }

    private String getRequiredChildElementContent(Element parentElement, String childElementTagName) {
        String content = "";
        NodeList childNodes = parentElement.getElementsByTagName(childElementTagName);
        if (childNodes.getLength() > 0) {
            if (childNodes.getLength() > 1) {
                logger.log(Level.WARNING, "Found multiple {0} child elements for {1} element, ignoring all but first occurrence ({2})", new Object[]{
                    childElementTagName,
                    parentElement.getTagName(),
                    resultsFilePath}); // NON-NLS
            }
            Element childElement = (Element) childNodes.item(0);
            content = childElement.getTextContent();
            if (content.isEmpty()) {
                logger.log(Level.WARNING, "Ignoring {0} element, {1} child element has no content ({2})", new Object[]{
                    parentElement.getTagName(),
                    childElementTagName,
                    resultsFilePath}); // NON-NLS
            }
        } else {
            logger.log(Level.WARNING, "Ignoring {0} element missing {1} child element ({2})", new Object[]{
                parentElement.getTagName(),
                childElementTagName,
                resultsFilePath});  // NON-NLS   
        }
        return content;
    }
}
