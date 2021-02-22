/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.centralrepository.contentviewer;

import java.util.Objects;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
     * Used as a key to ensure we eliminate duplicates from the result set by
     * not overwriting CR correlation instances.
     */
   final class UniquePathKey {

         private static final Logger logger= Logger.getLogger(UniquePathKey.class.getName());
        private final String dataSourceID;
        private final String filePath;
        private final String type;
        private final String caseUUID;

        UniquePathKey(OtherOccurrenceNodeInstanceData nodeData) {
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
                    tempCaseUUID = OtherOccurrencesPanel.getPlaceholderUUID();
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