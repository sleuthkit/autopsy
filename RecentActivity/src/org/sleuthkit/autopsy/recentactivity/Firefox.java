/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.recentactivity;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import java.sql.*;
import java.util.logging.Level;
import java.util.logging.Logger;
//<editor-fold defaultstate="collapsed" desc="comment">
import java.lang.*;
//</editor-fold>
import java.util.*;
import java.io.File;
import java.io.IOException;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
/**
 *
 * @author Alex
 */
public class Firefox {

    private static final String ffquery = "SELECT moz_historyvisits.id,url,title,visit_count, datetime(moz_historyvisits.visit_date/1000000,'unixepoch','localtime') as visit_date,from_visit FROM moz_places, moz_historyvisits WHERE moz_places.id = moz_historyvisits.place_id AND hidden = 0";
    private static final String ffcookiequery = "SELECT name,value,host,expiry,datetime(moz_cookies.lastAccessed/1000000,'unixepoch','localtime') as lastAccessed,creationTime FROM moz_cookies";
    private static final String ffbookmarkquery = "SELECT fk, moz_bookmarks.title, url FROM moz_bookmarks INNER JOIN moz_places ON moz_bookmarks.fk=moz_places.id";
    
    public ArrayList<HashMap> als = new ArrayList<HashMap>();
    public Logger logger = Logger.getLogger(this.getClass().getName());
    public ArrayList<HashMap> cookies = new ArrayList<HashMap>();
    public ArrayList<HashMap> bookmarks = new ArrayList<HashMap>();
    public int FireFoxCount = 0;
       
   public Firefox(){
       
   }

