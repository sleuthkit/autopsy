 /*
 *
 * Autopsy Forensic Browser
 * 
 * Copyright 2012-2018 Basis Technology Corp.
 * 
 * Copyright 2012 42six Solutions.
 * Contact: aebadirad <at> 42six <dot> com
 * Project Contact/Architect: carrier <at> sleuthkit <dot> org
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

import org.sleuthkit.autopsy.coreutils.SQLiteDBConnect;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 * @author Alex
 */
class Util {

    private static Logger logger = Logger.getLogger(Util.class.getName());

    private Util() {
    }

    public static boolean pathexists(String path) {
        File file = new File(path);
        boolean exists = file.exists();
        return exists;
    }

    public static String utcConvert(String utc) {
        SimpleDateFormat formatter = new SimpleDateFormat("MM-dd-yyyy HH:mm");
        String tempconvert = formatter.format(new Date(Long.parseLong(utc)));
        return tempconvert;
    }

    public static String readFile(String path) throws IOException {
        FileInputStream stream = new FileInputStream(new File(path));
        try {
            FileChannel fc = stream.getChannel();
            MappedByteBuffer bb = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
            /*
             * Instead of using default, pass in a decoder.
             */
            return Charset.defaultCharset().decode(bb).toString();
        } finally {
            stream.close();
        }
    }

    public static String getBaseDomain(String url) {
        String host = null;
        //strip protocol
        String cleanUrl = url.replaceFirst("/.*:\\/\\//", "");

        //strip after slashes
        String dirToks[] = cleanUrl.split("/\\//");
        if (dirToks.length > 0) {
            host = dirToks[0];
        } else {
            host = cleanUrl;
        }

        //get the domain part from host (last 2)
        StringTokenizer tok = new StringTokenizer(host, ".");
        StringBuilder hostB = new StringBuilder();
        int toks = tok.countTokens();

        for (int count = 0; count < toks; ++count) {
            String part = tok.nextToken();
            int diff = toks - count;
            if (diff < 3) {
                hostB.append(part);
            }
            if (diff == 2) {
                hostB.append(".");
            }
        }

        return hostB.toString();
    }

    public static String extractDomain(String value) {
        if (value == null) {
            return "";

        }
        String result = "";
        // String domainPattern = "(\\w+)\\.(AC|AD|AE|AERO|AF|AG|AI|AL|AM|AN|AO|AQ|AR|ARPA|AS|ASIA|AT|AU|AW|AX|AZ|BA|BB|BD|BE|BF|BG|BH|BI|BIZ|BJ|BM|BN|BO|BR|BS|BT|BV|BW|BY|BZ|CA|CAT|CC|CD|CF|CG|CH|CI|CK|CL|CM|CN|CO|COM|COOP|CR|CU|CV|CW|CX|CY|CZ|DE|DJ|DK|DM|DO|DZ|EC|EDU|EE|EG|ER|ES|ET|EU|FI|FJ|FK|FM|FO|FR|GA|GB|GD|GE|GF|GG|GH|GI|GL|GM|GN|GOV|GP|GQ|GR|GS|GT|GU|GW|GY|HK|HM|HN|HR|HT|HU|ID|IE|IL|IM|IN|INFO|INT|IO|IQ|IR|IS|IT|JE|JM|JO|JOBS|JP|KE|KG|KH|KI|KM|KN|KP|KR|KW|KY|KZ|LA|LB|LC|LI|LK|LR|LS|LT|LU|LV|LY|MA|MC|MD|ME|MG|MH|MIL|MK|ML|MM|MN|MO|MOBI|MP|MQ|MR|MS|MT|MU|MUSEUM|MV|MW|MX|MY|MZ|NA|NAME|NC|NE|NET|NF|NG|NI|NL|NO|NP|NR|NU|NZ|OM|ORG|PA|PE|PF|PG|PH|PK|PL|PM|PN|PR|PRO|PS|PT|PW|PY|QA|RE|RO|RS|RU|RW|SA|SB|SC|SD|SE|SG|SH|SI|SJ|SK|SL|SM|SN|SO|SR|ST|SU|SV|SX|SY|SZ|TC|TD|TEL|TF|TG|TH|TJ|TK|TL|TM|TN|TO|TP|TR|TRAVEL|TT|TV|TW|TZ|UA|UG|UK|US|UY|UZ|VA|VC|VE|VG|VI|VN|VU|WF|WS|XXX|YE|YT|ZA|ZM|ZW(co\\.[a-z].))";
        //  Pattern p = Pattern.compile(domainPattern,Pattern.CASE_INSENSITIVE);
        //  Matcher m = p.matcher(value);
        //  while (m.find()) {
        //  result = value.substring(m.start(0),m.end(0));
        //  }

        try {
            URL url = new URL(value);
            result = url.getHost();
        } catch (MalformedURLException ex) {
            //do not log if not a valid URL, and handle later
            //Logger.getLogger(Util.class.getName()).log(Level.SEVERE, null, ex);
        }

        //was not a valid URL, try a less picky method
        if (result == null || result.trim().isEmpty()) {
            return getBaseDomain(value);
        }

        return result;
    }

