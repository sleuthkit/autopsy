/*
 * Autopsy Forensic Browser
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
package org.sleuthkit.autopsy.datasourcesummary.datamodel;

import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Common tools for mocking in data source summary.
 */
public final class DataSourceSummaryMockUtils {

    /**
     * Creates a pair of a mock SleuthkitCase and mock Blackboard.
     *
     * @param returnArr The return result when calling getArtifacts on the
     *                  blackboard.
     *
     * @return The pair of a mock SleuthkitCase and mock Blackboard.
     *
     * @throws TskCoreException
     */
    static Pair<SleuthkitCase, Blackboard> getArtifactsTSKMock(List<BlackboardArtifact> returnArr) throws TskCoreException {
        SleuthkitCase mockCase = mock(SleuthkitCase.class);
        Blackboard mockBlackboard = mock(Blackboard.class);
        when(mockCase.getBlackboard()).thenReturn(mockBlackboard);
        when(mockBlackboard.getArtifacts(anyInt(), anyLong())).thenReturn(returnArr);
        return Pair.of(mockCase, mockBlackboard);
    }

    private DataSourceSummaryMockUtils() {
    }
}
