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

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.sleuthkit.autopsy.coreutils.XMLUtil;
import org.sleuthkit.datamodel.Content;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Parses an XML representation of externally generated results (artifacts,
 * derived files, and reports).
 */
public final class ExternalResultsXMLParser implements ExternalResultsParser {

    private static final Logger logger = Logger.getLogger(ExternalResultsXMLParser.class.getName());
    private static final String XSD_FILE = "autopsy_external_results.xsd"; //NON-NLS
    private final String resultsFilePath;
    private final ExternalResults externalResults;

    /**
     * Constructor.
     *
     * @param importFilePath Full path of the results file to be parsed.
     */
    ExternalResultsXMLParser(Content dataSource, String resultsFilePath) {
        this.resultsFilePath = resultsFilePath;
        externalResults = new ExternalResults(dataSource);
    }

    @Override
    public ExternalResults parse() {
        try {
            // Note that XMLUtil.loadDoc() logs a warning if the file does not
            // conform to the XSD, but still returns a Document object. Until 
            // this behavior is improved, validation is still required. If 
            // XMLUtil.loadDoc() does return null, it failed to load the 
            // document and it logged the error.
            final Document doc = XMLUtil.loadDoc(ExternalResultsXMLParser.class, this.resultsFilePath, XSD_FILE);
            if (doc != null) {
                final Element rootElem = doc.getDocumentElement();
                if (rootElem != null && rootElem.getNodeName().equals(ExternalResultsXML.ROOT_ELEM.toString())) {
                    parseArtifacts(rootElem);
                    parseReports(rootElem);
                    parseDerivedFiles(rootElem);
                } else {
                    logger.log(Level.SEVERE, "Did not find {0} root element of {2}", new Object[]{
                        ExternalResultsXML.ROOT_ELEM.toString(), this.resultsFilePath}); //NON-NLS
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error parsing " + this.resultsFilePath, e); //NON-NLS
        }
        return externalResults;
    }

    @Override
    public List<String> getErrorMessages() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
        
    private void parseArtifacts(final Element root) {
        NodeList artifactsListNodes = root.getElementsByTagName(ExternalResultsXML.ARTIFACTS_LIST_ELEM.toString());
        for (int i = 0; i < artifactsListNodes.getLength(); ++i) {
            Element artifactsListElem = (Element) artifactsListNodes.item(i);
            NodeList artifactNodes = artifactsListElem.getElementsByTagName(ExternalResultsXML.ARTIFACT_ELEM.toString());
            for (int j = 0; j < artifactNodes.getLength(); ++j) {
                Element artifactElem = (Element) artifactNodes.item(j);
                final String type = getElementAttributeValue(artifactElem, ExternalResultsXML.TYPE_ATTR.toString());
                if (!type.isEmpty()) {
                    Element sourceFileElem = getChildElement(artifactElem, ExternalResultsXML.SOURCE_FILE_ELEM.toString());
                    if (sourceFileElem != null) {
                        final String sourceFilePath = this.getChildElementContent((Element) sourceFileElem, ExternalResultsXML.PATH_ELEM.toString());
                        if (!sourceFilePath.isEmpty()) {
                            ExternalResults.Artifact artifact = externalResults.addArtifact(type, sourceFilePath);
                            parseArtifactAttributes(artifactElem, artifact);
                        }
                    }
                }
            }
        }
    }

    private void parseArtifactAttributes(final Element artifactElem, ExternalResults.Artifact artifact) {
        NodeList attributeNodesList = artifactElem.getElementsByTagName(ExternalResultsXML.ATTRIBUTE_ELEM.toString());
        for (int i = 0; i < attributeNodesList.getLength(); ++i) {
            // Get the type of the artifact attribute.
            Element attributeElem = (Element) attributeNodesList.item(i);
            final String type = getElementAttributeValue(attributeElem, ExternalResultsXML.TYPE_ATTR.toString());
            if (type.isEmpty()) {
                continue;
            }            
            // Get the value of the artifact attribute.
            Element valueElem = this.getChildElement(attributeElem, ExternalResultsXML.VALUE_ELEM.toString());
            if (valueElem == null) {
                continue;
            }
            final String value = valueElem.getTextContent();
            if (value.isEmpty()) {
                logger.log(Level.WARNING, "Found {0} element that has no content in {1}", new Object[]{
                    ExternalResultsXML.VALUE_ELEM.toString(), this.resultsFilePath});
                continue;
            }            
            // Get the value type.
            String valueType = valueElem.getAttribute(ExternalResultsXML.TYPE_ATTR.toString());
            if (valueType.isEmpty()) {
                valueType = ExternalResultsXML.VALUE_TYPE_TEXT.toString();
            }            
            // Get the source module for the artifact attribute.
            String sourceModule = "";
            NodeList sourceModuleNodes = attributeElem.getElementsByTagName(ExternalResultsXML.SOURCE_MODULE_ELEM.toString());
            if (sourceModuleNodes.getLength() > 0) {
                if (sourceModuleNodes.getLength() > 1) {
                    logger.log(Level.WARNING, "Found multiple {0} child elements for {1} element in {2}, ignoring all but first occurrence", new Object[]{
                        ExternalResultsXML.SOURCE_MODULE_ELEM.toString(),
                        attributeElem.getTagName(),
                        this.resultsFilePath}); // NON-NLS
                }
                Element srcModuleElem = (Element) sourceModuleNodes.item(0);
                sourceModule = srcModuleElem.getTextContent();
            }            
            // Add the attribute to the artifact.
            artifact.addAttribute(type, value, valueType, sourceModule);
        }
    }

    private void parseReports(Element root) {
        NodeList reportsListNodes = root.getElementsByTagName(ExternalResultsXML.REPORTS_LIST_ELEM.toString());
        for (int i = 0; i < reportsListNodes.getLength(); ++i) {
            Element reportsListElem = (Element) reportsListNodes.item(i);
            NodeList reportNodes = reportsListElem.getElementsByTagName(ExternalResultsXML.REPORT_ELEM.toString());
            for (int j = 0; j < reportNodes.getLength(); ++j) {
                Element reportElem = (Element) reportNodes.item(j);
                String displayName = getChildElementContent(reportElem, ExternalResultsXML.DISPLAY_NAME_ELEM.toString());
                if (displayName.isEmpty()) {
                    continue;
                }
                String path = getChildElementContent(reportElem, ExternalResultsXML.LOCAL_PATH_ELEM.toString());
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
                String path = getChildElementContent(derivedFileElem, ExternalResultsXML.LOCAL_PATH_ELEM.toString());
                if (path.isEmpty()) {
                    continue;
                }
                String parentPath = getChildElementContent((Element) derivedFileNodes.item(j), ExternalResultsXML.PARENT_PATH_ELEM.toString());
                if (parentPath.isEmpty()) {
                    continue;
                }
                externalResults.addDerivedFile(path, parentPath);
            }
        }
    }

    private Element getChildElement(Element parentElement, String childElementTagName) {
        Element childElem = null;
        NodeList childNodes = parentElement.getElementsByTagName(childElementTagName);
        if (childNodes.getLength() > 0) {
            if (childNodes.getLength() > 1) {
                logger.log(Level.WARNING, "Found multiple {0} child elements for {1} element in {2}, ignoring all but first occurrence", new Object[]{
                    childElementTagName,
                    parentElement.getTagName(),
                    this.resultsFilePath}); // NON-NLS
            }
        }
        return childElem;
    }

    private String getElementAttributeValue(Element element, String attributeName) {
        final String attributeValue = element.getAttribute(attributeName);
        if (attributeValue.isEmpty()) {
            logger.log(Level.WARNING, "Found {0} element missing {1} attribute in {2}", new Object[]{
                element.getTagName(),
                attributeName,
                this.resultsFilePath});
        }
        return attributeValue;
    }

    private String getChildElementContent(Element parentElement, String childElementTagName) {
        String content = "";
        NodeList childNodes = parentElement.getElementsByTagName(childElementTagName);
        if (childNodes.getLength() > 0) {
            if (childNodes.getLength() > 1) {
                logger.log(Level.WARNING, "Found multiple {0} child elements for {1} element in {2}, ignoring all but first occurrence", new Object[]{
                    childElementTagName,
                    parentElement.getTagName(),
                    this.resultsFilePath}); // NON-NLS
            }
            Element childElement = (Element) childNodes.item(0);
            content = childElement.getTextContent();
            if (content.isEmpty()) {
                logger.log(Level.WARNING, "Found {0} element with {1} child element that has no content in {2}", new Object[]{
                    parentElement.getTagName(),
                    childElementTagName,
                    this.resultsFilePath}); // NON-NLS
            }
        } else {
            logger.log(Level.WARNING, "Found {0} element missing {1} child element in {2}", new Object[]{
                parentElement.getTagName(),
                childElementTagName,
                this.resultsFilePath});  // NON-NLS   
        }
        return content;
    }
}
