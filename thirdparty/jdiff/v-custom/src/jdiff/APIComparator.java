package jdiff;

import java.util.*;

/**
 * This class contains method to compare two API objects.
 * The differences are stored in an APIDiff object.
 *
 * See the file LICENSE.txt for copyright details.
 * @author Matthew Doar, mdoar@pobox.com
 */
public class APIComparator {

    /** 
     * Top-level object representing the differences between two APIs. 
     * It is this object which is used to generate the report later on.
     */
    public APIDiff apiDiff;

    /** 
     * Package-level object representing the differences between two packages. 
     * This object is also used to determine which file to write documentation
     * differences into.
     */
    public PackageDiff pkgDiff;

    /** Default constructor. */
    public APIComparator() {
        apiDiff = new APIDiff();
    }   

    /** For easy local access to the old API object. */
    private static API oldAPI_;
    /** For easy local access to the new API object. */
    private static API newAPI_;

    /** 
     * Compare two APIs. 
     */
    public void compareAPIs(API oldAPI, API newAPI) {
        System.out.println("JDiff: comparing the old and new APIs ...");
        oldAPI_ = oldAPI;
        newAPI_ = newAPI;

        double differs = 0.0;

        apiDiff.oldAPIName_ = oldAPI.name_;
        apiDiff.newAPIName_ = newAPI.name_;

        Collections.sort(oldAPI.packages_);
        Collections.sort(newAPI.packages_);

        // Find packages which were removed in the new API
        Iterator iter = oldAPI.packages_.iterator();
        while (iter.hasNext()) {
            PackageAPI oldPkg = (PackageAPI)(iter.next());
            // This search is looking for an *exact* match. This is true in
            // all the *API classes.
            int idx = Collections.binarySearch(newAPI.packages_, oldPkg);
            if (idx < 0) {
                // If there an instance of a package with the same name 
                // in both the old and new API, then treat it as changed,
                // rather than removed and added. There will never be more than
                // one instance of a package with the same name in an API.
                int existsNew = newAPI.packages_.indexOf(oldPkg);
                if (existsNew != -1) {
                    // Package by the same name exists in both APIs
                    // but there has been some or other change.
                    differs += 2.0 * comparePackages(oldPkg, (PackageAPI)(newAPI.packages_.get(existsNew)));
                }  else {
                    if (trace)
                        System.out.println("Package " + oldPkg.name_ + " was removed");
                    apiDiff.packagesRemoved.add(oldPkg);
                    differs += 1.0;
                }
            } else {
                // The package exists unchanged in name or doc, but may 
                // differ in classes and their members, so it still needs to 
                // be compared.
                differs += 2.0 * comparePackages(oldPkg, (PackageAPI)(newAPI.packages_.get(idx)));
            }
        } // while (iter.hasNext())

        // Find packages which were added or changed in the new API
        iter = newAPI.packages_.iterator();
        while (iter.hasNext()) {
            PackageAPI newPkg = (PackageAPI)(iter.next());
            int idx = Collections.binarySearch(oldAPI.packages_, newPkg);
            if (idx < 0) {
                // See comments above
                int existsOld = oldAPI.packages_.indexOf(newPkg);
                if (existsOld != -1) {
                    // Don't mark a package as added or compare it 
                    // if it was already marked as changed
                } else {
                    if (trace)
                        System.out.println("Package " + newPkg.name_ + " was added");
                    apiDiff.packagesAdded.add(newPkg);
                    differs += 1.0;
                }
            } else {
                // It will already have been compared above.
            }
        } // while (iter.hasNext())

        // Now that the numbers of members removed and added are known
        // we can deduce more information about changes.
        MergeChanges.mergeRemoveAdd(apiDiff);

// The percent change statistic reported for all elements in each API is  
// defined recursively as follows:
// 
// %age change = 100 * (added + removed + 2*changed)
//               -----------------------------------
//               sum of public elements in BOTH APIs
//
// The definition ensures that if all classes are removed and all new classes
// added, the change will be 100%.
// Evaluation of the visibility of elements has already been done when the 
// XML was written out.
// Note that this doesn't count changes in the modifiers of classes and 
// packages. Other changes in members are counted.
        Long denom = new Long(oldAPI.packages_.size() + newAPI.packages_.size());
        // This should never be zero because an API always has packages?
        if (denom.intValue() == 0) {
            System.out.println("Error: no packages found in the APIs.");
            return;
        }
        if (trace)
            System.out.println("Top level changes: " + differs + "/" + denom.intValue());
        differs = (100.0 * differs)/denom.doubleValue();

        // Some differences such as documentation changes are not tracked in 
        // the difference statistic, so a value of 0.0 does not mean that there
        // were no differences between the APIs.
        apiDiff.pdiff = differs;
        Double percentage = new Double(differs);
        int approxPercentage = percentage.intValue();
        if (approxPercentage == 0)
            System.out.println(" Approximately " + percentage + "% difference between the APIs");
        else
            System.out.println(" Approximately " + approxPercentage + "% difference between the APIs");

        Diff.closeDiffFile();
    }   

