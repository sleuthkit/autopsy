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


/** This module attempts to extract web queries from major search engines
 *  by querying the blackboard for web history and bookmark artifacts, and extracting
 *  search text from them. 
 * 
 * 
 *  Additions to the search engines require editing the following:
 *      SearchEngine enum,
 *      getSearchEngine(),
 *      extractSearchEngineQuery()
 **/
package org.sleuthkit.autopsy.recentactivity;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JPanel;
import org.sleuthkit.autopsy.ingest.IngestImageWorkerController;
import org.sleuthkit.autopsy.ingest.IngestManagerProxy;
import org.sleuthkit.autopsy.ingest.IngestServiceImage;
import org.sleuthkit.autopsy.ingest.ServiceDataEvent;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.Image;



public class SearchEngineURLQueryAnalyzer extends Extract implements IngestServiceImage{
    static final String moduleName = "SEUQA";



    /** The record of supported engines**/
    private static enum SearchEngine {
        NONE("None"), 
        Google("Google"), 
        Bing("Bing"), 
        Yahoo("Yahoo"), 
        Baidu("Baidu"), 
        Sogou("Sogou"), 
        Soso("Soso"), 
        Yandex("Yandex"), 
        Youdao("Youdao"), 
        Biglobe("Biglobe"), 
        Linkestan("Linkestan"), 
        Parseek("Parseek"), 
        Parset("Parset");
        
        private final String name;
        private int total = 0;
        
        SearchEngine(String name){
            this.name = name;
        }
        
        private int getTotal(){
            return total;
        }
        private void increment(){
            ++total;
        }
        private String getName(){
            return name;
        }
    };

           
    SearchEngineURLQueryAnalyzer(){

}
    
    
    /** 
     * Returns which of the supported SearchEngines, if any, the given string belongs to. 
     * 
     * @param domain the URL string to be determined
     **/
    private static SearchEngine getSearchEngine(String domain){
        if(domain.contains(".com")){
            String[] d = domain.split(".com");
            if(d.length != 0 && d[0].contains(".baidu")){
                return SearchEngine.Baidu;
            }
            else if(d.length != 0 && d.length != 0 && d[0].contains(".bing")){
                return SearchEngine.Bing;
            }
            else if(d.length != 0 && d[0].contains(".yahoo")){
                return SearchEngine.Yahoo;
            }
            else if(d.length != 0 && d[0].contains(".google")){
                return SearchEngine.Google;
            }
            else if(d.length != 0 && d[0].contains(".youdao")){
                return SearchEngine.Youdao;
            }
            else if(d.length !=0 && d[0].contains(".soso.com")){
                return SearchEngine.Soso;
            }
            else if(d.length !=0 && d[0].contains(".sogou.com")){
                return SearchEngine.Sogou;
            }
            else if(d.length != 0 && d[0].contains(".linkestan.com")){
                return SearchEngine.Linkestan;
            }
            else if(d.length != 0 && d[0].contains(".parseek.com")){
                return SearchEngine.Parseek;
            }
            else if(d.length !=0 && d[0].contains(".parset.com")){
                return SearchEngine.Parset;
            }
        }
        else if (domain.contains(".ru")){
            String[] d = domain.split(".ru");
            if(d[0].contains("yandex")){
                return SearchEngine.Yandex;
            }
        }
        else if (domain.contains(".ne.jp")){
            String[] d = domain.split(".ne.jp");
            if(d[0].contains("biglobe")){
                return SearchEngine.Biglobe;
            }
        }
      return SearchEngine.NONE;
    }
        
