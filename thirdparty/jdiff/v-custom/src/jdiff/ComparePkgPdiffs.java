package jdiff;

import java.util.*;

/** 
 * Class to compare two PackageDiff objects.
 *
 * See the file LICENSE.txt for copyright details.
 * @author Matthew Doar, mdoar@pobox.com
 */
class ComparePkgPdiffs implements Comparator {
    /** 
     * Compare two package diffs by their percentage difference,
     * and then by name.
     */
    public int compare(Object obj1, Object obj2){
        PackageDiff p1 = (PackageDiff)obj1;
        PackageDiff p2 = (PackageDiff)obj2;
        if (p1.pdiff < p2.pdiff)
            return 1;
        if (p1.pdiff > p2.pdiff)
            return -1;
        return p1.name_.compareTo(p2.name_);
    }
}
