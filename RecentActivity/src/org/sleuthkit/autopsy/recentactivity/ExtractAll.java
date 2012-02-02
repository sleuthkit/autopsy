/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.recentactivity;

import org.sleuthkit.autopsy.ingest.IngestImageWorkerController;

/**
 *
 * @author Alex
 */
public class ExtractAll {
    
       void ExtractAll(){
            
        }
       
       public boolean extractToBlackboard(IngestImageWorkerController controller){
           controller.switchToDeterminate(3);
           try{
               // Will make registry entries later, comment out for DEMO ONLY
               // ExtractRegistry eree = new ExtractRegistry();
                //eree.getregistryfiles();
               controller.switchToDeterminate(3);
               
                Firefox ffre = new Firefox();
                ffre.getffdb();  
                controller.progress(1);
                if (controller.isCancelled())
                    return true;
                
                Chrome chre = new Chrome();
                chre.getchdb();
                controller.progress(2);
                if (controller.isCancelled())
                    return true;
                
                ExtractIE eere = new ExtractIE();
                eere.parsePascoResults();
                controller.progress(3);
                if (controller.isCancelled())
                    return true;
                //Find a way to put these results into BB
               
                return true;
           }
           catch(Error e){
               return false;
           }
          
       }
        public int getExtractCount(){
           int count = 0;
           try{
               // Will make registry entries later, comment out for DEMO ONLY
               // ExtractRegistry eree = new ExtractRegistry();
                //eree.getregistryfiles();
                Firefox ffre = new Firefox();
                ffre.getffdb();
                count = count + ffre.FireFoxCount;
                Chrome chre = new Chrome();
                chre.getchdb();
                count = count + chre.ChromeCount;
                
               ExtractIE eere = new ExtractIE();
               eere.parsePascoResults();
               count = count + eere.PASCO_RESULTS_LIST.size();
                //Find a way to put these results into BB
              //  ArrayList<HashMap<String,Object>> IEresults = eere.PASCO_RESULTS_LIST; 
                return count;
           }
           catch(Error e){
               return 0;
           }
          
       }
}
