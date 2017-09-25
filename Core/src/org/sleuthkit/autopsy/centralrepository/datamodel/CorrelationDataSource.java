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

import java.io.Serializable;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskDataException;

/**
 *
 * Stores information about a Data Source in the correlation engine
 *
 */
public class CorrelationDataSource implements Serializable {

    private static final long serialVersionUID = 1L;

    private final int dataSourceId;   //< Id in the central repo
    private final String deviceID;
    private final String name;


    CorrelationDataSource(int dataSourceId,
            String deviceID,
            String name) {
        this.dataSourceId = dataSourceId;
        this.deviceID = deviceID;
        this.name = name;
    }
    
    /**
     * Create a CorrelationDataSource object from a TSK Content object.
     * 
     * @param dataSource
     * @return 
     * @throws EamDbException 
     */
    public static CorrelationDataSource fromTSKDataSource(Content dataSource) throws EamDbException {
        Case curCase;
        try {
            curCase = Case.getCurrentCase();
        } catch (IllegalStateException ex) {
            throw new EamDbException("Autopsy case is closed");
        }
        String deviceId;
        try {
            deviceId = curCase.getSleuthkitCase().getDataSource(dataSource.getId()).getDeviceId();
        } catch (TskDataException | TskCoreException ex) {
            throw new EamDbException("Error getting data source info: " + ex.getMessage());
        }
        return new CorrelationDataSource(-1, deviceId, dataSource.getName());
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("(");
        str.append("ID=").append(Integer.toString(getDataSourceID()));
        str.append(",deviceID=").append(getDeviceID());
        str.append(",name=").append(getName());
        str.append(")");
        return str.toString();
    }

    /**
     * @return the ID
     */
    public int getDataSourceID() {
        return dataSourceId;
    }

    /**
     * @return the deviceID
     */
    public String getDeviceID() {
        return deviceID;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }
}
