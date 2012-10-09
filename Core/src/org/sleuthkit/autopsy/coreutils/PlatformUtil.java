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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.logging.Level;
import javax.xml.XMLConstants;
import org.openide.modules.InstalledFileLocator;
import org.openide.modules.Places;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.*;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;


/**
 *
 * Platform utilities
 */
public class PlatformUtil {

    private static String javaPath = null;
    public static final String keywordXSD = "KeywordsSchema.xsd";
    public static final String hashsetXSD = "HashsetsSchema.xsd";
    public static final String pipelineXSD = "PipelineConfigSchema.xsd";
    public static final String searchEngineXSD = "SearchEngineSchema.xsd";
    
           
            

    /**
     * Get root path where the application is installed
     *
     * @return absolute path string to the install root dir
     */
    public static String getInstallPath() {
        File coreFolder = InstalledFileLocator.getDefault().locate("core", PlatformUtil.class.getPackage().getName(), false);
        File rootPath = coreFolder.getParentFile().getParentFile();
        return rootPath.getAbsolutePath();
    }

    /**
     * Get root path where the application modules are installed
     *
     * @return absolute path string to the install modules root dir, or null if
     * not found
     */
    public static String getInstallModulesPath() {
        File coreFolder = InstalledFileLocator.getDefault().locate("core", PlatformUtil.class.getPackage().getName(), false);

        File rootPath = coreFolder.getParentFile();
        String modulesPath = rootPath.getAbsolutePath() + File.separator + "modules";
        File modulesPathF = new File(modulesPath);
        if (modulesPathF.exists() && modulesPathF.isDirectory()) {
            return modulesPath;
        } else {
            rootPath = rootPath.getParentFile();
            modulesPath = rootPath.getAbsolutePath() + File.separator + "modules";
            modulesPathF = new File(modulesPath);
            if (modulesPathF.exists() && modulesPathF.isDirectory()) {
                return modulesPath;
            } else {
                return null;
            }
        }

    }

    /**
     * Get root path where the user modules are installed
     *
     * @return absolute path string to the install modules root dir, or null if
     * not found
     */
    public static String getUserModulesPath() {
        return getUserDirectory().getAbsolutePath() + File.separator + "modules";
    }

    /**
     * get file path to the java executable binary use embedded java if
     * available, otherwise use system java in PATH no validation is done if
     * java exists in PATH
     *
     * @return file path to java binary
     */
    public synchronized static String getJavaPath() {
        if (javaPath != null) {
            return javaPath;
        }

        File jrePath = new File(getInstallPath() + File.separator + "jre6");

        if (jrePath != null && jrePath.exists() && jrePath.isDirectory()) {
            System.out.println("Embedded jre6 directory found in: " + jrePath.getAbsolutePath());
            javaPath = jrePath.getAbsolutePath() + File.separator + "bin" + File.separator + "java";
        } else {
            //else use system installed java in PATH env variable
            javaPath = "java";

        }

        System.out.println("Using java binary path: " + javaPath);


        return javaPath;
    }

    /**
     * Get user directory where application wide user settings, cache, temp
     * files are stored
     *
     * @return File object representing user directory
     */
    public static File getUserDirectory() {
        return Places.getUserDirectory();
    }
    
    /**
     * Get user config directory path
     * @return Get user config directory path string
     */
    public static String getUserConfigDirectory() {
        return Places.getUserDirectory() + File.separator + "config";
    }

    /**
     * Get log directory path
     * @return Get log directory path string
     */
    public static String getLogDirectory() {
        return Places.getUserDirectory().getAbsolutePath() + File.separator
                + "var" + File.separator + "log" + File.separator;
    }

    public static String getDefaultPlatformFileEncoding() {
        return System.getProperty("file.encoding");
    }

    public static String getDefaultPlatformCharset() {
        return Charset.defaultCharset().name();
    }

    public static String getLogFileEncoding() {
        return Charset.forName("UTF-8").name();
    }

