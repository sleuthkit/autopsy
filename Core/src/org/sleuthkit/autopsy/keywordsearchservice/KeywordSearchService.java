/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
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
package org.sleuthkit.autopsy.keywordsearchservice;

import java.io.Closeable;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * @deprecated Use org.sleuthkit.datamodel.KeywordSearchService instead.
 */
@Deprecated
public interface KeywordSearchService extends Closeable {

    /**
     * Takes a Blackboard artifact and adds all of its attributes to the keyword
     * search index.
     *
     * @param artifact
     *
     * @throws org.sleuthkit.datamodel.TskCoreException
     */
    public void indexArtifact(BlackboardArtifact artifact) throws TskCoreException;

    /**
     * Checks if we can communicate with the KeywordSearchService using the
     * passed-in host and port. Closes the connection upon exit. Throws if it
     * cannot communicate.
     *
     * @param host the remote hostname or IP address of the server
     * @param port the remote port of the server
     *
     * @throws KeywordSearchServiceException
     */
    public void tryConnect(String host, int port) throws KeywordSearchServiceException;

    }