    /** 
     * Compare two packages.
     */
    public double comparePackages(PackageAPI oldPkg, PackageAPI newPkg) {
        if (trace)
            System.out.println("Comparing old package " + oldPkg.name_ + 
                               " and new package " + newPkg.name_);
        pkgDiff = new PackageDiff(oldPkg.name_);
        double differs = 0.0;

        Collections.sort(oldPkg.classes_);
        Collections.sort(newPkg.classes_);
      
        // Find classes which were removed in the new package
        Iterator iter = oldPkg.classes_.iterator();
        while (iter.hasNext()) {
            ClassAPI oldClass = (ClassAPI)(iter.next());
            // This search is looking for an *exact* match. This is true in
            // all the *API classes.
            int idx = Collections.binarySearch(newPkg.classes_, oldClass);
            if (idx < 0) {
                // If there an instance of a class with the same name 
                // in both the old and new package, then treat it as changed,
                // rather than removed and added. There will never be more than
                // one instance of a class with the same name in a package.
                int existsNew = newPkg.classes_.indexOf(oldClass);
                if (existsNew != -1) {
                    // Class by the same name exists in both packages
                    // but there has been some or other change.
                    differs += 2.0 * compareClasses(oldClass, (ClassAPI)(newPkg.classes_.get(existsNew)), pkgDiff);
                }  else {
                    if (trace)
                        System.out.println("  Class " + oldClass.name_ + " was removed");
                    pkgDiff.classesRemoved.add(oldClass);
                    differs += 1.0;
                }
            } else {
                // The class exists unchanged in name or modifiers, but may 
                // differ in members, so it still needs to be compared.
                differs += 2.0 * compareClasses(oldClass, (ClassAPI)(newPkg.classes_.get(idx)), pkgDiff);
            }
        } // while (iter.hasNext())

        // Find classes which were added or changed in the new package
        iter = newPkg.classes_.iterator();
        while (iter.hasNext()) {
            ClassAPI newClass = (ClassAPI)(iter.next());
            int idx = Collections.binarySearch(oldPkg.classes_, newClass);
            if (idx < 0) {
                // See comments above
                int existsOld = oldPkg.classes_.indexOf(newClass);
                if (existsOld != -1) {
                    // Don't mark a class as added or compare it 
                    // if it was already marked as changed
                } else {
                    if (trace)
                        System.out.println("  Class " + newClass.name_ + " was added");
                    pkgDiff.classesAdded.add(newClass);
                    differs += 1.0;
                }
            } else {
                // It will already have been compared above.
            }
        } // while (iter.hasNext())

        // Check if the only change was in documentation. Bug 472521.
        boolean differsFlag = false;
        if (docChanged(oldPkg.doc_, newPkg.doc_)) {
            String link = "<a href=\"pkg_" + oldPkg.name_ + HTMLReportGenerator.reportFileExt + "\" class=\"hiddenlink\">";
            String id = oldPkg.name_ + "!package";
            String title = link + "Package <b>" + oldPkg.name_ + "</b></a>";
            pkgDiff.documentationChange_ = Diff.saveDocDiffs(pkgDiff.name_, null, oldPkg.doc_, newPkg.doc_, id, title);
            differsFlag = true;
        }

        // Only add to the parent Diff object if some difference has been found
        if (differs != 0.0 || differsFlag) 
            apiDiff.packagesChanged.add(pkgDiff);

        Long denom = new Long(oldPkg.classes_.size() + newPkg.classes_.size());
        // This should never be zero because a package always has classes?
        if (denom.intValue() == 0) {
            System.out.println("Warning: no classes found in the package " + oldPkg.name_);
            return 0.0;
        }
        if (trace)
            System.out.println("Package " + pkgDiff.name_ + " had a difference of " + differs + "/" + denom.intValue());
        pkgDiff.pdiff = 100.0 * differs/denom.doubleValue();
        return differs/denom.doubleValue();
    } // comparePackages()

