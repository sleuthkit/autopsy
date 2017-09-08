package jdiff;

import java.util.*;
import com.sun.javadoc.*;

/**
 * The changes between two classes.
 *
 * See the file LICENSE.txt for copyright details.
 * @author Matthew Doar, mdoar@pobox.com
 */
class ClassDiff {

    /** Name of the class. */
    public String name_;

    /** Set if this class is an interface in the new API. */
    public boolean isInterface_;

    /** 
     * A string describing the changes in inheritance. 
     */
    public String inheritanceChange_ = null;

    /** 
     * A string describing the changes in documentation. 
     */
    public String documentationChange_ = null;

    /** 
     * A string describing the changes in modifiers. 
     * Changes can be in whether this is a class or interface, whether it is
     * abstract, static, final, and in its visibility.
     */
    public String modifiersChange_ = null;

    /** Constructors added in the new API. */
    public List ctorsAdded = null;
    /** Constructors removed in the new API. */
    public List ctorsRemoved = null;
    /** Constructors changed in the new API. */
    public List ctorsChanged = null;

    /** Methods added in the new API. */
    public List methodsAdded = null;
    /** Methods removed in the new API. */
    public List methodsRemoved = null;
    /** Methods changed in the new API. */
    public List methodsChanged = null;

    /** Fields added in the new API. */
    public List fieldsAdded = null;
    /** Fields removed in the new API. */
    public List fieldsRemoved = null;
    /** Fields changed in the new API. */
    public List fieldsChanged = null;

    /* The percentage difference for this class. */
    public double pdiff = 0.0;

    /** Default constructor. */
    public ClassDiff(String name) {
        name_ = name;
        isInterface_ = false;

        ctorsAdded = new ArrayList(); // ConstructorAPI[]
        ctorsRemoved = new ArrayList(); // ConstructorAPI[]
        ctorsChanged = new ArrayList(); // MemberDiff[]

        methodsAdded = new ArrayList(); // MethodAPI[]
        methodsRemoved = new ArrayList(); // MethodAPI[]
        methodsChanged = new ArrayList(); // MemberDiff[]

        fieldsAdded = new ArrayList(); // FieldAPI[]
        fieldsRemoved = new ArrayList(); // FieldAPI[]
        fieldsChanged = new ArrayList(); // MemberDiff[]
    }   

    /** 
     * Compare the inheritance details of two classes and produce 
     * a String for the inheritanceChanges_ field in this class.
     * If there is no difference, null is returned.
     */
    public static String diff(ClassAPI oldClass, ClassAPI newClass) {
        Collections.sort(oldClass.implements_);
        Collections.sort(newClass.implements_);
        String res = "";
        boolean hasContent = false;
        if (oldClass.extends_ != null && newClass.extends_ != null &&
            oldClass.extends_.compareTo(newClass.extends_) != 0) {
            res += "The superclass changed from <code>" + oldClass.extends_ + "</code> to <code>" + newClass.extends_ + "</code>.<br>";
            hasContent = true;
        }
        // Check for implemented interfaces which were removed
        String removedInterfaces = "";
        int numRemoved = 0;
        Iterator iter = oldClass.implements_.iterator();
        while (iter.hasNext()) {
            String oldInterface = (String)(iter.next());
            int idx = Collections.binarySearch(newClass.implements_, oldInterface);
            if (idx < 0) {
                if (numRemoved != 0)
                    removedInterfaces += ", ";
                removedInterfaces += oldInterface;
                numRemoved++;
            }
        }
        String addedInterfaces = "";
        int numAdded = 0;
        iter = newClass.implements_.iterator();
        while (iter.hasNext()) {
            String newInterface = (String)(iter.next());
            int idx = Collections.binarySearch(oldClass.implements_, newInterface);
            if (idx < 0) {
                if (numAdded != 0)
                    addedInterfaces += ", ";
                addedInterfaces += newInterface;
                numAdded++;
            }
        }
        if (numRemoved != 0) {
            if (hasContent)
                res += " ";
            if (numRemoved == 1)
                res += "Removed interface <code>" + removedInterfaces + "</code>.<br>";
            else
                res += "Removed interfaces <code>" + removedInterfaces + "</code>.<br>";
            hasContent = true;
        }
        if (numAdded != 0) {
            if (hasContent)
                res += " ";
            if (numAdded == 1)
                res += "Added interface <code>" + addedInterfaces + "</code>.<br>";
            else
                res += "Added interfaces <code>" + addedInterfaces + "</code>.<br>";
            hasContent = true;
        }
        if (res.compareTo("") == 0)
            return null;
        return res;
    }

    /** Add a change in the modifiers. */
    public void addModifiersChange(String commonModifierChanges) {
        if (commonModifierChanges != null) {
            if (modifiersChange_ == null)
                modifiersChange_ = commonModifierChanges;
            else
                modifiersChange_ += " " + commonModifierChanges;
        }
    }
}

