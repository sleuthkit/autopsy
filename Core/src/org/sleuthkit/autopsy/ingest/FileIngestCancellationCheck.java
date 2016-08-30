/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.ingest;

import org.sleuthkit.autopsy.datamodel.ContentUtils;

/**
 * File ingest implementation of CancellationCheck
 */
public class FileIngestCancellationCheck implements ContentUtils.CancellationCheck {
    
    private final IngestJobContext context;
    
    public FileIngestCancellationCheck(IngestJobContext context) {
        this.context = context;
    }
    
    @Override
    public boolean isCancelled() {
        return context.fileIngestIsCancelled();
    }

}
