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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.ingest.IngestImageWorkerController;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
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
                boolean Success;
                String temps = currentCase.getTempDirectory() + "\\" + Regfiles.get(j).getName().toString();
                ContentUtils.writeToFile(Regfiles.get(j), new File(currentCase.getTempDirectory() + "\\" + Regfiles.get(j).getName()));
                File regFile = new File(temps);
               
                 String txtPath = executeRegRip(temps, j);
                 if(txtPath.length() > 0)
                 {
                    Success = parseReg(txtPath);
                 }
                 else
                 {
                     Success = false;
                 }
             //At this point pasco2 proccessed the index files.
             //Now fetch the results, parse them and the delete the files.
             if(Success)
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
	private  String executeRegRip(String regFilePath, int fileIndex)
    {
       String txtPath = regFilePath + Integer.toString(fileIndex) + ".txt";
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
                
                String command = rrpath + "rip.exe -r " + regFilePath +" -f " + type + " >> " + txtPath;

                JavaSystemCaller.Exec.execute(command);
               

       }
       catch(Exception e)
       {
          
          logger.log(Level.SEVERE, "ExtractRegistry::executeRegRip() -> " ,e.getMessage() );
       }

       return txtPath;
    }
  
   
     private boolean parseReg(String regRecord)
    {
        Case currentCase = Case.getCurrentCase(); // get the most updated case
        SleuthkitCase tempDb = currentCase.getSleuthkitCase();
     
       String[] result = regRecord.split("----------------------------------------");
       for(String tempresult : result)
       {
           try{
                
               if(tempresult.contains("not found") || tempresult.contains("no values"))
               {
                   
               }
               else
               {
                BlackboardArtifact bbart = tempDb.getRootObjects().get(0).newArtifact(ARTIFACT_TYPE.TSK_RECENT_OBJECT);  
                   if(tempresult.contains("Username"))
                   {
                    Pattern p = Pattern.compile("Username\\[.*?\\]"); 
                    Matcher m = p.matcher(tempresult);
                    while (m.find()) {
                         String s = m.group(1);
                    
                       BlackboardAttribute bbatturl = new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_USERNAME.getTypeID(), "RecentActivity", "Registry", s);
                        bbart.addAttribute(bbatturl);  
                    }             
                   }

                   if(tempresult.contains("Time["))
                   {
                    Pattern p = Pattern.compile("Time\\[.*?\\]"); 
                    Matcher m = p.matcher(tempresult);
                    while (m.find()) {
                     String s = m.group(1);
                     BlackboardAttribute bbattdate = new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_LAST_ACCESSED.getTypeID(), "RecentActivity", "Registry", s);
                     bbart.addAttribute(bbattdate);
                    }
                   
                    }
               }
           }
           catch (Exception ex)
           {
            logger.log(Level.WARNING, "Error while trying to read into a sqlite db." +  ex);      
           }
       }
   

       
       return true;
    }

}
