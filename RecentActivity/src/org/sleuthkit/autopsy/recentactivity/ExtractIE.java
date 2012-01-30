package org.sleuthkit.autopsy.recentactivity;

//IO imports
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

// SQL imports
import java.sql.ResultSet;

//Util Imports
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
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.datamodel.KeyValueThing;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.SleuthkitCase;


public class ExtractIE { // implements BrowserActivity {

   //Constants region
   private final String indexDatQueryStr = "select * from tsk_files where name LIKE '%index.dat%'";

   private final String PASCO_HOME = System.getenv("PASCO_HOME");
   private final String PASCO_RESULTS_PATH =  PASCO_HOME + "\\results";
   private final String PASCO_LIB_PATH = PASCO_HOME + "\\pasco2.jar;"
                                                  +  PASCO_HOME + "\\lib\\*";

   //Results List to be referenced/used outside the class
   public ArrayList<HashMap<String,Object>> PASCO_RESULTS_LIST = new ArrayList<HashMap<String,Object>>();
   //Look Up Table  that holds Pasco2 results
   private  HashMap<String, Object> PASCO_RESULTS_LUT ;

   private KeyValueThing IE_PASCO_LUT = new KeyValueThing(BrowserType.IE.name(), BrowserType.IE.getType());

   public LinkedHashMap<String, Object> IE_OBJ;

   //Get this case
   private Case currentCase = Case.getCurrentCase();
   private SleuthkitCase tempDb = currentCase.getSleuthkitCase();

   //Singleton logger object.
   private final Logger logger = Logger.getLogger(this.getClass().getName());

   public ExtractIE(){
      init();
   }

   //@Override
   public KeyValueThing getRecentActivity()
   {
      return IE_PASCO_LUT;
   }

   void init()
   {
      try
      {
         Collection<FsContent> FsContentCollection;
         ResultSet rs = tempDb.runQuery(indexDatQueryStr);
         FsContentCollection = tempDb.resultSetToFsContents(rs);

         String temps;
         String indexFileName;
         int index = 0;

         for(FsContent fsc : FsContentCollection)
         {
             // Since each result represent an index.dat file,
             // just create these files with the following notation:
             // index<Number>.dat (i.e. index0.dat, index1.dat,..., indexN.dat)
             // Write each index.dat file to a temp directory.
             indexFileName = "index" + Integer.toString(index) + ".dat";
             temps = currentCase.getTempDirectory() + "\\" + indexFileName;
             File datFile = new File(temps);
             ContentUtils.writeToFile(fsc, datFile);
             boolean bPascProcSuccess = executePasco(temps, index);

             //At this point pasco2 proccessed the index files.
             //Now fetch the results, parse them and the delete the files.
             if(bPascProcSuccess)
             {
                //Delete index<n>.dat file since it was succcessfully by Pasco
                datFile.delete();
             }
             ++index;
         }
      }
      catch(Exception ioex)
      {
         logger.log(Level.SEVERE, "Error while trying to write index.dat files.", ioex);
      }
   }


    //Simple wrapper to JavaSystemCaller.Exec() to execute pasco2 jar
    // TODO: Hardcoded command args/path needs to be removed. Maybe set some constants and set env variables for classpath
    // I'm not happy with this code. Can't stand making a system call, is not an acceptable solution but is a hack for now.
	private  boolean executePasco(String indexFilePath, int fileIndex)
    {
       boolean success = true;

       try
       {
			List<String> command = new ArrayList<String>();

            command.add("-cp");
		    command.add(PASCO_LIB_PATH);
            command.add(" isi.pasco2.Main");
            command.add(" -T history");
            command.add(indexFilePath);
            command.add(" > " + PASCO_RESULTS_PATH + "\\pasco2Result" + Integer.toString(fileIndex) + ".txt");

            String[] cmd = command.toArray(new String[0]);

            JavaSystemCaller.Exec.execute("java", cmd);

       }
       catch(Exception e)
       {
          success = false;
          logger.log(Level.SEVERE, "ExtractIE::executePasco() -> " ,e.getMessage() );
       }

       return success;
    }

    public void parsePascoResults()
    {
       // First thing we want to do is check to make sure the results directory
       // is not empty.
       File rFile = new File(PASCO_RESULTS_PATH);

       //Let's make sure our list and lut are empty.
       //PASCO_RESULTS_LIST.clear();

       if(rFile.exists())
       {
          //Give me a list of pasco results in that directory
          File[] pascoFiles = rFile.listFiles();

          if(pascoFiles.length > 0)
          {
             try
             {
                for (File file : pascoFiles)
                {

                   // Make sure the file the is not empty or the Scanner will
                   // throw a "No Line found" Exception
                   if (file != null && file.length() > 0 )
                   {
                      Scanner fileScanner = new Scanner(new FileInputStream(file.toString()));
                      //Skip the first three lines
                      fileScanner.nextLine();
                      fileScanner.nextLine();
                      fileScanner.nextLine();

                      while (fileScanner.hasNext())
                      {
                         String line = fileScanner.nextLine();
                         //Need to change this pattern a bit because there might
                         //be instances were "V" might not apply.
                         String pattern = "(?)URL(\\s)(V|\\:)";
                         Pattern p = Pattern.compile(pattern);
                         Matcher m  = p.matcher(line);
                         if(m.find())
                         {
                            String[] lineBuff = line.split("\\t");
                            PASCO_RESULTS_LUT = new HashMap<String,Object>();
                            PASCO_RESULTS_LUT.put(BrowserActivityType.Url.name(), lineBuff[1]);
                            PASCO_RESULTS_LUT.put("Title", lineBuff[2]);
                            PASCO_RESULTS_LUT.put("Count", lineBuff[0]);
                            PASCO_RESULTS_LUT.put("Last Accessed", lineBuff[3]);
                           
                            PASCO_RESULTS_LUT.put("Reference", "None");
                            //KeyValueThing
                            //This will be redundant in terms IE.name() because of
                            //the way they implemented KeyValueThing
                            IE_OBJ = new LinkedHashMap<String,Object>();
                            IE_OBJ.put(BrowserType.IE.name(), PASCO_RESULTS_LUT);
                            IE_PASCO_LUT.addMap(IE_OBJ);

                            PASCO_RESULTS_LIST.add(PASCO_RESULTS_LUT);
                         }
                      }
                   }
                   //TODO: Fix Delete issue
                   boolean bDelete = file.delete();
                }
             }
             catch(IOException ioex)
             {
                logger.log(Level.SEVERE, "ExtractIE::parsePascosResults() -> " ,ioex.getMessage() );
             }

          }
       }
    }

}
