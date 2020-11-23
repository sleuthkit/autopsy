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
package org.sleuthkit.autopsy.discovery.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.sleuthkit.datamodel.BlackboardArtifact;

public class DateArtifactWrapper {

    private final String date;
    private final List<BlackboardArtifact> artifactList = new ArrayList<>();

    DateArtifactWrapper(String date, List<BlackboardArtifact> artifactList) {
        this.date = date;
        this.artifactList.addAll(artifactList);
    }

    /**
     * @return the date
     */
    String getDate() {
        return date;
    }

    /**
     * @return the count
     */
    int getCount() {
        return artifactList.size();
    }

    List<BlackboardArtifact> getArtifactList() {
        return Collections.unmodifiableList(artifactList);
    }

}
