/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.recentactivity;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.openide.modules.InstalledFileLocator;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.ingest.IngestImageWorkerController;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.SleuthkitCase;



/**
 *
 * @author Alex \System32\Config
 */
public class ExtractRegistry {

      public Logger logger = Logger.getLogger(this.getClass().getName());
     private String RR_PATH;
     boolean rrFound = false;
     
    ExtractRegistry(){
        final File rrRoot = InstalledFileLocator.getDefault().locate("rr", ExtractRegistry.class.getPackage().getName(), false);
         if (rrRoot == null) {
             logger.log(Level.SEVERE, "RegRipper not found");
             rrFound = false;
             return;
         }
         else {
             rrFound = true;
         }
         
        final String rrHome = rrRoot.getAbsolutePath();
        logger.log(Level.INFO, "RegRipper home: " + rrHome);
             
        RR_PATH  = rrHome + File.separator + "rip.exe";
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
                Content orgFS = Regfiles.get(j);
                long orgId = orgFS.getId();
                String temps = currentCase.getTempDirectory() + "\\" + Regfiles.get(j).getName().toString();
                ContentUtils.writeToFile(Regfiles.get(j), new File(currentCase.getTempDirectory() + "\\" + Regfiles.get(j).getName()));
                File regFile = new File(temps);
               
                 String txtPath = executeRegRip(temps, j);
                 if(txtPath.length() > 0)
                 {
                    Success = parseReg(txtPath,orgId);
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

                String command = RR_PATH + " -r " + regFilePath +" -f " + type + "> " + txtPath;
                JavaSystemCaller.Exec.execute(command);
               

       }
       catch(Exception e)
       {
          
          logger.log(Level.SEVERE, "ExtractRegistry::executeRegRip() -> " ,e.getMessage() );
       }

       return txtPath;
    }
  
   
     private boolean parseReg(String regRecord, long orgId)
    {
        Case currentCase = Case.getCurrentCase(); // get the most updated case
        SleuthkitCase tempDb = currentCase.getSleuthkitCase();
         try {
           String regString = new Scanner(new File(regRecord)).useDelimiter("\\Z").next();
           String startdoc = "<document>";
           String result = regString.replaceAll("----------------------------------------","");
           String enddoc = "</document>";
           String stringdoc = startdoc + result + enddoc;
           SAXBuilder sb = new SAXBuilder();
           Document document = sb.build(new StringReader(stringdoc));
           Element root = document.getRootElement();
           List types = root.getChildren();
           Iterator iterator = types.iterator();
           //for(int i = 0; i < types.size(); i++)
           //for(Element tempnode : types)
            while (iterator.hasNext()) {
              String time = "";
               String context = "";
               Element tempnode = (Element) iterator.next();
              // Element tempnode = types.get(i);
               context = tempnode.getName();
               Element timenode = tempnode.getChild("time");
                    time = timenode.getTextTrim();
               
               Element artroot = tempnode.getChild("artifacts");
               List artlist = artroot.getChildren();
            BlackboardArtifact bbart = tempDb.getContentById(orgId).newArtifact(ARTIFACT_TYPE.TSK_RECENT_OBJECT);
            Collection<BlackboardAttribute> bbattributes = new ArrayList<BlackboardAttribute>();
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_LAST_ACCESSED.getTypeID(), "RecentActivity", context, time));
              Iterator aiterator = artlist.iterator();
               while (aiterator.hasNext()) {
                 Element artnode = (Element) aiterator.next();
                 String name = artnode.getAttributeValue("name");
                 String value = artnode.getTextTrim();  
                  bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME.getTypeID(), "RecentActivity", context, name));
                 bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_VALUE.getTypeID(), "RecentActivity", context, value));
               }
                
                
                 
              
                bbart.addAttributes(bbattributes);
            }
           }
           catch (Exception ex)
           {
               String hi = "";
            logger.log(Level.WARNING, "Error while trying to read into a sqlite db." +  ex);      
           }
   

       
       return true;
    }

}
