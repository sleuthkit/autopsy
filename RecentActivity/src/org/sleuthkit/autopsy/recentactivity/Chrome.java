 /*
 *
 * Autopsy Forensic Browser
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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import java.sql.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.*;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import org.sleuthkit.autopsy.ingest.IngestImageWorkerController;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.ServiceDataEvent;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;

/**
 *
 * @author Alex
 */
public class Chrome {

    public static final String chquery = "SELECT urls.url, urls.title, urls.visit_count, urls.typed_count, "
            + "last_visit_time, urls.hidden, visits.visit_time, (SELECT urls.url FROM urls WHERE urls.id=visits.url) as from_visit, visits.transition FROM urls, visits WHERE urls.id = visits.url";
    public static final String chcookiequery = "select name, value, host_key, expires_utc,last_access_utc, creation_utc from cookies";
    public static final String chbookmarkquery = "SELECT starred.title, urls.url, starred.date_added, starred.date_modified, urls.typed_count,urls._last_visit_time FROM starred INNER JOIN urls ON urls.id = starred.url_id";
    public static final String chdownloadquery = "select full_path, url, start_time, received_bytes from downloads";
    public static final String chloginquery = "select origin_url, username_value, signon_realm from logins";
    private final Logger logger = Logger.getLogger(this.getClass().getName());
    public int ChromeCount = 0;

    public Chrome() {
    }

