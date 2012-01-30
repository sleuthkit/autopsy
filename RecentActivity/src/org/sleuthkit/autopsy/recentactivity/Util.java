/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.recentactivity;
import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.SleuthkitCase;
/**
 *
 * @author Alex
 */
public class Util {
public Logger logger = Logger.getLogger(this.getClass().getName());    
    
  private Util(){
      
  }

public static boolean pathexists(String path){
    File file=new File(path);
    boolean exists = file.exists();
    return exists;
}

public static boolean imgpathexists(String path){
    Case currentCase = Case.getCurrentCase(); // get the most updated case
    SleuthkitCase tempDb = currentCase.getSleuthkitCase();
    
    int count = 0;
    try { 
     List<FsContent> FFSqlitedb;
     ResultSet rs = tempDb.runQuery("select * from tsk_files where parent_path LIKE '%"+ path + "%'");
     FFSqlitedb = tempDb.resultSetToFsContents(rs);
     count = FFSqlitedb.size();
    }
    catch (SQLException ex) 
        {
           //logger.log(Level.WARNING, "Error while trying to contact SQLite db.", ex);
        }
    finally {
        
        if(count > 0)
            {
            return true;
            }
        else
            {
             return false;
            }
        }    

    }
}