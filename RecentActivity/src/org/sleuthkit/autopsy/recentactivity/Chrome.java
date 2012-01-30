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
import java.lang.*;
import java.util.*;
import java.io.File;
import java.io.IOException;
/**
 *
 * @author Alex
 */


public class Chrome {

   
   public static final String chquery = "SELECT urls.url, urls.title, urls.visit_count, urls.typed_count, "
           + "urls.last_visit_time, urls.hidden, visits.visit_time, visits.from_visit, visits.transition FROM urls, visits WHERE urls.id = visits.url";
   public static final String chcookiequery = "select name, value, host, expires_utc, last_access_utc, creation_utc from cookies";
   public static final String chbookmarkquery = "SELECT starred.title, urls.url, starred.date_added, starred.date_modified, urls.typed_count, urls._last_visit_time FROM starred INNER JOIN urls ON urls.id = starred.url_id";
   public List<Map> als = new ArrayList();
   public ArrayList<HashMap> cookies = new ArrayList<HashMap>();
    public ArrayList<HashMap> bookmarks = new ArrayList<HashMap>();
   private final Logger logger = Logger.getLogger(this.getClass().getName());
    
    public Chrome(){
 
   }
  
     public void getchdb(){
         
        try 
        {   
            Case currentCase = Case.getCurrentCase(); // get the most updated case
            SleuthkitCase tempDb = currentCase.getSleuthkitCase();
            List<FsContent> FFSqlitedb;  
            Map<String, Object> kvs = new LinkedHashMap<String, Object>();
            
            ResultSet rs = tempDb.runQuery("select * from tsk_files where name LIKE 'History' AND parent_path LIKE '%Chrome%'");
            FFSqlitedb = tempDb.resultSetToFsContents(rs);
            
            int j = 0;
     
            while (j < FFSqlitedb.size())
            {
                String temps = currentCase.getTempDirectory() + "\\" + FFSqlitedb.get(j).getName().toString() + j + ".db";
                String connectionString = "jdbc:sqlite:" + temps;
                 ContentUtils.writeToFile(FFSqlitedb.get(j), new File(currentCase.getTempDirectory() + "\\" + FFSqlitedb.get(j).getName().toString() + j + ".db"));
                
                try
                {
                   dbconnect tempdbconnect = new dbconnect("org.sqlite.JDBC",connectionString);
                   ResultSet temprs = tempdbconnect.executeQry(chquery);
                   
                   
                   
                   while(temprs.next()) 
                   {
                      kvs.clear();       
                      kvs.put("Url", temprs.getString("url"));
                      kvs.put("Title", ((temprs.getString("title") != null) ? temprs.getString("title") : "No Title"));
                      kvs.put("Count", temprs.getString("visit_count"));
                      kvs.put("Last Accessed", temprs.getString("visit_date"));
                      kvs.put("Reference", temprs.getString("from_visit"));
                      als.add(kvs);
                     
                   } 
                   tempdbconnect.closeConnection();
                   temprs.close();
             
                 }
                 catch (Exception ex)
                 {
                    logger.log(Level.WARNING, "Error while trying to read into a sqlite db." + connectionString, ex);      
                 }
		 
                j++;
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
            ResultSet rs = tempDb.runQuery("select * from tsk_files where name LIKE 'Cookies' and parent_path LIKE '%Chrome%'");
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
                   ResultSet temprs = tempdbconnect.executeQry(chcookiequery);  
                   while(temprs.next()) 
                   {
                      HashMap<String, Object> kvs = new HashMap<String, Object>();
                      kvs.put("Url", temprs.getString("host"));
                      kvs.put("Title", ((temprs.getString("name") != null) ? temprs.getString("name") : "No name"));
                      kvs.put("Count", temprs.getString("value"));
                      kvs.put("Last Accessed", temprs.getString("access_utc"));
                      kvs.put("Reference", temprs.getString("creation_utc"));
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
        
        //COOKIES section
          // This gets the cookie info
         try 
        {   
            Case currentCase = Case.getCurrentCase(); // get the most updated case
            SleuthkitCase tempDb = currentCase.getSleuthkitCase();
            List<FsContent> FFSqlitedb;  
            ResultSet rs = tempDb.runQuery("select * from tsk_files where name LIKE 'Bookmarks' and parent_path LIKE '%Chrome%'");
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
                   ResultSet temprs = tempdbconnect.executeQry(chbookmarkquery);  
                   while(temprs.next()) 
                   {
                      HashMap<String, Object> kvs = new HashMap<String, Object>();
                      kvs.put("Url", temprs.getString("urls.url"));
                      kvs.put("Title", ((temprs.getString("starred.title") != null) ? temprs.getString("starred.title") : "No name"));
                      kvs.put("Count", temprs.getString("urls.typed_count"));
                      kvs.put("Last Accessed", temprs.getString("urls._last_visit_time"));
                      kvs.put("Reference", temprs.getString("starred.date_added"));
                      bookmarks.add(kvs);
                      
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
