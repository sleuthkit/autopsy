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

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.texttranslation.NoServiceProviderException;
import org.sleuthkit.autopsy.texttranslation.TextTranslationService;
import org.sleuthkit.autopsy.texttranslation.TranslationException;
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

    /**
     * Returns a mock TextTranslationService.
     *
     * @param onTranslate A function that performs the translation. If null, a
     *                    null result is always returned for .translate method.
     * @param hasProvider What to return for the hasProvider method.
     *
     * @return The mocked text translation service.
     *
     * @throws NoServiceProviderException
     * @throws TranslationException
     */
    public static TextTranslationService getTextTranslationService(Function<String, String> onTranslate, boolean hasProvider)
            throws NoServiceProviderException, TranslationException {
        TextTranslationService translationService = mock(TextTranslationService.class);
        when(translationService.hasProvider()).thenReturn(hasProvider);

        when(translationService.translate(anyString())).thenAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            String input = (String) args[0];
            if (onTranslate == null) {
                throw new NoServiceProviderException("No onTranslate function provided");
            }
            
            return (input == null) ? null : onTranslate.apply(input);
        });
        
        return translationService;
    }
    
    
    /**
     * 
     * @param loggerName
     * @return
     * @throws InstantiationException
     * @throws NoSuchMethodException
     * @throws SecurityException 
     */
    public static Logger getTSKLogger(String loggerName) 
            throws InstantiationException, NoSuchMethodException, SecurityException {
        
        // The logger doesn't appear to respond well to mocking with mockito.
        // It appears that the issue may have to do with mocking methods in the java.* packages
        // since the autopsy logger extends the java.util.logging.Logger class:
        // https://javadoc.io/static/org.mockito/mockito-core/3.5.13/org/mockito/Mockito.html#39
        Constructor<Logger> constructor = Logger.class.getConstructor(String.class, String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(loggerName, null);
    }
    
    private TskMockUtils() {
    }
}
