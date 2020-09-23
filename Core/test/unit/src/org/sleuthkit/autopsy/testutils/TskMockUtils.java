/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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
 *
 * @author gregd
 */
public class TskMockUtils {

    public static DataSource mockDataSource(long dataSourceId) {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getName()).thenReturn("");
        when(dataSource.getId()).thenReturn(dataSourceId);
        return dataSource;
    }
    
    public static BlackboardArtifact mockArtifact(BlackboardArtifact.Type artifactType, long artifactId,
            DataSource dataSource, BlackboardAttribute...attributes) throws TskCoreException {
        
        BlackboardArtifact artifact = mock(BlackboardArtifact.class);

        final Map<Integer, BlackboardAttribute> attributeTypes = Stream.of(attributes)
                .collect(Collectors.toMap((attr) -> attr.getAttributeType().getTypeID(), Function.identity()));
        
        when(artifact.getArtifactID()).thenReturn(artifactId);

        when(artifact.getArtifactTypeID()).thenReturn(artifactType.getTypeID());
        
        when(artifact.getAttribute(any())).thenAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            BlackboardArtifact.Type type = (BlackboardArtifact.Type) args[0];
            return attributeTypes.get(type.getTypeID());
        });

        when(artifact.getAttributes()).thenReturn(new ArrayList<>(attributeTypes.values()));

        when(artifact.getDataSource()).thenReturn(dataSource);
        return artifact;
    }
}
