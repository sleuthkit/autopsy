/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.coreutils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.nio.file.Paths;
import java.util.logging.Level;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * XML Utilities
 *
 * This class provides basic utilities for working with XML files, such as
 * -Validating XML files against a given schema -Saving documents to disk
 * -Loading documents from disk
 */
public class XMLUtil {
    
    static {
        // this is to ensure using xalan for the transformer factory: https://stackoverflow.com/a/64364531/2375948
        System.setProperty("javax.xml.transform.TransformerFactory","com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl");
    }
    
    private static DocumentBuilder getDocumentBuilder() throws ParserConfigurationException {
        // See JIRA-6958 for details about class loading and jaxb.
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(XMLUtil.class.getClassLoader());
            DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
            return builderFactory.newDocumentBuilder();
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }
    
    private static SchemaFactory getSchemaFactory(String schemaLanguage) {
        // See JIRA-6958 for details about class loading and jaxb.
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(XMLUtil.class.getClassLoader());
            return SchemaFactory.newInstance(schemaLanguage);
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }
    
    private static TransformerFactory getTransformerFactory() {
        // See JIRA-6958 for details about class loading and jaxb.
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(XMLUtil.class.getClassLoader());
            return TransformerFactory.newInstance();
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    /**
     * Creates a W3C DOM.
     *
     * @return The document object.
     *
     * @throws ParserConfigurationException
     */
    public static Document createDocument() throws ParserConfigurationException {
        return getDocumentBuilder().newDocument();
    }

    /**
     * Loads an XML document into a WC3 DOM and validates it using a schema
     * packaged as a class resource.
     *
     * @param docPath            The full path to the XML document.
     * @param clazz              The class associated with the schema resource.
     * @param schemaResourceName The name of the schema resource.
     *
     * @return The WC3 DOM document object.
     *
     * @throws IOException
     * @throws ParserConfigurationException
     * @throws SAXException
     */
    public static <T> Document loadDocument(String docPath, Class<T> clazz, String schemaResourceName) throws IOException, ParserConfigurationException, SAXException {
        Document doc = loadDocument(docPath);
        validateDocument(doc, clazz, schemaResourceName);
        return doc;
    }

    /**
     * Loads an XML document into a WC3 DOM.
     *
     * @param docPath The full path to the XML document.
     *
     * @return The WC3 DOM document object.
     *
     * @throws ParserConfigurationException
     * @throws SAXException
     * @throws IOException
     */
    public static Document loadDocument(String docPath) throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilder builder = getDocumentBuilder();
        Document doc = builder.parse(new FileInputStream(docPath));
        return doc;
    }