       public void getffdb(){
         //Make these seperate, this is for history
        try 
        {   
            Case currentCase = Case.getCurrentCase(); // get the most updated case
            SleuthkitCase tempDb = currentCase.getSleuthkitCase();
            List<FsContent> FFSqlitedb;  
            ResultSet rs = tempDb.runQuery("select * from tsk_files where name LIKE '%places.sqlite%' and parent_path LIKE '%Firefox%'");
            FFSqlitedb = tempDb.resultSetToFsContents(rs);
                    FireFoxCount = FFSqlitedb.size();
            int j = 0;
     
            while (j < FFSqlitedb.size())
            {
                String temps = currentCase.getTempDirectory() + "\\" + FFSqlitedb.get(j).getName().toString() + j + ".db";
                String connectionString = "jdbc:sqlite:" + temps;
                ContentUtils.writeToFile(FFSqlitedb.get(j), new File(currentCase.getTempDirectory() + "\\" + FFSqlitedb.get(j).getName().toString() + j + ".db"));
                File dbFile = new File(temps);
                 
                
                try
                {
                   
                   dbconnect tempdbconnect = new dbconnect("org.sqlite.JDBC",connectionString);
                   ResultSet temprs = tempdbconnect.executeQry(ffquery);  
                   while(temprs.next()) 
                   {
                      BlackboardArtifact bbart = FFSqlitedb.get(j).newArtifact(ARTIFACT_TYPE.TSK_WEB_HISTORY);
                      HashMap<String, Object> kvs = new HashMap<String, Object>();
                      kvs.put("Url", temprs.getString("url"));
                      kvs.put("Title", ((temprs.getString("title") != null) ? temprs.getString("title") : "No Title"));
                      kvs.put("Count", temprs.getString("visit_count"));
                      kvs.put("Last Accessed", temprs.getString("visit_date"));
                      kvs.put("Reference", temprs.getString("from_visit"));
                      
                      BlackboardAttribute bbatturl = new BlackboardAttribute(1,"RecentActivity","FireFox",temprs.getString("url"));
                      bbart.addAttribute(bbatturl);
                       BlackboardAttribute bbattdate = new BlackboardAttribute(31,"RecentActivity","FireFox",temprs.getString("visit_date"));
                      bbart.addAttribute(bbattdate);
                       BlackboardAttribute bbattref = new BlackboardAttribute(32,"RecentActivity","FireFox",temprs.getString("from_visit"));
                      bbart.addAttribute(bbattref);
                       BlackboardAttribute bbatttitle = new BlackboardAttribute(3,"RecentActivity","FireFox",((temprs.getString("title") != null) ? temprs.getString("title") : "No Title"));
                      bbart.addAttribute(bbatturl);
                      //bbart.addAttribute(ATTRIBUTE_TYPE.TSK_URL, temprs.getString("url"), "RecentActivity","FireFox");
                      //bbart.addAttribute(ATTRIBUTE_TYPE.TSK_DATETIME, temprs.getString("visit_date"), "RecentActivity","FireFox");
                      //bbart.addAttribute(ATTRIBUTE_TYPE.TSK_REFERRER, temprs.getString("from_visit"), "RecentActivity","FireFox");
                      //bbart.addAttribute(ATTRIBUTE_TYPE.TSK_NAME, ((temprs.getString("title") != null) ? temprs.getString("title") : "No Title"), "RecentActivity","FireFox");
                      als.add(kvs);
                      
                   }
                   temprs.close(); 
                   ResultSet tempbm = tempdbconnect.executeQry(ffbookmarkquery);  
                   while(tempbm.next()) 
                   {
                      BlackboardArtifact bbart = FFSqlitedb.get(j).newArtifact(ARTIFACT_TYPE.TSK_WEB_BOOKMARK);
                      HashMap<String, Object> kvs = new HashMap<String, Object>();
                      kvs.put("Url", temprs.getString("url"));
                      kvs.put("Title", ((temprs.getString("title") != null) ? temprs.getString("title") : "No Title"));
                      kvs.put("Count", "");
                      kvs.put("Last Accessed", "");
                      kvs.put("Reference", "");   
                      BlackboardAttribute bbatturl = new BlackboardAttribute(5, ((temprs.getString("url") != null) ? temprs.getString("url") : "No URL"), "RecentActivity","FireFox");
                      bbart.addAttribute(bbatturl);
                      BlackboardAttribute bbatttitle = new BlackboardAttribute(3, ((temprs.getString("title") != null) ? temprs.getString("title").replaceAll("'", "''") : "No Title"), "RecentActivity","FireFox");
                      bbart.addAttribute(bbatttitle);
                     
                      bookmarks.add(kvs);
                      
                   } 
                   tempbm.close();
                   tempdbconnect.closeConnection();
                   
 
                 }
                 catch (Exception ex)
                 {
                    logger.log(Level.WARNING, "Error while trying to read into a sqlite db." + connectionString, ex);      
                 }
                j++;
                dbFile.delete();
            }
        }
        catch (SQLException ex) 
        {
           logger.log(Level.WARNING, "Error while trying to get Firefox SQLite db.", ex);
        }
        catch(IOException ioex)
        {   
            logger.log(Level.WARNING, "Error while trying to write to the file system.", ioex);
        }
        
        
        //COOKIES section
          // This gets the cookie info
         try 
        {   
            Case currentCase = Case.getCurrentCase(); // get the most updated case
            SleuthkitCase tempDb = currentCase.getSleuthkitCase();
            List<FsContent> FFSqlitedb;  
            ResultSet rs = tempDb.runQuery("select * from tsk_files where name LIKE '%cookies.sqlite%' and parent_path LIKE '%Firefox%'");
            FFSqlitedb = tempDb.resultSetToFsContents(rs);
            
            int j = 0;
     
            while (j < FFSqlitedb.size())
            {
                String temps = currentCase.getTempDirectory() + "\\" + FFSqlitedb.get(j).getName().toString() + j + ".db";
                String connectionString = "jdbc:sqlite:" + temps;
                ContentUtils.writeToFile(FFSqlitedb.get(j), new File(currentCase.getTempDirectory() + "\\" + FFSqlitedb.get(j).getName().toString() + j + ".db"));
                File dbFile = new File(temps);
                 try
                {
                   dbconnect tempdbconnect = new dbconnect("org.sqlite.JDBC",connectionString);
                   ResultSet temprs = tempdbconnect.executeQry(ffcookiequery);  
                   while(temprs.next()) 
                   {
                      BlackboardArtifact bbart = FFSqlitedb.get(j).newArtifact(ARTIFACT_TYPE.TSK_WEB_COOKIE);
                      HashMap<String, Object> kvs = new HashMap<String, Object>();
                      kvs.put("Url", temprs.getString("host"));
                      kvs.put("Title", ((temprs.getString("name") != null) ? temprs.getString("name") : "No name"));
                      kvs.put("Count", temprs.getString("value"));
                      kvs.put("Last Accessed", temprs.getString("lastAccessed"));
                      kvs.put("Reference", temprs.getString("creationTime"));
                     BlackboardAttribute bbatturl = new BlackboardAttribute(1, temprs.getString("host"), "RecentActivity", "FireFox");
                     bbart.addAttribute(bbatturl);
                     BlackboardAttribute bbattdate = new BlackboardAttribute(2, temprs.getString("lastAccessed"), "RecentActivity", "FireFox");
                     bbart.addAttribute(bbattdate);
                     BlackboardAttribute bbattvalue = new BlackboardAttribute(26, temprs.getString("value"), "RecentActivity", "FireFox");
                     bbart.addAttribute(bbattvalue);
                     BlackboardAttribute bbatttitle = new BlackboardAttribute(3, ((temprs.getString("name") != null) ? temprs.getString("name") : "No name"), "RecentActivity","FireFox");
                     bbart.addAttribute(bbatttitle);

                      
                      cookies.add(kvs);
                      
                   } 
                   tempdbconnect.closeConnection();
                   temprs.close();
                    
                 }
                 catch (Exception ex)
                 {
                    logger.log(Level.WARNING, "Error while trying to read into a sqlite db." + connectionString, ex);      
                 }
                j++;
                dbFile.delete();
            }
        }
        catch (SQLException ex) 
        {
           logger.log(Level.WARNING, "Error while trying to get Firefox SQLite db.", ex);
        }
        catch(IOException ioex)
        {   
            logger.log(Level.WARNING, "Error while trying to write to the file system.", ioex);
        }
   } 
}
   //@Override
//   public HashMap<String,String> ExtractActivity() {
//     return ExtractActivity;
//       
//    }
    
                
    
   
   
    
        
