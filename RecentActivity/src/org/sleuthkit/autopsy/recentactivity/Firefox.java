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
/**
 *
 * @author Alex
 */
public class Firefox {

    private static final String ffquery = "SELECT moz_historyvisits.id,url,title,visit_count,visit_date,from_visit FROM moz_places, moz_historyvisits WHERE moz_places.id = moz_historyvisits.place_id AND hidden = 0";
    private static final String ffcookiequery = "SELECT name,value,host,expiry,lastAccessed,creationTime FROM moz_cookies";
    private static final String ffbookmarkquery = "SELECT fk, moz_bookmarks.title, url FROM moz_bookmarks INNER JOIN moz_places ON moz_bookmarks.fk=moz_places.id";
    
    public ArrayList<HashMap> als = new ArrayList<HashMap>();
    public Logger logger = Logger.getLogger(this.getClass().getName());
    public ArrayList<HashMap> cookies = new ArrayList<HashMap>();
    public ArrayList<HashMap> bookmarks = new ArrayList<HashMap>();
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
                      
                      HashMap<String, Object> kvs = new HashMap<String, Object>();
                      kvs.put("Url", temprs.getString("url"));
                      kvs.put("Title", ((temprs.getString("title") != null) ? temprs.getString("title") : "No Title"));
                      kvs.put("Count", temprs.getString("visit_count"));
                      kvs.put("Last Accessed", temprs.getString("visit_date"));
                      kvs.put("Reference", temprs.getString("from_visit"));
                      als.add(kvs);
                      
                   }
                   temprs.close(); 
                   ResultSet tempbm = tempdbconnect.executeQry(ffbookmarkquery);  
                   while(tempbm.next()) 
                   {
                      
                      HashMap<String, Object> kvs = new HashMap<String, Object>();
                      kvs.put("Url", temprs.getString("url"));
                      kvs.put("Title", ((temprs.getString("title") != null) ? temprs.getString("title") : "No Title"));
                      kvs.put("Count", "");
                      kvs.put("Last Accessed", "");
                      kvs.put("Reference", "");
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
                      
                      HashMap<String, Object> kvs = new HashMap<String, Object>();
                      kvs.put("Url", temprs.getString("host"));
                      kvs.put("Title", ((temprs.getString("name") != null) ? temprs.getString("name") : "No name"));
                      kvs.put("Count", temprs.getString("value"));
                      kvs.put("Last Accessed", temprs.getString("lastAccessed"));
                      kvs.put("Reference", temprs.getString("creationTime"));
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
    
                
    
   
   
    
        
