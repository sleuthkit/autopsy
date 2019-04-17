/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.communications;

import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.sleuthkit.datamodel.AbstractContent;

/**
 *
 * 
 */
public class ThumbnailNode  extends AbstractNode{
    
    private final AbstractContent content;
    
    public ThumbnailNode(AbstractContent content) {
       super(Children.LEAF);
       this.content = content;
       
       setDisplayName(content.getName());
    }
    
}