   /**
    * Attempts to extract the query from a URL.
    * @param se SearchEngine, used to determine format of search query. 
    * @param url The URL string to be dissected.
    * @return The extracted search query.
    */
    private String extractSearchEngineQuery(SearchEngine se, String url){
        String x = "";

//English Search Engines        
        
        //google.com
        if(se.equals(SearchEngine.Google)){
            if(url.contains("?q=")){
                x = split2(url, "\\?q=");
            }
            else {
                x = split2(url, "&q=");
            }
        }

        //yahoo.com
        else if(se.equals(SearchEngine.Yahoo)){
            x = split2(url, "\\?p=");
        }
        
        //bing.com
        else if (se.equals(SearchEngine.Bing)){
            x = split2(url, "\\?q=");
        }
        
//Chinese Search Engines
        
        //baidu.com
        else if (se.equals(SearchEngine.Baidu)){
            if(url.contains("?wd=")){
                x = split2(url, "\\?wd=");
            }
            else if(url.contains("?kw=")){
                x = split2(url, "\\?kw=");
            }
            else if(url.contains("baidu.com/q?") || url.contains("baidu.com/m?") || url.contains("baidu.com/i?")){
                x = split2(url, "word=");
            }
            else if (url.contains("/qw=") || url.contains("?qw=")){
                x = split2(url, "\\qw=");
            }
            else if (url.contains("bs=")){
                x = split2(url, "&bs=");
            }
        }
        
        //sogou.com
        else if(se.equals(SearchEngine.Sogou)){
            x = split2(url, "query=");
        }
        
        //Soso.com
        else if (se.equals(SearchEngine.Soso)){
            if(url.contains("p=S")){
                x = split2(url, "p=S");
            }
            else if (url.contains("?w=")){
                x = split2(url, "\\?w=");
            }
            else {
                x = split2(url, "&w=");
            }
            
            
        }

        //youdao.com
        else if(se.equals(SearchEngine.Youdao)){
            if(url.contains("search?q=")){
                x = split2(url, "\\?q=");
            }
            else if (url.contains("?i=")){
                x = split2(url, "\\?i=");
            }
        }
  
 //Russian Search Engines
        
        //yandex.ru
        else if(se.equals(SearchEngine.Yandex)){
            if(url.contains("?text=")){
            x = split2(url, "\\?text=");
            }
            else{
                x = split2(url, "&text=");
            }
        }
        
 //Japanese Search Engines       
      
        //biglobe.ne.jp
        else if(se.equals(SearchEngine.Biglobe)){
            if(url.contains("?search=")){
                x = split2(url, "\\?search=");
            }
            else if(url.contains("?q=")){
                x = split2(url, "\\?q=");
            }
            else if(url.contains("/key/")){
                x = split2(url, "/key/");
            }
            
            else if (url.contains("&q=")){
                x = split2(url, "&q=");
            }
        }
 
//Persian & Arabic Search Engines        
        
        //Linkestan.com
        else if(se.equals(SearchEngine.Linkestan)){
            x = split2(url, "\\?psearch=");
        }
        
        //Parseek.com
        else if(se.equals(SearchEngine.Parseek)){
            x = split2(url, "\\?q=");
        }
        
        //Parset.com
        else if(se.equals(SearchEngine.Parset)){
            x = split2(url, "\\?Keyword=");
        }
        
        try{ //try to decode the url
        String decoded = URLDecoder.decode(x, "UTF-8");
            return decoded;
        }
        catch(UnsupportedEncodingException uee){ //if it fails, return the encoded string
            logger.info("Error during URL decoding: " + uee);
            return x;
        }
  
    }

    
/**
 * Splits URLs based on a delimeter (key). .contains() and .split() 
 * @param url The URL to be split
 * @param splitkey the delimeter used to split the URL into its search query.
 * @return The extracted search query
 **/
    private String split2(String url, String splitkey){
        String basereturn = "NULL";
        String splitKeyConverted = splitkey;
        //Want to determine if string contains a string based on splitkey, but we want to split the string on splitKeyConverted due to regex
        if(splitkey.contains("\\?")){
            splitKeyConverted = splitkey.replace("\\?", "?"); //Handling java -> regex conversions and viceversa
        }
        if (url.contains(splitKeyConverted)){
            String[] sp = url.split(splitkey);
            if(sp.length >= 2){
                if(sp[sp.length -1].contains("&")){
                    basereturn = sp[sp.length -1].split("&")[0];
                    
                }
                else{
                    basereturn = sp[sp.length -1];
                }
            }
        }
        return basereturn;
    }
   
    
    