    public void getchdb(List<String> image, IngestImageWorkerController controller) {

        try {
            Case currentCase = Case.getCurrentCase(); // get the most updated case
            SleuthkitCase tempDb = currentCase.getSleuthkitCase();
            List<FsContent> FFSqlitedb = null;
            Map<String, Object> kvs = new LinkedHashMap<String, Object>();
            String allFS = new String();
            for (int i = 0; i < image.size(); i++) {
                if (i == 0) {
                    allFS += " AND (0";
                }
                allFS += " OR fs_obj_id = '" + image.get(i) + "'";
                if (i == image.size() - 1) {
                    allFS += ")";
                }
            }

            try {
                ResultSet rs = tempDb.runQuery("select * from tsk_files where name LIKE 'History' and name NOT LIKE '%journal%' AND parent_path LIKE '%Chrome%'" + allFS);
                FFSqlitedb = tempDb.resultSetToFsContents(rs);
                ChromeCount = FFSqlitedb.size();
                rs.close();
                rs.getStatement().close();
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Error while trying to read into a sqlite db.{0}", ex);
            }
            int j = 0;
            while (j < FFSqlitedb.size()) {
                String temps = currentCase.getTempDirectory() + "\\" + FFSqlitedb.get(j).getName().toString() + j + ".db";
                String connectionString = "jdbc:sqlite:" + temps;
                try {
                    ContentUtils.writeToFile(FFSqlitedb.get(j), new File(currentCase.getTempDirectory() + "\\" + FFSqlitedb.get(j).getName().toString() + j + ".db"));
                } catch (Exception ex) {
                    logger.log(Level.WARNING, "Error while trying to write to disk.{0}", ex);
                }
                File dbFile = new File(temps);
                if (controller.isCancelled()) {
                    dbFile.delete();
                    break;
                }
                try {
                    dbconnect tempdbconnect = new dbconnect("org.sqlite.JDBC", connectionString);
                    ResultSet temprs = tempdbconnect.executeQry(chquery);

                    while (temprs.next()) {
                        try {
                            String domain = Util.extractDomain(temprs.getString("url"));
                            BlackboardArtifact bbart = FFSqlitedb.get(j).newArtifact(ARTIFACT_TYPE.TSK_WEB_HISTORY);
                            Collection<BlackboardAttribute> bbattributes = new ArrayList<BlackboardAttribute>();
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL.getTypeID(), "RecentActivity", "", temprs.getString("url")));
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_LAST_ACCESSED.getTypeID(), "RecentActivity", "Last Accessed", (temprs.getLong("last_visit_time") / 10000)));
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_REFERRER.getTypeID(), "RecentActivity", "", temprs.getString("from_visit")));
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME.getTypeID(), "RecentActivity", "", ((temprs.getString("title") != null) ? temprs.getString("title") : "")));
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(), "RecentActivity", "", "Chrome"));
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN.getTypeID(), "RecentActivity", "", domain));
                            bbart.addAttributes(bbattributes);
                        } catch (Exception ex) {
                            logger.log(Level.WARNING, "Error while trying to insert BB artifact.{0}", ex);
                        }

                    }
                    tempdbconnect.closeConnection();
                    temprs.close();

                } catch (Exception ex) {
                    logger.log(Level.WARNING, "Error while trying to read into a sqlite db." + connectionString, ex);
                }

                j++;
                dbFile.delete();
            }
            IngestManager.fireServiceDataEvent(new ServiceDataEvent("Recent Activity", BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_HISTORY));
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Error while trying to get Chrome SQLite db.", ex);
        }


        //COOKIES section
        // This gets the cookie info
        try {
            Case currentCase = Case.getCurrentCase(); // get the most updated case
            SleuthkitCase tempDb = currentCase.getSleuthkitCase();
            String allFS = new String();
            for (int i = 0; i < image.size(); i++) {
                if (i == 0) {
                    allFS += " AND (0";
                }
                allFS += " OR fs_obj_id = '" + image.get(i) + "'";
                if (i == image.size() - 1) {
                    allFS += ")";
                }
            }
            List<FsContent> FFSqlitedb = null;
            try {
                ResultSet rs = tempDb.runQuery("select * from tsk_files where name LIKE '%Cookies%' and name NOT LIKE '%journal%' and parent_path LIKE '%Chrome%'" + allFS);
                FFSqlitedb = tempDb.resultSetToFsContents(rs);
                rs.close();
                rs.getStatement().close();
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Error while trying to read into a sqlite db.{0}", ex);
            }
            int j = 0;

            while (j < FFSqlitedb.size()) {
                String temps = currentCase.getTempDirectory() + "\\" + FFSqlitedb.get(j).getName().toString() + j + ".db";
                String connectionString = "jdbc:sqlite:" + temps;
                try {
                    ContentUtils.writeToFile(FFSqlitedb.get(j), new File(currentCase.getTempDirectory() + "\\" + FFSqlitedb.get(j).getName().toString() + j + ".db"));
                } catch (Exception ex) {
                    logger.log(Level.WARNING, "Error while trying to write IO.{0}", ex);
                }
                File dbFile = new File(temps);
                if (controller.isCancelled()) {
                    dbFile.delete();
                    break;
                }
                try {
                    dbconnect tempdbconnect = new dbconnect("org.sqlite.JDBC", connectionString);
                    ResultSet temprs = tempdbconnect.executeQry(chcookiequery);
                    while (temprs.next()) {
                        try {
                            BlackboardArtifact bbart = FFSqlitedb.get(j).newArtifact(ARTIFACT_TYPE.TSK_WEB_COOKIE);
                            Collection<BlackboardAttribute> bbattributes = new ArrayList<BlackboardAttribute>();
                            String domain = temprs.getString("host_key");
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL.getTypeID(), "RecentActivity", "", temprs.getString("host_key")));
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID(), "RecentActivity", "Last Visited", (temprs.getLong("last_access_utc") / 10000)));
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_VALUE.getTypeID(), "RecentActivity", "", temprs.getString("value")));
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME.getTypeID(), "RecentActivity", "Title", ((temprs.getString("name") != null) ? temprs.getString("name") : "")));
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(), "RecentActivity", "", "Chrome"));
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN.getTypeID(), "RecentActivity", "", domain));
                            bbart.addAttributes(bbattributes);
                        } catch (Exception ex) {
                            logger.log(Level.WARNING, "Error while trying to read into a sqlite db.{0}", ex);
                        }
                    }
                    tempdbconnect.closeConnection();
                    temprs.close();

                } catch (Exception ex) {
                    logger.log(Level.WARNING, "Error while trying to read into a sqlite db." + connectionString, ex);
                }
                j++;
                dbFile.delete();
            }
            IngestManager.fireServiceDataEvent(new ServiceDataEvent("Recent Activity", BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_COOKIE));
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Error while trying to get Chrome SQLite db.", ex);
        }


        //BOokmarks section
        // This gets the bm info
        try {
            Case currentCase = Case.getCurrentCase(); // get the most updated case
            SleuthkitCase tempDb = currentCase.getSleuthkitCase();
            String allFS = new String();
            for (int i = 0; i < image.size(); i++) {
                if (i == 0) {
                    allFS += " AND (0";
                }
                allFS += " OR fs_obj_id = '" + image.get(i) + "'";
                if (i == image.size() - 1) {
                    allFS += ")";
                }
            }
            List<FsContent> FFSqlitedb = null;
            try {
                ResultSet rs = tempDb.runQuery("select * from tsk_files where name LIKE 'Bookmarks' and name NOT LIKE '%journal%' and parent_path LIKE '%Chrome%'" + allFS);
                FFSqlitedb = tempDb.resultSetToFsContents(rs);
                rs.close();
                rs.getStatement().close();
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Error while trying to read into a sqlite db.{0}", ex);
            }
            int j = 0;

            while (j < FFSqlitedb.size()) {
                String temps = currentCase.getTempDirectory() + "\\" + FFSqlitedb.get(j).getName().toString() + j + ".db";
                try {
                    ContentUtils.writeToFile(FFSqlitedb.get(j), new File(currentCase.getTempDirectory() + "\\" + FFSqlitedb.get(j).getName().toString() + j + ".db"));
                } catch (Exception ex) {
                    logger.log(Level.WARNING, "Error while trying to write IO {0}", ex);
                }
                File dbFile = new File(temps);
                if (controller.isCancelled()) {
                    dbFile.delete();
                    break;
                }
                try {

                    final JsonParser parser = new JsonParser();
                    JsonElement jsonElement = parser.parse(new FileReader(temps));
                    JsonObject test = jsonElement.getAsJsonObject();
                    JsonObject whatever = test.get("roots").getAsJsonObject();
                    JsonObject whatever2 = whatever.get("bookmark_bar").getAsJsonObject();
                    JsonArray whatever3 = whatever2.getAsJsonArray("children");
                    for (JsonElement result : whatever3) {
                        try {
                            JsonObject address = result.getAsJsonObject();
                            String url = address.get("url").getAsString();
                            String name = address.get("name").getAsString();
                            Long date = address.get("date_added").getAsLong();
                            String domain = Util.extractDomain(url);
                            BlackboardArtifact bbart = FFSqlitedb.get(j).newArtifact(ARTIFACT_TYPE.TSK_WEB_BOOKMARK);
                            Collection<BlackboardAttribute> bbattributes = new ArrayList<BlackboardAttribute>();
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_LAST_ACCESSED.getTypeID(), "RecentActivity", "Last Visited", (date / 10000)));
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL.getTypeID(), "RecentActivity", "", url));
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME.getTypeID(), "RecentActivity", "", name));
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(), "RecentActivity", "", "Chrome"));
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN.getTypeID(), "RecentActivity", "", domain));
                            bbart.addAttributes(bbattributes);
                        } catch (Exception ex) {
                            logger.log(Level.WARNING, "Error while trying to insert BB artifact{0}", ex);
                        }
                    }


                } catch (Exception ex) {
                    logger.log(Level.WARNING, "Error while trying to read into the Bookmarks for Chrome." + ex);
                }
                j++;
                dbFile.delete();
            }
            IngestManager.fireServiceDataEvent(new ServiceDataEvent("Recent Activity", BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_BOOKMARK));
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Error while trying to get Chrome SQLite db.", ex);
        }


        //Downloads section
        // This gets the downloads info
        try {
            Case currentCase = Case.getCurrentCase(); // get the most updated case
            SleuthkitCase tempDb = currentCase.getSleuthkitCase();
            List<FsContent> FFSqlitedb = null;
            String allFS = new String();
            for (int i = 0; i < image.size(); i++) {
                if (i == 0) {
                    allFS += " AND (0";
                }
                allFS += " OR fs_obj_id = '" + image.get(i) + "'";
                if (i == image.size() - 1) {
                    allFS += ")";
                }
            }
            try {
                ResultSet rs = tempDb.runQuery("select * from tsk_files where name LIKE 'History' and name NOT LIKE '%journal%' and  parent_path LIKE '%Chrome%'" + allFS);
                FFSqlitedb = tempDb.resultSetToFsContents(rs);
                rs.close();
                rs.getStatement().close();
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Error while trying to read into a sqlite db.{0}", ex);
            }
            int j = 0;

            while (j < FFSqlitedb.size()) {
                String temps = currentCase.getTempDirectory() + "\\" + FFSqlitedb.get(j).getName().toString() + j + ".db";
                String connectionString = "jdbc:sqlite:" + temps;
                try {
                    ContentUtils.writeToFile(FFSqlitedb.get(j), new File(currentCase.getTempDirectory() + "\\" + FFSqlitedb.get(j).getName().toString() + j + ".db"));
                } catch (Exception ex) {
                    logger.log(Level.WARNING, "Error while trying to read into a sqlite db.{0}", ex);
                }
                File dbFile = new File(temps);
                if (controller.isCancelled()) {
                    dbFile.delete();
                    break;
                }
                try {
                    dbconnect tempdbconnect = new dbconnect("org.sqlite.JDBC", connectionString);
                    ResultSet temprs = tempdbconnect.executeQry(chdownloadquery);
                    while (temprs.next()) {
                        try {
                            BlackboardArtifact bbart = FFSqlitedb.get(j).newArtifact(ARTIFACT_TYPE.TSK_WEB_DOWNLOAD);
                            Collection<BlackboardAttribute> bbattributes = new ArrayList<BlackboardAttribute>();
                            String domain = Util.extractDomain(temprs.getString("url"));
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_LAST_ACCESSED.getTypeID(), "RecentActivity", "Last Visited", (temprs.getLong("start_time") / 10000)));
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL.getTypeID(), "RecentActivity", "", ((temprs.getString("url") != null) ? temprs.getString("url") : "")));
                            //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME.getTypeID(), "RecentActivity","", ((temprs.getString("title") != null) ? temprs.getString("title").replaceAll("'", "''") : "")));
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PATH.getTypeID(), "Recent Activity", "", temprs.getString("full_path")));
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PATH_ID.getTypeID(), "RecentActivity", "", Util.findID(temprs.getString("full_path"))));
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN.getTypeID(), "RecentActivity", "", domain));
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(), "RecentActivity", "", "Chrome"));
                            bbart.addAttributes(bbattributes);
                        } catch (Exception ex) {
                            logger.log(Level.WARNING, "Error while trying to read into a sqlite db.{0}", ex);
                        }

                    }
                    tempdbconnect.closeConnection();
                    temprs.close();
                    IngestManager.fireServiceDataEvent(new ServiceDataEvent("Recent Activity", BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_DOWNLOAD));

                } catch (Exception ex) {
                    logger.log(Level.WARNING, "Error while trying to read into a sqlite db." + connectionString, ex);
                }
                j++;
                dbFile.delete();
            }
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Error while trying to get Chrome SQLite db.", ex);
        }


        //Login/Password section
        // This gets the user info
        try {
            Case currentCase = Case.getCurrentCase(); // get the most updated case
            SleuthkitCase tempDb = currentCase.getSleuthkitCase();
            String allFS = new String();
            for (int i = 0; i < image.size(); i++) {
                if (i == 0) {
                    allFS += " AND (0";
                }
                allFS += " OR fs_obj_id = '" + image.get(i) + "'";
                if (i == image.size() - 1) {
                    allFS += ")";
                }
            }
            List<FsContent> FFSqlitedb = null;
            try {
                ResultSet rs = tempDb.runQuery("select * from tsk_files where name LIKE 'signons.sqlite' and name NOT LIKE '%journal%' and parent_path LIKE '%Chrome%'" + allFS);
                FFSqlitedb = tempDb.resultSetToFsContents(rs);
                rs.close();
                rs.getStatement().close();
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Error while trying to read into a sqlite db.{0}", ex);
            }
            int j = 0;

            while (j < FFSqlitedb.size()) {
                String temps = currentCase.getTempDirectory() + "\\" + FFSqlitedb.get(j).getName().toString() + j + ".db";
                String connectionString = "jdbc:sqlite:" + temps;
                try {
                    ContentUtils.writeToFile(FFSqlitedb.get(j), new File(currentCase.getTempDirectory() + "\\" + FFSqlitedb.get(j).getName().toString() + j + ".db"));
                } catch (Exception ex) {
                    logger.log(Level.WARNING, "Error while trying to read into a sqlite db.{0}", ex);
                }
                File dbFile = new File(temps);
                if (controller.isCancelled()) {
                    dbFile.delete();
                    break;
                }
                try {
                    dbconnect tempdbconnect = new dbconnect("org.sqlite.JDBC", connectionString);
                    ResultSet temprs = tempdbconnect.executeQry(chloginquery);
                    while (temprs.next()) {
                        try {
                            BlackboardArtifact bbart = FFSqlitedb.get(j).newArtifact(ARTIFACT_TYPE.TSK_WEB_HISTORY);
                            Collection<BlackboardAttribute> bbattributes = new ArrayList<BlackboardAttribute>();
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL.getTypeID(), "RecentActivity", "", ((temprs.getString("origin_url") != null) ? temprs.getString("origin_url") : "")));
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_USERNAME.getTypeID(), "RecentActivity", "", ((temprs.getString("username_value") != null) ? temprs.getString("username_value").replaceAll("'", "''") : "")));
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN.getTypeID(), "Recent Activity", "", temprs.getString("signon_realm")));
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN.getTypeID(), "RecentActivity", "", Util.extractDomain(((temprs.getString("origin_url") != null) ? temprs.getString("origin_url") : ""))));
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(), "RecentActivity", "", "Chrome"));
                            bbart.addAttributes(bbattributes);
                        } catch (Exception ex) {
                            logger.log(Level.WARNING, "Error while trying to read into a sqlite db.{0}", ex);
                        }
                    }
                    tempdbconnect.closeConnection();
                    temprs.close();

                } catch (Exception ex) {
                    logger.log(Level.WARNING, "Error while trying to read into a sqlite db." + connectionString, ex);
                }
                j++;
                dbFile.delete();
            }
            IngestManager.fireServiceDataEvent(new ServiceDataEvent("Recent Activity", BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_HISTORY));
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Error while trying to get Chrome SQLite db.", ex);
        }

    }
}
