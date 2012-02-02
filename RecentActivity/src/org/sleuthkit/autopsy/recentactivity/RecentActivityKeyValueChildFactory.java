package org.sleuthkit.autopsy.recentactivity;

import java.util.Collection;
import java.util.List;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.datamodel.KeyValueNode;
import org.sleuthkit.autopsy.datamodel.KeyValue;

public class RecentActivityKeyValueChildFactory extends ChildFactory<KeyValue> {

    private Collection<KeyValue> things;

    public RecentActivityKeyValueChildFactory(Collection<KeyValue> things) {
        this.things = things;
    }

    @Override
    protected boolean createKeys(List<KeyValue> toPopulate) {
        return toPopulate.addAll(things);
    }

    @Override
    protected Node createNodeForKey(KeyValue thing) {
        return new KeyValueNode(thing, Children.LEAF);
    }
}
