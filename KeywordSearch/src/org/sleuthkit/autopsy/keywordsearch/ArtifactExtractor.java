/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.keywordsearch;

import java.io.InputStream;
import java.io.Reader;
import org.sleuthkit.datamodel.BlackboardArtifact;

public class ArtifactExtractor extends TextProvider<Void, BlackboardArtifact> {

    @Override
    boolean noExtractionOptionsAreEnabled() {
        return false;
    }

    @Override
    void logWarning(String msg, Exception ex) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    Void newAppendixProvider() {
        return null;
    }

    @Override
    InputStream getInputStream(BlackboardArtifact source) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    Reader getReader(InputStream stream, BlackboardArtifact source, Void appendix) throws Ingester.IngesterException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

}
