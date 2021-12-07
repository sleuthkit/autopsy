/*
 * Central Repository
 *
 * Copyright 2015-2021 Basis Technology Corp.
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
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskDataException;

/**
 *
 * Stores information about a Data Source in the Central Repository
 *
 */
public class CorrelationDataSource implements Serializable {

    private static final long serialVersionUID = 1L;

    private final int caseID; //the value in the id column of the case table in the central repo
    private final int dataSourceID;   //< Id in the central repo
    private final Long dataSourceObjectID; //< Id for data source in the caseDB 
    private final String deviceID;  //< Unique to its associated case (not necessarily globally unique)
    private final String name;
    private String md5Hash;
    private String sha1Hash;
    private String sha256Hash;

    /**
     * Create a CorrelationDataSource object.
     *
     * @param correlationCase    CorrelationCase object data source is
     *                           associated with. Must have been created by
     *                           EamDB and have a valid ID.
     * @param deviceId           User specified ID for device (unique per case)
     * @param name               User specified name
     * @param dataSourceObjectId The object ID for the datasource
     * @param md5Hash            The MD5 hash value
     * @param sha1Hash           The SHA-1 hash value
     * @param sha256Hash         The SHA-256 hash value
     */
    public CorrelationDataSource(CorrelationCase correlationCase,
            String deviceId,
            String name,
            Long dataSourceObjectId,
            String md5Hash,
            String sha1Hash,
            String sha256Hash) {
        this(correlationCase.getID(), -1, deviceId, name, dataSourceObjectId, md5Hash, sha1Hash, sha256Hash);
    }

    /**
     * Create a CorrelationDataSource object.
     *
     * @param caseId             Row ID for Case in DB
     * @param dataSourceId       Row ID for this data source in DB (or -1)
     * @param deviceId           User specified ID for device (unique per case)
     * @param name               User specified name
     * @param dataSourceObjectId The object ID for the datasource
     * @param md5Hash            The MD5 hash value
     * @param sha1Hash           The SHA-1 hash value
     * @param sha256Hash         The SHA-256 hash value
     */
    CorrelationDataSource(int caseId,
            int dataSourceId,
            String deviceId,
            String name,
            Long dataSourceObjectId,
            String md5Hash,
            String sha1Hash,
            String sha256Hash) {
        this.caseID = caseId;
        this.dataSourceID = dataSourceId;
        this.deviceID = deviceId;
        this.name = name;
        this.dataSourceObjectID = dataSourceObjectId;
        this.md5Hash = md5Hash;
        this.sha1Hash = sha1Hash;
        this.sha256Hash = sha256Hash;
    }

    /**
     * Creates a central repository data source object from a case database data
     * source. If the data source is not already present in the central
     * repository, it is added.
     *
     * @param correlationCase The central repository case associated with the
     *                        data aosurce.
     * @param dataSource      The case database data source.
     *
     * @return The central repository data source.
     *
     * @throws CentralRepoException This exception is thrown if there is an
     *                              error creating the central repository data
     *                              source.
     */
    public static CorrelationDataSource fromTSKDataSource(CorrelationCase correlationCase, Content dataSource) throws CentralRepoException {
        if (!CentralRepository.isEnabled()) {
            throw new CentralRepoException(String.format("Central repository is not enabled, cannot create central repository data source for '%s'", dataSource));
        }

        Case curCase;
        try {
            curCase = Case.getCurrentCaseThrows();
        } catch (NoCurrentCaseException ex) {
            throw new CentralRepoException("Error getting current case", ex);
        }

        CorrelationDataSource correlationDataSource = CentralRepository.getInstance().getDataSource(correlationCase, dataSource.getId());
        if (correlationDataSource == null) {
            String deviceId;
            String md5 = null;
            String sha1 = null;
            String sha256 = null;
            try {
                deviceId = curCase.getSleuthkitCase().getDataSource(dataSource.getId()).getDeviceId();

                if (dataSource instanceof Image) {
                    Image image = (Image) dataSource;
                    md5 = image.getMd5();
                    sha1 = image.getSha1();
                    sha256 = image.getSha256();
                }
            } catch (TskDataException | TskCoreException ex) {
                throw new CentralRepoException("Error getting data source info from case database", ex);
            }
            correlationDataSource = new CorrelationDataSource(correlationCase, deviceId, dataSource.getName(), dataSource.getId(), md5, sha1, sha256);
            correlationDataSource = CentralRepository.getInstance().newDataSource(correlationDataSource);
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
    public int getID() {
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
     * Get the object id for the data source in the case db
     *
     * @return dataSourceObjectID or NULL if not available
     */
    public Long getDataSourceObjectID() {
        return dataSourceObjectID;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @return the MD5 hash value
     */
    public String getMd5() {
        return (md5Hash == null ? "" : md5Hash);
    }

    /**
     * Set the MD5 hash value and persist to the Central Repository if
     * available.
     *
     * @param md5Hash The MD5 hash value.
     *
     * @throws CentralRepoException If there's an issue updating the Central
     *                              Repository.
     */
    public void setMd5(String md5Hash) throws CentralRepoException {
        this.md5Hash = md5Hash;

        if (dataSourceObjectID != -1) {
            CentralRepository.getInstance().updateDataSourceMd5Hash(this);
        }
    }

    /**
     * @return the SHA-1 hash value
     */
    public String getSha1() {
        return (sha1Hash == null ? "" : sha1Hash);
    }

    /**
     * Set the SHA-1 hash value and persist to the Central Repository if
     * available.
     *
     * @param sha1Hash The SHA-1 hash value.
     */
    public void setSha1(String sha1Hash) throws CentralRepoException {
        this.sha1Hash = sha1Hash;

        if (dataSourceObjectID != -1) {
            CentralRepository.getInstance().updateDataSourceSha1Hash(this);
        }
    }

    /**
     * @return the SHA-256 hash value
     */
    public String getSha256() {
        return (sha256Hash == null ? "" : sha256Hash);
    }

    /**
     * Set the SHA-256 hash value and persist to the Central Repository if
     * available.
     *
     * @param sha256Hash The SHA-256 hash value.
     */
    public void setSha256(String sha256Hash) throws CentralRepoException {
        this.sha256Hash = sha256Hash;

        if (dataSourceObjectID != -1) {
            CentralRepository.getInstance().updateDataSourceSha256Hash(this);
        }
    }
}