    /**
     * Validates a WC3 DOM using a schema packaged as a class resource.
     *
     * @param doc
     * @param clazz
     * @param schemaResourceName
     *
     * @throws SAXException
     * @throws IOException
     */
    public static <T> void validateDocument(final Document doc, Class<T> clazz, String schemaResourceName) throws SAXException, IOException {
        PlatformUtil.extractResourceToUserConfigDir(clazz, schemaResourceName, false);
        File schemaFile = new File(Paths.get(PlatformUtil.getUserConfigDirectory(), schemaResourceName).toAbsolutePath().toString());
        SchemaFactory schemaFactory = getSchemaFactory(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        Schema schema = schemaFactory.newSchema(schemaFile);
        Validator validator = schema.newValidator();
        validator.validate(new DOMSource(doc), new DOMResult());
    }

    /**
     * Saves a WC3 DOM by writing it to an XML document.
     *
     * @param doc      The WC3 DOM document object.
     * @param docPath  The full path to the XML document.
     * @param encoding Encoding scheme to use for the XML document, e.g.,
     *                 "UTF-8."
     *
     * @throws TransformerConfigurationException
     * @throws FileNotFoundException
     * @throws UnsupportedEncodingException
     * @throws TransformerException
     * @throws IOException
     */
    public static void saveDocument(final Document doc, String encoding, String docPath) throws TransformerConfigurationException, FileNotFoundException, UnsupportedEncodingException, TransformerException, IOException {
        TransformerFactory xf = getTransformerFactory();
        xf.setAttribute("indent-number", 1); //NON-NLS
        Transformer xformer = xf.newTransformer();
        xformer.setOutputProperty(OutputKeys.METHOD, "xml"); //NON-NLS
        xformer.setOutputProperty(OutputKeys.INDENT, "yes"); //NON-NLS
        xformer.setOutputProperty(OutputKeys.ENCODING, encoding);
        xformer.setOutputProperty(OutputKeys.STANDALONE, "yes"); //NON-NLS
        xformer.setOutputProperty(OutputKeys.VERSION, "1.0");
        File file = new File(docPath);
        try (FileOutputStream stream = new FileOutputStream(file)) {
            Result out = new StreamResult(new OutputStreamWriter(stream, encoding));
            xformer.transform(new DOMSource(doc), out);
            stream.flush();
        }
    }

    /**
     * Utility to validate XML files against pre-defined schema files.
     *
     * The schema files are extracted automatically when this function is
     * called, the XML being validated is not. Be sure the XML file is already
     * extracted otherwise it will return false.
     *
     * @param xmlfile    The XML file to validate, in DOMSource format
     * @param clazz      class frm package to extract schema file from
     * @param schemaFile The file name of the schema to validate against, must
     *                   exist as a resource in the same package as where this
     *                   function is being called.
     *
     * For example usages, please see KeywordSearchListsXML, HashDbXML, or
     * IngestModuleLoader.
     *
     */
    // TODO: Deprecate.
    public static <T> boolean xmlIsValid(DOMSource xmlfile, Class<T> clazz, String schemaFile) {
        try {
            PlatformUtil.extractResourceToUserConfigDir(clazz, schemaFile, false);
            File schemaLoc = new File(PlatformUtil.getUserConfigDirectory() + File.separator + schemaFile);
            SchemaFactory schm = getSchemaFactory(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            try {
                Schema schema = schm.newSchema(schemaLoc);
                Validator validator = schema.newValidator();
                DOMResult result = new DOMResult();
                validator.validate(xmlfile, result);
                return true;
            } catch (SAXException e) {
                Logger.getLogger(clazz.getName()).log(Level.WARNING, "Unable to validate XML file.", e); //NON-NLS
                return false;
            }
        } catch (IOException e) {
            Logger.getLogger(clazz.getName()).log(Level.WARNING, "Unable to load XML file [" + xmlfile.toString() + "] of type [" + schemaFile + "]", e); //NON-NLS
            return false;
        }
    }

    /**
     * Evaluates XML files against an XSD.
     *
     * The schema files are extracted automatically when this function is
     * called, the XML being validated is not. Be sure the XML file is already
     * extracted otherwise it will return false.
     *
     * @param doc   The XML DOM to validate
     * @param clazz class from package to extract schema from
     * @param type  The file name of the schema to validate against, must exist
     *              as a resource in the same package as where this function is
     *              being called
     *
     * For example usages, please see KeywordSearchListsXML, HashDbXML, or
     * IngestModuleLoader.
     *
     */
    // TODO: Deprecate.
    public static <T> boolean xmlIsValid(Document doc, Class<T> clazz, String type) {
        DOMSource dms = new DOMSource(doc);
        return xmlIsValid(dms, clazz, type);
    }

    /**
     * Loads XML files from disk
     *
     * @param clazz   the class this method is invoked from
     * @param xmlPath the full path to the file to load
     */
    // TODO: Deprecate.
    public static <T> Document loadDoc(Class<T> clazz, String xmlPath) {
        Document ret = null;
        try {
            DocumentBuilder builder = getDocumentBuilder();
            ret = builder.parse(new FileInputStream(xmlPath));
        } catch (ParserConfigurationException e) {
            Logger.getLogger(clazz.getName()).log(Level.SEVERE, "Error loading XML file " + xmlPath + " : can't initialize parser.", e); //NON-NLS
        } catch (SAXException e) {
            Logger.getLogger(clazz.getName()).log(Level.SEVERE, "Error loading XML file " + xmlPath + " : can't parse XML.", e); //NON-NLS
        } catch (IOException e) {
            //error reading file
            Logger.getLogger(clazz.getName()).log(Level.SEVERE, "Error loading XML file " + xmlPath + " : can't read file.", e); //NON-NLS
        }
        return ret;
    }

    /**
     * Loads XML files from disk
     *
     * @param clazz   the class this method is invoked from
     * @param xmlPath the full path to the file to load
     * @param xsdPath the full path to the file to validate against
     */
    // TODO: Deprecate
    public static <T> Document loadDoc(Class<T> clazz, String xmlPath, String xsdPath) {
        Document ret = loadDoc(clazz, xmlPath);
        if (!XMLUtil.xmlIsValid(ret, clazz, xsdPath)) {
            Logger.getLogger(clazz.getName()).log(Level.WARNING, "Error loading XML file: could not validate against [{0}], results may not be accurate", xsdPath); //NON-NLS
        }
        return ret;
    }

    /**
     * Saves XML files to disk
     *
     * @param clazz    the class this method is invoked from
     * @param xmlPath  the full path to save the XML to
     * @param encoding to encoding, such as "UTF-8", to encode the file with
     * @param doc      the document to save
     */
    // TODO: Deprecate.
    public static <T> boolean saveDoc(Class<T> clazz, String xmlPath, String encoding, final Document doc) {
        TransformerFactory xf = getTransformerFactory();
        xf.setAttribute("indent-number", 1); //NON-NLS
        boolean success = false;
        try {
            Transformer xformer = xf.newTransformer();
            xformer.setOutputProperty(OutputKeys.METHOD, "xml"); //NON-NLS
            xformer.setOutputProperty(OutputKeys.INDENT, "yes"); //NON-NLS
            xformer.setOutputProperty(OutputKeys.ENCODING, encoding);
            xformer.setOutputProperty(OutputKeys.STANDALONE, "yes"); //NON-NLS
            xformer.setOutputProperty(OutputKeys.VERSION, "1.0");
            File file = new File(xmlPath);
            try (FileOutputStream stream = new FileOutputStream(file)) {
                Result out = new StreamResult(new OutputStreamWriter(stream, encoding));
                xformer.transform(new DOMSource(doc), out);
                stream.flush();
            }
            success = true;

        } catch (UnsupportedEncodingException e) {
            Logger.getLogger(clazz.getName()).log(Level.SEVERE, "Should not happen", e); //NON-NLS
        } catch (TransformerConfigurationException e) {
            Logger.getLogger(clazz.getName()).log(Level.SEVERE, "Error writing XML file", e); //NON-NLS
        } catch (TransformerException e) {
            Logger.getLogger(clazz.getName()).log(Level.SEVERE, "Error writing XML file", e); //NON-NLS
        } catch (FileNotFoundException e) {
            Logger.getLogger(clazz.getName()).log(Level.SEVERE, "Error writing XML file: cannot write to file: " + xmlPath, e); //NON-NLS
        } catch (IOException e) {
            Logger.getLogger(clazz.getName()).log(Level.SEVERE, "Error writing XML file: cannot write to file: " + xmlPath, e); //NON-NLS
        }
        return success;
    }

}
