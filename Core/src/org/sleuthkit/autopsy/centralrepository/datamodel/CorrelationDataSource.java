/*
 * Central Repository
 *
 * Copyright 2015-2018 Basis Technology Corp.
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
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
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

    private final int caseID; //the value in the id column of the case table in the central repo
    private final int dataSourceID;   //< Id in the central repo
    private final String deviceID;  //< Unique to its associated case (not necessarily globally unique)
    private final String name;

    /**
     * @param correlationCase CorrelationCase object data source is associated with. Must have been created by EamDB and have a valid ID.
     * @param deviceId User specified case-specific ID
     * @param name Display name of data source
     */
    public CorrelationDataSource(CorrelationCase correlationCase, String deviceId, String name) {
        this(correlationCase.getID(), -1, deviceId, name);
    }  
    
    /**
     * 
     * @param caseId Row ID for Case in DB
     * @param dataSourceId Row ID for this data source in DB (or -1)
     * @param deviceId User specified ID for device (unique per case)
     * @param name User specified name
     */
    CorrelationDataSource(int caseId,
            int dataSourceId,
            String deviceId,
            String name) {
        this.caseID = caseId;
        this.dataSourceID = dataSourceId;
        this.deviceID = deviceId;
        this.name = name;
    }

    /**
     * Create a CorrelationDataSource object from a TSK Content object.
     * This will add it to the central repository.
     *
     * @param correlationCase the current CorrelationCase used for ensuring
     *                        uniqueness of DataSource
     * @param dataSource      the sleuthkit datasource that is being added to
     *                        the central repository
     *
     * @return
     *
     * @throws EamDbException
     */
    public static CorrelationDataSource fromTSKDataSource(CorrelationCase correlationCase, Content dataSource) throws EamDbException {
        Case curCase;
        try {
            curCase = Case.getCurrentCaseThrows();
        } catch (NoCurrentCaseException ex) {
            throw new EamDbException("Autopsy case is closed");
        }
        String deviceId;
        try {
            deviceId = curCase.getSleuthkitCase().getDataSource(dataSource.getId()).getDeviceId();
        } catch (TskDataException | TskCoreException ex) {
            throw new EamDbException("Error getting data source info: " + ex.getMessage());
        }
        
        CorrelationDataSource correlationDataSource = null;
        if (EamDbUtil.useCentralRepo()) {
            correlationDataSource = EamDb.getInstance().getDataSource(correlationCase, deviceId);
        }
        if (correlationDataSource == null) {
            correlationDataSource = new CorrelationDataSource(correlationCase, deviceId, dataSource.getName());
            if (EamDbUtil.useCentralRepo()) {
                EamDb.getInstance().newDataSource(correlationDataSource);
            }
        }
        return correlationDataSource;
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("(");
        str.append("ID=").append(Integer.toString(getID()));
        str.append(",caseID=").append(Integer.toString(getCaseID()));
        str.append(",deviceID=").append(getDeviceID());
        str.append(",name=").append(getName());
        str.append(")");
        return str.toString();
    }

    /**
     * Get the database row ID
     *
     * @return the ID or -1 if unknown
     */
    int getID() {
        return dataSourceID;
    }

    /**
     * Get the device ID that is unique to the case
     *
     * @return the deviceID
     */
    public String getDeviceID() {
        return deviceID;
    }

    /**
     * Get the Case ID that is unique
     *
     * @return
     */
    public int getCaseID() {
        return caseID;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }
}
