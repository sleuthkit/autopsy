/*
 * Central Repository
 *
 * Copyright 2015-2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.centralrepository.datamodel;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * CallBack Interface to process artifact instance. Used in EamDb.processInstance
 * to process instances in instances table in Central Repository
 */
public interface InstanceTableCallback {
    
    /**
    * Process the artifact instance
    * 
    * @param resultSet artifact instance table.
    */
    public void process(ResultSet resultSet);
    
    
    /**
     * 
     * @param resultSet artifact instance table
     * @return ID of the instance
     * @throws SQLException 
     */
    public static int getId(ResultSet resultSet) throws SQLException{
        return resultSet.getInt("id");
    }
    
    /**
     * 
     * @param resultSet artifact instance table
     * @return Case ID of a given instance
     * @throws SQLException 
     */
    public static int getCaseId(ResultSet resultSet) throws SQLException {
        return resultSet.getInt("case_id");
    }
    
    /**
     * 
     * @param resultSet artifact instance table
     * @return Data source id of a particular instance
     * @throws SQLException 
     */
    public static int getDataSourceId(ResultSet resultSet) throws SQLException {
        return resultSet.getInt("data_source_id");
    }
    
    /**
     * 
     * @param resultSet artifact instance table
     * @return md5 hash value of the instance
     * @throws SQLException 
     */
    public static String getValue(ResultSet resultSet) throws SQLException {
        return resultSet.getString("value");
    }
    
    /**
     * 
     * @param resultSet artifact instance table
     * @return file path of the instance
     * @throws SQLException 
     */
    public static String getFilePath(ResultSet resultSet) throws SQLException {
        return resultSet.getString("file_path");
    }
    
    /**
     * 
     * @param resultSet artifact instance table
     * @return status integer based on whether instance is marked notable or not
     * @throws SQLException 
     */
    public static int getKnownStatus(ResultSet resultSet) throws SQLException {
        return resultSet.getInt("known_status");
    }
    
    /**
     * 
     * @param resultSet artifact instance table
     * @return previous comment made for the instance
     * @throws SQLException 
     */
    public static String getComment(ResultSet resultSet) throws SQLException {
        return resultSet.getString("comment");
    }
    
}
