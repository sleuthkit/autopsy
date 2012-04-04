/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.recentactivity;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang.NullArgumentException;
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

public static String utcConvert(String utc){
                SimpleDateFormat formatter = new SimpleDateFormat("MM-dd-yyyy HH:mm");
               String tempconvert = formatter.format(new Date(Long.parseLong(utc)));
               return tempconvert;
}

public static String readFile(String path) throws IOException {
  FileInputStream stream = new FileInputStream(new File(path));
  try {
    FileChannel fc = stream.getChannel();
    MappedByteBuffer bb = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
    /* Instead of using default, pass in a decoder. */
    return Charset.defaultCharset().decode(bb).toString();
  }
  finally {
    stream.close();
  }
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
     final Statement s = rs.getStatement();
     rs.close();
     if (s != null)
        s.close();
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

public static String extractDomain(String value){
    if (value == null) throw new NullArgumentException("domains to extract");
        String result = "";
        String domainPattern = "[a-z0-9\\-\\.]+\\.(com|org|net|mil|edu|(co\\.[a-z].))";
        Pattern p = Pattern.compile(domainPattern,Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(value);
        while (m.find()) {
        result = value.substring(m.start(0),m.end(0));
        }
    return result;
    }
}