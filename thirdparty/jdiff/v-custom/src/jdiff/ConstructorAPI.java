package jdiff;

import java.io.*;
import java.util.*;

/** 
 * Class to represent a constructor, analogous to ConstructorDoc in the 
 * Javadoc doclet API. 
 *
 * The method used for Collection comparison (compareTo) must make its
 * comparison based upon everything that is known about this constructor.
 *
 * See the file LICENSE.txt for copyright details.
 * @author Matthew Doar, mdoar@pobox.com
 */
class ConstructorAPI implements Comparable {
    /** 
     * The type of the constructor, being all the parameter types
     * separated by commas.
     */
    public String type_ = null;
    
    /** 
     * The exceptions thrown by this constructor, being all the exception types
     * separated by commas. "no exceptions" if no exceptions are thrown.
     */
    public String exceptions_ = "no exceptions";
    
    /** Modifiers for this class. */
    public Modifiers modifiers_;

    /** The doc block, default is null. */
    public String doc_ = null;

    /** Constructor. */
    public ConstructorAPI(String type, Modifiers modifiers) {
        type_ = type;
        modifiers_ = modifiers;
    }

    /** Compare two ConstructorAPI objects by type and modifiers. */
    public int compareTo(Object o) {
        ConstructorAPI constructorAPI = (ConstructorAPI)o;
        int comp = type_.compareTo(constructorAPI.type_);
        if (comp != 0)
            return comp;
        comp = exceptions_.compareTo(constructorAPI.exceptions_);
        if (comp != 0)
            return comp;
        comp = modifiers_.compareTo(constructorAPI.modifiers_);
        if (comp != 0)
            return comp;
        if (APIComparator.docChanged(doc_, constructorAPI.doc_))
            return -1;
        return 0;
    }

    /** 
     * Tests two constructors, using just the type, used by indexOf(). 
     */
    public boolean equals(Object o) {
        if (type_.compareTo(((ConstructorAPI)o).type_) == 0)
            return true;
        return false;
    }
}  