    /**
     * Utility to extract a resource file to a user configuration directory, if it does not
     * exist - useful for setting up default configurations.
     *
     * @param resourceClass class in the same package as the resourceFile to
     * extract
     * @param resourceFile resource file name to extract
     * @return true if extracted, false otherwise (if file already exists)
     * @throws IOException exception thrown if extract the file failed for IO
     * reasons
     */
    public static boolean extractResourceToUserConfigDir(final Class resourceClass, final String resourceFile) throws IOException {
        final File userDir = new File(getUserConfigDirectory());

        final File resourceFileF = new File(userDir + File.separator + resourceFile);
        if (resourceFileF.exists()) {
            return false;
        }

        InputStream inputStream = resourceClass.getResourceAsStream(resourceFile);

        OutputStream out = null;
        InputStream in = null;
        try {

            in = new BufferedInputStream(inputStream);
            OutputStream outFile = new FileOutputStream(resourceFileF);
            out = new BufferedOutputStream(outFile);
            int readBytes = 0;
            while ((readBytes = in.read()) != -1) {
                out.write(readBytes);
            }
        } finally {
            if (in != null) {
                in.close();
            }
            if (out != null) {
                out.flush();
                out.close();
            }
        }
        return true;
    }
    
    /** Utility to evaluate XML files against pre-defined schema files.
     *  The schema files are extracted automatically when this function is called, the xml being validated is not.
     *  Be sure the xml file is already extracted otherwise the return will be false.
     * @param xmlfile The xml file to validate, in DOMSource format
     * @param type The type of schema to validate against, available from PlatformUtil.{keywordXSD, hashsetXSD, searchEngineXSD, pipelineXSD}
     */
    public static boolean xmlIsValid(DOMSource xmlfile, Class clazz, String type) {
      try{
        extractResourceToUserConfigDir(clazz, type);
        File schemaLoc = new File(PlatformUtil.getUserConfigDirectory() + File.separator + type);
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
           Logger.getLogger(clazz.getName()).log(Level.WARNING, "Unable to load XML file [" + xmlfile.toString() + "] of type ["+type+"]", e);
            return false;
        }
    }
    
     /** Utility to evaluate XML files against pre-defined schema files.
     *  The schema files are extracted automatically when this function is called, the xml being validated is not.
     *  Be sure the xml file is already extracted otherwise the return will be false.
     * @param xmlfile The xml file to validate
     * @param type The type of schema to validate against, available from PlatformUtil.{keywordXSD, hashsetXSD, searchEngineXSD, pipelineXSD}
     */
    public static boolean xmlIsValid(Document doc, Class clazz, String type){
           DOMSource dms = new DOMSource(doc);
           return xmlIsValid(dms, clazz, type);
    }
    
    public static Document loadDoc(Class clazz, String xmlPath, String xsdPath) {
        DocumentBuilderFactory builderFactory =
                DocumentBuilderFactory.newInstance();
        Document ret = null;

        try {
            DocumentBuilder builder = builderFactory.newDocumentBuilder();
            ret = builder.parse(
                    new FileInputStream(xmlPath));
        } catch (ParserConfigurationException e) {
            Logger.getLogger(clazz.getName()).log(Level.SEVERE, "Error loading xml file: can't initialize parser.", e);

        } catch (SAXException e) {
            Logger.getLogger(clazz.getName()).log(Level.SEVERE, "Error loading xml file: can't parse XML.", e);

        } catch (IOException e) {
            //error reading file
            Logger.getLogger(clazz.getName()).log(Level.SEVERE, "Error loading xml file: can't read file.", e);

        }
        if (!PlatformUtil.xmlIsValid(ret, clazz, xsdPath)) {
            Logger.getLogger(clazz.getName()).log(Level.SEVERE, "Error loading xml file: could not validate against [" + xsdPath + "], results may not be accurate");
        }

        return ret;
    }
}