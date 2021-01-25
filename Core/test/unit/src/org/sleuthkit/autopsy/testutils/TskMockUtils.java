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
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.sleuthkit.autopsy.texttranslation.NoServiceProviderException;
import org.sleuthkit.autopsy.texttranslation.TextTranslationService;
import org.sleuthkit.autopsy.texttranslation.TranslationException;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.Content;
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
    public static DataSource getDataSource(long dataSourceId) {
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
    public static BlackboardArtifact getArtifact(BlackboardArtifact.Type artifactType, long artifactId,
            DataSource dataSource, BlackboardAttribute... attributes) throws TskCoreException {
        return getArtifact(artifactType, null, artifactId, dataSource, attributes);
    }

    /**
     * Gets a mock Blackboard artifact.
     *
     * @param artifactType The artifact type for the artifact.
     * @param parent       The parent file of the artifact.
     * @param artifactId   The artifact id.
     * @param dataSource   The datasource.
     * @param attributes   The attributes for the artifact.
     *
     * @return The mocked artifact.
     *
     * @throws TskCoreException
     */
    public static BlackboardArtifact getArtifact(BlackboardArtifact.Type artifactType, Content parent, long artifactId,
            DataSource dataSource, BlackboardAttribute... attributes) throws TskCoreException {

        BlackboardArtifact artifact = mock(BlackboardArtifact.class);

        final Map<BlackboardAttribute.Type, BlackboardAttribute> attributeTypes = Stream.of(attributes)
                .filter(attr -> attr != null)
                .collect(Collectors.toMap((attr) -> attr.getAttributeType(), Function.identity()));

        when(artifact.getParent()).thenReturn(parent);

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

    public static BlackboardArtifact getArtifact(BlackboardArtifact.Type artifactType, long artifactId,
            DataSource dataSource, List<BlackboardAttribute> attributes) throws TskCoreException {

        return getArtifact(artifactType, artifactId, dataSource, attributes.toArray(new BlackboardAttribute[0]));
    }

    private static final String DEFAULT_ATTR_SOURCE = "TEST SOURCE";

    public static BlackboardAttribute getAttribute(ATTRIBUTE_TYPE attrType, Object value) {

        return getAttribute(new BlackboardAttribute.Type(attrType), DEFAULT_ATTR_SOURCE, value);
    }

    public static BlackboardAttribute getAttribute(BlackboardAttribute.Type attrType, String source, Object value) {
        switch (attrType.getValueType()) {
            case STRING:
            case JSON:
                if (value instanceof String) {
                    return new BlackboardAttribute(attrType, source, (String) value);
                }
                break;
            case DATETIME:
            case LONG:
                if (value instanceof Long) {
                    return new BlackboardAttribute(attrType, source, (Long) value);
                }
                break;
            case INTEGER:
                if (value instanceof Integer) {
                    return new BlackboardAttribute(attrType, source, (Integer) value);
                }
                break;
            case DOUBLE:
                if (value instanceof Double) {
                    return new BlackboardAttribute(attrType, source, (Double) value);
                }
                break;
            case BYTE:
                if (value instanceof byte[]) {
                    return new BlackboardAttribute(attrType, source, (byte[]) value);
                }
                break;
            default:
                throw new IllegalArgumentException(String.format("Unknown attribute value type: %s", attrType.getValueType()));
        }

        throw new IllegalArgumentException(String.format("Attribute type expected type of %s but received argument of %s", attrType.getValueType(), value));
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
            if (onTranslate == null) {
                throw new NoServiceProviderException("No onTranslate function provided");
            }

            Object[] args = invocation.getArguments();
            String input = (String) args[0];
            return (input == null) ? null : onTranslate.apply(input);
        });

        return translationService;
    }

    /**
     * Returns an AbstractFile mocking getPath and getName.
     *
     * @param objId The object id.
     * @param path  The path for the file.
     * @param name  The name
     *
     * @return
     */
    public static AbstractFile getAbstractFile(long objId, String path, String name) {
        AbstractFile mocked = mock(AbstractFile.class);
        when(mocked.getId()).thenReturn(objId);
        when(mocked.getName()).thenReturn(name);
        when(mocked.getParentPath()).thenReturn(path);
        return mocked;
    }

    private static void setConsoleHandler(Logger logger) {
        // taken from https://stackoverflow.com/a/981230
        // Handler for console (reuse it if it already exists)
        Handler consoleHandler = null;

        //see if there is already a console handler
        for (Handler handler : logger.getHandlers()) {
            if (handler instanceof ConsoleHandler) {
                //found the console handler
                consoleHandler = handler;
                break;
            }
        }

        if (consoleHandler == null) {
            //there was no console handler found, create a new one
            consoleHandler = new ConsoleHandler();
            logger.addHandler(consoleHandler);
        }

        //set the console handler to fine:
        consoleHandler.setLevel(java.util.logging.Level.FINEST);
    }

    /**
     * Retrieves an autopsy logger that does not write to disk.
     *
     * @param loggerName The name of the logger.
     *
     * @return The autopsy logger for the console
     *
     * @throws InstantiationException
     * @throws IllegalStateException
     */
    public static Logger getJavaLogger(String loggerName) {
        // The logger doesn't appear to respond well to mocking with mockito.
        // It appears that the issue may have to do with mocking methods in the java.* packages
        // since the autopsy logger extends the java.util.logging.Logger class:
        // https://javadoc.io/static/org.mockito/mockito-core/3.5.13/org/mockito/Mockito.html#39
        Logger logger = Logger.getLogger(loggerName);
        setConsoleHandler(logger);
        return logger;
    }

    private TskMockUtils() {
    }
}
