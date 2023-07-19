/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datamodel;

import org.openide.nodes.Children;
import org.openide.util.NbBundle;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 * Analysis Results node support.
 */
@NbBundle.Messages({
    "AnalysisResults_name=Analysis Results",})
public class AnalysisResults implements AutopsyVisitableItem {

    /**
     * Returns the name of this node that is the key in the children object.
     *
     * @return The name of this node that is the key in the children object.
     */
    public static String getName() {
        return Bundle.AnalysisResults_name();
    }

    /**
     * Parent node of all analysis results.
     */
    static class RootNode extends Artifacts.BaseArtifactNode {

        /**
         * Main constructor.
         *
         * @param filteringDSObjId The data source object id for which results
         *                         should be filtered. If no filtering should
         *                         occur, this number should be less than or
         *                         equal to 0.
         */
        RootNode(long filteringDSObjId) {
            super(Children.create(new Artifacts.TypeFactory(BlackboardArtifact.Category.ANALYSIS_RESULT, filteringDSObjId), true),
                    "org/sleuthkit/autopsy/images/analysis_result.png",
                    AnalysisResults.getName(),
                    AnalysisResults.getName());
        }
    }

    private final long datasourceObjId;

    /**
     * Main constructor.
     */
    public AnalysisResults() {
        this(0);
    }

    /**
     * Main constructor.
     *
     * @param dsObjId The data source object id.
     */
    public AnalysisResults(long dsObjId) {
        this.datasourceObjId = dsObjId;
    }

    @Override
    public <T> T accept(AutopsyItemVisitor<T> visitor) {
        return visitor.visit(this);
    }

    /**
     * Returns whether or not there is a data source object for which results
     * should be filtered.
     *
     * @return Whether or not there is a data source object for which results
     *         should be filtered.
     */
    Long getFilteringDataSourceObjId() {
        return datasourceObjId;
    }
}
