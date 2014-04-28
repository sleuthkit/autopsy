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


package org.sleuthkit.autopsy.modules.externalresults;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.sleuthkit.autopsy.coreutils.XMLUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 *
 */
public class ExternalResultsXML implements ExternalResultsParser {
    private static final Logger logger = Logger.getLogger(ExternalResultsXML.class.getName());    
            
    private static final String XSDFILE = "autopsy_external_results.xsd"; //NON-NLS
    
    private static final String ROOT_EL = "autopsy_results"; //NON-NLS
    private static final String DATASRC_EL = "data_source"; //NON-NLS
    private static final String ARTLIST_EL = "artifacts"; //NON-NLS
    private static final String ART_EL = "artifact"; //NON-NLS
    private static final String FILE_EL = "file"; //NON-NLS
    private static final String PATH_EL = "path"; //NON-NLS
    private static final String ATTR_EL = "attribute"; //NON-NLS
    private static final String VALUE_EL = "value"; //NON-NLS
    private static final String SRC_EL = "source"; //NON-NLS
    private static final String CONTEXT_EL = "context"; //NON-NLS
    private static final String REPORTLIST_EL = "reports"; //NON-NLS
    private static final String REPORT_EL = "report"; //NON-NLS
    private static final String DISPLAYNAME_EL = "display_name"; //NON-NLS
    private static final String DERIVEDLIST_EL = "derived_files"; //NON-NLS
    private static final String DERIVED_EL = "derived_file"; //NON-NLS
    private static final String LOCALPATH_EL = "local_path"; //NON-NLS
    private static final String TYPE_ATTR = "type"; //NON-NLS
    private static final String NAME_ATTR = "name"; //NON-NLS

    private String importFilePath;
    private ResultsData resultsData = null;
    
    /**
     * 
     * @param importFilePath 
     */
    ExternalResultsXML(String importFilePath) {
        this.importFilePath = importFilePath;
    }
    
    /**
     * Parses info for artifacts, derived files, and reports in the given XML file.
     * @return 
     */
    @Override
    public ResultsData parse() {
        resultsData = new ResultsData();
        try
        {
            final Document doc = XMLUtil.loadDoc(ExternalResultsXML.class, importFilePath, XSDFILE);
            if (doc == null) {
                return null;
            }
            
            Element root = doc.getDocumentElement();
            if (root == null) {
                logger.log(Level.SEVERE, "Error loading XML file: invalid file format (bad root)."); //NON-NLS
                return null;
            }            
            if (!root.getNodeName().equals(ROOT_EL)) {
                logger.log(Level.SEVERE, "Error loading XML file: root element must be " + ROOT_EL + ")."); //NON-NLS
                return null;                
            }

            parseDataSource(root);
            parseArtifacts(root);
            parseReports(root);
            parseDerivedFiles(root);
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error loading XML file.", e); //NON-NLS
            return null;
        }
        return resultsData;
    }

    /**
     * 
     * @param root
     * @throws Exception 
     */
     private void parseDataSource(Element root ) throws Exception {
        NodeList nodeList = root.getElementsByTagName(DATASRC_EL);
        final int numNodes = nodeList.getLength();

        for(int index = 0; index < numNodes; ++index) {                
            Element el = (Element)nodeList.item(index);
            resultsData.addDataSource(el.getTextContent());
        }
    }   
    
    /**
     * 
     * @param root 
     */
    private void parseArtifacts(Element root ) throws Exception {
        NodeList nodeList = root.getElementsByTagName(ARTLIST_EL);

        // for each artifacts list (normally there should be just 1)
        for(int index = 0; index < nodeList.getLength(); ++index) {             
            Element el = (Element)nodeList.item(index);
            NodeList subNodeList = el.getElementsByTagName(ART_EL);
            
            // for each artifact
            for(int subIndex = 0; subIndex < subNodeList.getLength(); ++subIndex) {             
                Element subEl = (Element)subNodeList.item(subIndex);
                final String type = subEl.getAttribute(TYPE_ATTR);
                final int artResultsIndex = resultsData.addArtifact(type);
                parseAttributes(subEl, artResultsIndex);
                parseFiles(subEl, artResultsIndex);
            }
        }
    }
    
