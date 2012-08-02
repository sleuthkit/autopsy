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
package org.sleuthkit.autopsy.exifparser;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.image.ImageParser;
import org.sleuthkit.autopsy.datamodel.FileTypeExtensions;
import org.sleuthkit.autopsy.ingest.IngestManagerProxy;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestMessage.MessageType;
import org.sleuthkit.autopsy.ingest.IngestServiceAbstractFile;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.TskData.TSK_DB_FILES_TYPE_ENUM;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Example implementation of an image ingest service 
 * 
 */
public final class ExifParserFileIngestService implements IngestServiceAbstractFile {

    private static final Logger logger = Logger.getLogger(ExifParserFileIngestService.class.getName());
    private static ExifParserFileIngestService defaultInstance = null;
    private IngestManagerProxy managerProxy;
    private static int messageId = 0;
    private Tika tika;

    //public constructor is required
    //as multiple instances are created for processing multiple images simultenously
    public ExifParserFileIngestService() {
    }

    //default instance used for service registration
    public static synchronized ExifParserFileIngestService getDefault() {
        if (defaultInstance == null) {
            defaultInstance = new ExifParserFileIngestService();
        }
        return defaultInstance;
    }

    @Override
    public ProcessResult process(AbstractFile content) {
        if(content.getType().equals(TSK_DB_FILES_TYPE_ENUM.FS)) {
            FsContent fsContent = (FsContent) content;
            if(fsContent.isFile()) {
                if(parsableFormat(fsContent)) {
                    return processFile(fsContent);
                }
            }
        }
        
        return ProcessResult.UNKNOWN;
    }
    
    public ProcessResult processFile(FsContent f) {
        InputStream in = null;
        
        try {
            logger.log(Level.INFO, "processing image file " + this.toString());
            
            /*in = new ReadContentInputStream(f);
            BufferedInputStream bin = new BufferedInputStream(in);
            System.out.println("\n-----------" + f.getName() + "-----------\n");
            
            Metadata metadata = ImageMetadataReader.readMetadata(bin, true);
            for (Directory directory : metadata.getDirectories()) {
                for (Tag tag : directory.getTags()) {
                    System.out.println(tag);
                }
            }
            
            bin.close();*/
            org.apache.tika.metadata.Metadata metadata = new org.apache.tika.metadata.Metadata();
            ContentHandler handler = new DefaultHandler();
            Parser parser = new ImageParser();
            ParseContext context = new ParseContext();

            in = new ReadContentInputStream(f);
            String mimeType = tika.detect(in);
            metadata.set(org.apache.tika.metadata.Metadata.CONTENT_TYPE, mimeType);

            parser.parse(in,handler,metadata,context);
            
            System.out.println("-"+f.getName());
            for(int i = 0; i <metadata.names().length; i++) {
                String name = metadata.names()[i];
                System.out.println(name + " : " + metadata.get(name));
            }
            
            
            return ProcessResult.OK;
            
        //} catch (ImageProcessingException ex) {
            //System.out.println("ImageProcessingException: " + ex);
        } catch (SAXException ex) {
            //Logger.getLogger(ExifParserFileIngestService.class.getName()).log(Level.SEVERE, null, ex);
        } catch (TikaException ex) {
            System.out.println("TIKA: " + ex);
            //Logger.getLogger(ExifParserFileIngestService.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            System.out.println("IO: " + ex);
            //logger.log(Level.WARNING, "IO exception when processing image.", ex);
        } finally {
            if(in!=null) {
                try {
                    in.close();
                } catch (IOException ex) {
                    //logger.log(Level.WARNING, "Failed to close InputStream.", ex);
                }
            }
        }
        
        // If we got here, there was an error
        return ProcessResult.ERROR;
    }
    
    private boolean parsableFormat(FsContent f) {
        // Get the name, extension
        String name = f.getName();
        int dotIndex = name.lastIndexOf(".");
        if (dotIndex == -1) {
            return false;
        }
        String ext = name.substring(dotIndex).toLowerCase();
        
        for(String s:FileTypeExtensions.getImageExtensions()) {
            if(ext.equals(s)) { return true; }
        }
        
        return false;
    }

    @Override
    public void complete() {
        logger.log(Level.INFO, "completed image file processing " + this.toString());

        final IngestMessage msg = IngestMessage.createMessage(++messageId, MessageType.INFO, this, "Complete");
        managerProxy.postMessage(msg);

        //service specific cleanup due to completion here
    }

    @Override
    public String getName() {
        return "Ingest Image Files";
    }
    
    @Override
    public String getDescription() {
        return "Ingests all image files and retrieves their metadata";
    }

    @Override
    public void init(IngestManagerProxy managerProxy) {
        logger.log(Level.INFO, "init() " + this.toString());
        this.managerProxy = managerProxy;

        //service specific initialization here
        this.tika = new Tika();

    }

    @Override
    public void stop() {
        logger.log(Level.INFO, "stop()");
        managerProxy.postMessage(IngestMessage.createMessage(++messageId, MessageType.INFO, this, "Stopped"));

        //service specific cleanup due to interruption here
    }

    @Override
    public ServiceType getType() {
        return ServiceType.AbstractFile;
    }

     @Override
    public boolean hasSimpleConfiguration() {
        return false;
    }
    
    @Override
    public boolean hasAdvancedConfiguration() {
        return false;
    }

    @Override
    public javax.swing.JPanel getSimpleConfiguration() {
        return null;
    }
    
    @Override
    public javax.swing.JPanel getAdvancedConfiguration() {
        return null;
    }
    
    @Override
    public boolean hasBackgroundJobsRunning() {
        return false;
    }
    
    @Override
    public void saveAdvancedConfiguration() {
    }
    
    @Override
    public void saveSimpleConfiguration() {
    }
}
