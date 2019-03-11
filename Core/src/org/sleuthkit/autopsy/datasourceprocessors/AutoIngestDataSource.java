/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datasourceprocessors;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.concurrent.ThreadSafe;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback.DataSourceProcessorResult;
import org.sleuthkit.datamodel.Content;

@ThreadSafe
public class AutoIngestDataSource {

    private final String deviceId;
    private final Path path;
    private DataSourceProcessorResult resultCode;
    private List<String> errorMessages;
    private List<Content> content;

    public AutoIngestDataSource(String deviceId, Path path) {
        this.deviceId = deviceId;
        this.path = path;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public Path getPath() {
        return this.path;
    }

    public synchronized void setDataSourceProcessorOutput(DataSourceProcessorResult result, List<String> errorMessages, List<Content> content) {
        this.resultCode = result;
        this.errorMessages = new ArrayList<>(errorMessages);
        this.content = new ArrayList<>(content);
    }

    public synchronized DataSourceProcessorResult getResultDataSourceProcessorResultCode() {
        return resultCode;
    }

    public synchronized List<String> getDataSourceProcessorErrorMessages() {
        return new ArrayList<>(errorMessages);
    }

    public synchronized List<Content> getContent() {
        return new ArrayList<>(content);
    }

}
