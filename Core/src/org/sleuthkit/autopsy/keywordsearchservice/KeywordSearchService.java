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
import java.io.IOException;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 */
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
     * Checks if we can communicate with Solr using the passed-in host and port.
     * Closes the connection upon exit. Throws if it cannot communicate with
     * Solr.
     *
     * @param host the remote hostname or IP address of the Solr server
     * @param port the remote port for Solr
     *
     * @throws java.io.IOException
     * @throws org.sleuthkit.datamodel.TskCoreException
     */
    public void tryConnect(String host, String port) throws NumberFormatException, IOException, TskCoreException;

    /**
     * This method handles exceptions from the connection tester, tryConnect(),
     * returning the appropriate user-facing text for the exception received.
     *
     * @param ex        the exception that was returned
     * @param ipAddress the IP address to connect to
     *
     * @return returns the String message to show the user
     */
    public String getUserWarning(Exception ex, String ipAddress);
}
