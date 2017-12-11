package jdiff;

import java.util.*;

/**
 * Emit a standard text report with only the names
 * of all packages which need a major version number change.
 */
public class ScriptReport {

    /** Default constructor. */
    public ScriptReport() { }

    /**
     * Checks to see if the tested module is backwards compatible.
     *
     * @return 100 if no changes
     *         101 if compatible changes
     *         102 if not compatible
     */
    public int run(APIComparator comp) {
        // Get the APIDiff
        APIDiff apiDiff = comp.apiDiff;

        if(apiDiff.packagesRemoved.size() > 0) {
            return 102;
        }

        Iterator piter = apiDiff.packagesChanged.iterator();
        while (piter.hasNext()) {
            PackageDiff pkgDiff = (PackageDiff)(piter.next());
            if(pkgDiff.classesRemoved.size() > 0) {
                return 102;
            }

            Iterator citer = pkgDiff.classesChanged.iterator();
            while(citer.hasNext()) {
                ClassDiff classDiff = (ClassDiff)(citer.next());
                if(classDiff.methodsRemoved.size() > 0) {
                    return 102;
                }

                Iterator miter = classDiff.methodsChanged.iterator();
                while (miter.hasNext()) {
                    // Check if method has different return type
                    MemberDiff memberDiff = (MemberDiff)(miter.next());
                    if(!memberDiff.oldType_ .equals(memberDiff.newType_)) {
                        return 102;
                    }
                }
            }
        }
        
        // If there were any changes, but we haven't returned yet
        // they must all be backwards compatible changes
        if(apiDiff.packagesChanged.size() > 0) {
            return 101;
        }
        // If we've reached here there must be no changes at all
        return 100;
    }

}
