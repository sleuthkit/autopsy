package jdiff;

import java.util.*;

/**
 * Convert some remove and add operations into change operations.
 *
 * Once the numbers of members removed and added are known
 * we can deduce more information about changes. For instance, if there are
 * two methods with the same name, and one or more of them has a 
 * parameter type change, then this can only be reported as removing 
 * the old version(s) and adding the new version(s), because there are 
 * multiple methods with the same name. 
 *
 * However, if only <i>one</i> method with a given name is removed, and  
 * only <i>one</i> method with the same name is added, we can convert these
 * operations to a change operation. For constructors, this is true if 
 * the types are the same. For fields, the field names have to be the same, 
 * though this should never occur, since field names are unique.
 *
 * Another merge which can be made is if two or more methods with the same name
 * were marked as removed and added because of changes other than signature.
 *
 * See the file LICENSE.txt for copyright details.
 * @author Matthew Doar, mdoar@pobox.com
 */
class MergeChanges {

    /**
     * Convert some remove and add operations into change operations.
     *
     * Note that if a single thread modifies a collection directly while it is 
     * iterating over the collection with a fail-fast iterator, the iterator 
     * will throw java.util.ConcurrentModificationException   
     */
    public static void mergeRemoveAdd(APIDiff apiDiff) {
        // Go through all the ClassDiff objects searching for the above cases.
        Iterator iter = apiDiff.packagesChanged.iterator();
        while (iter.hasNext()) {
            PackageDiff pkgDiff = (PackageDiff)(iter.next());
            Iterator iter2 = pkgDiff.classesChanged.iterator();
            while (iter2.hasNext()) {
                ClassDiff classDiff = (ClassDiff)(iter2.next());
                // Note: using iterators to step through the members gives a
                // ConcurrentModificationException exception with large files.
                // Constructors
                ConstructorAPI[] ctorArr = new ConstructorAPI[classDiff.ctorsRemoved.size()];
                ctorArr = (ConstructorAPI[])classDiff.ctorsRemoved.toArray(ctorArr);
                for (int ctorIdx = 0; ctorIdx < ctorArr.length; ctorIdx++) {
                    ConstructorAPI removedCtor = ctorArr[ctorIdx];
                    mergeRemoveAddCtor(removedCtor, classDiff, pkgDiff);
                }
                // Methods
                MethodAPI[] methodArr = new MethodAPI[classDiff.methodsRemoved.size()];
                methodArr = (MethodAPI[])classDiff.methodsRemoved.toArray(methodArr);
                for (int methodIdx = 0; methodIdx < methodArr.length; methodIdx++) {
                    MethodAPI removedMethod = methodArr[methodIdx];
                    // Only merge locally defined methods
                    if (removedMethod.inheritedFrom_ == null)
                        mergeRemoveAddMethod(removedMethod, classDiff, pkgDiff);
                }
                // Fields
                FieldAPI[] fieldArr = new FieldAPI[classDiff.fieldsRemoved.size()];
                fieldArr = (FieldAPI[])classDiff.fieldsRemoved.toArray(fieldArr);
                for (int fieldIdx = 0; fieldIdx < fieldArr.length; fieldIdx++) {
                    FieldAPI removedField = fieldArr[fieldIdx]; 
                    // Only merge locally defined fields
                    if (removedField.inheritedFrom_ == null)
                        mergeRemoveAddField(removedField, classDiff, pkgDiff);
                }
            }
        }        
    }

