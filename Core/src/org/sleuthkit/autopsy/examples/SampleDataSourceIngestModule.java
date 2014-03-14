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
import org.sleuthkit.autopsy.ingest.IngestDataSourceWorkerController;
import org.sleuthkit.autopsy.ingest.IngestModuleDataSource;
import org.sleuthkit.autopsy.ingest.IngestModuleInit;
import org.sleuthkit.autopsy.ingest.PipelineContext;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Sample DataSource-level ingest module that doesn't do much at all. 
 * Just exists to show basic idea of these modules
 */
 public class SampleDataSourceIngestModule extends org.sleuthkit.autopsy.ingest.IngestModuleDataSource {
    /* Data Source modules operate on a disk or set of logical files. They
     * are passed in teh data source refernce and query it for things they want.
     */
    @Override
    public void process(PipelineContext<IngestModuleDataSource> pipelineContext, Content dataSource, IngestDataSourceWorkerController controller) {
        
        Case case1 = Case.getCurrentCase();
        SleuthkitCase sleuthkitCase = case1.getSleuthkitCase();
        
        Services services = new Services(sleuthkitCase);
        FileManager fm = services.getFileManager();
        try {
            /* you can use the findFiles method in FileManager (or similar ones in
             * SleuthkitCase to find files based only on their name.  This
             * one finds files that have a .doc extension. */
            List<AbstractFile> docFiles = fm.findFiles(dataSource, "%.doc");
            for (AbstractFile file : docFiles) {
                // do something with each doc file
            }
            
            /* We can also do more general queries with findFilesWhere, which 
             * allows us to make our own WHERE clause in the database. 
             */
            long currentTime = System.currentTimeMillis()/1000;
            // go back 2 weeks
            long minTime = currentTime - (14 * 24 * 60 * 60);
            List<FsContent> otherFiles = sleuthkitCase.findFilesWhere("crtime > " + minTime);
            // do something with these files...
            
        } catch (TskCoreException ex) {
            Logger log = Logger.getLogger(SampleDataSourceIngestModule.class);
            log.fatal("Error retrieving files from database:  " + ex.getLocalizedMessage());
        }    
    }

    @Override
    public void init(IngestModuleInit initContext) throws IngestModuleException {
    }

    @Override
    public void complete() {
    }

    @Override
    public void stop() {
    }

    @Override
    public String getName() {
        return "SampleDataSourceIngestModule";    
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public String getDescription() {
        return "Doesn't do much";
    }

    @Override
    public boolean hasBackgroundJobsRunning() {
        return false;
    }
}
