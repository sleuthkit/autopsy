/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013 Basis Technology Corp.
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
package org.sleuthkit.autopsy.scalpel.jni;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.openide.util.NbBundle;
import org.sleuthkit.datamodel.TskFileRange;

/**
 * 
 */
public class ScalpelOutputParser {
    
    private static final String OUTPUT_START = NbBundle.getMessage(ScalpelOutputParser.class,
                                                                   "ScalpelOutputParser.outputStart.text");
    
    public static class CarvedFileMeta {

        private String fileName;
        private TskFileRange byteRange;

        public CarvedFileMeta(String fileName, long byteStart, long byteLength) {
            this.fileName = fileName;
            this.byteRange = new TskFileRange(byteStart, byteLength, 0);
        }

        public String getFileName() {
            return fileName;
        }

        public TskFileRange getTskFileRange() {
            return byteRange;
        }
        
        public long getByteStart() {
            return byteRange.getByteStart();
        }
        
        public long getByteLength() {
            return byteRange.getByteLen();
        }

        @Override
        public String toString() {
            return NbBundle.getMessage(this.getClass(), "ScalpelOutputParser.toString.text",
                                       fileName, byteRange.getByteStart(), byteRange.getByteLen());
        }
    }
    
    public static List<CarvedFileMeta> parse(File scalpelOutputFile) throws FileNotFoundException, IOException {
        
        // create and initialize the list to return
        List<CarvedFileMeta> carvedFileMeta = new ArrayList<CarvedFileMeta>();
        
        // create a BufferedReader
        BufferedReader in = new BufferedReader(new FileReader(scalpelOutputFile));
        
        // creat and initialize a line
        String line = in.readLine();
        
        // read lines until we get to the start of the output section
        while (!line.equals(OUTPUT_START)) {
            line = in.readLine();
        }
        
        // skip the next line which is a table header
        in.readLine();
        
        // read the first line of the table
        line = in.readLine();

        // this begins the carved files section; loop until an empty line is read
        while (!line.isEmpty()) {
            
            // split the line into tokens
            String[] fields = line.split("\\s+"); //NON-NLS
            
            // get the fields of interest
            String fileName = fields[0];
            long byteStart = Long.parseLong(fields[1]);
            long byteLength = Long.parseLong(fields[3]);
            
            // create and add a CarvedFileMeta object to the list
            carvedFileMeta.add(new CarvedFileMeta(fileName, byteStart, byteLength));
            
            // read the next line
            line = in.readLine();
        }
        
        // close the BufferedReader
        in.close();
        
        return carvedFileMeta;
    }
}
