/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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
package solr4indexupgrade;

import java.io.File;
import java.io.IOException;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer; 
import org.apache.lucene.index.IndexUpgrader;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory; 
import org.apache.lucene.store.FSDirectory; 

/**
 * This class upgrades Solr 4 index to Solr 5 index. 
 */
public class Solr4IndexUpgrade {

    /**
     * Upgrades Solr 4 index to Solr 5 index.
     * @param args the command line arguments
     * @throws java.io.IOException
     */
    public static void main(String[] args) throws IOException {
        
        if (args.length != 1) {
            System.out.println("Must pass 1 input argument");
            showUsage();
            throw new IllegalArgumentException("Must pass 1 argument");
        }
        String solr4path = args[0];
        upgrade(solr4path);
    }
    
    /** 
     * Display usage information for JDiff.
     */
    public static void showUsage() {
        System.out.println("usage: java -jar Solr4IndexUpgrade.jar \"\\path\\to\\index\"");
    }
  
    private static void upgrade(String solr4path) throws IOException {
        
        Directory dir = FSDirectory.open(new File(solr4path).toPath());
        
        // upgrade from Solr 4 to Solr 5
        IndexWriterConfig config;
        Analyzer analyzer = new StandardAnalyzer();
        config = new IndexWriterConfig(analyzer);
        //config.setCodec(new Lucene50Codec());
       //IndexWriter writer = new IndexWriter(dir, config);
        IndexUpgrader upgrader = new IndexUpgrader(dir, config, true);
        upgrader.upgrade();
    }
}
