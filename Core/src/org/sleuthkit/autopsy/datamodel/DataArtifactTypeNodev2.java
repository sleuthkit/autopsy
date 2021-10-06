/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.datamodel;

import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.datamodel.ThreePanelDAO.DataArtifactTableSearchResultsDTO;
import org.sleuthkit.autopsy.datamodel.utils.IconsUtil;

/**
 *
 * @author gregd
 */
public class DataArtifactTypeNodev2 extends AbstractNode {
    private final SearchResultChildFactory<?,?> factory;
    
    public DataArtifactTypeNodev2(DataArtifactTableSearchResultsDTO initialResults) {
        this(initialResults, new DataArtifactFactoryv2(initialResults));
    }
        
    private DataArtifactTypeNodev2(DataArtifactTableSearchResultsDTO initialResults, DataArtifactFactoryv2 factory) {
        super(Children.create(factory, true));
        this.factory = factory;
        setName(initialResults.getArtifactType().getTypeName());
        setDisplayName(initialResults.getArtifactType().getDisplayName());
        String iconPath = IconsUtil.getIconFilePath(initialResults.getArtifactType().getTypeID());
        setIconBaseWithExtension(iconPath != null && iconPath.charAt(0) == '/' ? iconPath.substring(1) : iconPath);
    }

    @Override
    protected Sheet createSheet() {
        Sheet sheet = super.createSheet();
        Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
        if (sheetSet == null) {
            sheetSet = Sheet.createPropertiesSet();
            sheet.put(sheetSet);
        }

        sheetSet.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "ArtifactTypeNode.createSheet.artType.name"),
                NbBundle.getMessage(this.getClass(), "ArtifactTypeNode.createSheet.artType.displayName"),
                NbBundle.getMessage(this.getClass(), "ArtifactTypeNode.createSheet.artType.desc"),
                getDisplayName()));

            sheetSet.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "ArtifactTypeNode.createSheet.childCnt.name"),
                    NbBundle.getMessage(this.getClass(), "ArtifactTypeNode.createSheet.childCnt.displayName"),
                    NbBundle.getMessage(this.getClass(), "ArtifactTypeNode.createSheet.childCnt.desc"),
                    this.factory.getResultCount()));
            
        return sheet;
    }
}
