/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2014 Basis Technology Corp.
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
package org.sleuthkit.autopsy.corecomponentinterfaces;

/**
 * An GUI agnostic DataSourceProcessorProgressMonitor interface for
 * DataSourceProcesssors to indicate progress. It models after a JProgressbar
 * though it could use any underlying implementation (or NoOps)
 */
public interface DataSourceProcessorProgressMonitor {

    void setIndeterminate(boolean indeterminate);

    void setProgress(int progress);

    void setProgressText(String text);
}
