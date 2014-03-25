/*
 * Sample module in the public domain.  Feel free to use this as a template
 * for your modules.
 * 
 *  Contact: Brian Carrier [carrier <at> sleuthkit [dot] org]
 *
 *  This is free and unencumbered software released into the public domain.
 *  
 *  Anyone is free to copy, modify, publish, use, compile, sell, or
 *  distribute this software, either in source code form or as a compiled
 *  binary, for any purpose, commercial or non-commercial, and by any
 *  means.
 *  
 *  In jurisdictions that recognize copyright laws, the author or authors
 *  of this software dedicate any and all copyright interest in the
 *  software to the public domain. We make this dedication for the benefit
 *  of the public at large and to the detriment of our heirs and
 *  successors. We intend this dedication to be an overt act of
 *  relinquishment in perpetuity of all present and future rights to this
 *  software under copyright law.
 *  
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 *  EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 *  MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 *  IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 *  OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 *  ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 *  OTHER DEALINGS IN THE SOFTWARE. 
 */
package org.sleuthkit.autopsy.examples;

import java.util.List;
import org.apache.log4j.Logger;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.casemodule.services.Services;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModule;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleStatusHelper;
import org.sleuthkit.autopsy.ingest.IngestModule;
import org.sleuthkit.autopsy.ingest.IngestModuleAdapter;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Sample data source ingest module that doesn't do much. Note that the
 * IngestModuleAdapter abstract class could have been used as a base class to 
 * obtain default implementations of many of the DataSourceIngestModule methods.
 */
// RJCTODO: Remove inheritance from IngestModuleAdapter to show full implementation of interface
// provide better documentation, and provide more extensive demonstration of how to 
// use various ingest services.
class SampleDataSourceIngestModule extends IngestModuleAdapter implements DataSourceIngestModule {

    private static final Logger logger = Logger.getLogger(SampleDataSourceIngestModule.class);

    @Override
    public ProcessResult process(Content dataSource, DataSourceIngestModuleStatusHelper statusHelper) {
        Case case1 = Case.getCurrentCase();
        SleuthkitCase sleuthkitCase = case1.getSleuthkitCase();

        Services services = new Services(sleuthkitCase);
        FileManager fileManager = services.getFileManager();
        try {
            List<AbstractFile> docFiles = fileManager.findFiles(dataSource, "%.doc");
            for (AbstractFile file : docFiles) {
                // do something with each doc file
            }

            long currentTime = System.currentTimeMillis() / 1000;
            long minTime = currentTime - (14 * 24 * 60 * 60); // Go back two weeks.
            List<FsContent> otherFiles = sleuthkitCase.findFilesWhere("crtime > " + minTime);
            // do something with these files...

        } catch (TskCoreException ex) {
            logger.fatal("Error retrieving files from database:  " + ex.getLocalizedMessage());
            return IngestModule.ProcessResult.OK;
        }
        
        return IngestModule.ProcessResult.OK;
    }
}
