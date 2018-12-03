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
                                 new TikaTextExtractor());
        
    /**
     * Auto detects the corrent text extractor given the file and mimetype. Context 
     * 
     * @param file
     * @param mimeType
     * @param context
     * @return 
     * @throws org.sleuthkit.autopsy.textextractors.TextExtractorFactory.NoSpecializedExtractorException 
     */
    public static ContentTextExtractor getSpecializedExtractor(AbstractFile file, 
            String mimeType, ExtractionContext context) throws NoSpecializedExtractorException {
        for(ContentTextExtractor candidate : extractors) {
            candidate.parseContext(context);
            if(candidate.isSupported(file, mimeType)) {
                try {
                    ContentTextExtractor extractorInstance = candidate.getClass().newInstance();
                    extractorInstance.parseContext(context);
                    return extractorInstance;
                } catch (InstantiationException | IllegalAccessException ex) {
                    
                }
            }
        }
        
        throw new NoSpecializedExtractorException("Could not find a suitable extractor for mimetype ["+mimeType+"]");
    }
    
    public static StringsTextExtractor getDefaultExtractor(ExtractionContext context) {
        StringsTextExtractor instance = new StringsTextExtractor();
        instance.parseContext(context);
        return instance;
    }
    
    public static class NoSpecializedExtractorException extends Exception {
        public NoSpecializedExtractorException(String msg) {
            super(msg);
        }
    }
}
