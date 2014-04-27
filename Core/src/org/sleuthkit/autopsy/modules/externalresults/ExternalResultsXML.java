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
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.sleuthkit.autopsy.coreutils.XMLUtil;
import org.sleuthkit.datamodel.TskException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 *
 */
public class ExternalResultsXML implements ExternalResultsParser {
    private static final Logger logger = Logger.getLogger(ExternalResultsXML.class.getName());    
            
    private static final String ENCODING = "UTF-8"; //NON-NLS
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

    private String reportFilePath;
    private ResultsData resultsData = null;
    
    /**
     * 
     * @param reportPath 
     */
    ExternalResultsXML(String reportPath) {
        ///@todo find an xml file to parse
        reportFilePath = reportPath + File.separator + "ext-test1.xml";
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
            final Document doc = XMLUtil.loadDoc(ExternalResultsXML.class, reportFilePath, XSDFILE);
            if (doc == null) {
                return null;
            }
            
            Element root = doc.getDocumentElement();
            if (root == null) {
                logger.log(Level.SEVERE, "Error loading XML file: invalid file format (bad root)."); //NON-NLS
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

     private void parseDataSource(Element root ) throws Exception {
        NodeList nodeList = root.getElementsByTagName(DATASRC_EL);
        final int numNodes = nodeList.getLength();

        if (numNodes == 0) {
            throw new Exception("Error: No data source specified in XML file.");
        }

        for(int index = 0; index < numNodes; ++index) {                
            Element el = (Element)nodeList.item(index);

        }
    }   
    
    private void parseArtifacts(Element root ) {
        NodeList nodeList = root.getElementsByTagName(ARTLIST_EL);
        final int numNodes = nodeList.getLength();

        if (numNodes == 0) {
            return;
        }

        for(int index = 0; index < numNodes; ++index) {                
            Element el = (Element)nodeList.item(index);

        }
    }
    
    private void parseReports(Element root ) {
        NodeList nodeList = root.getElementsByTagName(REPORTLIST_EL);
        final int numNodes = nodeList.getLength();

        if (numNodes == 0) {
            return;
        }

        for(int index = 0; index < numNodes; ++index) {                
            Element el = (Element)nodeList.item(index);

        }
    }    
    
    private void parseDerivedFiles(Element root ) {
        NodeList nodeList = root.getElementsByTagName(DERIVEDLIST_EL);
        final int numNodes = nodeList.getLength();

        if (numNodes == 0) {
            return;
        }

        for(int index = 0; index < numNodes; ++index) {                
            Element el = (Element)nodeList.item(index);

        }
    }    
}