    /** 
     * Compare two classes. 
     *
     * Need to compare constructors, methods and fields.
     */
    public double compareClasses(ClassAPI oldClass, ClassAPI newClass, PackageDiff pkgDiff) {
        if (trace)
            System.out.println("  Comparing old class " + oldClass.name_ + 
                               " and new class " + newClass.name_);
        boolean differsFlag = false;
        double differs = 0.0;
        ClassDiff classDiff = new ClassDiff(oldClass.name_);
        classDiff.isInterface_ = newClass.isInterface_; // Used in the report

        // Track changes in modifiers - class or interface
        if (oldClass.isInterface_ != newClass.isInterface_) {
            classDiff.modifiersChange_  = "Changed from ";
            if (oldClass.isInterface_)
                classDiff.modifiersChange_ += "an interface to a class.";
            else
                classDiff.modifiersChange_ += "a class to an interface.";
            differsFlag = true;
        }
        // Track changes in inheritance
        String inheritanceChange = ClassDiff.diff(oldClass, newClass);
        if (inheritanceChange != null) {
            classDiff.inheritanceChange_ = inheritanceChange;
            differsFlag = true;
        }
        // Abstract or not
        if (oldClass.isAbstract_ != newClass.isAbstract_) {
            String changeText = "";
            if (oldClass.isAbstract_)
                changeText += "Changed from abstract to non-abstract.";
            else
                changeText += "Changed from non-abstract to abstract.";
            classDiff.addModifiersChange(changeText);
            differsFlag = true;
        }
        // Track changes in documentation
        if (docChanged(oldClass.doc_, newClass.doc_)) {
            String fqName = pkgDiff.name_ + "." + classDiff.name_;
            String link = "<a href=\"" + fqName + HTMLReportGenerator.reportFileExt + "\" class=\"hiddenlink\">";
            String id = pkgDiff.name_ + "." + classDiff.name_ + "!class";
            String title = link + "Class <b>" + classDiff.name_ + "</b></a>";
            classDiff.documentationChange_ = Diff.saveDocDiffs(pkgDiff.name_,
 classDiff.name_, oldClass.doc_, newClass.doc_, id, title);
            differsFlag = true;
        }
        // All other modifiers
        String modifiersChange = oldClass.modifiers_.diff(newClass.modifiers_);
        if (modifiersChange != null) {
            differsFlag = true;
            if (modifiersChange.indexOf("Change from deprecated to undeprecated") != -1) {
                System.out.println("JDiff: warning: change from deprecated to undeprecated for class " + pkgDiff.name_ + "." + newClass.name_);
                
            }
        }
        classDiff.addModifiersChange(modifiersChange);
        
        // Track changes in members
        boolean differsCtors = 
            compareAllCtors(oldClass, newClass, classDiff);
        boolean differsMethods = 
            compareAllMethods(oldClass, newClass, classDiff);
        boolean differsFields = 
            compareAllFields(oldClass, newClass, classDiff);
        if (differsCtors || differsMethods || differsFields) 
            differsFlag = true;
        
        if (trace) {
            System.out.println("  Ctors differ? " + differsCtors + 
                ", Methods differ? " + differsMethods + 
                ", Fields differ? " + differsFields);
        }

        // Only add to the parent if some difference has been found
        if (differsFlag) 
            pkgDiff.classesChanged.add(classDiff);

        // Get the numbers of affected elements from the classDiff object
         differs = 
            classDiff.ctorsRemoved.size() + classDiff.ctorsAdded.size() +
            classDiff.ctorsChanged.size() +
            classDiff.methodsRemoved.size() + classDiff.methodsAdded.size() +
            classDiff.methodsChanged.size() +
            classDiff.fieldsRemoved.size() + classDiff.fieldsAdded.size() +
            classDiff.fieldsChanged.size();
         Long denom = new Long(
             oldClass.ctors_.size() + 
             numLocalMethods(oldClass.methods_) + 
             numLocalFields(oldClass.fields_) +
             newClass.ctors_.size() + 
             numLocalMethods(newClass.methods_) + 
             numLocalFields(newClass.fields_));
         if (denom.intValue() == 0) {
             // This is probably a placeholder interface, but documentation
             // or modifiers etc may have changed
             if (differsFlag) {
                 classDiff.pdiff = 0.0; // 100.0 is too much
                 return 1.0;
             } else {
                 return 0.0;
             }
         }
         // Handle the case where the only change is in documentation or
         // the modifiers
         if (differsFlag && differs == 0.0) {
             differs = 1.0;
         }
         if (trace)
             System.out.println("  Class " + classDiff.name_ + " had a difference of " + differs + "/" + denom.intValue());
         classDiff.pdiff = 100.0 * differs/denom.doubleValue();
         return differs/denom.doubleValue();
    } // compareClasses()

