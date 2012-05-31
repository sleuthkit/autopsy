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


package org.sleuthkit.autopsy.keywordsearch;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.sleuthkit.autopsy.datamodel.AbstractFileStringStream;
import org.sleuthkit.autopsy.keywordsearch.Ingester.IngesterException;
import org.sleuthkit.datamodel.AbstractFile;


/**
 * Utility to extract and index a file as file chunks
 */
public class FileExtract {
    
    private int numChunks;
    public static final long MAX_CHUNK_SIZE = 10 * 1024 * 1024L;
    private static final Logger logger = Logger.getLogger(FileExtract.class.getName());
    private static final long MAX_STRING_CHUNK_SIZE = 1 * 1024 * 1024L;
    private AbstractFile sourceFile;
    
    //single static buffer for all extractions.  Safe, indexing can only happen in one thread
    private static final byte[] STRING_CHUNK_BUF = new byte[(int) MAX_STRING_CHUNK_SIZE];
    private static final int BOM_LEN = 3;
    static {
        //prepend UTF-8 BOM to start of the buffer
            STRING_CHUNK_BUF[0] = (byte)0xEF;
            STRING_CHUNK_BUF[1] = (byte)0xBB;
            STRING_CHUNK_BUF[2] = (byte)0xBF;
    }
    
    public FileExtract(AbstractFile sourceFile) {
        this.sourceFile = sourceFile;
        numChunks = 0; //unknown until indexing is done
    }
    
    public int getNumChunks() {
        return this.numChunks;
    }
    
    public AbstractFile getSourceFile() {
        return sourceFile;
    }
    
    
    public boolean index(Ingester ingester) throws IngesterException {
        boolean success = false;

        AbstractFileStringStream stringStream = null;
        try {
            success = true;
            //break string into chunks 
            //Note: could use DataConversion.toString() since we are operating on fixed chunks
            //but FsContentStringStream handles string boundary case better
            stringStream = new AbstractFileStringStream(sourceFile, AbstractFileStringStream.Encoding.UTF8);
            long readSize = 0;
            
            while ((readSize = stringStream.read(STRING_CHUNK_BUF, BOM_LEN, (int) MAX_STRING_CHUNK_SIZE - BOM_LEN)) != -1) {
                //FileOutputStream debug = new FileOutputStream("c:\\temp\\" + sourceFile.getName() + Integer.toString(this.numChunks+1));
                //debug.write(STRING_CHUNK_BUF, 0, (int)readSize);
                
                FileExtractedChild chunk = new FileExtractedChild(this, this.numChunks + 1);
                
                try {
                    chunk.index(ingester, STRING_CHUNK_BUF, readSize + BOM_LEN);
                    ++this.numChunks;
                } catch (IngesterException ingEx) {
                    success = false;
                    logger.log(Level.WARNING, "Ingester had a problem with extracted strings from file '" + sourceFile.getName() + "' (id: " + sourceFile.getId() + ").", ingEx);   
                    throw ingEx; //need to rethrow/return to signal error and move on
                } 
                //debug.close();    
            }
            
            
            //after all chunks, ingest the parent file without content itself, and store numChunks
            ingester.ingest(this);

        } catch (IOException ex) {
            logger.log(Level.WARNING, "Unable to read string stream and send to Solr, file: " + sourceFile.getName(), ex);
            success = false;
        } finally {
            if (stringStream != null) {
                try {
                    stringStream.close();
                } catch (IOException ex) {
                    logger.log(Level.WARNING, "Error closing string stream, file: " + sourceFile.getName(), ex);
                }
            }
        }
        
        
        return success;
    }
}
/**
 * Represents each string chunk, a child of FileExtracted file
 */
class FileExtractedChild {
    
    private int chunkID;
    private FileExtract parent;
    
    FileExtractedChild(FileExtract parent, int chunkID) {
        this.parent = parent;
        this.chunkID = chunkID;
    }
    
    public FileExtract getParentFile() {
        return parent;
    }
    
    public int getChunkId() {
        return chunkID;
    }
    
    /**
     * return String representation of the absolute id (parent and child)
     * @return 
     */
    public String getIdString() {
        return getFileExtractChildId(this.parent.getSourceFile().getId(), this.chunkID);
    }
    
    
    public boolean index(Ingester ingester, byte[] content, long contentSize) throws IngesterException {
        boolean success = true;
        ByteContentStream bcs = new ByteContentStream(content, contentSize, parent.getSourceFile(), AbstractFileStringStream.Encoding.UTF8);
        try {
            ingester.ingest(this, bcs);
            //logger.log(Level.INFO, "Ingesting string chunk: " + this.getName() + ": " + chunkID);
            
        } catch (Exception ingEx) {
            success = false;
            throw new IngesterException("Problem ingesting file string chunk: " + parent.getSourceFile().getId() + ", chunk: " + chunkID, ingEx);
        }
        return success;
    }
    
    public static String getFileExtractChildId(long parentID, int childID) {
        return Long.toString(parentID) + "_" + Integer.toString(childID);
    }
}
