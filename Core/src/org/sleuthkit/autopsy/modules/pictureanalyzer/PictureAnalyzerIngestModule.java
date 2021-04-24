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
package org.sleuthkit.autopsy.modules.pictureanalyzer;

import java.util.Collection;

import org.openide.util.Lookup;
import org.openide.util.NbBundle;

import org.sleuthkit.autopsy.ingest.FileIngestModuleAdapter;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.modules.pictureanalyzer.spi.PictureProcessor;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector;

import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskData;

/**
 * Ingest module to do arbitrary work on image files. Some examples include
 * extracting EXIF, converting between formats, and creating thumbnails. This
 * module acts as a container for multiple PictureProcessors, which are the
 * classes that do the work mentioned in the examples above.
 */
public class PictureAnalyzerIngestModule extends FileIngestModuleAdapter {

    private FileTypeDetector fileTypeDetector;
    private Collection<? extends PictureProcessor> registry;
    private IngestJobContext context;

    @Override
    public ProcessResult process(AbstractFile file) {
        // Skip non files, known files, and unallocated/slack files.
        if (!file.isFile() || file.getKnown().equals(TskData.FileKnown.KNOWN) || 
               (file.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS)
                || (file.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.SLACK)))) {
            return ProcessResult.OK;
        }
        
        for (PictureProcessor processor : registry) {                    
            final String fileMimeType = fileTypeDetector.getMIMEType(file);
            
            for (String supportedMimeType : processor.mimeTypes()) {
                
                if (context.fileIngestIsCancelled()) {
                    return ProcessResult.OK;
                }

                if (supportedMimeType.equalsIgnoreCase(fileMimeType)) {
                    processor.process(context, file);
                }
            }
        }
        return ProcessResult.OK;
    }

    @Override
    @NbBundle.Messages({
        "PictureAnalyzerIngestModule.cannot_run_file_type=Cannot run file type detection."
    })
    public void startUp(IngestJobContext context) throws IngestModuleException {
        registry = Lookup.getDefault().lookupAll(PictureProcessor.class);
        this.context = context;
        try {
            fileTypeDetector = new FileTypeDetector();
        } catch (FileTypeDetector.FileTypeDetectorInitException ex) {
            throw new IngestModuleException(Bundle.PictureAnalyzerIngestModule_cannot_run_file_type(), ex);
        }
    }

}