    /** 
     * Compare all the constructors in two classes. 
     *
     * The compareTo method in the ConstructorAPI class acts only upon the type.
     */
    public boolean compareAllCtors(ClassAPI oldClass, ClassAPI newClass, 
                                   ClassDiff classDiff) {
        if (trace)
            System.out.println("    Comparing constructors: #old " + 
              oldClass.ctors_.size() + ", #new " + newClass.ctors_.size());
        boolean differs = false;
        boolean singleCtor = false; // Set if there is only one ctor
        
        Collections.sort(oldClass.ctors_);
        Collections.sort(newClass.ctors_);
      
        // Find ctors which were removed in the new class
        Iterator iter = oldClass.ctors_.iterator();
        while (iter.hasNext()) {
            ConstructorAPI oldCtor = (ConstructorAPI)(iter.next());
            int idx = Collections.binarySearch(newClass.ctors_, oldCtor);
            if (idx < 0) {
                int oldSize = oldClass.ctors_.size();
                int newSize = newClass.ctors_.size();
                if (oldSize == 1 && oldSize == newSize) {
                    // If there is one constructor in the oldClass and one
                    // constructor in the new class, then mark it as changed
                    MemberDiff memberDiff = new MemberDiff(oldClass.name_);
                    memberDiff.oldType_ = oldCtor.type_;
                    memberDiff.oldExceptions_ = oldCtor.exceptions_;
                    ConstructorAPI newCtor  = (ConstructorAPI)(newClass.ctors_.get(0));
                    memberDiff.newType_ = newCtor.type_;
                    memberDiff.newExceptions_ = newCtor.exceptions_;
                    // Track changes in documentation
                    if (docChanged(oldCtor.doc_, newCtor.doc_)) {
                        String type = memberDiff.newType_;
                        if (type.compareTo("void") == 0)
                            type = "";
                        String fqName = pkgDiff.name_ + "." + classDiff.name_;
                        String link1 = "<a href=\"" + fqName + HTMLReportGenerator.reportFileExt + "\" class=\"hiddenlink\">";
                        String link2 = "<a href=\"" + fqName + HTMLReportGenerator.reportFileExt + "#" + fqName + ".ctor_changed(" + type + ")\" class=\"hiddenlink\">";
                        String id = pkgDiff.name_ + "." + classDiff.name_ + ".ctor(" + HTMLReportGenerator.simpleName(type) + ")";
                        String title = link1 + "Class <b>" + classDiff.name_ + 
                            "</b></a>, " + link2 + "constructor <b>" + classDiff.name_ + "(" + HTMLReportGenerator.simpleName(type) + ")</b></a>";
                        memberDiff.documentationChange_ = Diff.saveDocDiffs(
                            pkgDiff.name_, classDiff.name_, oldCtor.doc_, newCtor.doc_, id, title);
                    }
                    String modifiersChange = oldCtor.modifiers_.diff(newCtor.modifiers_);
                    if (modifiersChange != null && modifiersChange.indexOf("Change from deprecated to undeprecated") != -1) {
                        System.out.println("JDiff: warning: change from deprecated to undeprecated for a constructor in class" + newClass.name_);
                    }
                    memberDiff.addModifiersChange(modifiersChange);
                    if (trace)
                        System.out.println("    The single constructor was changed");
                    classDiff.ctorsChanged.add(memberDiff);
                    singleCtor = true;
                } else {
                    if (trace)
                        System.out.println("    Constructor " + oldClass.name_ + " was removed");
                    classDiff.ctorsRemoved.add(oldCtor);
                }
                differs = true;
            }
        } // while (iter.hasNext())

        // Find ctors which were added in the new class
        iter = newClass.ctors_.iterator();
        while (iter.hasNext()) {
            ConstructorAPI newCtor = (ConstructorAPI)(iter.next());
            int idx = Collections.binarySearch(oldClass.ctors_, newCtor);
            if (idx < 0) {
                if (!singleCtor) {
                    if (trace)
                        System.out.println("    Constructor " + oldClass.name_ + " was added");
                    classDiff.ctorsAdded.add(newCtor);
                    differs = true;
                }
            }
        } // while (iter.hasNext())

        return differs;
    } // compareAllCtors()

