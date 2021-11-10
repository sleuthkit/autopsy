/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.mainui.nodes;

import org.openide.nodes.Children;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.datamodel.TskContentItem;
import org.sleuthkit.autopsy.mainui.datamodel.FileSystemRowDTO.PoolRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.SearchResultsDTO;

/**
 *
 */
public class PoolNode extends BaseNode<SearchResultsDTO, PoolRowDTO> {
    public PoolNode(SearchResultsDTO results, PoolRowDTO row) {
        super(Children.LEAF,  
                Lookups.fixed(row.getContent(), new TskContentItem<>(row.getContent())), 
                results, row);
        
        String name = row.getContent().getType().getName();
        setDisplayName(name);
        setShortDescription(name);
        setIconBaseWithExtension("org/sleuthkit/autopsy/images/pool-icon.png");
    }
}
