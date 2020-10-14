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
package org.sleuthkit.autopsy.testutils;

import java.util.ArrayList;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Tools for mocking items in TSK.
 */
public class TskMockUtils {

    /**
     * Creates a mock data source. The data source currently mocks getName
     * returning "" and getId returning the provided id.
     *
     * @param dataSourceId The id for the data source.
     *
     * @return The mocked datasource.
     */
    public static DataSource mockDataSource(long dataSourceId) {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getName()).thenReturn("");
        when(dataSource.getId()).thenReturn(dataSourceId);
        return dataSource;
    }

    /**
     * Mocks a blackboard artifact returning the values provided for appropriate
     * getters. Currently mocks methods: getArtifactID() getArtifactTypeID()
     * getAttribute(BlackboardAttribute.Type) getAttributes()
     *
     * @param artifactType The artifact's type.
     * @param artifactId   The id for the artifact.
     * @param dataSource   The data source.
     * @param attributes   The attributes for the artifact.
     *
     * @return The generated BlackboardArtifact.
     *
     * @throws TskCoreException
     */
    public static BlackboardArtifact mockArtifact(BlackboardArtifact.Type artifactType, long artifactId,
            DataSource dataSource, BlackboardAttribute... attributes) throws TskCoreException {

        BlackboardArtifact artifact = mock(BlackboardArtifact.class);

        final Map<BlackboardAttribute.Type, BlackboardAttribute> attributeTypes = Stream.of(attributes)
                .collect(Collectors.toMap((attr) -> attr.getAttributeType(), Function.identity()));

        when(artifact.getArtifactID()).thenReturn(artifactId);

        when(artifact.getArtifactTypeID()).thenReturn(artifactType.getTypeID());

        when(artifact.getAttribute(any(BlackboardAttribute.Type.class))).thenAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            BlackboardAttribute.Type type = (BlackboardAttribute.Type) args[0];
            return attributeTypes.get(type);
        });

        when(artifact.getAttributes()).thenReturn(new ArrayList<>(attributeTypes.values()));

        when(artifact.getDataSource()).thenReturn(dataSource);
        return artifact;
    }

    private TskMockUtils() {
    }
}
