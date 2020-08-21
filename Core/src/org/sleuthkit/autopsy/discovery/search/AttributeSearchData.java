/*
 * Autopsy
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.discovery.search;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import org.openide.util.NbBundle;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 * Utility enums for searches made for attributes with Discovery.
 */
public class AttributeSearchData implements SearchData {

    private static final Set<BlackboardArtifact.ARTIFACT_TYPE> DOMAIN_ARTIFACT_TYPES = EnumSet.of(BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_BOOKMARK, BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_CACHE, BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_COOKIE, BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_DOWNLOAD, BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_HISTORY, BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_SEARCH_QUERY, BlackboardArtifact.ARTIFACT_TYPE.TSK_EMAIL_MSG);

    @Override
    public ResultType getResultType() {
        return ResultType.ATTRIBUTE;
    }

    /**
     * Enum representing the attribute type.
     */
    @NbBundle.Messages({
        "AttributeSearchData.AttributeType.Domain.displayName=Domain",
        "AttributeSearchData.AttributeType.Other.displayName=Other"})
    public enum AttributeType {

        DOMAIN(0, Bundle.AttributeSearchData_AttributeType_Domain_displayName(), DOMAIN_ARTIFACT_TYPES),
        OTHER(1, Bundle.AttributeSearchData_AttributeType_Other_displayName(), new HashSet<>());

        private final int ranking;  // For ordering in the UI
        private final String displayName;
        private final Set<BlackboardArtifact.ARTIFACT_TYPE> artifactTypes = new HashSet<>();

        AttributeType(int value, String displayName, Set<BlackboardArtifact.ARTIFACT_TYPE> types) {
            this.ranking = value;
            this.displayName = displayName;
            this.artifactTypes.addAll(types);
        }

        /**
         * Get the BlackboardArtifact types matching this category.
         *
         * @return Collection of BlackboardArtifact types.
         */
        public Collection<BlackboardArtifact.ARTIFACT_TYPE> getBlackboardTypes() {
            return Collections.unmodifiableCollection(artifactTypes);
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
        public int getRanking() {
            return ranking;
        }

        public static AttributeType fromBlackboardArtifact(final BlackboardArtifact.ARTIFACT_TYPE type) {
            switch (type) {
                case TSK_WEB_BOOKMARK:
                    return DOMAIN;
                default:
                    return OTHER;
            }
        }

    }

}