    public static String getFileName(String value) {
        String filename = "";
        String filematch = "^([a-zA-Z]\\:)(\\\\[^\\\\/:*?<>\"|]*(?<!\\[ \\]))*(\\.[a-zA-Z]{2,6})$"; //NON-NLS

        Pattern p = Pattern.compile(filematch, Pattern.CASE_INSENSITIVE | Pattern.DOTALL | Pattern.COMMENTS);
        Matcher m = p.matcher(value);
        if (m.find()) {
            filename = m.group(1);

        }
        int lastPos = value.lastIndexOf('\\');
        filename = (lastPos < 0) ? value : value.substring(lastPos + 1);
        return filename.toString();
    }

    public static String getPath(String txt) {
        String path = "";

        //String drive ="([a-z]:\\\\(?:[-\\w\\.\\d]+\\\\)*(?:[-\\w\\.\\d]+)?)";	// Windows drive
        String drive = "([a-z]:\\\\\\S.+)"; //NON-NLS
        Pattern p = Pattern.compile(drive, Pattern.CASE_INSENSITIVE | Pattern.COMMENTS);
        Matcher m = p.matcher(txt);
        if (m.find()) {
            path = m.group(1);

        } else {

            String network = "(\\\\(?:\\\\[^:\\s?*\"<>|]+)+)";    // Windows network NON-NLS

            Pattern p2 = Pattern.compile(network, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            Matcher m2 = p2.matcher(txt);
            if (m2.find()) {
                path = m2.group(1);
            }
        }
        return path;
    }

    public static long findID(Content dataSource, String path) {
        String parent_path = path.replace('\\', '/'); // fix Chrome paths
        if (parent_path.length() > 2 && parent_path.charAt(1) == ':') {
            parent_path = parent_path.substring(2); // remove drive letter (e.g., 'C:')
        }
        int index = parent_path.lastIndexOf('/');
        String name = parent_path.substring(++index);
        parent_path = parent_path.substring(0, index);
        List<AbstractFile> files = null;
        try {
            FileManager fileManager = Case.getOpenCase().getServices().getFileManager();
            files = fileManager.findFiles(dataSource, name, parent_path);
        } catch (TskCoreException | NoCurrentCaseException ex) {
            logger.log(Level.WARNING, "Error fetching 'index.data' files for Internet Explorer history."); //NON-NLS
        }

        if (files == null || files.isEmpty()) {
            return -1;
        }
        return files.get(0).getId();
    }

    public static boolean checkColumn(String column, String tablename, String connection) {
        String query = "PRAGMA table_info(" + tablename + ")"; //NON-NLS
        boolean found = false;
        ResultSet temprs;
        try {
            SQLiteDBConnect tempdbconnect = new SQLiteDBConnect("org.sqlite.JDBC", "jdbc:sqlite:" + connection); //NON-NLS
            temprs = tempdbconnect.executeQry(query);
            while (temprs.next()) {
                if (temprs.getString("name") == null ? column == null : temprs.getString("name").equals(column)) { //NON-NLS
                    found = true;
                }
            }
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Error while trying to get columns from sqlite db." + connection, ex); //NON-NLS
        }
        return found;
    }

    public static ResultSet runQuery(String query, String connection) {
        ResultSet results = null;
        try {
            SQLiteDBConnect tempdbconnect = new SQLiteDBConnect("org.sqlite.JDBC", "jdbc:sqlite:" + connection); //NON-NLS
            results = tempdbconnect.executeQry(query);
            tempdbconnect.closeConnection();
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Error while trying to run sql query: " + query + " : " + connection, ex); //NON-NLS
        }
        return results;
    }
}
