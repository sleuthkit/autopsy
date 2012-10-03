/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011 Basis Technology Corp.
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
package org.sleuthkit.autopsy.hashdatabase;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.hashdatabase.HashDb.DBType;
import org.sleuthkit.datamodel.SleuthkitJNI;
import org.sleuthkit.datamodel.TskCoreException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class HashDbXML {
    private static final String ROOT_EL = "hash_sets";
    private static final String SET_EL = "hash_set";
    private static final String SET_NAME_ATTR = "name";
    private static final String SET_TYPE_ATTR = "type"; 
    private static final String SET_USE_FOR_INGEST_ATTR = "use_for_ingest";
    private static final String SET_SHOW_INBOX_MESSAGES = "show_inbox_messages";
    private static final String PATH_EL = "hash_set_path";
    private static final String PATH_NUMBER_ATTR = "number";
    private static final String CUR_HASHSETS_FILE_NAME = "hashsets.xml";
    private static final String ENCODING = "UTF-8";
    private static final String CUR_HASHSET_FILE = PlatformUtil.getUserConfigDirectory() + File.separator + CUR_HASHSETS_FILE_NAME;
    private static final String SET_CALC = "hash_calculate";
    private static final String SET_VALUE = "value";
    private static final Logger logger = Logger.getLogger(HashDbXML.class.getName());
    private static HashDbXML currentInstance;
    
    private List<HashDb> knownBadSets;
    private HashDb nsrlSet;
    private String xmlFile;
    private boolean calculate;
    
    private HashDbXML(String xmlFile) {
        knownBadSets = new ArrayList<HashDb>();
        this.xmlFile = xmlFile;
    }
    
    /**
     * get instance for managing the current keyword list of the application
     */
    static synchronized HashDbXML getCurrent() {
        if (currentInstance == null) {
            currentInstance = new HashDbXML(CUR_HASHSET_FILE);
            currentInstance.reload();
        }
        return currentInstance;
    }
    
    /**
     * Get the hash sets
     */
    public List<HashDb> getAllSets() {
        List<HashDb> ret = new ArrayList<HashDb>();
        if(nsrlSet != null) {
            ret.add(nsrlSet);
        }
        ret.addAll(knownBadSets);
        return ret;
    }
    
    /** 
     * Get the Known Bad sets
     */
    public List<HashDb> getKnownBadSets() {
        return knownBadSets;
    }
    
    /** 
     * Get the NSRL set
     */
    public HashDb getNSRLSet() {
        return nsrlSet;
    }
    
    /**
     * Add a known bad hash set
     */
    public void addKnownBadSet(HashDb set) {
        knownBadSets.add(set);
        //save();
    }
    
    /**
     * Add a known bad hash set
     */
    public void addKnownBadSet(int index, HashDb set) {
        knownBadSets.add(index, set);
        //save();
    }
    
    /**
     * Set the NSRL hash set (override old set)
     */
    public void setNSRLSet(HashDb set) {
        this.nsrlSet = set;
        //save();
    }
    
    /**
     * Remove a hash known bad set
     */
    public void removeKnownBadSetAt(int index) {
        knownBadSets.remove(index);
        //save();
    }
    
    /** 
     * Remove the NSRL database
     */
    public void removeNSRLSet() {
        this.nsrlSet = null;
        //save();
    }
    
    /**
     * load the file or create new
     */
    public void reload() {
        boolean created = false;

        knownBadSets.clear();
        nsrlSet = null;
        
        if (!this.setsFileExists()) {
            //create new if it doesn't exist
            save();
            created = true;
        }

        //load, if fails to load create new; save regardless
        load();
        if (!created) {
            //create new if failed to load
            save();
        }
    }
    
    /**
     * Sets the local variable calculate to the given boolean.
     * @param set the state to make calculate
     */
    public void setCalculate(boolean set) {
        this.calculate = set;
        //save();
    }
    
    /**
     * Returns the value of the local boolean calculate.
     * @return true if calculate is true, false otherwise
     */
    public boolean getCalculate() {
        return this.calculate;
    }
    
    /**
     * writes out current sets file replacing the last one
     */
    public boolean save() {
        boolean success = false;

        DocumentBuilderFactory dbfac = DocumentBuilderFactory.newInstance();

        try {
            DocumentBuilder docBuilder = dbfac.newDocumentBuilder();
            Document doc = docBuilder.newDocument();

            Element rootEl = doc.createElement(ROOT_EL);
            doc.appendChild(rootEl);

            for (HashDb set : knownBadSets) {
                String useForIngest = Boolean.toString(set.getUseForIngest());
                String showInboxMessages = Boolean.toString(set.getShowInboxMessages());
                List<String> paths = set.getDatabasePaths();
                String type = DBType.KNOWN_BAD.toString();

                Element setEl = doc.createElement(SET_EL);
                setEl.setAttribute(SET_NAME_ATTR, set.getName());
                setEl.setAttribute(SET_TYPE_ATTR, type);
                setEl.setAttribute(SET_USE_FOR_INGEST_ATTR, useForIngest);
                setEl.setAttribute(SET_SHOW_INBOX_MESSAGES, showInboxMessages);

                for (int i = 0; i < paths.size(); i++) {
                    String path = paths.get(i);
                    Element pathEl = doc.createElement(PATH_EL);
                    pathEl.setAttribute(PATH_NUMBER_ATTR, Integer.toString(i));
                    pathEl.setTextContent(path);
                    setEl.appendChild(pathEl);
                }
                rootEl.appendChild(setEl);
            }
            
            if(nsrlSet != null) {
                String useForIngest = Boolean.toString(nsrlSet.getUseForIngest());
                String showInboxMessages = Boolean.toString(nsrlSet.getShowInboxMessages());
                List<String> paths = nsrlSet.getDatabasePaths();
                String type = DBType.NSRL.toString();

                Element setEl = doc.createElement(SET_EL);
                setEl.setAttribute(SET_NAME_ATTR, nsrlSet.getName());
                setEl.setAttribute(SET_TYPE_ATTR, type);
                setEl.setAttribute(SET_USE_FOR_INGEST_ATTR, useForIngest);
                setEl.setAttribute(SET_SHOW_INBOX_MESSAGES, showInboxMessages);

                for (int i = 0; i < paths.size(); i++) {
                    String path = paths.get(i);
                    Element pathEl = doc.createElement(PATH_EL);
                    pathEl.setAttribute(PATH_NUMBER_ATTR, Integer.toString(i));
                    pathEl.setTextContent(path);
                    setEl.appendChild(pathEl);
                }
                rootEl.appendChild(setEl);
            }
            
            String calcValue = Boolean.toString(calculate);
            Element setCalc = doc.createElement(SET_CALC);
            setCalc.setAttribute(SET_VALUE, calcValue);
            rootEl.appendChild(setCalc);

            success = saveDoc(doc);
        } catch (ParserConfigurationException e) {
            logger.log(Level.SEVERE, "Error saving hash sets: can't initialize parser.", e);
        }
        return success;
    }

    /**
     * load and parse XML, then dispose
     */
    public boolean load() {
        final Document doc = loadDoc();
        if (doc == null) {
            return false;
        }

        Element root = doc.getDocumentElement();
        if (root == null) {
            logger.log(Level.SEVERE, "Error loading hash sets: invalid file format.");
            return false;
        }
        NodeList setsNList = root.getElementsByTagName(SET_EL);
        int numSets = setsNList.getLength();
        if(numSets==0) {
            logger.log(Level.WARNING, "No element hash_set exists.");
        }
        for (int i = 0; i < numSets; ++i) {
            Element setEl = (Element) setsNList.item(i);
            final String name = setEl.getAttribute(SET_NAME_ATTR);
            final String type = setEl.getAttribute(SET_TYPE_ATTR);
            final String useForIngest = setEl.getAttribute(SET_USE_FOR_INGEST_ATTR);
            final String showInboxMessages = setEl.getAttribute(SET_SHOW_INBOX_MESSAGES);
            Boolean useForIngestBool = Boolean.parseBoolean(useForIngest);
            Boolean showInboxMessagesBool = Boolean.parseBoolean(showInboxMessages);
            List<String> paths = new ArrayList<String>();

            // Parse all paths
            NodeList pathsNList = setEl.getElementsByTagName(PATH_EL);
            final int numPaths = pathsNList.getLength();
            for (int j = 0; j < numPaths; ++j) {
                Element pathEl = (Element) pathsNList.item(j);
                String number = pathEl.getAttribute(PATH_NUMBER_ATTR);
                String path = pathEl.getTextContent();
                
                // If either the database or it's index exist
                File database = new File(path);
                File index = new File(HashDb.toIndexPath(path));
                if(database.exists() || index.exists()) {
                    paths.add(path);
                } else {
                    // Ask for new path
                    int ret = JOptionPane.showConfirmDialog(null, "Database " + name + " could not be found at location\n"
                            + path + "\n"
                            + " Would you like to search for the file?", "Missing Database", JOptionPane.YES_NO_OPTION);
                    if (ret == JOptionPane.YES_OPTION) {
                        String filePath = searchForFile(name);
                        if(filePath!=null) {
                            paths.add(filePath);
                        }
                    }
                }
            }
            
            // Check everything was properly set
            if(name.isEmpty()) {
                logger.log(Level.WARNING, "Name was not set for hash_set at index {0}.", i);
            }
            if(type.isEmpty()) {
                logger.log(Level.SEVERE, "Type was not set for hash_set at index {0}, cannot make instance of HashDb class.", i);
                return false; // exit because this causes a fatal error
            }
            if(useForIngest.isEmpty()) {
                logger.log(Level.WARNING, "UseForIngest was not set for hash_set at index {0}.", i);
            }
            if(showInboxMessages.isEmpty()) {
                logger.log(Level.WARNING, "ShowInboxMessages was not set for hash_set at index {0}.", i);
            }
            
            if(paths.isEmpty()) {
                logger.log(Level.WARNING, "No paths were set for hash_set at index {0}. Removing the database.", i);
            } else {
                // No paths for this entry, the user most likely declined to search for them
                DBType typeDBType = DBType.valueOf(type);
                HashDb set = new HashDb(name, paths, useForIngestBool, showInboxMessagesBool, typeDBType);
                
                if(typeDBType == DBType.KNOWN_BAD) {
                    knownBadSets.add(set);
                } else if(typeDBType == DBType.NSRL) {
                    this.nsrlSet = set;
                }
            }
        }
        
        NodeList calcList = root.getElementsByTagName(SET_CALC);
        int numCalc = calcList.getLength(); // Shouldn't be more than 1
        if(numCalc==0) {
            logger.log(Level.WARNING, "No element hash_calculate exists.");
        }
        for(int i=0; i<numCalc; i++) {
            Element calcEl = (Element) calcList.item(i);
            final String value = calcEl.getAttribute(SET_VALUE);
            calculate = Boolean.parseBoolean(value);
        }
        return true;
    }
    
    /**
     * Ask the user to browse to a new Hash Database file with the same database
     * name as the one provided. If the names do not match, the database cannot
     * be added. If the user cancels the search, return null, meaning the user
     * would like to remove the entry for the missing database.
     * 
     * @param name the name of the database to add
     * @return the file path to the new database, or null if the user wants to
     *         delete the old database
     */
    private String searchForFile(String name) {
        // Initialize the file chooser and only allow hash databases to be opened
        JFileChooser fc = new JFileChooser();
        fc.setDragEnabled(false);
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        String[] EXTENSION = new String[] { "txt", "idx", "hash", "Hash" };
        FileNameExtensionFilter filter = new FileNameExtensionFilter(
                "Hash Database File", EXTENSION);
        fc.setFileFilter(filter);
        fc.setMultiSelectionEnabled(false);

        int retval = fc.showOpenDialog(null);
        // If the user selects an appropriate file
        if (retval == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            try {
                String filePath = f.getCanonicalPath();
                if (HashDb.isIndexPath(filePath)) {
                    filePath = HashDb.toDatabasePath(filePath);
                }
                String derivedName = SleuthkitJNI.getDatabaseName(filePath);
                // If the database has the same name as before, return it
                if(derivedName.equals(name)) {
                    return filePath;
                } else {
                    int tryAgain = JOptionPane.showConfirmDialog(null, "Database file cannot be added because it does not have the same name as the original.\n" +
                            "Would you like to try a different database?", "Invalid File", JOptionPane.YES_NO_OPTION);
                    if (tryAgain == JOptionPane.YES_OPTION) {
                        return searchForFile(name);
                    } else {
                        return null;
                    }
                }
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Couldn't get selected file path.", ex);
            } catch (TskCoreException ex) {
                int tryAgain = JOptionPane.showConfirmDialog(null, "Database file you chose cannot be opened.\n" + "If it was just an index, please try to recreate it from the database.\n" +
                        "Would you like to choose another database?", "Invalid File", JOptionPane.YES_NO_OPTION);
                if (tryAgain == JOptionPane.YES_OPTION) {
                    return searchForFile(name);
                } else {
                    return null;
                }
            }
        }
        // Otherwise the user cancelled, so delete the missing entry
        return null;
    }

    private boolean setsFileExists() {
        File f = new File(xmlFile);
        return f.exists() && f.canRead() && f.canWrite();
    }

    private Document loadDoc() {
        DocumentBuilderFactory builderFactory =
                DocumentBuilderFactory.newInstance();

        Document ret = null;


        try {
            DocumentBuilder builder = builderFactory.newDocumentBuilder();
            ret = builder.parse(
                    new FileInputStream(xmlFile));
        } catch (ParserConfigurationException e) {
            logger.log(Level.SEVERE, "Error loading hash sets: can't initialize parser.", e);

        } catch (SAXException e) {
            logger.log(Level.SEVERE, "Error loading hash sets: can't parse XML.", e);

        } catch (IOException e) {
            //error reading file
            logger.log(Level.SEVERE, "Error loading hash sets: can't read file.", e);

        }
        return ret;

    }

    private boolean saveDoc(final Document doc) {
        TransformerFactory xf = TransformerFactory.newInstance();
        xf.setAttribute("indent-number", new Integer(1));
        boolean success = false;
        try {
            Transformer xformer = xf.newTransformer();
            xformer.setOutputProperty(OutputKeys.METHOD, "xml");
            xformer.setOutputProperty(OutputKeys.INDENT, "yes");
            xformer.setOutputProperty(OutputKeys.ENCODING, ENCODING);
            xformer.setOutputProperty(OutputKeys.STANDALONE, "yes");
            xformer.setOutputProperty(OutputKeys.VERSION, "1.0");
            File file = new File(xmlFile);
            FileOutputStream stream = new FileOutputStream(file);
            Result out = new StreamResult(new OutputStreamWriter(stream, ENCODING));
            xformer.transform(new DOMSource(doc), out);
            stream.flush();
            stream.close();
            success = true;

        } catch (UnsupportedEncodingException e) {
            logger.log(Level.SEVERE, "Should not happen", e);
        } catch (TransformerConfigurationException e) {
            logger.log(Level.SEVERE, "Error writing hash sets XML", e);
        } catch (TransformerException e) {
            logger.log(Level.SEVERE, "Error writing hash sets XML", e);
        } catch (FileNotFoundException e) {
            logger.log(Level.SEVERE, "Error writing hash sets XML: cannot write to file: " + xmlFile, e);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error writing hash sets XML: cannot write to file: " + xmlFile, e);
        }
        return success;
    }
    
    
}