    /** 
     * Compare all the methods in two classes. 
     *
     * We have to deal with the cases where:
     *  - there is only one method with a given name, but its signature changes
     *  - there is more than one method with the same name, and some of them 
     *    may have signature changes
     * The simplest way to deal with this is to make the MethodAPI comparator
     * check the params and return type, as well as the name. This means that
     * changing a parameter's type would cause the method to be seen as 
     * removed and added. To avoid this for the simple case, check for before 
     * recording a method as removed or added.
     */
    public boolean compareAllMethods(ClassAPI oldClass, ClassAPI newClass, ClassDiff classDiff) {
        if (trace)
            System.out.println("    Comparing methods: #old " + 
                               oldClass.methods_.size() + ", #new " +
                               newClass.methods_.size());
        boolean differs = false;
        
        Collections.sort(oldClass.methods_);
        Collections.sort(newClass.methods_);
      
        // Find methods which were removed in the new class
        Iterator iter = oldClass.methods_.iterator();
        while (iter.hasNext()) {
            MethodAPI oldMethod = (MethodAPI)(iter.next());
            int idx = -1;
            MethodAPI[] methodArr = new MethodAPI[newClass.methods_.size()];
            methodArr = (MethodAPI[])newClass.methods_.toArray(methodArr);
            for (int methodIdx = 0; methodIdx < methodArr.length; methodIdx++) {
                MethodAPI newMethod = methodArr[methodIdx];
                if (oldMethod.compareTo(newMethod) == 0) {
                    idx  = methodIdx;
                    break;
                }
            }
// NOTE: there was a problem with the binarySearch for 
// java.lang.Byte.toString(byte b) returning -16 when the compareTo method
// returned 0 on entry 13. Changed to use arrays instead, so maybe it was
// an issue with methods having another List of params used indirectly by 
// compareTo(), unlike constructors and fields?
//            int idx = Collections.binarySearch(newClass.methods_, oldMethod);
            if (idx < 0) {
                // If there is only one instance of a method with this name 
                // in both the old and new class, then treat it as changed,
                // rather than removed and added.
                // Find how many instances of this method name there are in
                // the old and new class. The equals comparator is just on 
                // the method name.
                int startOld = oldClass.methods_.indexOf(oldMethod); 
                int endOld = oldClass.methods_.lastIndexOf(oldMethod);
                int startNew = newClass.methods_.indexOf(oldMethod); 
                int endNew = newClass.methods_.lastIndexOf(oldMethod);

                if (startOld != -1 && startOld == endOld && 
                    startNew != -1 && startNew == endNew) {
                    MethodAPI newMethod = (MethodAPI)(newClass.methods_.get(startNew));
                    // Only one method with that name exists in both packages,
                    // so it is valid to compare the two methods. We know it 
                    // has changed, because the binarySearch did not find it.
                    if (oldMethod.inheritedFrom_ == null || 
                        newMethod.inheritedFrom_ == null) {
                        // We also know that at least one of the methods is 
                        // locally defined.
                        compareMethods(oldMethod, newMethod, classDiff);
                        differs = true;
                    }
                } else if (oldMethod.inheritedFrom_ == null) {
                    // Only concerned with locally defined methods
                    if (trace)
                        System.out.println("    Method " + oldMethod.name_ + 
                                           "(" + oldMethod.getSignature() + 
                                           ") was removed");
                    classDiff.methodsRemoved.add(oldMethod);
                    differs = true;
                }
            }
        } // while (iter.hasNext())

        // Find methods which were added in the new class
        iter = newClass.methods_.iterator();
        while (iter.hasNext()) {
            MethodAPI newMethod = (MethodAPI)(iter.next());
            // Only concerned with locally defined methods
            if (newMethod.inheritedFrom_ != null)
                continue;
            int idx = -1;
            MethodAPI[] methodArr = new MethodAPI[oldClass.methods_.size()];
            methodArr = (MethodAPI[])oldClass.methods_.toArray(methodArr);
            for (int methodIdx = 0; methodIdx < methodArr.length; methodIdx++) {
                MethodAPI oldMethod = methodArr[methodIdx];
                if (newMethod.compareTo(oldMethod) == 0) {
                    idx  = methodIdx;
                    break;
                }
            }
// See note above about searching an array instead of binarySearch
//            int idx = Collections.binarySearch(oldClass.methods_, newMethod);
            if (idx < 0) {
                // See comments above
                int startOld = oldClass.methods_.indexOf(newMethod); 
                int endOld = oldClass.methods_.lastIndexOf(newMethod);
                int startNew = newClass.methods_.indexOf(newMethod); 
                int endNew = newClass.methods_.lastIndexOf(newMethod);

                if (startOld != -1 && startOld == endOld && 
                    startNew != -1 && startNew == endNew) {
                    // Don't mark a method as added if it was marked as changed
                    // The comparison will have been done just above here.
                } else {
                    if (trace)
                        System.out.println("    Method " + newMethod.name_ + 
                                           "(" + newMethod.getSignature() + ") was added");
                    classDiff.methodsAdded.add(newMethod);
                    differs = true;
                }
            }
        } // while (iter.hasNext())

        return differs;
    } // compareAllMethods()

