/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.datamodel;

import java.util.Arrays;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.datamodel.accounts.FileTypeExtensionFilters;
import org.sleuthkit.datamodel.SleuthkitCase;

/**
 *
 * @author wschaefer
 */
public class FileTypesNew extends DisplayableItemNode {
    
    @NbBundle.Messages("FileTypesNew.name.text=File Types_New")
    public static final String NAME = Bundle.FileTypesNew_name_text();

    public FileTypesNew(SleuthkitCase sleuthkitCase) {
        super(new RootContentChildren(Arrays.asList(
                
                new FileTypeExtensionFilters(sleuthkitCase)   
        )), Lookups.singleton(NAME));
        setName(NAME);
        setDisplayName(NAME);
        this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/file_types.png");
    }

    
    @Override
    public boolean isLeafTypeNode() {
       return false;
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> v) {
       return v.visit(this);
    }
    
     @Override
    @NbBundle.Messages({
        "FileTypesNew.createSheet.name.name=Name",
        "FileTypesNew.createSheet.name.displayName=Name",
        "FileTypesNew.createSheet.name.desc=no description"})
    protected Sheet createSheet() {
        Sheet s = super.createSheet();
        Sheet.Set ss = s.get(Sheet.PROPERTIES);
        if (ss == null) {
            ss = Sheet.createPropertiesSet();
            s.put(ss);
        }

        ss.put(new NodeProperty<>(Bundle.FileTypesNew_createSheet_name_name(),
                Bundle.FileTypesNew_createSheet_name_displayName(),
                Bundle.FileTypesNew_createSheet_name_desc(),
                NAME
        ));
        return s;
    }

}
