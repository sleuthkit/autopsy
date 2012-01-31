/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.recentactivity;

import java.util.ArrayList;
import java.util.HashMap;

/**
 *
 * @author Alex
 */
public class ExtractAll {
    
       void ExtractAll(){
            
        }
       
       public boolean extractToBlackboard(){
           
           try{
               // ExtractRegistry eree = new ExtractRegistry();
                //eree.getregistryfiles();
                Firefox ffre = new Firefox();
                ffre.getffdb();    
                Chrome chre = new Chrome();
                chre.getchdb();
               // ExtractIE eere = new ExtractIE();
               // eere.parsePascoResults();
                //Find a way to put these results into BB
              //  ArrayList<HashMap<String,Object>> IEresults = eere.PASCO_RESULTS_LIST; 
                return true;
           }
           catch(Error e){
               return false;
           }
          
       }
}
