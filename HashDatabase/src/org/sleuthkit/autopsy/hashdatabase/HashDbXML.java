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
package org.sleuthkit.autopsy.hashdatabase;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.coreutils.XMLUtil;
import org.sleuthkit.autopsy.hashdatabase.HashDb.KnownFilesType;
import org.sleuthkit.datamodel.SleuthkitJNI;
import org.sleuthkit.datamodel.TskCoreException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * This class is a singleton that manages the configuration of the hash databases
 * that serve as hash sets for the identification of known files, known good files, 
 * and known bad files. 
 */
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
    private static final String XSDFILE = "HashsetsSchema.xsd";
    private static final String ENCODING = "UTF-8";
    private static final String SET_CALC = "hash_calculate";
    private static final String SET_VALUE = "value";
    private static final Logger logger = Logger.getLogger(HashDbXML.class.getName());
    private static HashDbXML instance;
    private List<HashDb> knownBadSets = new ArrayList<>();
    private HashDb nsrlSet;
    private boolean alwaysCalculateHashes;
    private String xmlFile = PlatformUtil.getUserConfigDirectory() + File.separator + CUR_HASHSETS_FILE_NAME;
    
    /**
     * Gets the singleton instance of this class.
     */
    public static synchronized HashDbXML getInstance() {
        if (instance == null) {
            instance = new HashDbXML();
            instance.reload();
        }
        return instance;
    }

    private HashDbXML() {
    }
     
    /**
     * Adds a hash database to the hash set configuration.
     */
    public void addSet(HashDb set) {
        if (set.getKnownFilesType() == HashDb.KnownFilesType.NSRL) {
            setNSRLSet(set);
        }
        else {
            addKnownBadSet(set);
        }
    }
    
    /**
     * Sets the configured National Software Reference Library (NSRL) known 
     * files hash set. Does not save the configuration.
     */
    public void setNSRLSet(HashDb set) {
        this.nsrlSet = set;
    }

    /** 
     * Gets the configured National Software Reference Library (NSRL) known files hash set.
     * @return A HashDb object representing the hash set or null if an NSRL set 
     * has not been added to the configuration.
     */
    public HashDb getNSRLSet() {
        return nsrlSet;
    }

    /** 
     * Removes the configured National Software Reference Library (NSRL) known 
     * files hash set. Does not save the configuration.
     */
    public void removeNSRLSet() {
        this.nsrlSet = null;
    }

    /**
     * Adds a known bad files hash set to the configuration. Does not save the
     * configuration.
     */
    public void addKnownBadSet(HashDb set) {
        knownBadSets.add(set);
    }
        
    /**
     * Adds a known bad files hash set to the configuration. The set is added to
     * the internal known bad sets collection at the index specified by the 
     * caller. Note that this method does not save the configuration.
     */
    public void addKnownBadSet(int index, HashDb set) {
        knownBadSets.add(index, set);
    }
        
    /** 
     * Gets the configured known bad files hash sets.
     * @return A list, possibly empty, of HashDb objects representing the hash 
     * sets.
     */
    public List<HashDb> getKnownBadSets() {
        return Collections.unmodifiableList(knownBadSets);
    }
    
    /** 
     * Removes the known bad files hash set from the internal known bad files 
     * hash sets collection at the specified index. Does not save the configuration.
     */
    public void removeKnownBadSetAt(int index) {
        knownBadSets.remove(index);
    }
        
   /**
     * Gets the configured known files hash sets that accept updates. 
     * @return A list, possibly empty, of HashDb objects. 
     */
    public List<HashDb> getUpdateableHashSets() {
        ArrayList<HashDb> updateableDbs = new ArrayList<>();
        for (HashDb db : knownBadSets) {
            try {
                if (db.isUpdateable()) {
                    updateableDbs.add(db);
                }
            }
            catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Error checking updateable status of " + db.getDatabasePath(), ex);
            }
        }
        return Collections.unmodifiableList(updateableDbs);
    }

    /**
     * Gets all of the configured known files hash sets.
     * @return A list, possibly empty, of HashDb objects representing the hash 
     * sets.
     */
    public List<HashDb> getAllSets() {
        List<HashDb> hashDbs = new ArrayList<>();
        if (nsrlSet != null) {
            hashDbs.add(nsrlSet);
        }
        hashDbs.addAll(knownBadSets);
        return Collections.unmodifiableList(hashDbs);
    }
    
    /**
     * Reloads the configuration file if it exists, creates it otherwise.
     */
    public void reload() {
        // TODO: This does not look like it is correct. Revisit when time permits.
        boolean created = false;
        
        nsrlSet = null;
        knownBadSets.clear();
        
        if (!setsFileExists()) {
            save();
            created = true;
        }

        load();
        if (!created) {
            save();
        }
    }
    
    /**
     * Sets the value for the flag indicates whether hashes should be calculated
     * for content even if no hash databases are configured.
     */
    public void setShouldAlwaysCalculateHashes(boolean alwaysCalculateHashes) {
        this.alwaysCalculateHashes = alwaysCalculateHashes;
    }
    
    /**
     * Accesses the flag that indicates whether hashes should be calculated
     * for content even if no hash databases are configured.
     */
    public boolean shouldAlwaysCalculateHashes() {
        return alwaysCalculateHashes;
    }
    
    /**
     * Saves the known files hash sets configuration to disk. Note that the 
     * configuration is only saved on demand to support cancellation of 
     * configuration panels and dialogs.
     * @return True on success, false otherwise.
     */
    public boolean save() {
        boolean success = false;

        DocumentBuilderFactory dbfac = DocumentBuilderFactory.newInstance();

        try {
            DocumentBuilder docBuilder = dbfac.newDocumentBuilder();
            Document doc = docBuilder.newDocument();

            Element rootEl = doc.createElement(ROOT_EL);
            doc.appendChild(rootEl);

            // TODO: Remove all the multiple database paths stuff, it was a mistake. 
            for (HashDb set : knownBadSets) {
                String useForIngest = Boolean.toString(set.getUseForIngest());
                String showInboxMessages = Boolean.toString(set.getShowInboxMessages());
                List<String> paths = Collections.singletonList(set.getDatabasePath());
                String type = KnownFilesType.KNOWN_BAD.toString();

                Element setEl = doc.createElement(SET_EL);
                setEl.setAttribute(SET_NAME_ATTR, set.getDisplayName());
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
            
            // TODO: Remove all the multiple database paths stuff. 
            if(nsrlSet != null) {
                String useForIngest = Boolean.toString(nsrlSet.getUseForIngest());
                String showInboxMessages = Boolean.toString(nsrlSet.getShowInboxMessages());
                List<String> paths = Collections.singletonList(nsrlSet.getDatabasePath());
                String type = KnownFilesType.NSRL.toString();

                Element setEl = doc.createElement(SET_EL);
                setEl.setAttribute(SET_NAME_ATTR, nsrlSet.getDisplayName());
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

            String calcValue = Boolean.toString(alwaysCalculateHashes);
            Element setCalc = doc.createElement(SET_CALC);
            setCalc.setAttribute(SET_VALUE, calcValue);
            rootEl.appendChild(setCalc);

            success = XMLUtil.saveDoc(HashDbXML.class, xmlFile, ENCODING, doc);
        } 
        catch (ParserConfigurationException e) {
            logger.log(Level.SEVERE, "Error saving hash sets: can't initialize parser.", e);
        }
        return success;
    }

    private boolean load() {
        final Document doc = XMLUtil.loadDoc(HashDbXML.class, xmlFile, XSDFILE);
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
            List<String> paths = new ArrayList<>();

            // TODO: Remove all the multiple database paths stuff. 
            // RJCTODO: Rework this to do a search a bit differently, or simply indicate the file is missing...
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
                // No paths for this entry, the user most likely declined to search for them
                logger.log(Level.WARNING, "No paths were set for hash_set at index {0}. Removing the database.", i);
            } 
            else {
                KnownFilesType typeDBType = KnownFilesType.valueOf(type);
                try {
                    HashDb db = HashDb.openHashDatabase(name, paths.get(0), useForIngestBool, showInboxMessagesBool, typeDBType);
                    if (typeDBType == KnownFilesType.NSRL) {
                        setNSRLSet(db);
                    }
                    else {
                        addKnownBadSet(db);
                    }
                }
                catch (TskCoreException ex) {
                    Logger.getLogger(HashDbXML.class.getName()).log(Level.SEVERE, "Error opening hash database", ex);                
                    JOptionPane.showMessageDialog(null, "Unable to open " + paths.get(0) + " hash database.", "Open Hash Database Error", JOptionPane.ERROR_MESSAGE);
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
            alwaysCalculateHashes = Boolean.parseBoolean(value);
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
        FileNameExtensionFilter filter = new FileNameExtensionFilter("Hash Database File", EXTENSION);
        fc.setFileFilter(filter);
        fc.setMultiSelectionEnabled(false);

        String filePath = null;
        if (fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            try {
                filePath = f.getCanonicalPath();
            } 
            catch (IOException ex) {
                logger.log(Level.WARNING, "Couldn't get selected file path", ex);
            } 
        }
        
        return filePath;
    }

    private boolean setsFileExists() {
        File f = new File(xmlFile);
        return f.exists() && f.canRead() && f.canWrite();
    }    
    
    /**
     * Closes all open hash databases.
     * @throws TskCoreException 
     */
    // TODO: Think about whether this should be exposed, and if so, where it should be exposed.
    // The ability to add to hash databases implies that the databases should generally not be closed
    // until removal or application exit.  
    void closeHashDatabases() throws TskCoreException {
        SleuthkitJNI.closeHashDatabases();
    }            
}
