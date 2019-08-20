/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-16 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.timeline.ui.detailview;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.sleuthkit.autopsy.timeline.ui.detailview.datamodel.DetailViewEvent;

/**
 * Use this recursive function to flatten a tree of nodes into an single stream.
 * More specifically it takes an EventStripeNode and produces a stream of
 * EventStripes containing the stripes for the given node and all child
 * eventStripes, ignoring intervening EventCluster nodes.
 */
class StripeFlattener implements Function<EventNodeBase<?>, Stream<DetailViewEvent>> {

    @Override
    public Stream<DetailViewEvent> apply(EventNodeBase<?> node) {
        return Stream.concat(
                Stream.of(node.getEvent()),
                node.getSubNodes().stream().flatMap(clusterNode
                        -> clusterNode.getSubNodes().stream().flatMap(this)));
    }

    static public List<DetailViewEvent> flatten(Collection<EventNodeBase<?>> nodes) {
        return nodes.stream().flatMap(new StripeFlattener()).collect(Collectors.toList());
    }

}
