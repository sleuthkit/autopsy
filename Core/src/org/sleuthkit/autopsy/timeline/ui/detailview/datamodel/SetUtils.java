/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.ui.detailview.datamodel;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 *
 */
final class SetUtils {

    static public <X> Set<X> union(Set<X> setA, Set<X> setB) {
        HashSet<X> union = new HashSet<>(setA);
        union.addAll(setB);
        return union;
    }

    static public <X> SortedSet<X> copyAsSortedSet(Collection<X> setA, Comparator<X> comparator) {
        TreeSet<X> treeSet = new TreeSet<>(comparator);
        treeSet.addAll(setA);
        return treeSet;
    }

    private SetUtils() {
    }
}