    /**
     * 
     * @param root  Should be an artifact element
     * @param artResultsIndex 
     */
    private void parseAttributes(Element root, int artResultsIndex) {
        NodeList nodeList = root.getElementsByTagName(ATTR_EL);

        for(int index = 0; index < nodeList.getLength(); ++index) {                
            Element el = (Element)nodeList.item(index);
            final String type = el.getAttribute(TYPE_ATTR);
            final int attrResultsIndex = resultsData.addAttribute(artResultsIndex, type);

            // add values, if any
            NodeList valueNodeList = el.getElementsByTagName(VALUE_EL);
            for(int subindex = 0; subindex < valueNodeList.getLength(); ++subindex) { 
                Element subEl = (Element)valueNodeList.item(subindex);
                final String valueStr = subEl.getTextContent();
                final String valueType = subEl.getAttribute(TYPE_ATTR); //empty string is ok
                resultsData.addAttributeValue(artResultsIndex, attrResultsIndex, valueStr, valueType);
            }               

            // add source, if any
            NodeList srcNodeList = el.getElementsByTagName(SRC_EL);
            if (srcNodeList.getLength() > 0) {
                // we only use the first occurence
                Element subEl = (Element)srcNodeList.item(0);
                final String srcStr = subEl.getTextContent();
                resultsData.addAttributeSource(artResultsIndex, attrResultsIndex, srcStr);
            }

            // add context, if any
            NodeList contextNodeList = el.getElementsByTagName(CONTEXT_EL);
            if (contextNodeList.getLength() > 0) {
                // we only use the first occurence
                Element subEl = (Element)contextNodeList.item(0);
                final String contextStr = subEl.getTextContent();
                resultsData.addAttributeContext(artResultsIndex, attrResultsIndex, contextStr);
            }            
        }
    }        
    
    /**
     * 
     * @param root  Should be an artifact element
     * @param artResultsIndex 
     */
    private void parseFiles(Element root, int artResultsIndex) throws Exception {
        NodeList nodeList = root.getElementsByTagName(FILE_EL);

        if (nodeList.getLength() > 0) {
            // we only use the first occurence
            Element el = (Element)nodeList.item(0);

            // add path 
            NodeList subNodeList = el.getElementsByTagName(PATH_EL);
            if (nodeList.getLength() > 0) {
                // we only use the first occurence
                Element subEl = (Element)subNodeList.item(0);
                final String path = subEl.getTextContent();
                resultsData.addArtifactFile(artResultsIndex, path);
            } else {
                // error to have a file element without a path element
                throw new Exception("File element is missing path element.");
            }
        }
    }            
    
    /**
     * 
     * @param root 
     */
    private void parseReports(Element root ) throws Exception {
        NodeList nodeList = root.getElementsByTagName(REPORTLIST_EL);

        // for each reports list (normally there should be just 1)
        for(int index = 0; index < nodeList.getLength(); ++index) {             
            Element el = (Element)nodeList.item(index);
            NodeList subNodeList = el.getElementsByTagName(REPORT_EL);
            
            // for each report
            for(int subIndex = 0; subIndex < subNodeList.getLength(); ++subIndex) {             
                Element subEl = (Element)subNodeList.item(subIndex);                
                String displayName = "";
                String localPath = "";
                NodeList nameNodeList = subEl.getElementsByTagName(DISPLAYNAME_EL);
                if (nameNodeList.getLength() > 0) {
                    // we only use the first occurence
                    Element nameEl = (Element)nameNodeList.item(0);
                    displayName = nameEl.getTextContent();
                }
                NodeList pathNodeList = subEl.getElementsByTagName(LOCALPATH_EL);
                if (pathNodeList.getLength() > 0) {
                    // we only use the first occurence
                    Element pathEl = (Element)pathNodeList.item(0);
                    localPath = pathEl.getTextContent();
                }                
                if ((!displayName.isEmpty()) && (!localPath.isEmpty())) {
                    resultsData.addReport(displayName, localPath);
                } else {
                    // error to have a file element without a path element
                    throw new Exception("report element is missing display_name or local_path.");
                }
            }
        }
    }    
    
    /**
     * 
     * @param root 
     */
    private void parseDerivedFiles(Element root ) throws Exception {
        NodeList nodeList = root.getElementsByTagName(DERIVEDLIST_EL);
        
        // for each derived files list (normally there should be just 1)
        for(int index = 0; index < nodeList.getLength(); ++index) {             
            Element el = (Element)nodeList.item(index);
            NodeList subNodeList = el.getElementsByTagName(DERIVED_EL);
            
            // for each derived file
            for(int subIndex = 0; subIndex < subNodeList.getLength(); ++subIndex) {             
                Element subEl = (Element)subNodeList.item(subIndex);                
                String localPath = "";
                NodeList pathNodeList = subEl.getElementsByTagName(LOCALPATH_EL);
                if (pathNodeList.getLength() > 0) {
                    // we only use the first occurence
                    Element pathEl = (Element)pathNodeList.item(0);
                    localPath = pathEl.getTextContent();
                }                
                if (!localPath.isEmpty()) {
                    resultsData.addDerivedFile(localPath);
                } else {
                    // error to have a file element without a path element
                    throw new Exception("derived_files element is missing local_path.");
                }
            }
        }
    }    
}
