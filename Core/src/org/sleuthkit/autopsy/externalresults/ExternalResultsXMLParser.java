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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Parses an XML listing of externally generated results (artifacts, derived
 * files, reports).
 */
final class ExternalResultsXMLParser {

    private static final Logger logger = Logger.getLogger(ExternalResultsXMLParser.class.getName());
    private static final String XSD_FILE = "autopsy_external_results.xsd"; //NON-NLS
    private String importFilePath;
    private ExternalResults resultsData = null;

    /**
     * Constructor.
     *
     * @param importFilePath Path of the XML file to parse.
     */
    ExternalResultsXMLParser(String importFilePath) {
        this.importFilePath = importFilePath;
    }

    /**
     * Parses info for artifacts, derived files, and reports in the given XML
     * file.
     *
     * @return An object encapsulating the results as objects.
     */
    ExternalResults parse() {
        resultsData = new ExternalResults();
        try {
            final Document doc = XMLUtil.loadDoc(ExternalResultsXMLParser.class, importFilePath, XSD_FILE);
            if (doc == null) {
                return null;
            }

            Element root = doc.getDocumentElement();
            if (root == null) {
                logger.log(Level.SEVERE, "Error loading XML file {0} : invalid file format (bad root)", importFilePath); //NON-NLS
                return null;
            }

            if (!root.getNodeName().equals(ExternalResultsXML.ROOT_ELEM.toString())) {
                logger.log(Level.SEVERE, "Error loading XML file {0} : root element must be " + ExternalResultsXML.ROOT_ELEM.toString(), importFilePath); //NON-NLS
                return null;
            }

            parseDataSource(root);
            parseArtifacts(root);
            parseReports(root);
            parseDerivedFiles(root);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error loading XML file " + importFilePath, e); //NON-NLS
            return null;
        }
        return resultsData;
    }

    private void parseDataSource(Element root) throws Exception {
        NodeList nodeList = root.getElementsByTagName(ExternalResultsXML.DATA_SRC_ELEM.toString());
        final int numNodes = nodeList.getLength();
        for (int index = 0; index < numNodes; ++index) {
            Element el = (Element) nodeList.item(index);
            String dataSourceStr = el.getTextContent();
            // We allow an empty data source element...we just ignore it
            if (!dataSourceStr.isEmpty()) {
                resultsData.addDataSource(dataSourceStr);
            }
        }
    }

    private void parseArtifacts(Element root) throws Exception {
        NodeList nodeList = root.getElementsByTagName(ExternalResultsXML.ARTIFACTS_LIST_ELEM.toString());
        for (int index = 0; index < nodeList.getLength(); ++index) {
            Element artifactsListElem = (Element) nodeList.item(index);
            NodeList subNodeList = artifactsListElem.getElementsByTagName(ExternalResultsXML.ARTIFACT_ELEM.toString());
            for (int subIndex = 0; subIndex < subNodeList.getLength(); ++subIndex) {
                Element artifactElem = (Element) subNodeList.item(subIndex);
                final String type = artifactElem.getAttribute(ExternalResultsXML.TYPE_ATTR.toString());
                if (type.isEmpty()) {
                    logger.log(Level.WARNING, "Ignoring invalid artifact: no type specified.");
                    return;
                }
                final int artResultsIndex = resultsData.addArtifact(type);
                parseArtifactAttributes(artifactElem, artResultsIndex);
                parseArtifactFiles(artifactElem, artResultsIndex);
            }
        }
    }

    private void parseArtifactAttributes(Element artifactElem, int artResultsIndex) {
        NodeList nodeList = artifactElem.getElementsByTagName(ExternalResultsXML.ATTRIBUTE_ELEM.toString());
        for (int index = 0; index < nodeList.getLength(); ++index) {
            Element attributeElem = (Element) nodeList.item(index);
            final String type = attributeElem.getAttribute(ExternalResultsXML.TYPE_ATTR.toString());
            if (type.isEmpty()) {
                logger.log(Level.WARNING, "Ignoring invalid attribute: no type specified.");
                return;
            }
            final int attrResultsIndex = resultsData.addAttribute(artResultsIndex, type);

            // add values, if any
            NodeList valueNodeList = attributeElem.getElementsByTagName(ExternalResultsXML.VALUE_ELEM.toString());
            for (int subindex = 0; subindex < valueNodeList.getLength(); ++subindex) {
                Element valueElem = (Element) valueNodeList.item(subindex);
                final String valueStr = valueElem.getTextContent();
                final String valueType = valueElem.getAttribute(ExternalResultsXML.TYPE_ATTR.toString()); //empty string is ok
                resultsData.addAttributeValue(artResultsIndex, attrResultsIndex, valueStr, valueType);
            }

            // add source, if any
            NodeList srcNodeList = attributeElem.getElementsByTagName(ExternalResultsXML.SRC_ELEM.toString());
            if (srcNodeList.getLength() > 0) {
                // we only use the first occurence
                Element srcFileElem = (Element) srcNodeList.item(0);
                final String srcStr = srcFileElem.getTextContent();
                resultsData.addAttributeSource(artResultsIndex, attrResultsIndex, srcStr);
            }
        }
    }

