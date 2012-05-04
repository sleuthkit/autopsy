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

import java.io.File;
import java.net.URLDecoder;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.ingest.IngestImageWorkerController;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.ServiceDataEvent;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.SleuthkitCase;

/**
 *
 * @author Alex
 */
public class Firefox {

    private static final String ffquery = "SELECT moz_historyvisits.id,url,title,visit_count,(visit_date/1000) as visit_date,from_visit,(SELECT url FROM moz_places WHERE id=moz_historyvisits.from_visit) as ref FROM moz_places, moz_historyvisits WHERE moz_places.id = moz_historyvisits.place_id AND hidden = 0";
    private static final String ffcookiequery = "SELECT name,value,host,expiry,(lastAccessed/1000) as lastAccessed,(creationTime/1000) as creationTime FROM moz_cookies";
    private static final String ff3cookiequery = "SELECT name,value,host,expiry,(lastAccessed/1000) as lastAccessed FROM moz_cookies";
    private static final String ffbookmarkquery = "SELECT fk, moz_bookmarks.title, url FROM moz_bookmarks INNER JOIN moz_places ON moz_bookmarks.fk=moz_places.id";
    private static final String ffdownloadquery = "select target, source,(startTime/1000) as startTime, maxBytes  from moz_downloads";
    public Logger logger = Logger.getLogger(this.getClass().getName());
    public int FireFoxCount = 0;

    public Firefox() {
    }

    public void getffdb(List<String> image, IngestImageWorkerController controller) {
        //Make these seperate, this is for history
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
                ResultSet rs = tempDb.runQuery("select * from tsk_files where name LIKE '%places.sqlite%' and name NOT LIKE '%journal%' and parent_path LIKE '%Firefox%'" + allFS);
                FFSqlitedb = tempDb.resultSetToFsContents(rs);
                Statement s = rs.getStatement();
                rs.close();
                if (s != null) {
                    s.close();
                    FireFoxCount = FFSqlitedb.size();
                }
                rs.close();
                rs.getStatement().close();
            } catch (SQLException ex) {
                logger.log(Level.WARNING, "Error while trying to get Firefox SQLite db.", ex);
            }

