/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2013 Basis Technology Corp.
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
package org.sleuthkit.autopsy.thunderbirdparser;

import com.pff.PSTException;
import com.pff.PSTFile;
import com.pff.PSTFolder;
import com.pff.PSTMessage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Parser for extracting emails from  pst/ost Mircosoft Outlook data files.
 * 
 * @author jwallace
 */
public class PstParser {
    private static final Logger logger = Logger.getLogger(PstParser.class.getName());
    private static int PST_HEADER = 0x2142444E;
    
    /**
     * A map of PSTMessages to their Local path within the file's internal 
     * directory structure.
     */
    private Map<PSTMessage, String> results;
    
    PstParser() {
        results = new HashMap<>();
    }
    
    enum ParseResult {
        OK, ERROR, ENCRYPT;
    }
    
    /**
     * Parse and extract email messages from the pst/ost file.
     * 
     * @param file A pst or ost file.
     * @return ParseResult: OK on success, ERROR on an error, ENCRYPT if failed because the file is encrypted.
     */
    public ParseResult parse(File file) {
        PSTFile pstFile;
        try {
            pstFile = new PSTFile(file);
            processFolder(pstFile.getRootFolder(), "\\", true);
            return ParseResult.OK;
        } catch (PSTException | IOException ex) {
            String msg = file.getName() + ": Failed to create internal java-libpst PST file to parse:\n" + ex.getMessage();
            logger.log(Level.WARNING, msg);
            return ParseResult.ERROR;
        } catch (IllegalArgumentException ex) {
            logger.log(Level.INFO, "Found encrypted PST file.");
            return ParseResult.ENCRYPT;
        }
    }
    
    /**
     * Get the results of the parsing.
     * 
     * @return 
     */
    public Map<PSTMessage, String> getResults() {
        return results;
    }

    /**
     * Process this folder and all subfolders, adding every email found to results.
     * Accumulates the folder hierarchy path as it navigates the folder structure.
     * 
     * @param folder The folder to navigate and process
     * @param path The path to the folder within the pst/ost file's directory structure
     * @throws PSTException
     * @throws IOException 
     */
    private void processFolder(PSTFolder folder, String path, boolean root) {
        String newPath =  (root ? path : path + "\\" + folder.getDisplayName());
        
        if (folder.hasSubfolders()) {
            List<PSTFolder> subFolders;
            try {
                subFolders = folder.getSubFolders();
            } catch (PSTException | IOException ex) {
                subFolders = Collections.EMPTY_LIST;
                logger.log(Level.INFO, "java-libpst exception while getting subfolders: " + ex.getMessage());
            }
            
            for (PSTFolder f : subFolders) {
                processFolder(f, newPath, false);
            }
        }
        
        if (folder.getContentCount() != 0) {
            PSTMessage email;
            // A folder's children are always emails, never other folders.
            try {
                while ((email = (PSTMessage) folder.getNextChild()) != null) {
                    results.put(email, newPath);
                }
            } catch (PSTException | IOException ex) {
                logger.log(Level.INFO, "java-libpst exception while getting emails from a folder: " + ex.getMessage());
            }
        }
    }
    
    /**
     * Identify a file as a pst/ost file by it's header.
     * 
     * @param file
     * @return 
     */
    public static boolean isPstFile(AbstractFile file) {
        byte[] buffer = new byte[4];
        try {
            int read = file.read(buffer, 0, 4);
            if (read != 4) {
                return false;
            }
            ByteBuffer bb = ByteBuffer.wrap(buffer);
            return  bb.getInt() == PST_HEADER;
        } catch (TskCoreException ex) {
            System.out.println("Exception");
            return false;
        }
    }
}