    /**
     * Convert some removed and added constructors into changed constructors.
     */
    public static void mergeRemoveAddCtor(ConstructorAPI removedCtor, ClassDiff classDiff, PackageDiff pkgDiff) {
        // Search on the type of the constructor
        int startRemoved = classDiff.ctorsRemoved.indexOf(removedCtor);
        int endRemoved = classDiff.ctorsRemoved.lastIndexOf(removedCtor);
        int startAdded = classDiff.ctorsAdded.indexOf(removedCtor);
        int endAdded = classDiff.ctorsAdded.lastIndexOf(removedCtor);
        if (startRemoved != -1 && startRemoved == endRemoved && 
            startAdded != -1 && startAdded == endAdded) {
            // There is only one constructor with the type of the
            // removedCtor in both the removed and added constructors.
            ConstructorAPI addedCtor = (ConstructorAPI)(classDiff.ctorsAdded.get(startAdded));
            // Create a MemberDiff for this change
            MemberDiff ctorDiff = new MemberDiff(classDiff.name_);
            ctorDiff.oldType_ = removedCtor.type_;
            ctorDiff.newType_ = addedCtor.type_; // Should be the same as removedCtor.type
            ctorDiff.oldExceptions_ = removedCtor.exceptions_;
            ctorDiff.newExceptions_ = addedCtor.exceptions_;
            ctorDiff.addModifiersChange(removedCtor.modifiers_.diff(addedCtor.modifiers_));
            // Track changes in documentation
            if (APIComparator.docChanged(removedCtor.doc_, addedCtor.doc_)) {
                String type = ctorDiff.newType_;
                if (type.compareTo("void") == 0)
                    type = "";
                String fqName = pkgDiff.name_ + "." + classDiff.name_;
                String link1 = "<a href=\"" + fqName + HTMLReportGenerator.reportFileExt + "\" class=\"hiddenlink\">";
                String link2 = "<a href=\"" + fqName + HTMLReportGenerator.reportFileExt + "#" + fqName + ".ctor_changed(" + type + ")\" class=\"hiddenlink\">";
                String id = pkgDiff.name_ + "." + classDiff.name_ + ".ctor(" + HTMLReportGenerator.simpleName(type) + ")";
                String title = link1 + "Class <b>" + classDiff.name_ + 
                    "</b></a>, " + link2 + "constructor <b>" + classDiff.name_ + "(" + HTMLReportGenerator.simpleName(type) + ")</b></a>";
                ctorDiff.documentationChange_ = Diff.saveDocDiffs(pkgDiff.name_, classDiff.name_, removedCtor.doc_, addedCtor.doc_, id, title);
            }
            classDiff.ctorsChanged.add(ctorDiff);
            // Now remove the entries from the remove and add lists
            classDiff.ctorsRemoved.remove(startRemoved);
            classDiff.ctorsAdded.remove(startAdded);
            if (trace && ctorDiff.modifiersChange_ != null)
                System.out.println("Merged the removal and addition of constructor into one change: " + ctorDiff.modifiersChange_);
        }
    }

    /**
     * Convert some removed and added methods into changed methods.
     */
    public static void mergeRemoveAddMethod(MethodAPI removedMethod, 
                                            ClassDiff classDiff, 
                                            PackageDiff pkgDiff) {
        mergeSingleMethods(removedMethod, classDiff, pkgDiff);
        mergeMultipleMethods(removedMethod, classDiff, pkgDiff);
    }

    /**
     * Convert single removed and added methods into a changed method.
     */
    public static void mergeSingleMethods(MethodAPI removedMethod, ClassDiff classDiff, PackageDiff pkgDiff) {
        // Search on the name of the method
        int startRemoved = classDiff.methodsRemoved.indexOf(removedMethod);
        int endRemoved = classDiff.methodsRemoved.lastIndexOf(removedMethod);
        int startAdded = classDiff.methodsAdded.indexOf(removedMethod);
        int endAdded = classDiff.methodsAdded.lastIndexOf(removedMethod);
        if (startRemoved != -1 && startRemoved == endRemoved && 
            startAdded != -1 && startAdded == endAdded) {
            // There is only one method with the name of the
            // removedMethod in both the removed and added methods.
            MethodAPI addedMethod = (MethodAPI)(classDiff.methodsAdded.get(startAdded));
            if (addedMethod.inheritedFrom_ == null) {
                // Create a MemberDiff for this change
                MemberDiff methodDiff = new MemberDiff(removedMethod.name_);
                methodDiff.oldType_ = removedMethod.returnType_;
                methodDiff.newType_ = addedMethod.returnType_;
                methodDiff.oldSignature_ = removedMethod.getSignature();
                methodDiff.newSignature_ = addedMethod.getSignature();
                methodDiff.oldExceptions_ = removedMethod.exceptions_;
                methodDiff.newExceptions_ = addedMethod.exceptions_;
                // The addModifiersChange field may not have been
                // initialized yet if there were multiple methods of the same
                // name.
                diffMethods(methodDiff, removedMethod, addedMethod);
                methodDiff.addModifiersChange(removedMethod.modifiers_.diff(addedMethod.modifiers_));
                // Track changes in documentation
                if (APIComparator.docChanged(removedMethod.doc_, addedMethod.doc_)) {
                    String sig = methodDiff.newSignature_;
                    if (sig.compareTo("void") == 0)
                        sig = "";
                    String fqName = pkgDiff.name_ + "." + classDiff.name_;
                    String link1 = "<a href=\"" + fqName + HTMLReportGenerator.reportFileExt + "\" class=\"hiddenlink\">";
                    String link2 = "<a href=\"" + fqName + HTMLReportGenerator.reportFileExt + "#" + fqName + "." + addedMethod.name_ + "_changed(" + sig + ")\" class=\"hiddenlink\">";
                    String id = pkgDiff.name_ + "." + classDiff.name_ + ".dmethod." + addedMethod.name_ + "(" + HTMLReportGenerator.simpleName(sig) + ")";
                    String title = link1 + "Class <b>" + classDiff.name_ + "</b></a>, " +
                        link2 +  HTMLReportGenerator.simpleName(methodDiff.newType_) + " <b>" + addedMethod.name_ + "(" + HTMLReportGenerator.simpleName(sig) + ")</b></a>";
                    methodDiff.documentationChange_ = Diff.saveDocDiffs(pkgDiff.name_, classDiff.name_, removedMethod.doc_, addedMethod.doc_, id, title);
                }
                classDiff.methodsChanged.add(methodDiff);
                // Now remove the entries from the remove and add lists
                classDiff.methodsRemoved.remove(startRemoved);
                classDiff.methodsAdded.remove(startAdded);
                if (trace) {
                    System.out.println("Merged the removal and addition of method " + 
                                       removedMethod.name_ + 
                                       " into one change");
                }
            } //if (addedMethod.inheritedFrom_ == null)
        }
    }

