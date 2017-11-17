/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 - 2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.hashdatabase;

import java.io.InputStream;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.StringBuilder;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.logging.Level;
import javax.swing.JOptionPane;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.core.RuntimeProperties;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.TskCoreException;

class EncaseHashSetParser {
    final byte[] encaseHeader = {(byte)0x48, (byte)0x41, (byte)0x53, (byte)0x48, (byte)0x0d, (byte)0x0a, (byte)0xff, (byte)0x00,
                                 (byte)0x02, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x01, (byte)0x00, (byte)0x00, (byte)0x00};
    InputStream inputStream;
    final int expectedHashes;
    int totalHashesRead = 0;
    
    /**
     * Opens the import file and parses the header.
     * @param filename The Encase hashset
     * @throws TskCoreException There was an error opening/reading the file or it is not the correct format
     */
    @NbBundle.Messages({"EncaseHashSetParser.fileOpenError.text=Error reading import file",
        "EncaseHashSetParser.wrongFormat.text=Hashset is not Encase format"})
    EncaseHashSetParser(String filename) throws TskCoreException{
        try{
            inputStream = new BufferedInputStream(new FileInputStream(filename));
            
            // Read in and test the 16 byte header
            byte[] header = new byte[16];
            readBuffer(header, 16);
            if(! Arrays.equals(header, encaseHeader)){
                displayError(NbBundle.getMessage(this.getClass(),
                            "EncaseHashSetParser.wrongFormat.text"));
                close();
                throw new TskCoreException("File " + filename + " does not have an Encase header");
            }
            
            // Read in the expected number of hashes
            byte[] sizeBuffer = new byte[4];
            readBuffer(sizeBuffer, 4);
            expectedHashes = ((sizeBuffer[3] & 0xff) << 24) | ((sizeBuffer[2] & 0xff) << 16)
                            | ((sizeBuffer[1] & 0xff) << 8) | (sizeBuffer[0] & 0xff);
            
            // Read in a bunch of nulls
            byte[] filler = new byte[0x3f4];
            readBuffer(filler, 0x3f4);
            
            // Read in the hash set name
            byte[] nameBuffer = new byte[0x50];
            readBuffer(nameBuffer, 0x50);
            
            // Read in the hash set type
            byte[] typeBuffer = new byte[0x28];
            readBuffer(typeBuffer, 0x28);   
            
        } catch (IOException ex){
            displayError(NbBundle.getMessage(this.getClass(),
                            "EncaseHashSetParser.fileOpenError.text"));
            close();
            throw new TskCoreException("Error reading " + filename, ex);
        } catch (TskCoreException ex){
            close();
            throw ex;
        }
    }
    
    int getExpectedHashes(){
        return expectedHashes;
    }
    
    synchronized boolean doneReading(){
        if(inputStream == null){
            return true;
        }
        
        return(totalHashesRead >= expectedHashes);
    }
    
    synchronized String getNextHash() throws TskCoreException{
        if(inputStream == null){
            return null;
        }
        
        byte[] hashBytes = new byte[16];
        byte[] divider = new byte[2];
        try{

            readBuffer(hashBytes, 16);
            readBuffer(divider, 2);

            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }

            totalHashesRead++;
            return sb.toString();
        } catch (IOException ex){
            // Log it and return what we've got
            Logger.getLogger(EncaseHashSetParser.class.getName()).log(Level.SEVERE, "Ran out of data while reading Encase hash sets", ex);
            close();
            throw new TskCoreException("Error reading hash", ex);
        }
    }
    
    synchronized final void close(){
        if(inputStream != null){
            try{
                inputStream.close();
            } catch (IOException ex){
                Logger.getLogger(EncaseHashSetParser.class.getName()).log(Level.SEVERE, "Error closing Encase hash set", ex);
            } finally {
                inputStream = null;
            }
        }
    }
    
    @NbBundle.Messages({"EncaseHashSetParser.outOfData.text=Ran out of data while parsing file"})
    private synchronized void readBuffer(byte[] buffer, int length) throws TskCoreException, IOException {
        if(inputStream == null){
            throw new TskCoreException("readBuffer called on null inputStream");
        }
        if(length != inputStream.read(buffer)){
            displayError(NbBundle.getMessage(this.getClass(),
                        "EncaseHashSetParser.outOfData.text"));
            close();
            throw new TskCoreException("Ran out of data while parsing Encase file");
        }
    }
    
    @NbBundle.Messages({"EncaseHashSetParser.error.title=Error importing Encase hashset"})
    private void displayError(String errorText){
        if(RuntimeProperties.runningWithGUI()){
            JOptionPane.showMessageDialog(null,
                        errorText,
                        NbBundle.getMessage(this.getClass(),
                                "EncaseHashSetParser.error.title"),
                        JOptionPane.ERROR_MESSAGE);
        }
    }
}
