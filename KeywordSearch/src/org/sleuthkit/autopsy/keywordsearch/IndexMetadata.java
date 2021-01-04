/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.keywordsearch;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.sleuthkit.autopsy.coreutils.XMLUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Provides access to the text index metadata stored in the index metadata file.
 */
class IndexMetadata {
    
    private final Path metadataFilePath;
    private final Path caseDirectoryPath;
    private final static String METADATA_FILE_NAME = "SolrCore.properties";
    private final static String ROOT_ELEMENT_NAME = "SolrCores"; //NON-NLS
    private final static String CORE_ELEMENT_NAME = "Core"; //NON-NLS
    private final static String CORE_NAME_ELEMENT_NAME = "CoreName"; //NON-NLS
    private final static String SCHEMA_VERSION_ELEMENT_NAME = "SchemaVersion"; //NON-NLS
    private final static String SOLR_VERSION_ELEMENT_NAME = "SolrVersion"; //NON-NLS
    private final static String TEXT_INDEX_PATH_ELEMENT_NAME = "TextIndexPath"; //NON-NLS
    private List<Index> indexes = new ArrayList<>();
    
    IndexMetadata(String caseDirectory, Index index) throws TextIndexMetadataException {
        this.metadataFilePath = Paths.get(caseDirectory, METADATA_FILE_NAME);
        this.caseDirectoryPath = Paths.get(caseDirectory);
        this.indexes.add(index);
        writeToFile();
    }
    
    IndexMetadata(String caseDirectory, List<Index> indexes) throws TextIndexMetadataException {

        this.metadataFilePath = Paths.get(caseDirectory, METADATA_FILE_NAME);
        this.caseDirectoryPath = Paths.get(caseDirectory);
        this.indexes = indexes;
        writeToFile();
    }
    
    /**
     * Constructs an object that provides access to the text index metadata stored in
     * an existing text index metadata file.
     *
     * @param caseDirectory The full path to the top level case output folder.
     *
     * @throws TextIndexMetadataException If the new text index metadata file cannot be
     *                               read.
     */
    IndexMetadata(String caseDirectory) throws TextIndexMetadataException {
        this.caseDirectoryPath = Paths.get(caseDirectory);
        this.metadataFilePath = Paths.get(caseDirectory, METADATA_FILE_NAME);
        if (!this.metadataFilePath.toFile().exists()) {
            throw new TextIndexMetadataException(String.format("Text index metadata file doesn't exist: %s", metadataFilePath));
        }
        readFromFile();
    }
    
    List<Index> getIndexes() {
        return indexes;
    }
    
    /**
     * Checks whether a text index metadata file exists.
     *
     * @param caseDirectory The full path to the top level case output folder.
     *
     * @return True if the file exists, false otherwise.
     */
    static boolean isMetadataFilePresent(String caseDirectory) {
        File file = Paths.get(caseDirectory, METADATA_FILE_NAME).toFile();
        if (file.exists()) {
            return true;
        }
        return false;
    }
    