    private void parseArtifactFiles(Element artifactElem, int artResultsIndex) throws Exception {
        NodeList nodeList = artifactElem.getElementsByTagName(ExternalResultsXML.FILE_ELEM.toString());
        if (nodeList.getLength() > 0) {
            // we only use the first occurence
            Element srcFileElem = (Element) nodeList.item(0);
            NodeList subNodeList = srcFileElem.getElementsByTagName(ExternalResultsXML.PATH_ELEM.toString());
            if (nodeList.getLength() > 0) {
                // we only use the first occurence
                Element pathElem = (Element) subNodeList.item(0);
                final String path = pathElem.getTextContent();
                resultsData.addArtifactFile(artResultsIndex, path);
            } else {
                // error to have a file element without a path element
                throw new Exception("file element is missing path element.");
            }
        }
    }

    private void parseReports(Element root) throws Exception {
        NodeList nodeList = root.getElementsByTagName(ExternalResultsXML.REPORTS_LIST_ELEM.toString());
        for (int index = 0; index < nodeList.getLength(); ++index) {
            Element reportsListElem = (Element) nodeList.item(index);
            NodeList subNodeList = reportsListElem.getElementsByTagName(ExternalResultsXML.REPORT_ELEM.toString());
            for (int subIndex = 0; subIndex < subNodeList.getLength(); ++subIndex) {
                Element reportElem = (Element) subNodeList.item(subIndex);
                String name = reportElem.getAttribute(ExternalResultsXML.NAME_ATTR.toString());
                String displayName = "";
                String localPath = "";
                NodeList nameNodeList = reportElem.getElementsByTagName(ExternalResultsXML.DISPLAY_NAME_ELEM.toString());
                if (nameNodeList.getLength() > 0) {
                    // we only use the first occurence
                    Element nameElem = (Element) nameNodeList.item(0);
                    displayName = nameElem.getTextContent();
                }
                NodeList pathNodeList = reportElem.getElementsByTagName(ExternalResultsXML.LOCAL_PATH_ELEM.toString());
                if (pathNodeList.getLength() > 0) {
                    // we only use the first occurence
                    Element pathElem = (Element) pathNodeList.item(0);
                    localPath = pathElem.getTextContent();
                }
                if ((!displayName.isEmpty()) && (!localPath.isEmpty())) {
                    resultsData.addReport(name, displayName, localPath);
                } else {
                    // error to have a file element without a path element
                    throw new Exception("report element is missing display_name or local_path.");
                }
            }
        }
    }

    private void parseDerivedFiles(Element rootElement) throws Exception {
        NodeList nodeList = rootElement.getElementsByTagName(ExternalResultsXML.DERIVED_FILES_LIST_ELEM.toString());
        for (int index = 0; index < nodeList.getLength(); ++index) {
            Element derivedFilesElem = (Element) nodeList.item(index);
            NodeList subNodeList = derivedFilesElem.getElementsByTagName(ExternalResultsXML.DERIVED_FILE_ELEM.toString());
            for (int subIndex = 0; subIndex < subNodeList.getLength(); ++subIndex) {
                Element derivedFileElem = (Element) subNodeList.item(subIndex);
                String localPath = "";
                NodeList pathNodeList = derivedFileElem.getElementsByTagName(ExternalResultsXML.LOCAL_PATH_ELEM.toString());
                if (pathNodeList.getLength() > 0) {
                    // we only use the first occurence
                    Element pathElem = (Element) pathNodeList.item(0);
                    localPath = pathElem.getTextContent();
                }
                String parentPath = "";
                NodeList parPathNodeList = derivedFileElem.getElementsByTagName(ExternalResultsXML.PARENT_PATH_ELEM.toString());
                if (parPathNodeList.getLength() > 0) {
                    // we only use the first occurence
                    Element parentPathElem = (Element) parPathNodeList.item(0);
                    parentPath = parentPathElem.getTextContent();
                }
                if (!localPath.isEmpty()) {
                    resultsData.addDerivedFile(localPath, parentPath);
                } else {
                    // error to have a file element without a path element
                    throw new Exception("derived_files element is missing local_path.");
                }
            }
        }
    }
}
