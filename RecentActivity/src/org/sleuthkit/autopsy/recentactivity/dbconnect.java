/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.recentactivity;

import java.sql.*;
/**
 *
 * @author Alex
 */
 public class dbconnect extends sqlitedbconnect{
        
        private String sDriverForclass = "org.sqlite.JDBC";
        public dbconnect(String sDriverForClass, String sUrlKey) throws Exception
        { 
            init(sDriverForClass, sUrlKey); 
            //Statement stmt = conn.createStatement();
            //String selecthistory = "SELECT moz_historyvisits.id,url,title,visit_count,visit_date,from_visit,rev_host FROM moz_places, moz_historyvisits WHERE moz_places.id = moz_historyvisits.place_id AND hidden = 0";
           // ResultSet rs = stmt.executeQuery(selecthistory); 
           
        }
        
        
    }
