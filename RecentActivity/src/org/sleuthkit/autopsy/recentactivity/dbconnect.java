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
           
        }
        
        
    }
