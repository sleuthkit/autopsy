/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 - 2013 Basis Technology Corp.
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

package org.sleuthkit.autopsy.fileextmismatch;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.coreutils.XMLUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Storage of file extension mismatch configuration, which maps mimetypes to
 * allowable filename extensions.
 */
class FileExtMismatchXML {
    private static final Logger logger = Logger.getLogger(FileExtMismatchXML.class.getName());    
    private static FileExtMismatchXML defaultInstance = null;
            
    private static final String ENCODING = "UTF-8";
    private static final String XSDFILE = "MismatchConfigSchema.xsd";
    
    private static final String ROOT_EL = "mismatch_config";
    private static final String SIG_EL = "signature";
    private static final String EXT_EL = "ext";    
    private static final String SIG_MIMETYPE_ATTR = "mimetype";
    
    private static final String DEFAULT_CONFIG_FILE_NAME = "mismatch_config.xml";  
        
    protected String filePath;
    
    FileExtMismatchXML(String filePath) {
        this.filePath = filePath;
               
        try {
            boolean extracted = PlatformUtil.extractResourceToUserConfigDir(FileExtMismatchXML.class, DEFAULT_CONFIG_FILE_NAME);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error copying default mismatch configuration to user dir ", ex);
        }        
    }

    /**
     * Singleton provides default configuration from user's directory; user CAN
     * modify this file.
     */
    public static FileExtMismatchXML getDefault() {
        if (defaultInstance == null) {
            final String FILTER_CONFIG_FILE = PlatformUtil.getUserConfigDirectory() + File.separator + DEFAULT_CONFIG_FILE_NAME;
            defaultInstance = new FileExtMismatchXML(FILTER_CONFIG_FILE);
        }
        return defaultInstance;
    }
     
    /**
     * Load and parse XML
     * 
     * @return Loaded hash map or null on error or null if data does not exist
     */
    public HashMap<String, String[]> load() {
        HashMap<String, String[]> sigTypeToExtMap = new HashMap<>();
        
        try
        {
            final Document doc = XMLUtil.loadDoc(FileExtMismatchXML.class, filePath, XSDFILE);
            if (doc == null) {
                return null;
            }
            
            Element root = doc.getDocumentElement();
            if (root == null) {
                logger.log(Level.SEVERE, "Error loading config file: invalid file format (bad root).");
                return null;
            }            
            
            NodeList sigNList = root.getElementsByTagName(SIG_EL);
            final int numSigs = sigNList.getLength();
            
            if (numSigs == 0) {
                return null;
            }
                
            for(int sigIndex = 0; sigIndex < numSigs; ++sigIndex) {                
                Element sigEl = (Element)sigNList.item(sigIndex);
                final String mimetype = sigEl.getAttribute(SIG_MIMETYPE_ATTR); 
             
                NodeList extNList = sigEl.getElementsByTagName(EXT_EL);
                final int numExts = extNList.getLength();

                if (numExts != 0) {
                    List<String> extStrings = new ArrayList<>();
                    for(int extIndex = 0; extIndex < numExts; ++extIndex) {
                        Element extEl = (Element)extNList.item(extIndex);
                        extStrings.add(extEl.getTextContent());
                    }
                    String[] sarray = extStrings.toArray(new String[0]);
                    sigTypeToExtMap.put(mimetype, sarray);
                } else {
                    sigTypeToExtMap.put(mimetype, null); //ok to have an empty type (the ingest module will not use it)
                }
            }
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error loading config file.", e);
            return null;
        }        
        return sigTypeToExtMap;
    }
    
    
    /**
     * Save XML to filePath, overwriting it if it already exists
     * 
     * @param sigTypeToExtMap String arrays of extensions mapped to each string mimetype.
     * @return Loaded hash map or null on error or null if data does not exist
     */
    public boolean save(HashMap<String, String[]> sigTypeToExtMap) {    
        boolean success;

        DocumentBuilderFactory dbfac = DocumentBuilderFactory.newInstance();

        try {
            DocumentBuilder docBuilder = dbfac.newDocumentBuilder();
            Document doc = docBuilder.newDocument();

            Element rootEl = doc.createElement(ROOT_EL);
            doc.appendChild(rootEl);
            
            ArrayList<String> appTypeList = new ArrayList<>(sigTypeToExtMap.keySet());
            Collections.sort(appTypeList);
            
            for (String appType : appTypeList) {
                Element sigEl = doc.createElement(SIG_EL);
                sigEl.setAttribute(SIG_MIMETYPE_ATTR, appType);
                
                String[] extArray = sigTypeToExtMap.get(appType);
                if (extArray != null) {
                    ArrayList<String> extList = new ArrayList<>(Arrays.asList(extArray));
                    Collections.sort(extList);
                    for (String ext : extList) {
                        Element extEl = doc.createElement(EXT_EL);
                        extEl.setTextContent(ext);
                        sigEl.appendChild(extEl);
                    }
                }
                rootEl.appendChild(sigEl);
            }

            success = XMLUtil.saveDoc(FileExtMismatchXML.class, filePath, ENCODING, doc);
            
        } catch (ParserConfigurationException e) {
            logger.log(Level.SEVERE, "Error saving keyword list: can't initialize parser.", e);
            success = false;
        }
        return success;        
    }
    
}
        
