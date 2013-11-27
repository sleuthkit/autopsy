/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012 Basis Technology Corp.
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
 *  -Validating XML files against a given schema
 *  -Saving documents to disk
 *  -Loading documents from disk
 * 
 */

public class XMLUtil {
    /**
     * Utility to validate XML files against pre-defined schema files.
     * 
     *  The schema files are extracted automatically when this function is called, the XML being validated is not.
     *  Be sure the XML file is already extracted otherwise it will return false.
     * @param xmlfile The XML file to validate, in DOMSource format
     * @param clazz class frm package to extract schema file from
     * @param schemaFile The file name of the schema to validate against, must exist as a resource in the same package as where this function is being called.
     * 
     * For example usages, please see KeywordSearchListsXML, HashDbXML, or IngestModuleLoader.
     * 
     */
    public static <T> boolean xmlIsValid(DOMSource xmlfile, Class<T> clazz, String schemaFile) {
      try{
        PlatformUtil.extractResourceToUserConfigDir(clazz, schemaFile);
        File schemaLoc = new File(PlatformUtil.getUserConfigDirectory() + File.separator + schemaFile);
        SchemaFactory schm = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        try{
        Schema schema = schm.newSchema(schemaLoc);
        Validator validator = schema.newValidator();
        DOMResult result = new DOMResult();
        validator.validate(xmlfile, result);
        return true;
        }
        catch(SAXException e){
            Logger.getLogger(clazz.getName()).log(Level.WARNING, "Unable to validate XML file.", e);
            return false;
        }
      }
      catch(IOException e){
           Logger.getLogger(clazz.getName()).log(Level.WARNING, "Unable to load XML file [" + xmlfile.toString() + "] of type ["+schemaFile+"]", e);
            return false;
        }
    }
    
    /**
     * Evaluates XML files against an XSD.
     * 
     *  The schema files are extracted automatically when this function is called, the XML being validated is not.
     *  Be sure the XML file is already extracted otherwise it will return false.
     * @param doc The XML DOM to validate
     * @param clazz class from package to extract schema from
     * @param type The file name of the schema to validate against, must exist as a resource in the same package as where this function is being called
     * 
     * For example usages, please see KeywordSearchListsXML, HashDbXML, or IngestModuleLoader.
     * 
     */
    public static <T> boolean xmlIsValid(Document doc, Class<T> clazz, String type){
           DOMSource dms = new DOMSource(doc);
           return xmlIsValid(dms, clazz, type);
    }
    
    
    
    /** 
     * Loads XML files from disk
     * 
     * @param clazz the class this method is invoked from
     * @param xmlPath the full path to the file to load
     * @param xsdPath the full path to the file to validate against
     * 
     */
    public static <T> Document loadDoc(Class<T> clazz, String xmlPath, String xsdPath) {
        DocumentBuilderFactory builderFactory =
                DocumentBuilderFactory.newInstance();
        Document ret = null;

        try {
            DocumentBuilder builder = builderFactory.newDocumentBuilder();
            ret = builder.parse(
                    new FileInputStream(xmlPath));
        } catch (ParserConfigurationException e) {
            Logger.getLogger(clazz.getName()).log(Level.SEVERE, "Error loading XML file: can't initialize parser.", e);

        } catch (SAXException e) {
            Logger.getLogger(clazz.getName()).log(Level.SEVERE, "Error loading XML file: can't parse XML.", e);

        } catch (IOException e) {
            //error reading file
            Logger.getLogger(clazz.getName()).log(Level.SEVERE, "Error loading XML file: can't read file.", e);

        }
        if (!XMLUtil.xmlIsValid(ret, clazz, xsdPath)) {
            Logger.getLogger(clazz.getName()).log(Level.SEVERE, "Error loading XML file: could not validate against [" + xsdPath + "], results may not be accurate");
        }

        return ret;
    }
    
    /** 
     * Saves XML files to disk
     * 
     * @param clazz the class this method is invoked from
     * @param xmlPath the full path to save the XML to
     * @param encoding to encoding, such as "UTF-8", to encode the file with
     * @param doc the document to save
     * 
     */
    public static <T> boolean saveDoc(Class<T> clazz, String xmlPath, String encoding, final Document doc) {
        TransformerFactory xf = TransformerFactory.newInstance();
        xf.setAttribute("indent-number", new Integer(1));
        boolean success = false;
        try {
            Transformer xformer = xf.newTransformer();
            xformer.setOutputProperty(OutputKeys.METHOD, "xml");
            xformer.setOutputProperty(OutputKeys.INDENT, "yes");
            xformer.setOutputProperty(OutputKeys.ENCODING, encoding);
            xformer.setOutputProperty(OutputKeys.STANDALONE, "yes");
            xformer.setOutputProperty(OutputKeys.VERSION, "1.0");
            File file = new File(xmlPath);
            FileOutputStream stream = new FileOutputStream(file);
            Result out = new StreamResult(new OutputStreamWriter(stream, encoding));
            xformer.transform(new DOMSource(doc), out);
            stream.flush();
            stream.close();
            success = true;

        } catch (UnsupportedEncodingException e) {
            Logger.getLogger(clazz.getName()).log(Level.SEVERE, "Should not happen", e);
        } catch (TransformerConfigurationException e) {
            Logger.getLogger(clazz.getName()).log(Level.SEVERE, "Error writing XML file", e);
        } catch (TransformerException e) {
            Logger.getLogger(clazz.getName()).log(Level.SEVERE, "Error writing XML file", e);
        } catch (FileNotFoundException e) {
            Logger.getLogger(clazz.getName()).log(Level.SEVERE, "Error writing XML file: cannot write to file: " + xmlPath, e);
        } catch (IOException e) {
            Logger.getLogger(clazz.getName()).log(Level.SEVERE, "Error writing XML file: cannot write to file: " + xmlPath, e);
        }
        return success;
    }
    
}
