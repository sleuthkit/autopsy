package jdiff;

import java.util.*;
import java.io.*;

/**
 * Emit HTML indexes which appear in the bottom left frame in the report. 
 * All indexes are links to JDiff-generated pages.
 *
 * See the file LICENSE.txt for copyright details.
 * @author Matthew Doar, mdoar@pobox.com
 */
public class HTMLIndexes {

    /** Constructor. */
    public HTMLIndexes(HTMLReportGenerator h) {
        h_ = h;
    }

    /** The HTMLReportGenerator instance used to write HTML. */
    private HTMLReportGenerator h_ = null;
    
    /** Emit all the bottom left frame index files. */
    public void emitAllBottomLeftFiles(String packagesIndexName, 
                                       String classesIndexName, 
                                       String constructorsIndexName, 
                                       String methodsIndexName,
                                       String fieldsIndexName, 
                                       String allDiffsIndexName, 
                                       APIDiff apiDiff) {
        
        // indexType values: 0 = removals only, 1 = additions only, 
        // 2 = changes only. 3 = all differences. Run all differences
        // first for all program element types so we know whether there
        // are any removals etc for the allDiffs index.
        emitBottomLeftFile(packagesIndexName, apiDiff, 3, "Package");
        emitBottomLeftFile(classesIndexName, apiDiff, 3, "Class");
        emitBottomLeftFile(constructorsIndexName, apiDiff, 3, "Constructor");
        emitBottomLeftFile(methodsIndexName, apiDiff, 3, "Method");
        emitBottomLeftFile(fieldsIndexName, apiDiff, 3, "Field");
        // The allindex must be done last, since it uses the results from 
        // the previous ones
        emitBottomLeftFile(allDiffsIndexName, apiDiff, 3, "All");
        // Now generate the other indexes
        for (int indexType = 0; indexType < 3; indexType++) {
            emitBottomLeftFile(packagesIndexName, apiDiff, indexType, "Package");
            emitBottomLeftFile(classesIndexName, apiDiff, indexType, "Class");
            emitBottomLeftFile(constructorsIndexName, apiDiff, indexType, "Constructor");
            emitBottomLeftFile(methodsIndexName, apiDiff, indexType, "Method");
            emitBottomLeftFile(fieldsIndexName, apiDiff, indexType, "Field");
            emitBottomLeftFile(allDiffsIndexName, apiDiff, indexType, "All");
        }
        if (missingSincesFile != null)
            missingSincesFile.close();
    }

