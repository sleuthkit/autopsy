/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
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

/**
 * Provides access to the text index metadata stored in the index metadata file.
 */
public class IndexMetadata {
    
    private final Path metadataFilePath;
    private final String metadataFileName = "SolrCore.properties";
    private final static String ROOT_ELEMENT_NAME = "SolrCores"; //NON-NLS
    private final static String CORE_ELEMENT_NAME = "Core"; //NON-NLS
    private final static String CORE_NAME_ELEMENT_NAME = "CoreName"; //NON-NLS
    private final static String SCHEMA_VERSION_ELEMENT_NAME = "SchemaVersion"; //NON-NLS
    private final static String SOLR_VERSION_ELEMENT_NAME = "SolrVersion"; //NON-NLS
    private final static String TEXT_INDEX_PATH_ELEMENT_NAME = "TextIndexPath"; //NON-NLS
    private List<Index> indexes = new ArrayList<>();
    
    IndexMetadata(String caseDirectory, Index index) throws TextIndexMetadataException {
        metadataFilePath = Paths.get(caseDirectory, metadataFileName);
        indexes.add(index);
        writeToFile();
    }
    
    IndexMetadata(String caseDirectory, List<Index> indexes) throws TextIndexMetadataException {
        metadataFilePath = Paths.get(caseDirectory, metadataFileName);
        this.indexes = indexes;
        writeToFile();
    }
    
    void addIndex(Index index) throws TextIndexMetadataException {
        indexes.add(index);
        writeToFile();
    }
    
    /**
     * Writes the case metadata to the metadata file.
     *
     * @throws CaseMetadataException If there is an error writing to the case
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
            throw new TextIndexMetadataException(String.format("Error writing to case metadata file %s", metadataFilePath), ex);
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
            createChildElement(doc, coreElement, TEXT_INDEX_PATH_ELEMENT_NAME, index.getIndexPath());
        }
    }
    
    /**
     * Creates an XML element for the case metadata XML DOM.
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
