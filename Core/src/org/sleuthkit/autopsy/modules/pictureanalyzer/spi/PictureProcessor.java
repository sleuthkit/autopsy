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
package org.sleuthkit.autopsy.modules.pictureanalyzer.spi;

import java.util.Set;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * SPI for doing work on picture files. ServiceProviders of this
 * interface will be picked up and run by the PictureAnalysisIngestModule.
 */
public interface PictureProcessor {

    /**
     * Perform work on the image file.
     *
     * @param context Job context to check for cancellation or add files to
     * the pipeline
     * @param file The image file to process
     */
    void process(IngestJobContext context, AbstractFile file);

    /**
     * Indicates the MIME types this processor supports.
     */
    Set<String> mimeTypes();

}
