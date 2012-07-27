/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.hashdatabase;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.sleuthkit.datamodel.FsContent;

/**
 *
 * @author dhurd
 */
public class HashDbSearchResultFactory extends ChildFactory<HashSearchPairs>{
    List<HashSearchPairs>  pairs;
    
    HashDbSearchResultFactory(List<HashSearchPairs> pairs) {
        this.pairs = pairs;
    }
    
    
    
    @Override
    protected boolean createKeys(List<HashSearchPairs> toPopulate) {
        toPopulate.addAll(pairs);
        return true;
    }

    @Override
    protected Node createNodeForKey(List<HashSearchPairs>  pairs) {
        return new HashDbSearchNode(pairs, Children.create(this, true));
    }
}
