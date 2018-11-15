/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.textextractors;

import com.google.common.collect.ImmutableList;
import org.sleuthkit.datamodel.AbstractFile;

/**
 *
 * @author dsmyda
 */
public class TextExtractorFactory {
    private static final ImmutableList<ContentTextExtractor> extractors = 
                ImmutableList.of(new HtmlTextExtractor(), 
                                 new SqliteTextExtractor(),
                                 new TikaTextExtractor(),
                                 new StringsTextExtractor());
        
    /**
     * Auto detects the corrent text extractor given the file and mimetype. Context 
     * 
     * @param file
     * @param mimeType
     * @param context
     * @return 
     */
    public static ContentTextExtractor getExtractor(AbstractFile file, String mimeType, ExtractionContext context) {
        ContentTextExtractor extractorInstance = null;

        for(ContentTextExtractor candidate : extractors) {
            candidate.parseContext(context);
            if(candidate.isSupported(file, mimeType)) {
                try {
                    extractorInstance = candidate.getClass().newInstance();
                    extractorInstance.parseContext(context);
                    break;
                } catch (InstantiationException | IllegalAccessException ex) {
                    
                }
            }
        }
        
        return extractorInstance;
    }
}