    /** 
     * Compare two methods which have the same name. 
     */
    public boolean compareMethods(MethodAPI oldMethod, MethodAPI newMethod, ClassDiff classDiff) {
        MemberDiff methodDiff = new MemberDiff(oldMethod.name_);
        boolean differs = false;
        // Check changes in return type
        methodDiff.oldType_ = oldMethod.returnType_;
        methodDiff.newType_ = newMethod.returnType_;
        if (oldMethod.returnType_.compareTo(newMethod.returnType_) != 0) {
            differs = true;
        }
        // Check changes in signature
        String oldSig = oldMethod.getSignature();
        String newSig = newMethod.getSignature();
        methodDiff.oldSignature_ = oldSig;
        methodDiff.newSignature_ = newSig;
        if (oldSig.compareTo(newSig) != 0) {
            differs = true;
        }
        // Changes in inheritance
        int inh = changedInheritance(oldMethod.inheritedFrom_, newMethod.inheritedFrom_);
        if (inh != 0)
            differs = true;
        if (inh == 1) {
            methodDiff.addModifiersChange("Method was locally defined, but is now inherited from " + linkToClass(newMethod, true) + ".");
            methodDiff.inheritedFrom_ = newMethod.inheritedFrom_;
        } else if (inh == 2) {
            methodDiff.addModifiersChange("Method was inherited from " + linkToClass(oldMethod, false) + ", but is now defined locally.");
        } else if (inh == 3) {
            methodDiff.addModifiersChange("Method was inherited from " + 
                                          linkToClass(oldMethod, false) + ", and is now inherited from " + linkToClass(newMethod, true) + ".");
            methodDiff.inheritedFrom_ = newMethod.inheritedFrom_;
        }
        // Abstract or not
        if (oldMethod.isAbstract_ != newMethod.isAbstract_) {
            String changeText = "";
            if (oldMethod.isAbstract_)
                changeText += "Changed from abstract to non-abstract.";
            else
                changeText += "Changed from non-abstract to abstract.";
            methodDiff.addModifiersChange(changeText);
            differs = true;
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
            differs = true;
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
            differs = true;
        }

        // Check changes in exceptions thrown
        methodDiff.oldExceptions_ = oldMethod.exceptions_;
        methodDiff.newExceptions_ = newMethod.exceptions_;
        if (oldMethod.exceptions_.compareTo(newMethod.exceptions_) != 0) {
            differs = true;
        }

        // Track changes in documentation
        if (docChanged(oldMethod.doc_, newMethod.doc_)) {
            String sig = methodDiff.newSignature_;
            if (sig.compareTo("void") == 0)
                sig = "";
            String fqName = pkgDiff.name_ + "." + classDiff.name_;
            String link1 = "<a href=\"" + fqName + HTMLReportGenerator.reportFileExt + "\" class=\"hiddenlink\">";
            String link2 = "<a href=\"" + fqName + HTMLReportGenerator.reportFileExt + "#" + fqName + "." + newMethod.name_ + "_changed(" + sig + ")\" class=\"hiddenlink\">";
            String id = pkgDiff.name_ + "." + classDiff.name_ + ".dmethod." + newMethod.name_ + "(" + HTMLReportGenerator.simpleName(sig) + ")";
            String title = link1 + "Class <b>" + classDiff.name_ + "</b></a>, " +
                link2 + HTMLReportGenerator.simpleName(methodDiff.newType_) + " <b>" + newMethod.name_ + "(" + HTMLReportGenerator.simpleName(sig) + ")</b></a>";
            methodDiff.documentationChange_ = Diff.saveDocDiffs(pkgDiff.name_, classDiff.name_, oldMethod.doc_, newMethod.doc_, id, title);
            differs = true;
        }

        // All other modifiers
        String modifiersChange = oldMethod.modifiers_.diff(newMethod.modifiers_);
        if (modifiersChange != null) {
            differs = true;
            if (modifiersChange.indexOf("Change from deprecated to undeprecated") != -1) {
                System.out.println("JDiff: warning: change from deprecated to undeprecated for method " +  classDiff.name_ + "." + newMethod.name_);
                
            }
        }
        methodDiff.addModifiersChange(modifiersChange);

        // Only add to the parent if some difference has been found
        if (differs) {
            if (trace) {
                System.out.println("    Method " + newMethod.name_ + 
                    " was changed: old: " + 
                   oldMethod.returnType_ + "(" + oldSig + "), new: " +
                   newMethod.returnType_ + "(" + newSig + ")");
                if (methodDiff.modifiersChange_ != null)
                    System.out.println("    Modifier change: " + methodDiff.modifiersChange_);
            }
            classDiff.methodsChanged.add(methodDiff);
        }

        return differs;
    } // compareMethods()

