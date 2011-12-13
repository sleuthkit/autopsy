package org.sleuthkit.autopsy.example;

import java.util.Collection;
import java.util.List;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.datamodel.KeyValueNode;
import org.sleuthkit.autopsy.datamodel.KeyValueThing;

public class ExampleKeyValueChildFactory extends ChildFactory<KeyValueThing> {

    private Collection<KeyValueThing> things;

    public ExampleKeyValueChildFactory(Collection<KeyValueThing> things) {
        this.things = things;
    }

    @Override
    protected boolean createKeys(List<KeyValueThing> toPopulate) {
        return toPopulate.addAll(things);
    }

    @Override
    protected Node createNodeForKey(KeyValueThing thing) {
        return new KeyValueNode(thing, Children.LEAF);
    }
}
