/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.ingest;

import org.sleuthkit.datamodel.AbstractFile;

/**
 * DO NOT USE: As of Java 8, interfaces can have default methods. IngestModule
 * now provides default no-op versions of startUp() and shutDown(). This class
 * is no longer needed and can be DEPRECATED when convenient.
 * * 
 * An adapter that provides no-op implementations of the startUp() and
 * shutDown() methods for file ingest modules.
 *
 */
public abstract class FileIngestModuleAdapter implements FileIngestModule {

    @Override
    public abstract ProcessResult process(AbstractFile file);

}
