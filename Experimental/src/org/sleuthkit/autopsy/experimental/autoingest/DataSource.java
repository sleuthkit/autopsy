/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.experimental.autoingest;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.concurrent.ThreadSafe;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback.DataSourceProcessorResult;
import org.sleuthkit.datamodel.Content;

@ThreadSafe
class DataSource {

    private final String deviceId;
    private final Path path;
    private DataSourceProcessorResult resultCode;
    private List<String> errorMessages;
    private List<Content> content;

    DataSource(String deviceId, Path path) {
        this.deviceId = deviceId;
        this.path = path;
    }

    String getDeviceId() {
        return deviceId;
    }

    Path getPath() {
        return this.path;
    }

    synchronized void setDataSourceProcessorOutput(DataSourceProcessorResult result, List<String> errorMessages, List<Content> content) {
        this.resultCode = result;
        this.errorMessages = new ArrayList<>(errorMessages);
        this.content = new ArrayList<>(content);
    }

    synchronized DataSourceProcessorResult getResultDataSourceProcessorResultCode() {
        return resultCode;
    }

    synchronized List<String> getDataSourceProcessorErrorMessages() {
        return new ArrayList<>(errorMessages);
    }

    synchronized List<Content> getContent() {
        return new ArrayList<>(content);
    }

}
