/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.discovery;

import org.openide.util.NbBundle;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 * Utility enums for searches made for artifacts with Discovery.
 */
public class ArtifactSearchData extends SearchData {

        
    @Override
    ResultType getResultType(){
        return ResultType.ARTIFACT;
    }
    
    
    /**
     * Enum representing the file type. We don't simply use
     * FileTypeUtils.FileTypeCategory because: - Some file types categories
     * overlap - It is convenient to have the "OTHER" option for files that
     * don't match the given types
     */
    @NbBundle.Messages({
        "ArtifactSearchData.ArtifactType.Domain.displayName=Domain",
        "ArtifactSearchData.ArtifactType.Other.displayName=Other"})
    enum ArtifactType {

        DOMAIN(0, Bundle.ArtifactSearchData_ArtifactType_Domain_displayName(), BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_BOOKMARK),
        OTHER(1, Bundle.ArtifactSearchData_ArtifactType_Other_displayName(), null);

        private final int ranking;  // For ordering in the UI
        private final String displayName;
        private final BlackboardArtifact.ARTIFACT_TYPE artifactType;

        ArtifactType(int value, String displayName, BlackboardArtifact.ARTIFACT_TYPE type) {
            this.ranking = value;
            this.displayName = displayName;
            this.artifactType = type;
        }

        /**
         * Get the MIME types matching this category.
         *
         * @return Collection of MIME type strings
         */
        BlackboardArtifact.ARTIFACT_TYPE getMediaTypes() {
            return artifactType;
        }

        @Override
        public String toString() {
            return displayName;
        }

        /**
         * Get the rank for sorting.
         *
         * @return the rank (lower should be displayed first)
         */
        int getRanking() {
            return ranking;
        }


        static ArtifactType fromBlackboardArtifact(final BlackboardArtifact.ARTIFACT_TYPE type) {
            switch (type) {
                case TSK_WEB_BOOKMARK:
                    return DOMAIN;
                default:
                    return OTHER;
            }
        }

    }

}
