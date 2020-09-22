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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.DataSourceInfoUtilities.SortOrder;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
//import static org.mockito.Mockito.*;

/**
 *
 * @author gregd
 */
public class GetArtifactsTest {
    private final void test(BlackboardArtifact.Type artifactType, DataSource dataSource, BlackboardAttribute.Type attributeType, 
            SortOrder sortOrder, List<BlackboardArtifact> returnArr, List<BlackboardArtifact> expectedArr) throws TskCoreException {
        
        SleuthkitCase mockCase = Mockito.mock(SleuthkitCase.class);
        Blackboard mockBlackboard = Mockito.mock(Blackboard.class);
        Mockito.when(mockCase.getBlackboard()).thenReturn(mockBlackboard);
        Mockito.when(mockBlackboard.getArtifacts(Mockito.anyInt(), Mockito.anyInt())).thenReturn(returnArr);
        Mockito.verify(mockBlackboard, Mockito.times(1)).getArtifacts(artifactType.getTypeID(), dataSource.getId());
        List<BlackboardArtifact> determinedArr = DataSourceInfoUtilities.getArtifacts(mockCase, artifactType, dataSource, attributeType, sortOrder);
        
        if (expectedArr == null && returnArr == null) {
            return;
        }
        
        Assert.assertTrue(expectedArr != null && determinedArr != null);
        
        Assert.assertEquals(expectedArr.size(), determinedArr.size());
        
        for (int i = 0; i < expectedArr.size(); i++) {
            Assert.assertEquals(expectedArr.get(i), determinedArr.get(i));
        }
    }
    
    private BlackboardArtifact createArtifact(BlackboardArtifact.Type artifactType, long artifactId,
            DataSource dataSource, BlackboardAttribute...attributes) throws TskCoreException {
        
        BlackboardArtifact artifact = Mockito.mock(BlackboardArtifact.class);

        final Map<Integer, BlackboardAttribute> attributeTypes = Stream.of(attributes)
                .collect(Collectors.toMap((attr) -> attr.getAttributeType().getTypeID(), Function.identity()));
        
        Mockito.when(artifact.getArtifactID()).thenReturn(artifactId);

        Mockito.when(artifact.getArtifactTypeID()).thenReturn(artifactType.getTypeID());
        
        Mockito.when(artifact.getAttribute(Mockito.any())).thenAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            BlackboardArtifact.Type type = (BlackboardArtifact.Type) args[0];
            return attributeTypes.get(type.getTypeID());
        });

        Mockito.when(artifact.getAttributes()).thenReturn(new ArrayList<>(attributeTypes.values()));

        Mockito.when(artifact.getDataSource()).thenReturn(dataSource);
        return artifact;
    }
        
    @Test
    public void normalSituation() throws TskCoreException {
        
    }
    
}
    
    /*
     
     normal case
     null artifact type
     2 different artifacts
     
     null attribute
     attribute not present on artifact
     
     attribute not present on some artifacts
     attributes with different sort fields
    
    confined to artifact
    sorts on sorter and correct attribute
    
    * 
    * sort order forwards and backwards
    * when tskcoreexception thrown
    * when empty list returned
    * when null list returned?
    * 
    * 
    * 
    * 
    * 
     */
}
