/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.recentactivity;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
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
//import org.apache.commons.lang.NullArgumentException;
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
    if (value == null) throw new java.lang.NullPointerException("domains to extract");
        String result = "";
       // String domainPattern = "(\\w+)\\.(AC|AD|AE|AERO|AF|AG|AI|AL|AM|AN|AO|AQ|AR|ARPA|AS|ASIA|AT|AU|AW|AX|AZ|BA|BB|BD|BE|BF|BG|BH|BI|BIZ|BJ|BM|BN|BO|BR|BS|BT|BV|BW|BY|BZ|CA|CAT|CC|CD|CF|CG|CH|CI|CK|CL|CM|CN|CO|COM|COOP|CR|CU|CV|CW|CX|CY|CZ|DE|DJ|DK|DM|DO|DZ|EC|EDU|EE|EG|ER|ES|ET|EU|FI|FJ|FK|FM|FO|FR|GA|GB|GD|GE|GF|GG|GH|GI|GL|GM|GN|GOV|GP|GQ|GR|GS|GT|GU|GW|GY|HK|HM|HN|HR|HT|HU|ID|IE|IL|IM|IN|INFO|INT|IO|IQ|IR|IS|IT|JE|JM|JO|JOBS|JP|KE|KG|KH|KI|KM|KN|KP|KR|KW|KY|KZ|LA|LB|LC|LI|LK|LR|LS|LT|LU|LV|LY|MA|MC|MD|ME|MG|MH|MIL|MK|ML|MM|MN|MO|MOBI|MP|MQ|MR|MS|MT|MU|MUSEUM|MV|MW|MX|MY|MZ|NA|NAME|NC|NE|NET|NF|NG|NI|NL|NO|NP|NR|NU|NZ|OM|ORG|PA|PE|PF|PG|PH|PK|PL|PM|PN|PR|PRO|PS|PT|PW|PY|QA|RE|RO|RS|RU|RW|SA|SB|SC|SD|SE|SG|SH|SI|SJ|SK|SL|SM|SN|SO|SR|ST|SU|SV|SX|SY|SZ|TC|TD|TEL|TF|TG|TH|TJ|TK|TL|TM|TN|TO|TP|TR|TRAVEL|TT|TV|TW|TZ|UA|UG|UK|US|UY|UZ|VA|VC|VE|VG|VI|VN|VU|WF|WS|XXX|YE|YT|ZA|ZM|ZW(co\\.[a-z].))";
      //  Pattern p = Pattern.compile(domainPattern,Pattern.CASE_INSENSITIVE);
      //  Matcher m = p.matcher(value);
      //  while (m.find()) {
      //  result = value.substring(m.start(0),m.end(0));
      //  }
        try{
        URL url = new URL(value);
        result = url.getHost();
        }
        catch(Exception e){
            
        }
        
    return result;
    }
}