package jdiff;

import java.util.*;
import com.sun.javadoc.*;

/**
 * The class contains the changes between two API objects; packages added,
 * removed and changed. The packages are represented by PackageDiff objects,
 * which contain the changes in each package, and so on.
 *
 * See the file LICENSE.txt for copyright details.
 * @author Matthew Doar, mdoar@pobox.com
 */
public class APIDiff {

    /** Packages added in the new API. */
    public List packagesAdded = null; // PackageAPI[]
    /** Packages removed in the new API. */
    public List packagesRemoved = null; // PackageAPI[]
    /** Packages changed in the new API. */
    public List packagesChanged = null; // PackageDiff[]

    /** Name of the old API. */
    public static String oldAPIName_;
    /** Name of the old API. */
    public static String newAPIName_;

    /* The overall percentage difference between the two APIs. */
    public double pdiff = 0.0;

    /** Default constructor. */
    public APIDiff() {
        oldAPIName_ = null;
        newAPIName_ = null;
        packagesAdded = new ArrayList(); // PackageAPI[]
        packagesRemoved = new ArrayList(); // PackageAPI[]
        packagesChanged = new ArrayList(); // PackageDiff[]
    }   
}