    private void getURLs(Image image, IngestImageWorkerController controller){
        int totalQueries = 0;
       try{ 
            //from blackboard_artifacts
            ArrayList<BlackboardArtifact> listArtifacts = currentCase.getSleuthkitCase().getMatchingArtifacts
                    ("WHERE (`artifact_type_id` = '" + ARTIFACT_TYPE.TSK_WEB_BOOKMARK.getTypeID() 
                    +"' OR `artifact_type_id` = '"+ ARTIFACT_TYPE.TSK_WEB_HISTORY.getTypeID() +"') ");  //List of every 'web_history' and 'bookmark' artifact
            logger.info("Processing " + listArtifacts.size() + " blackboard artifacts.");
       getAll:    
        for(BlackboardArtifact artifact : listArtifacts){
            //initializing default attributes
            String source = ""; //becomes "bookmark" if attribute type 2, remains blank otherwise
            String query = "";  
            String domain = ""; 
            String browser = ""; 
            long last_accessed = -1; 
            //from tsk_files
            FsContent fs = this.extractFiles(image, "select * from tsk_files where `obj_id` = '" + artifact.getObjectID() + "'").get(0); //associated file
            SearchEngine se = SearchEngine.NONE;
            //from blackboard_attributes
            ArrayList<BlackboardAttribute> listAttributes = currentCase.getSleuthkitCase().getMatchingAttributes("Where `artifact_id` = " + artifact.getArtifactID());
            getAttributes:
            for(BlackboardAttribute attribute : listAttributes){
                if(controller.isCancelled()){   
                    break getAll;       //User cancled the process.
                }
                if(attribute.getAttributeTypeID() == 1){
                    se = getSearchEngine(attribute.getValueString());
                    if(! se.equals(SearchEngine.NONE)){ 
                        query = extractSearchEngineQuery(se, attribute.getValueString());
                        domain = se.toString();
                        if(query.equals("NULL")){   //False positive match, artifact was not a query.
                            break getAttributes;
                        }
                    }
                    else if(se.equals(SearchEngine.NONE)){
                        break getAttributes;    //could not determine type. Will move onto next artifact
                    }
                }
                else if(attribute.getAttributeTypeID() == 4){
                    browser = attribute.getValueString();
                }
                else if(attribute.getArtifactID() == 2){
                    source = "bookmark";
                }
                else if(attribute.getAttributeTypeID() == 33){
                    last_accessed = attribute.getValueLong();
                }
            }
            
            if(!se.equals(SearchEngine.NONE) && !query.equals("NULL")){
                try{
                        Collection<BlackboardAttribute> bbattributes = new ArrayList<BlackboardAttribute>();
                        bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN.getTypeID(), "SEUQA", "", domain));
                        bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_TEXT.getTypeID(), "SEUQA", "", query));
                        bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(), "SEUQA", source, browser));
                        bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_LAST_ACCESSED.getTypeID(), "SEUQA", "", last_accessed));
                        this.addArtifact(ARTIFACT_TYPE.TSK_WEB_SEARCH_QUERY, fs , bbattributes);
                        se.increment();
                        ++totalQueries;
            }
                catch(Exception e){
                    logger.log(Level.SEVERE, "Error while add artifact.", e + " from " + fs.toString());
                    this.addErrorMessage(this.getName() + ": Error while adding artifact");
                }
                IngestManagerProxy.fireServiceDataEvent(new ServiceDataEvent("RecentActivity", BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_SEARCH_QUERY));
            }
            
            
        }
        }
        catch (Exception e){
            logger.info("Encountered error retrieving artifacts: " + e);
        }
       finally{
           if(controller.isCancelled()){
               logger.info("Operation terminated by user.");
           }
           logger.info("Extracted " + totalQueries + " queries from the blackboard");
       }
    }
    
    private String getTotals() {
        String total = "";
       for(SearchEngine se : SearchEngine.values()){
           if(se.getTotal() != 0){
               total += se.getName() + ": " + se.getTotal() + "\n";
           }
       }
       return total;
    }
            
    @Override
    public void process(Image image, IngestImageWorkerController controller) {
        this.getURLs(image, controller);
        logger.info("Search Engine stats: \n" + getTotals());
    }

    @Override
    public void init(IngestManagerProxy managerProxy) {
       logger.info("running init()");
    }

    @Override
    public void complete() {
        logger.info("running complete()");
    }

    @Override
    public void stop() {
        logger.info("running stop()");
    }

    @Override
    public String getName() {
        return this.moduleName;
    }

    @Override
    public String getDescription() {
         SearchEngine[] values = SearchEngine.values();
         String total = "";
         int i = 0;
         while(i < values.length){  //could alternatively just forbid values[0], but that's kind of volatile.
             if (values[i] != SearchEngine.NONE){
                 total += values[i].getName() + "\n";
             }
             i++;
         }
             
        return "Extracts search queries on the following search engines: " + total;
       
    }

    @Override
    public ServiceType getType() {
        return ServiceType.Image;
    }

    @Override
    public boolean hasBackgroundJobsRunning() {
        return false;
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
    public void saveSimpleConfiguration() {
       
    }

    @Override
    public void saveAdvancedConfiguration() {
       
    }

    @Override
    public JPanel getSimpleConfiguration() {
        return null;
    }

    @Override
    public JPanel getAdvancedConfiguration() {
        return null;
    }
    
    
    
}
