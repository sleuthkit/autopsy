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
package solr5indexupgrade;

import java.io.File;
import java.io.IOException;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.UpgradeIndexMergePolicy;
import org.apache.lucene.store.Directory; 
import org.apache.lucene.store.FSDirectory; 

/**
 * This class upgrades Solr 5 index to Solr 6 index. 
 */
public class Solr5IndexUpgrade {

    /**
     * Upgrades Solr 5 index to Solr 6 index.
     * @param args the command line arguments
     * @throws java.io.IOException
     */
    public static void main(String[] args) throws IOException {
        
        if (args.length != 1) {
            System.out.println("Must pass 1 argument");
            showUsage();
            throw new IllegalArgumentException("Must pass 1 argument");
        }
        String solr5path = args[0];
        upgrade(solr5path);
    }
    
    /** 
     * Display usage information for JDiff.
     */
    public static void showUsage() {
        System.out.println("usage: java -jar Solr5IndexUpgrade.jar \"\\path\\to\\index\"");
    }
  
    private static void upgrade(String solr4path) throws IOException {
        
        Directory dir = FSDirectory.open(new File(solr4path).toPath());
        

        // upgrade from Solr 5 to Solr 6        
        IndexWriterConfig iwc = new IndexWriterConfig(new KeywordAnalyzer());
        iwc.setMergePolicy(new UpgradeIndexMergePolicy(iwc.getMergePolicy()));
        IndexWriter w = new IndexWriter(dir, iwc);
        w.forceMerge(1);
        w.close();
    }
}
