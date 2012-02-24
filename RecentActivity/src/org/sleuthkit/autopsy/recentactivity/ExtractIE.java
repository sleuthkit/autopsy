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
package org.sleuthkit.autopsy.recentactivity;

//IO imports
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

// SQL imports
import java.sql.ResultSet;

//Util Imports
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// TSK Imports
import org.openide.modules.InstalledFileLocator;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.datamodel.KeyValue;
import org.sleuthkit.autopsy.ingest.IngestImageWorkerController;
import org.sleuthkit.autopsy.ingest.IngestImageWorkerController;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskException;

public class ExtractIE { // implements BrowserActivity {

    private static final Logger logger = Logger.getLogger(ExtractIE.class.getName());
    private String indexDatQueryStr = "select * from tsk_files where name LIKE '%index.dat%'";
    
    //sleauthkit db handle
    SleuthkitCase tempDb;
    
    //paths set in init()
    private String PASCO_RESULTS_PATH;
    private String PASCO_LIB_PATH;
    
    //Results List to be referenced/used outside the class
    public ArrayList<HashMap<String, Object>> PASCO_RESULTS_LIST = new ArrayList<HashMap<String, Object>>();
    //Look Up Table  that holds Pasco2 results
    private HashMap<String, Object> PASCO_RESULTS_LUT;
    private KeyValue IE_PASCO_LUT = new KeyValue(BrowserType.IE.name(), BrowserType.IE.getType());
    public LinkedHashMap<String, Object> IE_OBJ;

    
    boolean pascoFound = false;

    public ExtractIE(List<String> image, IngestImageWorkerController controller) {
        init(image, controller);
    }

    //@Override
    public KeyValue getRecentActivity() {
        return IE_PASCO_LUT;
    }

    private void init(List<String> image, IngestImageWorkerController controller) {
        final Case currentCase = Case.getCurrentCase();
        final String caseDir = Case.getCurrentCase().getCaseDirectory();
        PASCO_RESULTS_PATH = caseDir + File.separator + "recentactivity" + File.separator + "results";

        logger.log(Level.INFO, "Pasco results path: " + PASCO_RESULTS_PATH);
        
         final File pascoRoot = InstalledFileLocator.getDefault().locate("pasco2", ExtractIE.class.getPackage().getName(), false);
         if (pascoRoot == null) {
             logger.log(Level.SEVERE, "Pasco2 not found");
             pascoFound = false;
             return;
         }
         else {
             pascoFound = true;
         }
         
        final String pascoHome = pascoRoot.getAbsolutePath();
        logger.log(Level.INFO, "Pasco2 home: " + pascoHome);
             
        PASCO_LIB_PATH  = pascoHome + File.separator + "pasco2.jar" + File.pathSeparator
            + pascoHome + File.separator + "*";

        try {
            File resultsDir = new File(PASCO_RESULTS_PATH);
            resultsDir.mkdirs();

            Collection<FsContent> FsContentCollection;
            tempDb = currentCase.getSleuthkitCase();
            String allFS = new String();
            for(String img : image)
            {
               allFS += " and fs_obj_id = '" + img + "'";
            }
            ResultSet rs = tempDb.runQuery(indexDatQueryStr + allFS);
            FsContentCollection = tempDb.resultSetToFsContents(rs);
            rs.close();
            rs.getStatement().close(); 
            String temps;
            String indexFileName;

            for (FsContent fsc : FsContentCollection) {
                // Since each result represent an index.dat file,
                // just create these files with the following notation:
                // index<Number>.dat (i.e. index0.dat, index1.dat,..., indexN.dat)
                // Write each index.dat file to a temp directory.
                //BlackboardArtifact bbart = fsc.newArtifact(ARTIFACT_TYPE.TSK_WEB_HISTORY);
                indexFileName = "index" + Integer.toString((int)fsc.getId()) + ".dat";
                //indexFileName = "index" + Long.toString(bbart.getArtifactID()) + ".dat";
                temps = currentCase.getTempDirectory() + File.separator + indexFileName;
                File datFile = new File(temps);
                if (controller.isCancelled() ) {
                 datFile.delete();
                 break;
                }  
                try {
                    ContentUtils.writeToFile(fsc, datFile);
                }
                catch (IOException e) {
                    logger.log(Level.INFO, "Error while trying to write index.dat file " + datFile.getAbsolutePath(), e);
                }

                boolean bPascProcSuccess = executePasco(temps, (int)fsc.getId());

                //At this point pasco2 proccessed the index files.
                //Now fetch the results, parse them and the delete the files.
                if (bPascProcSuccess) {

                    //Delete index<n>.dat file since it was succcessfully by Pasco
                    datFile.delete();
                }
            }
        } catch (Exception ioex) {
            logger.log(Level.SEVERE, "Error while trying to write index.dat files.", ioex);
        }
    }

