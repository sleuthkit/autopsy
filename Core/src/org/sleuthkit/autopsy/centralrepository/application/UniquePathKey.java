/*
 * Central Repository
 *
 * Copyright 2017-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.centralrepository.application;

import java.util.Objects;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Used as a key to ensure we eliminate duplicates from the result set by not
 * overwriting CR correlation instances.
 */
public final class UniquePathKey {

    private static final Logger logger = Logger.getLogger(UniquePathKey.class.getName());
    private final String dataSourceID;
    private final String filePath;
    private final String type;
    private final String caseUUID;

    public UniquePathKey(NodeData nodeData) {
        super();
        dataSourceID = nodeData.getDeviceID();
        if (nodeData.getFilePath() != null) {
            filePath = nodeData.getFilePath().toLowerCase();
        } else {
            filePath = null;
        }
        type = nodeData.getType();
        String tempCaseUUID;
        try {
            tempCaseUUID = nodeData.getCorrelationAttributeInstance().getCorrelationCase().getCaseUUID();
        } catch (CentralRepoException ignored) {
            //non central repo nodeData won't have a correlation case
            try {
                tempCaseUUID = Case.getCurrentCaseThrows().getName();
                //place holder value will be used since correlation attribute was unavailble
            } catch (NoCurrentCaseException ex) {
                logger.log(Level.WARNING, "Unable to get current case", ex);
                tempCaseUUID = OtherOccurrences.getPlaceholderUUID();
            }
        }
        caseUUID = tempCaseUUID;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof UniquePathKey) {
            UniquePathKey otherKey = (UniquePathKey) (other);
            return (Objects.equals(otherKey.getDataSourceID(), this.getDataSourceID())
                    && Objects.equals(otherKey.getFilePath(), this.getFilePath())
                    && Objects.equals(otherKey.getType(), this.getType())
                    && Objects.equals(otherKey.getCaseUUID(), this.getCaseUUID()));
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getDataSourceID(), getFilePath(), getType(), getCaseUUID());
    }

    /**
     * Get the type of this UniquePathKey.
     *
     * @return the type
     */
    String getType() {
        return type;
    }

    /**
     * Get the file path for the UniquePathKey.
     *
     * @return the filePath
     */
    String getFilePath() {
        return filePath;
    }

    /**
     * Get the data source id for the UniquePathKey.
     *
     * @return the dataSourceID
     */
    String getDataSourceID() {
        return dataSourceID;
    }

    /**
     * Get the case uuid for the UniquePathKey
     *
     * @return the case UUID
     */
    String getCaseUUID() {
        return caseUUID;
    }
}
