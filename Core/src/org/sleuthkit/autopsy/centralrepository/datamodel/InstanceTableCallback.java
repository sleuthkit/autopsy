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
 * CallBack Interface to process attribute instance Table. Used in EamDb.processInstanceTable
 * is called only once. The implementation of this method needs to call resultset.next to
 * loop through each row of attribute instance table.
 */
public interface InstanceTableCallback {
    
    /**
    * Process the attribute instance
    * 
    * @param resultSet attribute instance table.
    */
    void process(ResultSet resultSet);
    
    
    /**
     * 
     * @param resultSet attribute instance table
     * @return ID of the instance
     * @throws SQLException 
     */
    static int getId(ResultSet resultSet) throws SQLException{
        return resultSet.getInt("id");
    }
    
    /**
     * 
     * @param resultSet attribute instance table
     * @return Case ID of a given instance
     * @throws SQLException 
     */
    static int getCaseId(ResultSet resultSet) throws SQLException {
        return resultSet.getInt("case_id");
    }
    
    /**
     * 
     * @param resultSet attribute instance table
     * @return Data source id of a particular instance
     * @throws SQLException 
     */
    static int getDataSourceId(ResultSet resultSet) throws SQLException {
        return resultSet.getInt("data_source_id");
    }
    
    /**
     * 
     * @param resultSet attribute instance table
     * @return md5 hash value of the instance
     * @throws SQLException 
     */
    static String getValue(ResultSet resultSet) throws SQLException {
        return resultSet.getString("value");
    }
    
    /**
     * 
     * @param resultSet attribute instance table
     * @return file path of the instance
     * @throws SQLException 
     */
    static String getFilePath(ResultSet resultSet) throws SQLException {
        return resultSet.getString("file_path");
    }
    
    /**
     * 
     * @param resultSet attribute instance table
     * @return status integer based on whether instance is marked notable or not
     * @throws SQLException 
     */
    static int getKnownStatus(ResultSet resultSet) throws SQLException {
        return resultSet.getInt("known_status");
    }
    
    /**
     * 
     * @param resultSet attribute instance table
     * @return previous comment made for the instance
     * @throws SQLException 
     */
    static String getComment(ResultSet resultSet) throws SQLException {
        return resultSet.getString("comment");
    }
    
    static long getFileObjectId(ResultSet resultSet) throws SQLException {
        return resultSet.getLong("file_obj_id");
    }
}