    /** 
     * Compare all the fields in two classes. 
     */
    public boolean compareAllFields(ClassAPI oldClass, ClassAPI newClass, 
                                    ClassDiff classDiff) {
        if (trace)
            System.out.println("    Comparing fields: #old " + 
                               oldClass.fields_.size() + ", #new " 
                               + newClass.fields_.size());
        boolean differs = false;
        
        Collections.sort(oldClass.fields_);
        Collections.sort(newClass.fields_);
      
        // Find fields which were removed in the new class
        Iterator iter = oldClass.fields_.iterator();
        while (iter.hasNext()) {
            FieldAPI oldField = (FieldAPI)(iter.next());
            int idx = Collections.binarySearch(newClass.fields_, oldField);
            if (idx < 0) {
                // If there an instance of a field with the same name 
                // in both the old and new class, then treat it as changed,
                // rather than removed and added. There will never be more than
                // one instance of a field with the same name in a class.
                int existsNew = newClass.fields_.indexOf(oldField);
                if (existsNew != -1) {
                    FieldAPI newField = (FieldAPI)(newClass.fields_.get(existsNew));
                    if (oldField.inheritedFrom_ == null || 
                        newField.inheritedFrom_ == null) {
                        // We also know that one of the fields is locally defined.
                        MemberDiff memberDiff = new MemberDiff(oldField.name_);
                        memberDiff.oldType_ = oldField.type_;
                        memberDiff.newType_ = newField.type_;
                        // Changes in inheritance
                        int inh = changedInheritance(oldField.inheritedFrom_, newField.inheritedFrom_);
                        if (inh != 0)
                            differs = true;
                        if (inh == 1) {
                            memberDiff.addModifiersChange("Field was locally defined, but is now inherited from " + linkToClass(newField, true) + ".");
                            memberDiff.inheritedFrom_ = newField.inheritedFrom_;
                        } else if (inh == 2) {
                            memberDiff.addModifiersChange("Field was inherited from " + linkToClass(oldField, false) + ", but is now defined locally.");
                        } else if (inh == 3) {
                            memberDiff.addModifiersChange("Field was inherited from " + linkToClass(oldField, false) + ", and is now inherited from " + linkToClass(newField, true) + ".");
                            memberDiff.inheritedFrom_ = newField.inheritedFrom_;
                        }
                        // Transient or not
                        if (oldField.isTransient_ != newField.isTransient_) {
                            String changeText = "";
                            if (oldField.isTransient_)
                                changeText += "Changed from transient to non-transient.";
                            else
                                changeText += "Changed from non-transient to transient.";
                            memberDiff.addModifiersChange(changeText);
                            differs = true;
                        }
                        // Volatile or not
                        if (oldField.isVolatile_ != newField.isVolatile_) {
                            String changeText = "";
                            if (oldField.isVolatile_)
                                changeText += "Changed from volatile to non-volatile.";
                            else
                                changeText += "Changed from non-volatile to volatile.";
                            memberDiff.addModifiersChange(changeText);
                            differs = true;
                        }
                        // Change in value of the field
                        if (oldField.value_ != null &&
                            newField.value_ != null &&
                            oldField.value_.compareTo(newField.value_) != 0) {
                            String changeText = "Changed in value from " + oldField.value_
                                + " to " + newField.value_ +".";
                            memberDiff.addModifiersChange(changeText);
                            differs = true;
                        }
                        // Track changes in documentation
                        if (docChanged(oldField.doc_, newField.doc_)) {
                            String fqName = pkgDiff.name_ + "." + classDiff.name_;
                            String link1 = "<a href=\"" + fqName + HTMLReportGenerator.reportFileExt + "\" class=\"hiddenlink\">";
                            String link2 = "<a href=\"" + fqName + HTMLReportGenerator.reportFileExt + "#" + fqName + "." + newField.name_ + "\" class=\"hiddenlink\">";
                            String id = pkgDiff.name_ + "." + classDiff.name_ + ".field." + newField.name_;
                            String title = link1 + "Class <b>" + classDiff.name_ + "</b></a>, " +
                                link2 + HTMLReportGenerator.simpleName(memberDiff.newType_) + " <b>" + newField.name_ + "</b></a>";
                            memberDiff.documentationChange_ = Diff.saveDocDiffs(pkgDiff.name_, classDiff.name_, oldField.doc_, newField.doc_, id, title);
                            differs = true;
                        }
                        
                        // Other differences
                        String modifiersChange = oldField.modifiers_.diff(newField.modifiers_);
                        memberDiff.addModifiersChange(modifiersChange);
                        if (modifiersChange != null && modifiersChange.indexOf("Change from deprecated to undeprecated") != -1) {
                            System.out.println("JDiff: warning: change from deprecated to undeprecated for class " + newClass.name_ + ", field " + newField.name_);
                        }
                        if (trace)
                            System.out.println("    Field " + newField.name_ + " was changed");
                        classDiff.fieldsChanged.add(memberDiff);
                        differs = true;
                    }
                } else if (oldField.inheritedFrom_ == null) {
                    if (trace)
                        System.out.println("    Field " + oldField.name_ + " was removed");
                    classDiff.fieldsRemoved.add(oldField);
                    differs = true;
                }
            }
        } // while (iter.hasNext())

        // Find fields which were added in the new class
        iter = newClass.fields_.iterator();
        while (iter.hasNext()) {
            FieldAPI newField = (FieldAPI)(iter.next());
            // Only concerned with locally defined fields
            if (newField.inheritedFrom_ != null)
                continue;
            int idx = Collections.binarySearch(oldClass.fields_, newField);
            if (idx < 0) {
                // See comments above
                int existsOld = oldClass.fields_.indexOf(newField);
                if (existsOld != -1) {
                    // Don't mark a field as added if it was marked as changed
                } else {
                    if (trace)
                        System.out.println("    Field " + newField.name_ + " was added");
                    classDiff.fieldsAdded.add(newField);
                    differs = true;
                }
            }
        } // while (iter.hasNext())

        return differs;
    } // compareFields()