    /**
     * Convert multiple removed and added methods into changed methods.
     * This handles the case where the methods' signatures are unchanged, but
     * something else changed.
     */
    public static void mergeMultipleMethods(MethodAPI removedMethod, ClassDiff classDiff, PackageDiff pkgDiff) {
        // Search on the name and signature of the method
        int startRemoved = classDiff.methodsRemoved.indexOf(removedMethod);
        int endRemoved = classDiff.methodsRemoved.lastIndexOf(removedMethod);
        int startAdded = classDiff.methodsAdded.indexOf(removedMethod);
        int endAdded = classDiff.methodsAdded.lastIndexOf(removedMethod);
        if (startRemoved != -1 && endRemoved != -1 && 
            startAdded != -1 && endAdded != -1) {
            // Find the index of the current removed method
            int removedIdx = -1;
            for (int i = startRemoved; i <= endRemoved; i++) {                
                if (removedMethod.equalSignatures(classDiff.methodsRemoved.get(i))) {
                    removedIdx = i;
                    break;
                }
            }
            if (removedIdx == -1) {
                System.out.println("Error: removed method index not found");
                System.exit(5);
            }
            // Find the index of the added method with the same signature, if 
            // it exists, and make sure it is defined locally.
            int addedIdx = -1;
            for (int i = startAdded; i <= endAdded; i++) {
                MethodAPI addedMethod2 = (MethodAPI)(classDiff.methodsAdded.get(i));
                if (addedMethod2.inheritedFrom_ == null &&
                    removedMethod.equalSignatures(addedMethod2))
                    addedIdx = i;
                    break;
            }
            if (addedIdx == -1)
                return;
            MethodAPI addedMethod = (MethodAPI)(classDiff.methodsAdded.get(addedIdx));
            // Create a MemberDiff for this change
            MemberDiff methodDiff = new MemberDiff(removedMethod.name_);
            methodDiff.oldType_ = removedMethod.returnType_;
            methodDiff.newType_ = addedMethod.returnType_;
            methodDiff.oldSignature_ = removedMethod.getSignature();
            methodDiff.newSignature_ = addedMethod.getSignature();
            methodDiff.oldExceptions_ = removedMethod.exceptions_;
            methodDiff.newExceptions_ = addedMethod.exceptions_;
                // The addModifiersChange field may not have been
                // initialized yet if there were multiple methods of the same
                // name.
                diffMethods(methodDiff, removedMethod, addedMethod);
            methodDiff.addModifiersChange(removedMethod.modifiers_.diff(addedMethod.modifiers_));
            // Track changes in documentation
            if (APIComparator.docChanged(removedMethod.doc_, addedMethod.doc_)) {
                String sig = methodDiff.newSignature_;
                if (sig.compareTo("void") == 0)
                    sig = "";
                String fqName = pkgDiff.name_ + "." + classDiff.name_;
                String link1 = "<a href=\"" + fqName + HTMLReportGenerator.reportFileExt + "\" class=\"hiddenlink\">";
                String link2 = "<a href=\"" + fqName + HTMLReportGenerator.reportFileExt + "#" + fqName + "." + addedMethod.name_ + "_changed(" + sig + ")\" class=\"hiddenlink\">";
                String id = pkgDiff.name_ + "." + classDiff.name_ + ".dmethod." + addedMethod.name_ + "(" + HTMLReportGenerator.simpleName(sig) + ")";
                String title = link1 + "Class <b>" + classDiff.name_ + "</b></a>, " +
                    link2 +  HTMLReportGenerator.simpleName(methodDiff.newType_) + " <b>" + addedMethod.name_ + "(" + HTMLReportGenerator.simpleName(sig) + ")</b></a>";
                methodDiff.documentationChange_ = Diff.saveDocDiffs(pkgDiff.name_, classDiff.name_, removedMethod.doc_, addedMethod.doc_, id, title);
            }
            classDiff.methodsChanged.add(methodDiff);
            // Now remove the entries from the remove and add lists
            classDiff.methodsRemoved.remove(removedIdx);
            classDiff.methodsAdded.remove(addedIdx);
            if (trace) {
                System.out.println("Merged the removal and addition of method " + 
                                   removedMethod.name_ + 
                                   " into one change. There were multiple methods of this name.");
            }
        }
    }

