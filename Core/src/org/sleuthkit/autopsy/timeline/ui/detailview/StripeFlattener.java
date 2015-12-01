/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.ui.detailview;

import java.util.function.Function;
import java.util.stream.Stream;
import org.sleuthkit.autopsy.timeline.datamodel.EventStripe;

/**
 * Use this recursive function to flatten a tree of nodes into an single stream.
 * More specifically it takes an EventStripeNode and produces a stream of
 * EventStripes containing the stripes for the given node and all child
 * eventStripes, ignoring intervening EventCluster nodes.
 *
 * @see
 * #loadSubBundles(org.sleuthkit.autopsy.timeline.zooming.DescriptionLoD.RelativeDetail)
 * for usage
 */
class StripeFlattener implements Function<EventStripeNode, Stream<EventStripe>> {

    @Override
    public Stream<EventStripe> apply(EventStripeNode node) {
        return Stream.concat(
                Stream.of(node.getEventStripe()),
                node.getSubNodes().stream().flatMap(clusterNode ->
                        clusterNode.getSubNodes().stream().flatMap(this)));
    }
}