            int j = 0;
              if(FFSqlitedb != null && !FFSqlitedb.isEmpty())
            {
            while (j < FFSqlitedb.size()) {
                String temps = currentCase.getTempDirectory() + File.separator + FFSqlitedb.get(j).getName().toString() + j + ".db";
                String connectionString = "jdbc:sqlite:" + temps;
                try {
                    ContentUtils.writeToFile(FFSqlitedb.get(j), new File(currentCase.getTempDirectory() + File.separator + FFSqlitedb.get(j).getName().toString() + j + ".db"));
                } catch (Exception ex) {
                    logger.log(Level.WARNING, "Error while trying to read into a sqlite db.{0}", ex);
                }
                File dbFile = new File(temps);
                if (controller.isCancelled()) {
                    dbFile.delete();
                    break;
                }
                    dbconnect tempdbconnect = new dbconnect("org.sqlite.JDBC", connectionString);
                    ResultSet temprs = tempdbconnect.executeQry(ffquery);
                while (temprs.next()) {
                    try {
                        BlackboardArtifact bbart = FFSqlitedb.get(j).newArtifact(ARTIFACT_TYPE.TSK_WEB_HISTORY);
                        Collection<BlackboardAttribute> bbattributes = new ArrayList<BlackboardAttribute>();
                        bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL.getTypeID(), "RecentActivity", "", ((temprs.getString("url") != null) ? temprs.getString("url") : "")));
                        bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_LAST_ACCESSED.getTypeID(), "RecentActivity", "Last Visited", temprs.getLong("visit_date")));
                        bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_REFERRER.getTypeID(), "RecentActivity", "", ((temprs.getString("ref") != null) ? temprs.getString("ref") : "")));
                        bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME.getTypeID(), "RecentActivity", "", ((temprs.getString("title") != null) ? temprs.getString("title") : "")));
                        bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(), "RecentActivity", "", "FireFox"));
                        bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN.getTypeID(), "RecentActivity", "", (Util.extractDomain((temprs.getString("url") != null) ? temprs.getString("url") : ""))));
                        bbart.addAttributes(bbattributes);
                    } catch (Exception ex) {
                        logger.log(Level.WARNING, "Error while trying to read into a sqlite db." + connectionString, ex);
                    }
                }
                temprs.close();
                tempdbconnect.closeConnection();



                try {
                    dbconnect tempdbconnect2 = new dbconnect("org.sqlite.JDBC", connectionString);
                    ResultSet tempbm = tempdbconnect2.executeQry(ffbookmarkquery);
                    while (tempbm.next()) {
                        try {
                            BlackboardArtifact bbart = FFSqlitedb.get(j).newArtifact(ARTIFACT_TYPE.TSK_WEB_BOOKMARK);
                            Collection<BlackboardAttribute> bbattributes = new ArrayList<BlackboardAttribute>();
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL.getTypeID(), "RecentActivity", "", ((tempbm.getString("url") != null) ? tempbm.getString("url") : "")));
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME.getTypeID(), "RecentActivity", "", ((tempbm.getString("title") != null) ? tempbm.getString("title").replaceAll("'", "''") : "")));
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(), "RecentActivity", "", "FireFox"));
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN.getTypeID(), "RecentActivity", "", Util.extractDomain(tempbm.getString("url"))));
                            bbart.addAttributes(bbattributes);
                        } catch (Exception ex) {
                            logger.log(Level.WARNING, "Error while trying to read into a sqlite db.{0}", ex);
                        }
                    }
                    tempbm.close();
                    tempdbconnect2.closeConnection();
                } catch (Exception ex) {
                    logger.log(Level.WARNING, "Error while trying to read into a sqlite db." + connectionString, ex);
                }


                j++;
                dbFile.delete();
            }
            IngestManager.fireServiceDataEvent(new ServiceDataEvent("Recent Activity", BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_HISTORY));
            IngestManager.fireServiceDataEvent(new ServiceDataEvent("Recent Activity", BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_BOOKMARK));
          }
        }
        catch (Exception ex) {
            logger.log(Level.WARNING, "Error while trying to read into a sqlite db.{0}", ex);
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
                ResultSet rs = tempDb.runQuery("select * from tsk_files where name LIKE '%cookies.sqlite%' and name NOT LIKE '%journal%' and parent_path LIKE '%Firefox%'" + allFS);
                FFSqlitedb = tempDb.resultSetToFsContents(rs);
                rs.close();
                rs.getStatement().close();
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Error while trying to read into a sqlite db.{0}", ex);
            }
            int j = 0;
            if(FFSqlitedb != null && !FFSqlitedb.isEmpty())
            {
            while (j < FFSqlitedb.size()) {
                String temps = currentCase.getTempDirectory() + File.separator + FFSqlitedb.get(j).getName().toString() + j + ".db";
                String connectionString = "jdbc:sqlite:" + temps;
                try {
                    ContentUtils.writeToFile(FFSqlitedb.get(j), new File(currentCase.getTempDirectory() + File.separator + FFSqlitedb.get(j).getName().toString() + j + ".db"));
                } catch (Exception ex) {
                    logger.log(Level.WARNING, "Error while trying to read into a sqlite db.{0}", ex);
                }
                File dbFile = new File(temps);
                if (controller.isCancelled()) {
                    dbFile.delete();
                    break;
                }
                boolean checkColumn = Util.checkColumn("creationTime", "moz_cookies", connectionString);
                String query;
                if (checkColumn) {
                    query = ffcookiequery;
                } else {
                    query = ff3cookiequery;
                }
                try {
                    dbconnect tempdbconnect = new dbconnect("org.sqlite.JDBC", connectionString);
                    ResultSet temprs = tempdbconnect.executeQry(query);
                    while (temprs.next()) {
                        try {
                            BlackboardArtifact bbart = FFSqlitedb.get(j).newArtifact(ARTIFACT_TYPE.TSK_WEB_COOKIE);
                            Collection<BlackboardAttribute> bbattributes = new ArrayList<BlackboardAttribute>();
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL.getTypeID(), "RecentActivity", "", temprs.getString("host")));
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID(), "RecentActivity", "Last Visited", temprs.getLong("lastAccessed")));
                            if (checkColumn == true) {
                                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID(), "RecentActivity", "Created", temprs.getLong("creationTime")));
                            }
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_VALUE.getTypeID(), "RecentActivity", "", temprs.getString("value")));
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME.getTypeID(), "RecentActivity", "Title", ((temprs.getString("name") != null) ? temprs.getString("name") : "")));
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(), "RecentActivity", "", "FireFox"));
                            String domain = Util.getBaseDomain(temprs.getString("host"));
                            domain = domain.replaceFirst("^\\.+(?!$)", "");
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
            }
            IngestManager.fireServiceDataEvent(new ServiceDataEvent("Recent Activity", BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_COOKIE));
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Error while trying to get Firefox SQLite db.", ex);
        }



        //Downloads section
        // This gets the downloads info
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
                ResultSet rs = tempDb.runQuery("select * from tsk_files where name LIKE 'downloads.sqlite' and name NOT LIKE '%journal%' and parent_path LIKE '%Firefox%'" + allFS);
                FFSqlitedb = tempDb.resultSetToFsContents(rs);
                rs.close();
                rs.getStatement().close();
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Error while trying to read into a sqlite db.{0}", ex);
            }

            int j = 0;
              if(FFSqlitedb != null && !FFSqlitedb.isEmpty())
            {
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
                    ResultSet temprs = tempdbconnect.executeQry(ffdownloadquery);
                    while (temprs.next()) {
                        try {
                            BlackboardArtifact bbart = FFSqlitedb.get(j).newArtifact(ARTIFACT_TYPE.TSK_WEB_DOWNLOAD);
                            Collection<BlackboardAttribute> bbattributes = new ArrayList<BlackboardAttribute>();
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_LAST_ACCESSED.getTypeID(), "RecentActivity", "Last Visited", temprs.getLong("startTime")));
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL.getTypeID(), "RecentActivity", "", ((temprs.getString("source") != null) ? temprs.getString("source") : "")));
                            String urldecodedtarget = URLDecoder.decode(temprs.getString("target").replaceAll("file:///", ""), "UTF-8");
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PATH_ID.getTypeID(), "RecentActivity", "", Util.findID(urldecodedtarget)));
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PATH.getTypeID(), "Recent Activity", "", urldecodedtarget));
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN.getTypeID(), "RecentActivity", "", Util.extractDomain(temprs.getString("source"))));
                            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(), "RecentActivity", "", "FireFox"));
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
            }
            IngestManager.fireServiceDataEvent(new ServiceDataEvent("Recent Activity", BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_DOWNLOAD));
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Error while trying to get FireFox SQLite db.", ex);
        }
    }
}