    /**
     * Track changes in methods related to abstract, native, and 
     * synchronized modifiers here.
     */
    public static void diffMethods(MemberDiff methodDiff, 
                                   MethodAPI oldMethod, 
                                   MethodAPI newMethod) {
        // Abstract or not
        if (oldMethod.isAbstract_ != newMethod.isAbstract_) {
            String changeText = "";
            if (oldMethod.isAbstract_)
                changeText += "Changed from abstract to non-abstract.";
            else
                changeText += "Changed from non-abstract to abstract.";
            methodDiff.addModifiersChange(changeText);
        }
        // Native or not
        if (Diff.showAllChanges && 
	    oldMethod.isNative_ != newMethod.isNative_) {
            String changeText = "";
            if (oldMethod.isNative_)
                changeText += "Changed from native to non-native.";
            else
                changeText += "Changed from non-native to native.";
            methodDiff.addModifiersChange(changeText);
        }
        // Synchronized or not
        if (Diff.showAllChanges && 
	    oldMethod.isSynchronized_ != newMethod.isSynchronized_) {
            String changeText = "";
            if (oldMethod.isSynchronized_)
                changeText += "Changed from synchronized to non-synchronized.";
            else
                changeText += "Changed from non-synchronized to synchronized.";
            methodDiff.addModifiersChange(changeText);
        }
    }

    /**
     * Convert some removed and added fields into changed fields.
     */
    public static void mergeRemoveAddField(FieldAPI removedField, ClassDiff classDiff, PackageDiff pkgDiff) {
        // Search on the name of the field
        int startRemoved = classDiff.fieldsRemoved.indexOf(removedField);
        int endRemoved = classDiff.fieldsRemoved.lastIndexOf(removedField);
        int startAdded = classDiff.fieldsAdded.indexOf(removedField);
        int endAdded = classDiff.fieldsAdded.lastIndexOf(removedField);
        if (startRemoved != -1 && startRemoved == endRemoved && 
            startAdded != -1 && startAdded == endAdded) {
            // There is only one field with the name of the
            // removedField in both the removed and added fields.
            FieldAPI addedField = (FieldAPI)(classDiff.fieldsAdded.get(startAdded));
            if (addedField.inheritedFrom_ == null) {
                // Create a MemberDiff for this change
                MemberDiff fieldDiff = new MemberDiff(removedField.name_);
                fieldDiff.oldType_ = removedField.type_;
                fieldDiff.newType_ = addedField.type_;
                fieldDiff.addModifiersChange(removedField.modifiers_.diff(addedField.modifiers_));
                // Track changes in documentation
                if (APIComparator.docChanged(removedField.doc_, addedField.doc_)) {
                    String fqName = pkgDiff.name_ + "." + classDiff.name_;
                    String link1 = "<a href=\"" + fqName + HTMLReportGenerator.reportFileExt + "\" class=\"hiddenlink\">";
                    String link2 = "<a href=\"" + fqName + HTMLReportGenerator.reportFileExt + "#" + fqName + "." + addedField.name_ + "\" class=\"hiddenlink\">";
                    String id = pkgDiff.name_ + "." + classDiff.name_ + ".field." + addedField.name_;
                    String title = link1 + "Class <b>" + classDiff.name_ + "</b></a>, " +
                        link2 + HTMLReportGenerator.simpleName(fieldDiff.newType_) + " <b>" + addedField.name_ + "</b></a>";
                    fieldDiff.documentationChange_ = Diff.saveDocDiffs(pkgDiff.name_, classDiff.name_, removedField.doc_, addedField.doc_, id, title);
                }
                classDiff.fieldsChanged.add(fieldDiff);
                // Now remove the entries from the remove and add lists
                classDiff.fieldsRemoved.remove(startRemoved);
                classDiff.fieldsAdded.remove(startAdded);
                if (trace) {
                    System.out.println("Merged the removal and addition of field " + 
                                       removedField.name_ + 
                                       " into one change");
                }
            } //if (addedField.inheritedFrom == null) 
        }
    }

    /** Set to enable increased logging verbosity for debugging. */
    private static boolean trace = false;

}
