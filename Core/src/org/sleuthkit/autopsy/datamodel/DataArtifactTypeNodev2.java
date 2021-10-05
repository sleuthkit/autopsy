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
import org.sleuthkit.autopsy.datamodel.utils.IconsUtil;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 *
 * @author gregd
 */
public class DataArtifactTypeNodev2 extends AbstractNode {

    private final BlackboardArtifact.Type artifactType;

    public DataArtifactTypeNodev2(BlackboardArtifact.Type artifactType, Long dataSourceId) {
        super(Children.create(new DataArtifactFactoryv2(artifactType, dataSourceId), true));
        setName(artifactType.getTypeName());
        setDisplayName(artifactType.getDisplayName());
        this.artifactType = artifactType;
        String iconPath = IconsUtil.getIconFilePath(artifactType.getTypeID());
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
                this.artifactType.getDisplayName()));

//            sheetSet.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "ArtifactTypeNode.createSheet.childCnt.name"),
//                    NbBundle.getMessage(this.getClass(), "ArtifactTypeNode.createSheet.childCnt.displayName"),
//                    NbBundle.getMessage(this.getClass(), "ArtifactTypeNode.createSheet.childCnt.desc"),
//                    getChildCount()));
        return sheet;
    }
}
