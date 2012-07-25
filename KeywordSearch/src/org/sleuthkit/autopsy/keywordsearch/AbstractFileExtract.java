/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012 Basis Technology Corp.
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

package org.sleuthkit.autopsy.keywordsearch;

import org.sleuthkit.datamodel.AbstractFile;

/**
 * Common methods for utilities that extract text and content and divide into
 * chunks
 */
interface AbstractFileExtract {

    /**
     * Get number of chunks resulted from extracting this AbstractFile
     * @return the number of chunks produced
     */
    int getNumChunks();

    /**
     * Get the source file associated with this extraction
     * @return the source AbstractFile
     */
    AbstractFile getSourceFile();

    /**
     * Index the Abstract File
     * @param sourceFile file to index
     * @return true if indexed successfully, false otherwise
     * @throws org.sleuthkit.autopsy.keywordsearch.Ingester.IngesterException 
     */
    boolean index(AbstractFile sourceFile) throws Ingester.IngesterException;
    
    /**
     * Determines if the extractor works only for specified types
     * is supportedTypes() or whether is a generic content extractor (such as string extractor)
     * @return 
     */
    boolean isContentTypeSpecific();
    
    /**
     * Determines if the file content is supported by the extractor, 
     * if isContentTypeSpecific() returns true.
     * @param file to test if its content should be supported
     * @return true if the file content is supported, false otherwise
     */
    boolean isSupported(AbstractFile file);
}
