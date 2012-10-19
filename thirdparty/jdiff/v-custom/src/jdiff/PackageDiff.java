package jdiff;

import java.util.*;
import com.sun.javadoc.*;

/**
 * Changes between two packages.
 *
 * See the file LICENSE.txt for copyright details.
 * @author Matthew Doar, mdoar@pobox.com
 */
class PackageDiff {

    public String name_;

    /** Classes added in the new API. */
    public List classesAdded = null;
    /** Classes removed in the new API. */
    public List classesRemoved = null;
    /** Classes changed in the new API. */
    public List classesChanged = null;

    /** 
     * A string describing the changes in documentation. 
     */
    public String documentationChange_ = null;

    /* The percentage difference for this package. */
    public double pdiff = 0.0;

    /** Default constructor. */
    public PackageDiff(String name) {
        name_ = name;
        classesAdded = new ArrayList(); // ClassAPI[]
        classesRemoved = new ArrayList(); // ClassAPI[]
        classesChanged = new ArrayList(); // ClassDiff[]
    }   
}