    //Simple wrapper to JavaSystemCaller.Exec() to execute pasco2 jar
    // TODO: Hardcoded command args/path needs to be removed. Maybe set some constants and set env variables for classpath
    // I'm not happy with this code. Can't stand making a system call, is not an acceptable solution but is a hack for now.
    private boolean executePasco(String indexFilePath, int fileIndex) {
        if (pascoFound == false)
            return false;
        boolean success = true;

        try {
            List<String> command = new ArrayList<String>();

            command.add("-cp");
            command.add("\"" + PASCO_LIB_PATH + "\"");
            command.add(" isi.pasco2.Main");
            command.add(" -T history");
            command.add("\"" + indexFilePath + "\"");
            command.add(" > \"" + PASCO_RESULTS_PATH + "\\pasco2Result." + Integer.toString(fileIndex) + ".txt\"");
           // command.add(" > " + "\"" + PASCO_RESULTS_PATH + File.separator + Long.toString(bbId) + "\"");
            String[] cmd = command.toArray(new String[0]);

            JavaSystemCaller.Exec.execute("java", cmd);

        } catch (Exception e) {
            success = false;
            logger.log(Level.SEVERE, "ExtractIE::executePasco() -> ", e.getMessage());
        }

        return success;
    }

    public void parsePascoResults() {
        if (pascoFound == false)
            return;
        // First thing we want to do is check to make sure the results directory
        // is not empty.
        File rFile = new File(PASCO_RESULTS_PATH);


        //Let's make sure our list and lut are empty.
        //PASCO_RESULTS_LIST.clear();

        if (rFile.exists()) {
            //Give me a list of pasco results in that directory
            File[] pascoFiles = rFile.listFiles();

            if (pascoFiles.length > 0) {
                try {
                    for (File file : pascoFiles) {
                       String fileName = file.getName();
                       long artObjId = Long.parseLong(fileName.substring(fileName.indexOf(".")+1, fileName.lastIndexOf(".")));
                        //bbartname = bbartname.substring(0, 4);

                        // Make sure the file the is not empty or the Scanner will
                        // throw a "No Line found" Exception
                        if (file != null && file.length() > 0) {
                            Scanner fileScanner = new Scanner(new FileInputStream(file.toString()));
                            //Skip the first three lines
                            fileScanner.nextLine();
                            fileScanner.nextLine();
                            fileScanner.nextLine();
                          //  long inIndexId = 0;

                            while (fileScanner.hasNext()) {
                                //long bbartId = Long.parseLong(bbartname + inIndexId++);

                                String line = fileScanner.nextLine();

                                //Need to change this pattern a bit because there might
                                //be instances were "V" might not apply.
                                String pattern = "(?)URL(\\s)(V|\\:)";
                                Pattern p = Pattern.compile(pattern);
                                Matcher m = p.matcher(line);
                                if (m.find()) {
                                    try {
                                        String[] lineBuff = line.split("\\t");
                                        PASCO_RESULTS_LUT = new HashMap<String, Object>();
                                        String url[] = lineBuff[1].split("@",2);
                                        String user = "";
                                        String realurl = "";
                                      if(url.length > 1)
                                      {
                                       user = url[0];
                                       realurl = url[1];
                                       realurl = realurl.replace("Visited:", "");
                                       realurl = realurl.replace(":.*:", "");
                                       realurl = realurl.replace(":Host:", "");
                                      }
                                       
                                     // TODO: Need to fix this so we have the right obj_id
                                        BlackboardArtifact bbart = tempDb.getRootObjects().get(0).newArtifact(ARTIFACT_TYPE.TSK_WEB_HISTORY);
                                        Collection<BlackboardAttribute> bbattributes = new ArrayList<BlackboardAttribute>();
                                        bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL.getTypeID(), "RecentActivity", "Internet Explorer", realurl));
                                       
                                        bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_LAST_ACCESSED.getTypeID(), "RecentActivity", "Internet Explorer", lineBuff[3]));
                                        
                                        bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_REFERRER.getTypeID(), "RecentActivity", "Internet Explorer", "No Ref"));
                                   
                                        bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME.getTypeID(), "RecentActivity", "Internet Explorer", lineBuff[2]));
                                       
                                        bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(),"RecentActivity","Internet Explorer","Internet Explorer"));
                                        
                                        bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_USERNAME.getTypeID(),"RecentActivity","Internet Explorer",user));
                                        bbart.addAttributes(bbattributes);

                                        //KeyValueThing
                                        //This will be redundant in terms IE.name() because of
                                        //the way they implemented KeyValueThing
                                        IE_OBJ = new LinkedHashMap<String, Object>();
                                        IE_OBJ.put(BrowserType.IE.name(), PASCO_RESULTS_LUT);
                                        IE_PASCO_LUT.addMap(IE_OBJ);

                                        PASCO_RESULTS_LIST.add(PASCO_RESULTS_LUT);
                                    } catch (TskException ex) {
                                        Exceptions.printStackTrace(ex);
                                    } catch (SQLException ex) {
                                        logger.log(Level.WARNING, "Couldn't find file with id: " + artObjId, ex);
                                    }
                                }

                            }
                        }
                        //TODO: Fix Delete issue
                        boolean bDelete = file.delete();
                    }
                } catch (IOException ioex) {
                    logger.log(Level.SEVERE, "ExtractIE::parsePascosResults() -> ", ioex.getMessage());
                }

            }
        }
    }
}
