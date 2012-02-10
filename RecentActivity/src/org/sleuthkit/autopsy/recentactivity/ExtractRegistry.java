/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.recentactivity;

import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.ingest.IngestImageWorkerController;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.SleuthkitCase;

/**
 *
 * @author Alex \System32\Config
 */
public class ExtractRegistry {

      public Logger logger = Logger.getLogger(this.getClass().getName());
    
    ExtractRegistry(){
    }
    
    
    
public void getregistryfiles(List<String> image, IngestImageWorkerController controller){
 try 
        {   
            Case currentCase = Case.getCurrentCase(); // get the most updated case
            SleuthkitCase tempDb = currentCase.getSleuthkitCase();
             String allFS = new String();
            for(String img : image)
            {
               allFS += " and fs_obj_id = '" + img + "'";
            }
            List<FsContent> Regfiles;  
            ResultSet rs = tempDb.runQuery("select * from tsk_files where lower(name) = 'ntuser.dat' OR lower(parent_path) LIKE '%/system32/config%' and (name = 'system' OR name = 'software' OR name = 'SECURITY' OR name = 'SAM' OR name = 'default')" + allFS);
            Regfiles = tempDb.resultSetToFsContents(rs);
            
            int j = 0;
     
            while (j < Regfiles.size())
            {
               
                String temps = currentCase.getTempDirectory() + "\\" + Regfiles.get(j).getName().toString();
                ContentUtils.writeToFile(Regfiles.get(j), new File(currentCase.getTempDirectory() + "\\" + Regfiles.get(j).getName()));
                File regFile = new File(temps);
               
                 boolean regSuccess = executeRegRip(temps, j);

             //At this point pasco2 proccessed the index files.
             //Now fetch the results, parse them and the delete the files.
             if(regSuccess)
             {
                //Delete dat file since it was succcessfully by Pasco
                regFile.delete();
             }
                j++;
                
                
             
            }
        }
        catch (SQLException ex) 
        {
           logger.log(Level.WARNING, "Error while trying to get Registry files", ex);
        }
        catch(IOException ioex)
        {   
            logger.log(Level.WARNING, "Error while trying to write to the file system.", ioex);
        }
}


    // TODO: Hardcoded command args/path needs to be removed. Maybe set some constants and set env variables for classpath
    // I'm not happy with this code. Can't stand making a system call, is not an acceptable solution but is a hack for now.
	private  boolean executeRegRip(String regFilePath, int fileIndex)
    {
       boolean success = true;
       String type = "";
   

       try
       {
            if(regFilePath.toLowerCase().contains("system"))
                {
                    type = "system";
                }
                if(regFilePath.toLowerCase().contains("software"))
                {
                    type = "software";
                }
                if(regFilePath.toLowerCase().contains("ntuser"))
                {
                    type = "autopsy";
                }
                if(regFilePath.toLowerCase().contains("default"))
                {
                    type = "default";
                }
                if(regFilePath.toLowerCase().contains("sam"))
                {
                    type = "sam";
                }
                if(regFilePath.toLowerCase().contains("security"))
                {
                    type = "security";
                }
                
                String rrpath = System.getProperty("user.dir");
                rrpath = rrpath.substring(0, rrpath.length()-14);
                rrpath = rrpath + "thirdparty\\rr\\";
                String command = rrpath + "rip.exe -r " + regFilePath +" -f " + type + " >> " + regFilePath + Integer.toString(fileIndex) + ".txt";

                JavaSystemCaller.Exec.execute(command);

       }
       catch(Exception e)
       {
          success = false;
          logger.log(Level.SEVERE, "ExtractRegistry::executeRegRip() -> " ,e.getMessage() );
       }

       return success;
    }


}