    /**
     * Writes the text index metadata to the metadata file.
     *
     * @throws TextIndexMetadataException If there is an error writing to the text index
     *                               metadata file.
     */
    private void writeToFile() throws TextIndexMetadataException {
        try {
            /*
             * Create the XML DOM.
             */
            Document doc = XMLUtil.createDocument();
            createXMLDOM(doc);
            doc.normalize();

            /*
             * Prepare the DOM for pretty printing to the metadata file.
             */
            Source source = new DOMSource(doc);
            StringWriter stringWriter = new StringWriter();
            Result streamResult = new StreamResult(stringWriter);
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes"); //NON-NLS
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2"); //NON-NLS
            transformer.transform(source, streamResult);

            /*
             * Write the DOM to the metadata file.
             */
            try (BufferedWriter fileWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(metadataFilePath.toFile())))) {
                fileWriter.write(stringWriter.toString());
                fileWriter.flush();
            }

        } catch (ParserConfigurationException | TransformerException | IOException ex) {
            throw new TextIndexMetadataException(String.format("Error writing to text index metadata file %s", metadataFilePath), ex);
        }
    }

    /*
     * Creates an XML DOM from the text index metadata.
     */
    private void createXMLDOM(Document doc) {
        /*
         * Create the root element and its children.
         */
        Element rootElement = doc.createElement(ROOT_ELEMENT_NAME);        
        doc.appendChild(rootElement);

        /*
         * Create the children of the Solr cores element.
         */
        for (Index index : indexes) {
            Element coreElement = doc.createElement(CORE_ELEMENT_NAME);
            rootElement.appendChild(coreElement);
            createChildElement(doc, coreElement, CORE_NAME_ELEMENT_NAME, index.getIndexName());
            createChildElement(doc, coreElement, SOLR_VERSION_ELEMENT_NAME, index.getSolrVersion());
            createChildElement(doc, coreElement, SCHEMA_VERSION_ELEMENT_NAME, index.getSchemaVersion());
            Path relativePath = caseDirectoryPath.relativize(Paths.get(index.getIndexPath()));
            createChildElement(doc, coreElement, TEXT_INDEX_PATH_ELEMENT_NAME, relativePath.toString());
        }
    }
    
    /**
     * Creates an XML element for the text index metadata XML DOM.
     *
     * @param doc            The document.
     * @param parentElement  The parent element of the element to be created.
     * @param elementName    The name of the element to be created.
     * @param elementContent The text content of the element to be created, may
     *                       be empty.
     */
    private void createChildElement(Document doc, Element parentElement, String elementName, String elementContent) {
        Element element = doc.createElement(elementName);
        element.appendChild(doc.createTextNode(elementContent));
        parentElement.appendChild(element);
    }
    
    
    /**
     * Reads the text index metadata from the metadata file.
     *
     * @throws TextIndexMetadataException If there is an error reading from the text index
     *                               metadata file.
     */
    private void readFromFile() throws TextIndexMetadataException {
        try {
            /*
             * Parse the file into an XML DOM and get the root element.
             */
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = builder.parse(metadataFilePath.toFile());
            doc.getDocumentElement().normalize();
            Element rootElement = doc.getDocumentElement();
            if (!rootElement.getNodeName().equals(ROOT_ELEMENT_NAME)) {
                throw new TextIndexMetadataException("Text index metadata file corrupted");
            }

            /*
             * Get the content of the children of the core element.
             */
            NodeList coreElements = doc.getElementsByTagName(CORE_ELEMENT_NAME);
            if (coreElements.getLength() == 0) {
                throw new TextIndexMetadataException("Text index metadata file corrupted");
            }
            int coreIndx = 0;
            while (coreIndx < coreElements.getLength()) {
                Element coreElement = (Element) coreElements.item(coreIndx);
                String coreName = getElementTextContent(coreElement, CORE_NAME_ELEMENT_NAME, true);
                String solrVersion = getElementTextContent(coreElement, SOLR_VERSION_ELEMENT_NAME, true);
                String schemaVersion = getElementTextContent(coreElement, SCHEMA_VERSION_ELEMENT_NAME, true);
                String relativeTextIndexPath = getElementTextContent(coreElement, TEXT_INDEX_PATH_ELEMENT_NAME, true);
                Path absoluteDatabasePath = caseDirectoryPath.resolve(relativeTextIndexPath);
                Index index = new Index(absoluteDatabasePath.toString(), solrVersion, schemaVersion, coreName, "");
                indexes.add(index);
                coreIndx++;
            }

        } catch (ParserConfigurationException | SAXException | IOException ex) {
            throw new TextIndexMetadataException(String.format("Error reading from text index metadata file %s", metadataFilePath), ex);
        }
    }

    /**
     * Gets the text content of an XML element.
     *
     * @param parentElement     The parent element.
     * @param elementName       The element name.
     * @param contentIsRequired Whether or not the content is required.
     *
     * @return The text content, may be empty if not required.
     *
     * @throws TextIndexMetadataException If the element is missing or content is
     *                               required and it is empty.
     */
    private String getElementTextContent(Element parentElement, String elementName, boolean contentIsRequired) throws TextIndexMetadataException {
        NodeList elementsList = parentElement.getElementsByTagName(elementName);
        if (elementsList.getLength() == 0) {
            throw new TextIndexMetadataException(String.format("Missing %s element from text index metadata file %s", elementName, metadataFilePath));
        }
        String textContent = elementsList.item(0).getTextContent();
        if (textContent.isEmpty() && contentIsRequired) {
            throw new TextIndexMetadataException(String.format("Empty %s element in text index metadata file %s", elementName, metadataFilePath));
        }
        return textContent;
    }    

    /**
     * Exception thrown by the IndexMetadata class when there is a problem
     * accessing the metadata for a text index.
     */
    public final static class TextIndexMetadataException extends Exception {

        private static final long serialVersionUID = 1L;

        private TextIndexMetadataException(String message) {
            super(message);
        }

        private TextIndexMetadataException(String message, Throwable cause) {
            super(message, cause);
        }
    }    
}
