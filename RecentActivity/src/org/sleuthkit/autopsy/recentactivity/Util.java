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
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
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
 * RecentActivity utility class.
 */
class Util {

    private static Logger logger = Logger.getLogger(Util.class.getName());
    
    /** Difference between Filetime epoch and Unix epoch (in ms). */
    private static final long FILETIME_EPOCH_DIFF = 11644473600000L;

    /** One millisecond expressed in units of 100s of nanoseconds. */
    private static final long FILETIME_ONE_MILLISECOND = 10 * 1000;

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
            files = Case.getCurrentCaseThrows().getSleuthkitCase().getFileManager().findFilesExactNameExactPath(dataSource, name, parent_path);
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
        SQLiteDBConnect tempdbconnect = null;
        try {
            tempdbconnect = new SQLiteDBConnect("org.sqlite.JDBC", "jdbc:sqlite:" + connection); //NON-NLS
            temprs = tempdbconnect.executeQry(query);
            while (temprs.next()) {
                if (temprs.getString("name") == null ? column == null : temprs.getString("name").equals(column)) { //NON-NLS
                    found = true;
                }
            }
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Error while trying to get columns from sqlite db." + connection, ex); //NON-NLS
        }
        finally{
            if (tempdbconnect != null) {
                tempdbconnect.closeConnection();
            }
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
    
    /**
     * Converts a windows FILETIME to java-unix epoch milliseconds
     * 
     * @param filetime 100 nanosecond intervals from jan 1, 1601
     * 
     * @return java-unix epoch milliseconds
     */
    static long filetimeToMillis(final long filetime) {
        return (filetime / FILETIME_ONE_MILLISECOND) - FILETIME_EPOCH_DIFF;
    }

}