    /** 
     * Emit a single bottom left frame with the given kind of differences for 
     * the given program element type in an alphabetical index. 
     *
     * @param indexBaseName The base name of the index file.
     * @param apiDiff The root element containing all the API differences.
     * @param indexType 0 = removals only, 1 = additions only,
     *                  2 = changes only, 3 = all differences, 
     * @param programElementType "Package", "Class", "Constructor",
     *                           "Method", "Field" or "All".  
     */
    public void emitBottomLeftFile(String indexBaseName, 
                                   APIDiff apiDiff, int indexType, 
                                   String programElementType) {
        String filename = indexBaseName;
        try {
            String title = "JDiff"; 
            if (indexType == 0) {
                filename += "_removals" + h_.reportFileExt;
                title = programElementType + " Removals Index";
            } else if (indexType == 1) {
                filename += "_additions" + h_.reportFileExt;
                title = programElementType + " Additions Index";
            } else if (indexType == 2) {
                filename += "_changes" + h_.reportFileExt;
                title = programElementType + " Changes Index";
            } else if (indexType == 3) {
                filename += "_all" + h_.reportFileExt;
                title = programElementType + " Differences Index";
            }
                
            FileOutputStream fos = new FileOutputStream(filename);
            h_.reportFile = new PrintWriter(fos);
            h_.writeStartHTMLHeader();
            h_.writeHTMLTitle(title);
            h_.writeStyleSheetRef();
            h_.writeText("</HEAD>");
            h_.writeText("<BODY>");
            
            if (programElementType.compareTo("Package") == 0) {
                emitPackagesIndex(apiDiff, indexType);
            } else if (programElementType.compareTo("Class") == 0) {
                emitClassesIndex(apiDiff, indexType);
            } else if (programElementType.compareTo("Constructor") == 0) {
                emitConstructorsIndex(apiDiff, indexType);
            } else if (programElementType.compareTo("Method") == 0) {
                emitMethodsIndex(apiDiff, indexType);
            } else if (programElementType.compareTo("Field") == 0) {
                emitFieldsIndex(apiDiff, indexType);
            } else if (programElementType.compareTo("All") == 0) {
                emitAllDiffsIndex(apiDiff, indexType);
            } else{
                System.out.println("Error: unknown program element type.");
                System.exit(3);
            }
            
            h_.writeHTMLFooter();
            h_.reportFile.close();
        } catch(IOException e) {
            System.out.println("IO Error while attempting to create " + filename);
            System.out.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    /** 
     * Generate a small header of letters which link to each section, but
     * do not emit a linked letter for the current section. Finish the list off
     * with a link to the top of the index.
     * Caching the results of this function would save about 10s with large APIs.
     */
    private void generateLetterIndex(List list, char currChar, boolean larger) {
        if (larger)
            return; // Currently not using the larger functionality
        int size = -2;
        if (larger)
            size = -1;
        Iterator iter = null;
        if (isAllNames)
            iter = allNames.iterator();
        else
            iter = list.iterator();
        char oldsw = '\0';
        while (iter.hasNext()) {
            Index entry = (Index)(iter.next());
            char sw = entry.name_.charAt(0);
            char swu = Character.toUpperCase(sw);
            if (swu != Character.toUpperCase(oldsw)) {
                // Don't emit a reference to the current letter
                if (Character.toUpperCase(sw) != Character.toUpperCase(currChar)) {
                    if (swu == '_') {
                        h_.writeText("<a href=\"#" + swu + "\"><font size=\"" + size + "\">" + "underscore" + "</font></a> ");
                    } else {
                        h_.writeText("<a href=\"#" + swu + "\"><font size=\"" + size + "\">" + swu + "</font></a> ");
                    }
                }
                oldsw = sw;
            }
        }
        h_.writeText(" <a href=\"#topheader\"><font size=\"" + size + "\">TOP</font></a>");
        h_.writeText("<br>");
    }

    /** 
     * Emit a header for an index, including suitable links for removed, 
     * added and changes sub-indexes. 
     */
    private void emitIndexHeader(String indexName, int indexType,
                                 boolean hasRemovals, 
                                 boolean hasAdditions, boolean hasChanges) {
        String linkIndexName = indexName.toLowerCase();
        boolean isAllDiffs = false;
        if (indexName.compareTo("All Differences") == 0) {
            linkIndexName = "alldiffs";
            isAllDiffs = true;
        }
        h_.writeText("<a NAME=\"topheader\"></a>"); // Named anchor
        h_.writeText("<table summary=\"Index for " + indexName + "\" width=\"100%\" border=\"0\" cellspacing=\"0\" cellpadding=\"0\">");
        h_.writeText("  <tr>");
        h_.writeText("  <td bgcolor=\"#FFFFCC\">");
        // The index name is also a hidden link to the *index_all page
        if (isAllDiffs)
          h_.writeText("<font size=\"+1\"><a href=\"" + linkIndexName + "_index_all" + h_.reportFileExt + "\" class=\"staysblack\">" + indexName + "</a></font>");
        else
          h_.writeText("<font size=\"+1\"><a href=\"" + linkIndexName + "_index_all" + h_.reportFileExt + "\" class=\"staysblack\">All " + indexName + "</a></font>");
        h_.writeText("  </td>");
        h_.writeText("  </tr>");

        h_.writeText("  <tr>");
        h_.writeText("  <td bgcolor=\"#FFFFFF\">");
        h_.writeText("  <FONT SIZE=\"-1\">");
        if (hasRemovals) {
          if (indexType == 0) {
            h_.writeText("<b>Removals</b>");
          } else {
            h_.writeText("<A HREF=\"" + linkIndexName + "_index_removals" + h_.reportFileExt + "\" class=\"hiddenlink\">Removals</A>");
          }
        } else {
            h_.writeText("<font color=\"#999999\">Removals</font>");
        }
        h_.writeText("  </FONT>");
        h_.writeText("  </td>");
        h_.writeText("  </tr>");

        h_.writeText("  <tr>");
        h_.writeText("  <td bgcolor=\"#FFFFFF\">");
        h_.writeText("  <FONT SIZE=\"-1\">");
        if (hasAdditions) {
          if (indexType == 1) {
            h_.writeText("<b>Additions</b>");
          } else {
            h_.writeText("<A HREF=\"" + linkIndexName + "_index_additions" + h_.reportFileExt + "\"class=\"hiddenlink\">Additions</A>");
          }
        } else {
            h_.writeText("<font color=\"#999999\">Additions</font>");
        }
        h_.writeText("  </FONT>");
        h_.writeText("  </td>");
        h_.writeText("  </tr>");

        h_.writeText("  <tr>");
        h_.writeText("  <td bgcolor=\"#FFFFFF\">");
        h_.writeText("  <FONT SIZE=\"-1\">");
        if (hasChanges) {
          if (indexType == 2) {
            h_.writeText("<b>Changes</b>");
          } else {
            h_.writeText("<A HREF=\"" + linkIndexName + "_index_changes" + h_.reportFileExt + "\"class=\"hiddenlink\">Changes</A>");
          }
        } else {
            h_.writeText("<font color=\"#999999\">Changes</font>");
        }
        h_.writeText("  </FONT>");
        h_.writeText("  </td>");
        h_.writeText("  </tr>");
        h_.writeText("  <tr>");
        h_.writeText("  <td>");
        h_.writeText("<font size=\"-2\"><b>Bold</b>&nbsp;is&nbsp;New,&nbsp;<strike>strike</strike>&nbsp;is&nbsp;deleted</font>");
        h_.writeText("  </td>");
        h_.writeText("  </tr>");
        h_.writeText("</table><br>");
    }

    /** Emit the index of packages, which appears in the bottom left frame. */
    public void emitPackagesIndex(APIDiff apiDiff, int indexType) {
        // Add all the names of packages to a new list, to be sorted later
        packageNames = new ArrayList(); // Index[]
        boolean hasRemovals = false;
        if (apiDiff.packagesRemoved.size() != 0)
            hasRemovals = true;
        boolean hasAdditions = false;
        if (apiDiff.packagesAdded.size() != 0)
            hasAdditions = true;
        boolean hasChanges = false;
        if (apiDiff.packagesChanged.size() != 0)
            hasChanges = true;
        recordDiffs(hasRemovals, hasAdditions, hasChanges);
        Iterator iter = apiDiff.packagesRemoved.iterator();
        while ((indexType == 3 || indexType == 0) && iter.hasNext()) {
            PackageAPI pkg = (PackageAPI)(iter.next());
            packageNames.add(new Index(pkg.name_, 0));
        }
        iter = apiDiff.packagesAdded.iterator();
        while ((indexType == 3 || indexType == 1) && iter.hasNext()) {
            PackageAPI pkg = (PackageAPI)(iter.next());
            packageNames.add(new Index(pkg.name_, 1));
        }
        iter = apiDiff.packagesChanged.iterator();
        while ((indexType == 3 || indexType == 2) && iter.hasNext()) {
            PackageDiff pkg = (PackageDiff)(iter.next());
            packageNames.add(new Index(pkg.name_, 2));
        }
        Collections.sort(packageNames);

        // No letter index needed for packages

        // Now emit all the package names and links to their respective files
        emitIndexHeader("Packages", indexType, hasRemovals, hasAdditions, hasChanges);

        // Extra line because no index is emitted
        h_.writeText("<br>");

        // Package names are unique, so no need to check for duplicates.
        iter = packageNames.iterator();
        char oldsw = '\0';
        while (iter.hasNext()) {
            Index pkg = (Index)(iter.next());
            oldsw = emitPackageIndexEntry(pkg, oldsw);
        }
    }

    /** 
     * Emit an index entry for a package. 
     * Package names are unique, so no need to check for duplicates.
     */
    public char emitPackageIndexEntry(Index pkg, char oldsw) {
        char res = oldsw;
        // See if we are in a new section of the alphabet
        char sw = pkg.name_.charAt(0);
        if (Character.toUpperCase(sw) != Character.toUpperCase(oldsw)) {
            // No need to emit section letters for packages
            res = sw;
            // Add the named anchor for this new letter
            h_.writeText("<A NAME=\"" + Character.toUpperCase(res) + "\"></A>");
        }
        // Package names are unique, so no need to check for duplicates.
        if (pkg.changeType_ == 0) {
            h_.writeText("<A HREF=\"" + h_.reportFileName + "-summary" + h_.reportFileExt + "#" + pkg.name_  + "\" class=\"hiddenlink\" target=\"rightframe\"><strike>" + pkg.name_ + "</strike></A><br>");
        } else if (pkg.changeType_ == 1) {
            h_.writeText("<A HREF=\"" + h_.reportFileName + "-summary" + h_.reportFileExt + "#" + pkg.name_  + "\" class=\"hiddenlink\" target=\"rightframe\"><b>" + pkg.name_ + "</b></A><br>");
        } else if (pkg.changeType_ == 2) {
            h_.writeText("<A HREF=\"pkg_" + pkg.name_ + h_.reportFileExt + "\" class=\"hiddenlink\" target=\"rightframe\">" + pkg.name_ + "</A><br>");
        }
        return res;
    }

    /** 
     * Emit all the entries and links for the given iterator
     * to their respective files. 
     */
    public void emitIndexEntries(Iterator iter) {
        char oldsw = '\0';
        int multipleMarker = 0;
        Index currIndex = null; // The entry which is emitted
        while (iter.hasNext()) {
            // The next entry after the current one
            Index nextIndex = (Index)(iter.next()); 
            if (currIndex == null) {
                currIndex = nextIndex; // Prime the pump
            } else {
                if (nextIndex.name_.compareTo(currIndex.name_) == 0) {
                    // It's a duplicate index, so emit the name and then
                    // the indented entries
                    if (multipleMarker == 0)
                        multipleMarker = 1; // Start of a duplicate index
                    else if (multipleMarker == 1)
                        multipleMarker = 2; // Inside a duplicate index
                    oldsw = emitIndexEntry(currIndex, oldsw, multipleMarker);
                } else {
                    if (multipleMarker == 1)
                        multipleMarker = 2; // Inside a duplicate index
                    oldsw = emitIndexEntry(currIndex, oldsw, multipleMarker);
                    multipleMarker = 0; // Not in a duplicate index any more
                }
                currIndex = nextIndex;
            }
        }
        // Emit the last entry left in currIndex
        if (multipleMarker == 1)
            multipleMarker = 2; // Inside a duplicate index
        if (currIndex != null)
            oldsw = emitIndexEntry(currIndex, oldsw, multipleMarker);
    }
    
    /** 
     * Whether to log all missing @since tags to a file or not. 
     * If false, just warn the user.
     */
    public static boolean logMissingSinces = true;

    /** The file used to output details of missing @since tags. */
    public static PrintWriter missingSincesFile = null;

    /** 
     * Emit elements in the given iterator which were added and 
     * missing @since tags. 
     */
    public void emitMissingSinces(Iterator iter) {
//        if (!logMissingSinces)
//            return;
        if (missingSincesFile == null) {
            String sinceFileName = h_.outputDir + JDiff.DIR_SEP + "missingSinces.txt";
            try {
                FileOutputStream fos = new FileOutputStream(sinceFileName);
                missingSincesFile = new PrintWriter(fos);
            } catch (IOException e) {
                System.out.println("IO Error while attempting to create " + sinceFileName);
                System.out.println("Error: " + e.getMessage());
                System.exit(1);
            }
        }
        while (iter.hasNext()) {
            Index currIndex = (Index)(iter.next()); 
            // Only display information about added elements
            if (currIndex.changeType_ != 1) 
                continue;
            String programElementType = currIndex.ename_;
            String details = null;
            if (programElementType.compareTo("class") == 0) {
                details = currIndex.pkgName_ + "." + currIndex.name_;
                if (currIndex.isInterface_)
                    details = details + " Interface";
                else
                    details = details + " Class";
            } else if (programElementType.compareTo("constructor") == 0) {
                details = currIndex.pkgName_ + "." + currIndex.name_ + " Constructor (" + currIndex.type_ + ")";
            } else if (programElementType.compareTo("method") == 0) {
                details = currIndex.pkgName_ + "." + currIndex.className_ + " " + "Method " + currIndex.name_ + "(" + currIndex.type_ + ")";
            } else if (programElementType.compareTo("field") == 0) {
                details = currIndex.pkgName_ + "." + currIndex.className_ + " " + "Field " + currIndex.name_;
            } else {
                System.out.println("Error: unknown program element type");
                System.exit(3);
            }
            if (currIndex.doc_ == null) {
                if (logMissingSinces)
                    missingSincesFile.println("NO DOC BLOCK: " + details);
                else
                    System.out.println("Warning: the doc block for the new element: " + details + " is missing, so there is no @since tag");
            } else if (currIndex.doc_.indexOf("@since") != -1) {
                if (logMissingSinces)
                    missingSincesFile.println("OK: " + details);
            } else {
                if (logMissingSinces)
                    missingSincesFile.println("MISSING @SINCE TAG: " + details);
                else
                    System.out.println("Warning: the doc block for the new element: " + details + " is missing an @since tag");
            }
        }
    }
    
    /** 
     * Emit a single entry and the link to its file.
     *
     * @param programElementType "Class", "Constructor",
     *                           "Method", or "Field".
     */
    public char emitIndexEntry(Index currIndex, char oldsw, int multipleMarker) {
        String programElementType = currIndex.ename_;
        if (programElementType.compareTo("class") == 0) {
            return emitClassIndexEntry(currIndex, oldsw, multipleMarker);
        } else if (programElementType.compareTo("constructor") == 0) {
            return emitCtorIndexEntry(currIndex, oldsw, multipleMarker);
        } else if (programElementType.compareTo("method") == 0) {
            return emitMethodIndexEntry(currIndex, oldsw, multipleMarker);
        } else if (programElementType.compareTo("field") == 0) {
            return emitFieldIndexEntry(currIndex, oldsw, multipleMarker);
        } else {
            System.out.println("Error: unknown program element type");
            System.exit(3);
        }
        return '\0';
    }

    /** Emit the index of classes, which appears in the bottom left frame. */
    public void emitClassesIndex(APIDiff apiDiff, int indexType) {
        // Add all the names of classes to a new list, to be sorted later
        classNames = new ArrayList(); // Index[]
        boolean hasRemovals = false;
        boolean hasAdditions = false;
        boolean hasChanges = false;
        Iterator iter = apiDiff.packagesChanged.iterator();
        while (iter.hasNext()) {
            PackageDiff pkgDiff = (PackageDiff)(iter.next());
            if (pkgDiff.classesRemoved.size() != 0)
                hasRemovals = true;
            if (pkgDiff.classesAdded.size() != 0)
                hasAdditions = true;
            if (pkgDiff.classesChanged.size() != 0)
                hasChanges = true;
            recordDiffs(hasRemovals, hasAdditions, hasChanges);
            String pkgName = pkgDiff.name_;
            Iterator iterClass = pkgDiff.classesRemoved.iterator();
            while ((indexType == 3 || indexType == 0) && iterClass.hasNext()) {
                ClassAPI cls = (ClassAPI)(iterClass.next());
                classNames.add(new Index(cls.name_, 0, pkgName, cls.isInterface_));
            }
            iterClass = pkgDiff.classesAdded.iterator();
            while ((indexType == 3 || indexType == 1) && iterClass.hasNext()) {
                ClassAPI cls = (ClassAPI)(iterClass.next());
                Index idx = new Index(cls.name_, 1, pkgName, cls.isInterface_);
                idx.doc_ = cls.doc_; // Used for checking @since
                classNames.add(idx);
            }
            iterClass = pkgDiff.classesChanged.iterator();
            while ((indexType == 3 || indexType == 2) && iterClass.hasNext()) {
                ClassDiff cls = (ClassDiff)(iterClass.next());
                classNames.add(new Index(cls.name_, 2, pkgName, cls.isInterface_));
            }
        }
        Collections.sort(classNames);
        emitIndexHeader("Classes", indexType, hasRemovals, hasAdditions, hasChanges);
        emitIndexEntries(classNames.iterator());
        if (indexType == 1)
            emitMissingSinces(classNames.iterator());
    }

    /** Emit an index entry for a class. */
    public char emitClassIndexEntry(Index cls, char oldsw, 
                                    int multipleMarker) {
        char res = oldsw;
        String className = cls.pkgName_ + "." + cls.name_;
        String classRef = cls.pkgName_ + "." + cls.name_;
        boolean isInterface = cls.isInterface_;
        // See if we are in a new section of the alphabet
        char sw = cls.name_.charAt(0);
        if (Character.toUpperCase(sw) != Character.toUpperCase(oldsw)) {
            res = sw;
            // Add the named anchor for this new letter
            h_.writeText("<A NAME=\"" + Character.toUpperCase(res) + "\"></A>");
            if (sw == '_')
                h_.writeText("<br><b>underscore</b>&nbsp;");
            else
                h_.writeText("<br><font size=\"+2\">" + Character.toUpperCase(sw) + "</font>&nbsp;");
            generateLetterIndex(classNames, sw, false);
        }
        // Deal with displaying duplicate indexes
        if (multipleMarker == 1) {
            h_.writeText("<i>" + cls.name_ + "</i><br>");
        }
        if (multipleMarker != 0)
            h_.indent(INDENT_SIZE);
        if (cls.changeType_ == 0) {
            // Emit a reference to the correct place for the class in the 
            // JDiff page for the package
            h_.writeText("<A HREF=\"pkg_" + cls.pkgName_ + h_.reportFileExt + 
                         "#" + cls.name_ + "\" class=\"hiddenlink\" target=\"rightframe\"><strike>" + cls.name_ + "</strike></A><br>");
        } else if (cls.changeType_ == 1) {
            String cn = cls.name_;
            if (multipleMarker != 0)
                cn = cls.pkgName_;
            if (isInterface)
                h_.writeText("<A HREF=\"pkg_" + cls.pkgName_ + h_.reportFileExt + "#" + cls.name_ + "\" class=\"hiddenlink\" target=\"rightframe\"><b><i>" + cn + "</i></b></A><br>");
            else
                h_.writeText("<A HREF=\"pkg_" + cls.pkgName_ + h_.reportFileExt + "#" + cls.name_ + "\" class=\"hiddenlink\" target=\"rightframe\"><b>" + cn + "</b></A><br>");
        } else if (cls.changeType_ == 2) {
            String cn = cls.name_;
            if (multipleMarker != 0)
                cn = cls.pkgName_;
            if (isInterface)
                h_.writeText("<A HREF=\"" + classRef + h_.reportFileExt + "\" class=\"hiddenlink\" target=\"rightframe\"><i>" + cn + "</i></A><br>");
            else
                h_.writeText("<A HREF=\"" + classRef + h_.reportFileExt + "\" class=\"hiddenlink\" target=\"rightframe\">" + cn + "</A><br>");
        }
        return res;
    }
    
    /** 
     * Emit the index of all constructors, which appears in the bottom left 
     * frame. 
     */
    public void emitConstructorsIndex(APIDiff apiDiff, int indexType) {
        // Add all the names of constructors to a new list, to be sorted later
        ctorNames = new ArrayList(); // Index[]
        boolean hasRemovals = false;
        boolean hasAdditions = false;
        boolean hasChanges = false;
        Iterator iter = apiDiff.packagesChanged.iterator();
        while (iter.hasNext()) {
            PackageDiff pkgDiff = (PackageDiff)(iter.next());
            String pkgName = pkgDiff.name_;
            Iterator iterClass = pkgDiff.classesChanged.iterator();
            while (iterClass.hasNext()) {
                ClassDiff classDiff = (ClassDiff)(iterClass.next());
                if (classDiff.ctorsRemoved.size() != 0)
                    hasRemovals = true;
                if (classDiff.ctorsAdded.size() != 0)
                    hasAdditions = true;
                if (classDiff.ctorsChanged.size() != 0)
                    hasChanges = true;
                recordDiffs(hasRemovals, hasAdditions, hasChanges);
                String className = classDiff.name_;
                Iterator iterCtor = classDiff.ctorsRemoved.iterator();
                while ((indexType == 3 || indexType == 0) && iterCtor.hasNext()) {
                    ConstructorAPI ctor = (ConstructorAPI)(iterCtor.next());
                    ctorNames.add(new Index(className, 0, pkgName, ctor.type_));
                }
                iterCtor = classDiff.ctorsAdded.iterator();
                while ((indexType == 3 || indexType == 1) && iterCtor.hasNext()) {
                    ConstructorAPI ctor = (ConstructorAPI)(iterCtor.next());
                    Index idx = new Index(className, 1, pkgName, ctor.type_);
                    idx.doc_ = ctor.doc_; // Used for checking @since
                    ctorNames.add(idx);
                }
                iterCtor = classDiff.ctorsChanged.iterator();
                while ((indexType == 3 || indexType == 2) && iterCtor.hasNext()) {
                    MemberDiff ctor = (MemberDiff)(iterCtor.next());
                    ctorNames.add(new Index(className, 2, pkgName, ctor.newType_));
                }
            }
        }
        Collections.sort(ctorNames);
        emitIndexHeader("Constructors", indexType, hasRemovals, hasAdditions, hasChanges);
        emitIndexEntries(ctorNames.iterator());
        if (indexType == 1)
            emitMissingSinces(ctorNames.iterator());
    }

    /** Emit an index entry for a constructor. */
    public char emitCtorIndexEntry(Index ctor, char oldsw, int multipleMarker) {
        char res = oldsw;
        String className = ctor.pkgName_ + "." + ctor.name_;
        String memberRef = ctor.pkgName_ + "." + ctor.name_;
        String type = ctor.type_;
        if (type.compareTo("void") == 0)
            type = "";
        String shownType = HTMLReportGenerator.simpleName(type);
        // See if we are in a new section of the alphabet
        char sw = ctor.name_.charAt(0);
        if (Character.toUpperCase(sw) != Character.toUpperCase(oldsw)) {
            res = sw;
            // Add the named anchor for this new letter
            h_.writeText("<A NAME=\"" + Character.toUpperCase(res) + "\"></A>");
            if (sw == '_')
                h_.writeText("<br><b>underscore</b>&nbsp;");
            else
                h_.writeText("<br><font size=\"+2\">" + Character.toUpperCase(sw) + "</font>&nbsp;");
            generateLetterIndex(ctorNames, sw, false);
        }
        // Deal with displaying duplicate indexes
        if (multipleMarker == 1) {
            h_.writeText("<i>" + ctor.name_ + "</i><br>");
        }
        if (multipleMarker != 0)
            h_.indent(INDENT_SIZE);
        // Deal with each type of difference
        // The output displayed for unique or duplicate entries is the same
        // for constructors.
        if (ctor.changeType_ == 0) {
            String commentID = className + ".ctor_removed(" + type + ")";
            h_.writeText("<nobr><A HREF=\"" + memberRef + h_.reportFileExt + "#" + commentID + "\" class=\"hiddenlink\" target=\"rightframe\"><strike>" + ctor.name_ + "</strike>");
            h_.emitTypeWithParens(shownType, false);
            h_.writeText("</A></nobr>&nbsp;constructor<br>");
        } else if (ctor.changeType_ == 1) {
            String commentID = className + ".ctor_added(" + type + ")";
            h_.writeText("<nobr><A HREF=\"" + memberRef + h_.reportFileExt + "#" + commentID + "\" class=\"hiddenlink\" target=\"rightframe\"><b>" + ctor.name_ + "</b>");
            h_.emitTypeWithParens(shownType, false);
            h_.writeText("</A></nobr>&nbsp;constructor<br>");
        } else if (ctor.changeType_ == 2) {
            String commentID = className + ".ctor_changed(" + type + ")";
            h_.writeText("<nobr><A HREF=\"" + memberRef + h_.reportFileExt + "#" + commentID + "\" class=\"hiddenlink\" target=\"rightframe\">" + ctor.name_);
            h_.emitTypeWithParens(shownType, false);
            h_.writeText("</A></nobr>&nbsp;constructor<br>");
        }
        return res;
    }

    /** 
     * Emit the index of all methods, which appears in the bottom left frame. 
     */
    public void emitMethodsIndex(APIDiff apiDiff, int indexType) {
        // Add all the names of methods to a new list, to be sorted later
        methNames = new ArrayList(); // Index[]
        boolean hasRemovals = false;
        boolean hasAdditions = false;
        boolean hasChanges = false;
        Iterator iter = apiDiff.packagesChanged.iterator();
        while (iter.hasNext()) {
            PackageDiff pkgDiff = (PackageDiff)(iter.next());
            String pkgName = pkgDiff.name_;
            Iterator iterClass = pkgDiff.classesChanged.iterator();
            while (iterClass.hasNext()) {
                ClassDiff classDiff = (ClassDiff)(iterClass.next());
                if (classDiff.methodsRemoved.size() != 0)
                    hasRemovals = true;
                if (classDiff.methodsAdded.size() != 0)
                    hasAdditions = true;
                if (classDiff.methodsChanged.size() != 0)
                    hasChanges = true;
                recordDiffs(hasRemovals, hasAdditions, hasChanges);
                String className = classDiff.name_;
                Iterator iterMeth = classDiff.methodsRemoved.iterator();
                while ((indexType == 3 || indexType == 0) && iterMeth.hasNext()) {
                    MethodAPI meth = (MethodAPI)(iterMeth.next());
                    methNames.add(new Index(meth.name_, 0, pkgName, className, meth.getSignature()));
                }
                iterMeth = classDiff.methodsAdded.iterator();
                while ((indexType == 3 || indexType == 1) && iterMeth.hasNext()) {
                    MethodAPI meth = (MethodAPI)(iterMeth.next());
                    Index idx = new Index(meth.name_, 1, pkgName, className, meth.getSignature());
                    idx.doc_ = meth.doc_; // Used for checking @since
                    methNames.add(idx);
                }
                iterMeth = classDiff.methodsChanged.iterator();
                while ((indexType == 3 || indexType == 2) && iterMeth.hasNext()) {
                    MemberDiff meth = (MemberDiff)(iterMeth.next());
                    methNames.add(new Index(meth.name_, 2, pkgName, className, meth.newSignature_));
                }
            }
        }
        Collections.sort(methNames);
        emitIndexHeader("Methods", indexType, hasRemovals, hasAdditions, hasChanges);
        emitIndexEntries(methNames.iterator());
        if (indexType == 1)
            emitMissingSinces(methNames.iterator());
    }

    /** Emit an index entry for a method. */
    public char emitMethodIndexEntry(Index meth, char oldsw, 
                                     int multipleMarker) {
        char res = oldsw;
        String className = meth.pkgName_ + "." + meth.className_;
        String memberRef = meth.pkgName_ + "." + meth.className_;
        String type = meth.type_;
        if (type.compareTo("void") == 0)
            type = "";
        String shownType = HTMLReportGenerator.simpleName(type);
        // See if we are in a new section of the alphabet
        char sw = meth.name_.charAt(0);
        if (Character.toUpperCase(sw) != Character.toUpperCase(oldsw)) {
            res = sw;
            // Add the named anchor for this new letter
            h_.writeText("<A NAME=\"" + Character.toUpperCase(res) + "\"></A>");
            if (sw == '_')
                h_.writeText("<br><b>underscore</b>&nbsp;");
            else
                h_.writeText("<br><font size=\"+2\">" + Character.toUpperCase(sw) + "</font>&nbsp;");
            generateLetterIndex(methNames, sw, false);
        }
        // Deal with displaying duplicate indexes
        if (multipleMarker == 1) {
            h_.writeText("<i>" + meth.name_ + "</i><br>");
        }
        if (multipleMarker != 0)
            h_.indent(INDENT_SIZE);
        // Deal with each type of difference
        if (meth.changeType_ == 0) {
            String commentID = className + "." + meth.name_ + "_removed(" + type + ")";                    
            if (multipleMarker == 0) {
                h_.writeText("<nobr><A HREF=\"" + memberRef + h_.reportFileExt + "#" + commentID + "\" class=\"hiddenlink\" target=\"rightframe\"><strike>" + meth.name_ + "</strike>");
                h_.emitTypeWithParens(shownType, false);
            } else {
                h_.writeText("<nobr><A HREF=\"" + memberRef + h_.reportFileExt + "#" + commentID + "\" class=\"hiddenlink\" target=\"rightframe\">type&nbsp;<strike>");
                h_.emitTypeWithParens(shownType, false);
                h_.writeText("</strike>&nbsp;in&nbsp;" + className);
            }
            h_.writeText("</A></nobr><br>");
        } else if (meth.changeType_ == 1) {
            String commentID = className + "." + meth.name_ + "_added(" + type + ")";                    
            if (multipleMarker == 0) {
                h_.writeText("<nobr><A HREF=\"" + memberRef + h_.reportFileExt + "#" + commentID + "\" class=\"hiddenlink\" target=\"rightframe\"><b>" + meth.name_ + "</b>");
                h_.emitTypeWithParens(shownType, false);
            } else {
                h_.writeText("<nobr><A HREF=\"" + memberRef + h_.reportFileExt + "#" + commentID + "\" class=\"hiddenlink\" target=\"rightframe\">type&nbsp;<b>");
                h_.emitTypeWithParens(shownType, false);
                h_.writeText("</b>&nbsp;in&nbsp;" + className);
            }
            h_.writeText("</A></nobr><br>");
        } else if (meth.changeType_ == 2) {
            String commentID = className + "." + meth.name_ + "_changed(" + type + ")";                    
            if (multipleMarker == 0) {
                h_.writeText("<nobr><A HREF=\"" + memberRef + h_.reportFileExt + "#" + commentID + "\" class=\"hiddenlink\" target=\"rightframe\">" + meth.name_);
                h_.emitTypeWithParens(shownType, false);
            } else {
                h_.writeText("<nobr><A HREF=\"" + memberRef + h_.reportFileExt + "#" + commentID + "\" class=\"hiddenlink\" target=\"rightframe\">type&nbsp;");
                h_.emitTypeWithParens(shownType, false);
                h_.writeText("&nbsp;in&nbsp;" + className);
            }
            h_.writeText("</A></nobr><br>");
        }
        return res;
    }

    /** 
     * Emit the index of all fields, which appears in the bottom left frame. 
     */
    public void emitFieldsIndex(APIDiff apiDiff, int indexType) {
        // Add all the names of fields to a new list, to be sorted later
        fieldNames = new ArrayList(); // Index[]
        boolean hasRemovals = false;
        boolean hasAdditions = false;
        boolean hasChanges = false;
        Iterator iter = apiDiff.packagesChanged.iterator();
        while (iter.hasNext()) {
            PackageDiff pkgDiff = (PackageDiff)(iter.next());
            String pkgName = pkgDiff.name_;
            Iterator iterClass = pkgDiff.classesChanged.iterator();
            while (iterClass.hasNext()) {
                ClassDiff classDiff = (ClassDiff)(iterClass.next());
                if (classDiff.fieldsRemoved.size() != 0)
                    hasRemovals = true;
                if (classDiff.fieldsAdded.size() != 0)
                    hasAdditions = true;
                if (classDiff.fieldsChanged.size() != 0)
                    hasChanges = true;
                recordDiffs(hasRemovals, hasAdditions, hasChanges);
                String className = classDiff.name_;
                Iterator iterField = classDiff.fieldsRemoved.iterator();
                while ((indexType == 3 || indexType == 0) && iterField.hasNext()) {
                    FieldAPI fld = (FieldAPI)(iterField.next());
                    fieldNames.add(new Index(fld.name_, 0, pkgName, className, fld.type_, true));
                }
                iterField = classDiff.fieldsAdded.iterator();
                while ((indexType == 3 || indexType == 1) && iterField.hasNext()) {
                    FieldAPI fld = (FieldAPI)(iterField.next());
                    Index idx = new Index(fld.name_, 1, pkgName, className, fld.type_, true);
                    idx.doc_ = fld.doc_; // Used for checking @since
                    fieldNames.add(idx);
                }
                iterField = classDiff.fieldsChanged.iterator();
                while ((indexType == 3 || indexType == 2) && iterField.hasNext()) {
                    MemberDiff fld = (MemberDiff)(iterField.next());
                    fieldNames.add(new Index(fld.name_, 2, pkgName, className, fld.newType_, true));
                }
            }
        }
        Collections.sort(fieldNames);
        emitIndexHeader("Fields", indexType, hasRemovals, hasAdditions, hasChanges);
        emitIndexEntries(fieldNames.iterator());
        if (indexType == 1)
            emitMissingSinces(fieldNames.iterator());
    }

    /** Emit an index entry for a field. */
    public char emitFieldIndexEntry(Index fld, char oldsw, 
                                    int multipleMarker) {
        char res = oldsw;
        String className = fld.pkgName_ + "." + fld.className_;
        String memberRef = fld.pkgName_ + "." + fld.className_;
        String type = fld.type_;
        if (type.compareTo("void") == 0)
            type = "";
        String shownType = HTMLReportGenerator.simpleName(type);
        // See if we are in a new section of the alphabet
        char sw = fld.name_.charAt(0);
        if (Character.toUpperCase(sw) != Character.toUpperCase(oldsw)) {
            res = sw;
            // Add the named anchor for this new letter
            h_.writeText("<A NAME=\"" + Character.toUpperCase(res) + "\"></A>");
            if (sw == '_')
                h_.writeText("<br><b>underscore</b>&nbsp;");
            else
                h_.writeText("<br><font size=\"+2\">" + Character.toUpperCase(sw) + "</font>&nbsp;");
            generateLetterIndex(fieldNames, sw, false);
        }
        // Deal with displaying duplicate indexes
        if (multipleMarker == 1) {
            h_.writeText("<i>" + fld.name_ + "</i><br>");
        }
        if (multipleMarker != 0) {
// More context than this is helpful here: h_.indent(INDENT_SIZE);
            h_.writeText("&nbsp;in&nbsp;");
        }
        // Deal with each type of difference
        if (fld.changeType_ == 0) {
            String commentID = className + "." + fld.name_;                    
            if (multipleMarker == 0) {            
                h_.writeText("<nobr><A HREF=\"" + memberRef + h_.reportFileExt + "#" + commentID + "\" class=\"hiddenlink\" target=\"rightframe\"><strike>" + fld.name_ + "</strike></A>");
                h_.writeText("</nobr><br>");
            } else {
                h_.writeText("<nobr><A HREF=\"" + memberRef + h_.reportFileExt + "#" + commentID + "\" class=\"hiddenlink\" target=\"rightframe\"><strike>" + className + "</strike></A>");
                h_.writeText("</nobr><br>");
            }
        } else if (fld.changeType_ == 1) {
            String commentID = className + "." + fld.name_;                    
            if (multipleMarker == 0) {            
                h_.writeText("<nobr><A HREF=\"" + memberRef + h_.reportFileExt + "#" + commentID + "\" class=\"hiddenlink\" target=\"rightframe\">" + fld.name_ + "</A>");
                h_.writeText("</nobr><br>");
            } else {
                h_.writeText("<nobr><A HREF=\"" + memberRef + h_.reportFileExt + "#" + commentID + "\" class=\"hiddenlink\" target=\"rightframe\">" + className + "</A>");
                h_.writeText("</nobr><br>");
            }
        } else if (fld.changeType_ == 2) {
            String commentID = className + "." + fld.name_;                    
            if (multipleMarker == 0) {            
                h_.writeText("<nobr><A HREF=\"" + memberRef + h_.reportFileExt + "#" + commentID + "\" class=\"hiddenlink\" target=\"rightframe\">" + fld.name_ + "</A>");
                h_.writeText("</nobr><br>");
            } else {
                h_.writeText("<nobr><A HREF=\"" + memberRef + h_.reportFileExt + "#" + commentID + "\" class=\"hiddenlink\" target=\"rightframe\">" + className + "</A>");
                h_.writeText("</nobr><br>");
            }
        }
        return res;
    }

    /** 
     * Emit the index of all changes, which appears in the bottom left frame.
     * Has to be run after all the other indexes have been written, since it
     * uses data from when they are generated.
     */
    public void emitAllDiffsIndex(APIDiff apiDiff, int indexType) {
        allNames = new ArrayList(); // Index[]
        // Add all the changes into one big list, and sort it by name,
        // ignoring case
        allNames.addAll(packageNames);
        allNames.addAll(classNames);
        allNames.addAll(ctorNames);
        allNames.addAll(methNames);
        allNames.addAll(fieldNames);
        // Compares two Index objects' names, ignoring case differences.
        Collections.sort(allNames);

        emitIndexHeader("All Differences", indexType, atLeastOneRemoval, 
                        atLeastOneAddition, atLeastOneChange);

        // Tell generateLetterIndex to use allNames as the list when 
        // using the other methods to generate the indexes.
        isAllNames = true; 
        
        // Now emit a line for each entry in the list in the appropriate 
        // format for each program element
        Iterator iter = allNames.iterator();
        char oldsw = '\0';
        int multipleMarker = 0;
        Index currIndex = null; // The entry which is emitted
        while (iter.hasNext()) {
            // The next entry after the current one
            Index nextIndex = (Index)(iter.next()); 
            if (currIndex == null) {
                currIndex = nextIndex; // Prime the pump
            } else {
                if (nextIndex.name_.compareTo(currIndex.name_) == 0) {
                    // It's a duplicate index, so emit the name and then
                    // the indented entries
                    if (multipleMarker == 0)
                        multipleMarker = 1; // Start of a duplicate index
                    else if (multipleMarker == 1)
                        multipleMarker = 2; // Inside a duplicate index
                    oldsw = emitIndexEntryForAny(currIndex, oldsw, multipleMarker);
                } else {
                    if (multipleMarker == 1)
                        multipleMarker = 2; // Inside a duplicate index
                    oldsw = emitIndexEntryForAny(currIndex, oldsw, multipleMarker);
                    multipleMarker = 0; // Not in a duplicate index any more
                }
                currIndex = nextIndex;
            }
        }
        // Emit the last entry left in currIndex
        if (multipleMarker == 1)
            multipleMarker = 2; // Inside a duplicate index
        if (currIndex != null)
            oldsw = emitIndexEntryForAny(currIndex, oldsw, multipleMarker);

        // Tell generateLetterIndex to stop using allNames as the list when 
        // using the other methods to generate the indexes.
        isAllNames = false; 
    }

    /** Call the appropriate *IndexEntry method for each entry. */
    public char emitIndexEntryForAny(Index currIndex, char oldsw, 
                                     int multipleMarker) {
        if (currIndex.ename_.compareTo("package") == 0) {
            h_.writeText("<!-- Package " + currIndex.name_ + " -->");
            return emitPackageIndexEntry(currIndex, oldsw);
        } else if (currIndex.ename_.compareTo("class") == 0) {
            h_.writeText("<!-- Class " + currIndex.name_ + " -->");
            return emitClassIndexEntry(currIndex, oldsw, multipleMarker);
        } else if (currIndex.ename_.compareTo("constructor") == 0) {
            h_.writeText("<!-- Constructor " + currIndex.name_ + " -->");
            return emitCtorIndexEntry(currIndex, oldsw, multipleMarker);
        } else if (currIndex.ename_.compareTo("method") == 0) {
            h_.writeText("<!-- Method " + currIndex.name_ + " -->");
            return emitMethodIndexEntry(currIndex, oldsw, multipleMarker);
        } else if (currIndex.ename_.compareTo("field") == 0) {
            h_.writeText("<!-- Field " + currIndex.name_ + " -->");
            return emitFieldIndexEntry(currIndex, oldsw, multipleMarker);
        }
        return '\0';
    }

    /** The list of all changes for all program elements. */
    private List allNames = null; // Index[]

    /** The list of all package changes. */
    private List packageNames = null; // Index[]

    /** The list of all class changes. */
    private List classNames = null; // Index[]

    /** The list of all constructor changes. */
    private List ctorNames = null; // Index[]

    /** The list of all method changes. */
    private List methNames = null; // Index[]

    /** The list of all field changes. */
    private List fieldNames = null; // Index[]

    /** If set, then use allNames to generate the letter indexes. */
    private boolean isAllNames = false;

    /** 
     * If any of the parameters are set, then set the respective atLeastOne
     * variable, used to generate the links at the top of the allDiffs index. 
     * Never unset an atLeastOne variable.
     */
    private void recordDiffs(boolean hasRemovals, boolean hasAdditions, 
                        boolean hasChanges) {
        if (hasRemovals)
            atLeastOneRemoval = true;
        if (hasAdditions)
            atLeastOneAddition = true;
        if (hasChanges)
            atLeastOneChange = true;
    }

    /** Set if there was at least one removal in the entire API. */
    private boolean atLeastOneRemoval = false;

    /** Set if there was at least one addition in the entire API. */
    private boolean atLeastOneAddition = false;

    /** Set if there was at least one change in the entire API. */
    private boolean atLeastOneChange = false;

    /** 
     * The number of non-breaking spaces to indent a duplicate indexes'
     * entries by. 
     */
    private final int INDENT_SIZE = 2;
}

/**
 * Class used to produce indexes of packages and classes. 
 *
 * See the file LICENSE.txt for copyright details.
 * @author Matthew Doar, mdoar@pobox.com
 */
class Index implements Comparable {

    /** The name of the program element this Index object represents. */
    public String ename_ = null;

    /** Name of the changed package, class or member. */
    public String name_ = null;
    
    /** Type of change. 0 = remove, 1 = add, 2 = change. */
    public int changeType_;
    
    /** Name of the changed package if name_ is a class name. */
    public String pkgName_ = null;
    
    /** Set if this class is an interface. */
    public boolean isInterface_= false;
    
    /** The doc block of added elements, default is null. */
    public String doc_ = null;
    
    /** 
     * The new member type. For methods, this is the signature.
     */
    public String type_ = null;

    /** 
     * The class name. Only used by methods.
     */
    public String className_ = null;

    /** Constructor for packages. */
    public Index(String name, int changeType) {
        ename_ = "package";
        name_ = name;
        changeType_ = changeType;
    }
    
    /** Constructor for classes. */
    public Index(String name, int changeType, String pkgName, boolean isInterface) {
        ename_ = "class";
        name_ = name;
        changeType_ = changeType;
        pkgName_ = pkgName;
        isInterface_ = isInterface;
    }
        
    /** Constructor for constructors. */
    public Index(String name, int changeType, String pkgName, String type) {
        ename_ = "constructor";
        name_ = name;
        changeType_ = changeType;
        pkgName_ = pkgName;
        type_  = type;
    }
        
    /** Constructor for methods. */
    public Index(String name, int changeType, String pkgName, 
                 String className, String type) {
        ename_ = "method";
        name_ = name;
        changeType_ = changeType;
        pkgName_ = pkgName;
        className_ = className;
        type_  = type;
    }
        
    /** 
     * Constructor for fields. 
     *
     * The boolean <code>fld</code> is simply there to differentiate this
     * constructor from the one for methods.
     */
    public Index(String name, int changeType, String pkgName, 
                 String className, String type, boolean fld) {
        ename_ = "field";
        name_ = name;
        changeType_ = changeType;
        pkgName_ = pkgName;
        className_ = className;
        type_  = type;
    }
        
        
    /** Compare two Index objects by their simple names, ignoring case. */
    public int compareTo(Object o) {
        return name_.compareToIgnoreCase(((Index)o).name_);
    }  
    
}

