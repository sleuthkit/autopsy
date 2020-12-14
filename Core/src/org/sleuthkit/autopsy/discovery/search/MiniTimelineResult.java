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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 * A wrapper to hold all the information that is associated with an item in the
 * first column of the mini timeline view.
 */
public class MiniTimelineResult {

    private final String date;
    private final List<BlackboardArtifact> artifactList = new ArrayList<>();

    /**
     * Construct a new MiniTimelineResult.
     *
     * @param date         The date the list of artifacts were observed on as a
     *                     String.
     * @param artifactList The list of artifacts observed on the specified date.
     */
    MiniTimelineResult(String date, List<BlackboardArtifact> artifactList) {
        this.date = date;
        this.artifactList.addAll(artifactList);
    }

    /**
     * Get the date the artifacts were observed.
     *
     * @return The date the artifacts were observed.
     */
    public String getDate() {
        return date;
    }

    /**
     * Get the number of artifacts observed on the specified date.
     *
     * @return The number of artifacts observed on the specified date.
     */
    public int getCount() {
        return artifactList.size();
    }

    /**
     * Get the list of artifacts that were observed for the date specified in
     * this object.
     *
     * @return The list of artifacts that were observed for the date specified
     *         in this object.
     */
    public List<BlackboardArtifact> getArtifactList() {
        return Collections.unmodifiableList(artifactList);
    }

}
