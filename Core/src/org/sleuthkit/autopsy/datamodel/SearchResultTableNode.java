/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.datamodel;

import java.util.Collections;
import java.util.List;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.datamodel.SearchResultChildFactory.NodeCreator;
import org.sleuthkit.autopsy.datamodel.ThreePanelDAO.ColumnKey;
import org.sleuthkit.autopsy.datamodel.ThreePanelDAO.RowResultDTO;
import org.sleuthkit.autopsy.datamodel.ThreePanelDAO.SearchResultsDTO;

/**
 *
 * @author gregd
 */
public class SearchResultTableNode<T extends SearchResultsDTO<S>, S extends RowResultDTO> extends AbstractNode {

    private final SearchResultChildFactory<T, S> factory;

    public SearchResultTableNode(NodeCreator<T, S> nodeCreator, T initialResults) {
        this(initialResults, new SearchResultChildFactory<>(nodeCreator, initialResults));
    }

    private SearchResultTableNode(SearchResultsDTO<S> initialResults, SearchResultChildFactory<T, S> factory) {
        super(Children.create(factory, true));
        this.factory = factory;
        
        setName(initialResults.getTypeId());
        setDisplayName(initialResults.getDisplayName());

//        String iconPath = IconsUtil.getIconFilePath(initialResults.getArtifactType().getTypeID());
//        setIconBaseWithExtension(iconPath != null && iconPath.charAt(0) == '/' ? iconPath.substring(1) : iconPath);
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