    /** 
     * Decide if two blocks of documentation changed. 
     *
     * @return true if both are non-null and differ, 
     *              or if one is null and the other is not.
     */
    public static boolean docChanged(String oldDoc, String newDoc) {
        if (!HTMLReportGenerator.reportDocChanges)
            return false; // Don't even count doc changes as changes
        if (oldDoc == null && newDoc != null)
            return true;
        if (oldDoc != null && newDoc == null)
            return true;
        if (oldDoc != null && newDoc != null && oldDoc.compareTo(newDoc) != 0)
            return true;
        return false;
    }

    /** 
     * Decide if two elements changed where they were defined. 
     *
     * @return 0 if both are null, or both are non-null and are the same.
     *         1 if the oldInherit was null and newInherit is non-null.
     *         2 if the oldInherit was non-null and newInherit is null.
     *         3 if the oldInherit was non-null and newInherit is non-null 
     *           and they differ.
     */
    public static int changedInheritance(String oldInherit, String newInherit) {
        if (oldInherit == null && newInherit == null)
            return 0;
        if (oldInherit == null && newInherit != null)
            return 1;
        if (oldInherit != null && newInherit == null)
            return 2;
        if (oldInherit.compareTo(newInherit) == 0)
            return 0;
        else
            return 3;
    }

    /** 
     * Generate a link to the Javadoc page for the given method.
     */
    public static String linkToClass(MethodAPI m, boolean useNew) {
        String sig = m.getSignature();
        if (sig.compareTo("void") == 0)
            sig = "";
        return linkToClass(m.inheritedFrom_, m.name_, sig, useNew);
    }

    /** 
     * Generate a link to the Javadoc page for the given field.
     */
    public static String linkToClass(FieldAPI m, boolean useNew) {
        return linkToClass(m.inheritedFrom_, m.name_, null, useNew);
    }

    /** 
     * Given the name of the class, generate a link to a relevant page.
     * This was originally for inheritance changes, so the JDiff page could 
     * be a class changes page, or a section in a removed or added classes 
     * table. Since there was no easy way to tell which type the link
     * should be, it is now just a link to the relevant Javadoc page.
     */
    public static String linkToClass(String className, String memberName, 
                                     String memberType, boolean useNew) {
        if (!useNew && HTMLReportGenerator.oldDocPrefix == null) {
            return "<tt>" + className + "</tt>"; // No link possible
        }
        API api = oldAPI_;
        String prefix = HTMLReportGenerator.oldDocPrefix;
        if (useNew) {
            api = newAPI_;
            prefix = HTMLReportGenerator.newDocPrefix;
        }
        ClassAPI cls = (ClassAPI)api.classes_.get(className);
        if (cls == null) {
            if (useNew)
                System.out.println("Warning: class " + className + " not found in the new API when creating Javadoc link");
            else
                System.out.println("Warning: class " + className + " not found in the old API when creating Javadoc link");
            return "<tt>" + className + "</tt>";
        }
        int clsIdx = className.indexOf(cls.name_);
        if (clsIdx != -1) {
            String pkgRef = className.substring(0, clsIdx);
            pkgRef = pkgRef.replace('.', '/');
            String res = "<a href=\"" + prefix + pkgRef + cls.name_ + ".html#" + memberName;
            if (memberType != null)
                res += "(" + memberType + ")";
            res += "\" target=\"_top\">" + "<tt>" + cls.name_ + "</tt></a>";
            return res;
        }
        return "<tt>" + className + "</tt>";
    }    

    /** 
     * Return the number of methods which are locally defined.
     */
    public int numLocalMethods(List methods) {
        int res = 0;
        Iterator iter = methods.iterator();
        while (iter.hasNext()) {
            MethodAPI m = (MethodAPI)(iter.next());
            if (m.inheritedFrom_ == null) 
                res++;
        }
        return res;
    }

    /** 
     * Return the number of fields which are locally defined.
     */
    public int numLocalFields(List fields) {
        int res = 0;
        Iterator iter = fields.iterator();
        while (iter.hasNext()) {
            FieldAPI f = (FieldAPI)(iter.next());
            if (f.inheritedFrom_ == null) 
                res++;
        }
        return res;
    }

    /** Set to enable increased logging verbosity for debugging. */
    private boolean trace = false;
}
