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
import org.sleuthkit.autopsy.ingest.IngestImageWorkerController;
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
    
    public Logger logger = Logger.getLogger(this.getClass().getName());

    public int FireFoxCount = 0;
       
   public Firefox(){
       
   }

       public void getffdb(List<String> image, IngestImageWorkerController controller){
         //Make these seperate, this is for history
        try 
        {   
            Case currentCase = Case.getCurrentCase(); // get the most updated case
            SleuthkitCase tempDb = currentCase.getSleuthkitCase();
            String allFS = new String();
            for(String img : image)
            {
               allFS += " and fs_obj_id = '" + img + "'";
            }        
            List<FsContent> FFSqlitedb;  

            ResultSet rs = tempDb.runQuery("select * from tsk_files where name LIKE '%places.sqlite%' and parent_path LIKE '%Firefox%'" + allFS);
            FFSqlitedb = tempDb.resultSetToFsContents(rs);
            Statement s = rs.getStatement();
            rs.close();
            if (s != null)
                s.close();
                    FireFoxCount = FFSqlitedb.size();
                      
            rs.close();
            rs.getStatement().close();
            int j = 0;
     
            while (j < FFSqlitedb.size())

            {         
                String temps = currentCase.getTempDirectory() + File.separator + FFSqlitedb.get(j).getName().toString() + j + ".db";
                String connectionString = "jdbc:sqlite:" + temps;
                ContentUtils.writeToFile(FFSqlitedb.get(j), new File(currentCase.getTempDirectory() + File.separator + FFSqlitedb.get(j).getName().toString() + j + ".db"));
                File dbFile = new File(temps);
                if (controller.isCancelled() ) {
                 dbFile.delete();
                 break;
                }  
                
                try
                {
                   
                   dbconnect tempdbconnect = new dbconnect("org.sqlite.JDBC",connectionString);
                   ResultSet temprs = tempdbconnect.executeQry(ffquery);  
                   while(temprs.next()) 
                   {
                      BlackboardArtifact bbart = FFSqlitedb.get(j).newArtifact(ARTIFACT_TYPE.TSK_WEB_HISTORY);
               
                      BlackboardAttribute bbatturl = new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL.getTypeID(),"RecentActivity","FireFox",temprs.getString("url"));
                      bbart.addAttribute(bbatturl);
                       BlackboardAttribute bbattdate = new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_LAST_ACCESSED.getTypeID(),"RecentActivity","FireFox",temprs.getString("visit_date"));
                      bbart.addAttribute(bbattdate);
                       BlackboardAttribute bbattref = new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_REFERRER.getTypeID(),"RecentActivity","FireFox",temprs.getString("from_visit"));
                      bbart.addAttribute(bbattref);
                       BlackboardAttribute bbatttitle = new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME.getTypeID(),"RecentActivity","FireFox",((temprs.getString("title") != null) ? temprs.getString("title") : "No Title"));
                      bbart.addAttribute(bbatttitle);
                       BlackboardAttribute bbattprog = new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(),"RecentActivity","FireFox","FireFox");
                       bbart.addAttribute(bbattprog);
                  
                      
                   }
                   temprs.close(); 
                   ResultSet tempbm = tempdbconnect.executeQry(ffbookmarkquery);  
                   while(tempbm.next()) 
                   {
                      BlackboardArtifact bbart = FFSqlitedb.get(j).newArtifact(ARTIFACT_TYPE.TSK_WEB_BOOKMARK);
           
                      BlackboardAttribute bbatturl = new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL.getTypeID(),"RecentActivity","FireFox",((temprs.getString("url") != null) ? temprs.getString("url") : "No URL"));
                      bbart.addAttribute(bbatturl);
                      BlackboardAttribute bbatttitle = new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME.getTypeID(), "RecentActivity","FireFox", ((temprs.getString("title") != null) ? temprs.getString("title").replaceAll("'", "''") : "No Title"));
                      bbart.addAttribute(bbatttitle);
                     BlackboardAttribute bbattprog = new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(),"RecentActivity","FireFox","FireFox");
                       bbart.addAttribute(bbattprog);
         
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
            String allFS = new String();
            for(String img : image)
            {
               allFS += " and fs_obj_id = '" + img + "'";
            }
            List<FsContent> FFSqlitedb;  

            ResultSet rs = tempDb.runQuery("select * from tsk_files where name LIKE '%cookies.sqlite%' and parent_path LIKE '%Firefox%'" + allFS);
            FFSqlitedb = tempDb.resultSetToFsContents(rs);   
            rs.close();
            rs.getStatement().close();  
            int j = 0;
     
            while (j < FFSqlitedb.size())
            {
                String temps = currentCase.getTempDirectory() + File.separator + FFSqlitedb.get(j).getName().toString() + j + ".db";
                String connectionString = "jdbc:sqlite:" + temps;
                ContentUtils.writeToFile(FFSqlitedb.get(j), new File(currentCase.getTempDirectory() + File.separator + FFSqlitedb.get(j).getName().toString() + j + ".db"));
                File dbFile = new File(temps);
                if (controller.isCancelled() ) {
                 dbFile.delete();
                 break;
                }  
                 try
                {
                   dbconnect tempdbconnect = new dbconnect("org.sqlite.JDBC",connectionString);
                   ResultSet temprs = tempdbconnect.executeQry(ffcookiequery);  
                   while(temprs.next()) 
                   {
                      BlackboardArtifact bbart = FFSqlitedb.get(j).newArtifact(ARTIFACT_TYPE.TSK_WEB_COOKIE);
                      BlackboardAttribute bbatturl = new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL.getTypeID(), "RecentActivity", "FireFox", temprs.getString("host"));
                     bbart.addAttribute(bbatturl);
                     BlackboardAttribute bbattdate = new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID(), "RecentActivity", "FireFox", temprs.getString("lastAccessed"));
                     bbart.addAttribute(bbattdate);
                     BlackboardAttribute bbattvalue = new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_TEXT.getTypeID(), "RecentActivity", "FireFox", temprs.getString("value"));
                     bbart.addAttribute(bbattvalue);
                     BlackboardAttribute bbatttitle = new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME.getTypeID(), "RecentActivity","FireFox",((temprs.getString("name") != null) ? temprs.getString("name") : "No name"));
                     bbart.addAttribute(bbatttitle);
                       BlackboardAttribute bbattprog = new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(),"RecentActivity","FireFox","FireFox");
                       bbart.addAttribute(bbattprog);
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
    
                
    
   
   
    
        
