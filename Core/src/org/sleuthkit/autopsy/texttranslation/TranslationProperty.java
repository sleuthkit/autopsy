/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.texttranslation;

import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.datamodel.AbstractFile;
/**
 *
 * @author dsmyda
 */
@ServiceProvider(service = CustomFileProperty.class)
public class TranslationProperty implements CustomFileProperty {

    @Override
    public boolean isDisabled() {
        return false;
    }

    @Override
    public String getPropertyName() {
        return "Translated Name";
    }

    @Override
    public Object getPropertyValue(AbstractFile content) {
        return "Foo";
    }

    @Override
    public Integer getColumnPosition() {
        return 1;
    }
}
