package jdiff;

import com.sun.javadoc.*;
import com.sun.javadoc.ParameterizedType;
import com.sun.javadoc.Type;

import java.util.*;
import java.io.*;
import java.lang.reflect.*;

/**
 * Converts a Javadoc RootDoc object into a representation in an 
 * XML file.
 *
 * See the file LICENSE.txt for copyright details.
 * @author Matthew Doar, mdoar@pobox.com
 */
public class RootDocToXML {

    /** Default constructor. */
    public RootDocToXML() {
    }

    /**
     * Write the XML representation of the API to a file.
     *
     * @param root  the RootDoc object passed by Javadoc
     * @return true if no problems encountered
     */
    public static boolean writeXML(RootDoc root) {
    	String tempFileName = outputFileName;
    	if (outputDirectory != null) {
	    tempFileName = outputDirectory;
	    if (!tempFileName.endsWith(JDiff.DIR_SEP)) 
		tempFileName += JDiff.DIR_SEP;
	    tempFileName += outputFileName;
    	}

        try {
            FileOutputStream fos = new FileOutputStream(tempFileName);
            outputFile = new PrintWriter(fos);
            System.out.println("JDiff: writing the API to file '" + tempFileName + "'...");
            if (root.specifiedPackages().length != 0 || root.specifiedClasses().length != 0) {
                RootDocToXML apiWriter = new RootDocToXML();
                apiWriter.emitXMLHeader();
                apiWriter.logOptions();
                apiWriter.processPackages(root);
                apiWriter.emitXMLFooter();
            }
            outputFile.close();
        } catch(IOException e) {
            System.out.println("IO Error while attempting to create " + tempFileName);
            System.out.println("Error: " +  e.getMessage());
            System.exit(1);
        }
        // If validation is desired, write out the appropriate api.xsd file
        // in the same directory as the XML file.
        if (XMLToAPI.validateXML) {
            writeXSD();
        }
        return true;
    }

    /**
     * Write the XML Schema file used for validation.
     */
    public static void writeXSD() {
        String xsdFileName = outputFileName;
        if (outputDirectory == null) {
	    int idx = xsdFileName.lastIndexOf('\\');
	    int idx2 = xsdFileName.lastIndexOf('/');
	    if (idx == -1 && idx2 == -1) {
		xsdFileName = "";
	    } else if (idx == -1 && idx2 != -1) {
		xsdFileName = xsdFileName.substring(0, idx2);
	    } else if (idx != -1  && idx2 == -1) {
		xsdFileName = xsdFileName.substring(0, idx);
	    } else if (idx != -1  && idx2 != -1) {
		int max = idx2 > idx ? idx2 : idx;
		xsdFileName = xsdFileName.substring(0, max);
	    }
	} else {
	    xsdFileName = outputDirectory;
	    if (!xsdFileName.endsWith(JDiff.DIR_SEP)) 
		 xsdFileName += JDiff.DIR_SEP;
	}
        xsdFileName += "api.xsd";
        try {
            FileOutputStream fos = new FileOutputStream(xsdFileName);
            PrintWriter xsdFile = new PrintWriter(fos);
            // The contents of the api.xsd file
            xsdFile.println("<?xml version=\"1.0\" encoding=\"iso-8859-1\" standalone=\"no\"?>");
            xsdFile.println("<xsd:schema xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\">");
            xsdFile.println("");
            xsdFile.println("<xsd:annotation>");
            xsdFile.println("  <xsd:documentation>");
            xsdFile.println("  Schema for JDiff API representation.");
            xsdFile.println("  </xsd:documentation>");
            xsdFile.println("</xsd:annotation>");
            xsdFile.println();
            xsdFile.println("<xsd:element name=\"api\" type=\"apiType\"/>");
            xsdFile.println("");
            xsdFile.println("<xsd:complexType name=\"apiType\">");
            xsdFile.println("  <xsd:sequence>");
            xsdFile.println("    <xsd:element name=\"package\" type=\"packageType\" minOccurs='1' maxOccurs='unbounded'/>");
            xsdFile.println("  </xsd:sequence>");
            xsdFile.println("  <xsd:attribute name=\"name\" type=\"xsd:string\"/>");
            xsdFile.println("  <xsd:attribute name=\"jdversion\" type=\"xsd:string\"/>");
            xsdFile.println("</xsd:complexType>");
            xsdFile.println();
            xsdFile.println("<xsd:complexType name=\"packageType\">");
            xsdFile.println("  <xsd:sequence>");
            xsdFile.println("    <xsd:choice maxOccurs='unbounded'>");
            xsdFile.println("      <xsd:element name=\"class\" type=\"classType\"/>");
            xsdFile.println("      <xsd:element name=\"interface\" type=\"classType\"/>");
            xsdFile.println("    </xsd:choice>");
            xsdFile.println("    <xsd:element name=\"doc\" type=\"xsd:string\" minOccurs='0' maxOccurs='1'/>");
            xsdFile.println("  </xsd:sequence>");
            xsdFile.println("  <xsd:attribute name=\"name\" type=\"xsd:string\"/>");
            xsdFile.println("</xsd:complexType>");
            xsdFile.println();
            xsdFile.println("<xsd:complexType name=\"classType\">");
            xsdFile.println("  <xsd:sequence>");
            xsdFile.println("    <xsd:element name=\"implements\" type=\"interfaceTypeName\" minOccurs='0' maxOccurs='unbounded'/>");
            xsdFile.println("    <xsd:element name=\"constructor\" type=\"constructorType\" minOccurs='0' maxOccurs='unbounded'/>");
            xsdFile.println("    <xsd:element name=\"method\" type=\"methodType\" minOccurs='0' maxOccurs='unbounded'/>");
            xsdFile.println("    <xsd:element name=\"field\" type=\"fieldType\" minOccurs='0' maxOccurs='unbounded'/>");
            xsdFile.println("    <xsd:element name=\"doc\" type=\"xsd:string\" minOccurs='0' maxOccurs='1'/>");
            xsdFile.println("  </xsd:sequence>");
            xsdFile.println("  <xsd:attribute name=\"name\" type=\"xsd:string\"/>");
            xsdFile.println("  <xsd:attribute name=\"extends\" type=\"xsd:string\" use='optional'/>");
            xsdFile.println("  <xsd:attribute name=\"abstract\" type=\"xsd:boolean\"/>");
            xsdFile.println("  <xsd:attribute name=\"src\" type=\"xsd:string\" use='optional'/>");
            xsdFile.println("  <xsd:attribute name=\"static\" type=\"xsd:boolean\"/>");
            xsdFile.println("  <xsd:attribute name=\"final\" type=\"xsd:boolean\"/>");
            xsdFile.println("  <xsd:attribute name=\"deprecated\" type=\"xsd:string\"/>");
            xsdFile.println("  <xsd:attribute name=\"visibility\" type=\"xsd:string\"/>");
            xsdFile.println("</xsd:complexType>");
            xsdFile.println();
            xsdFile.println("<xsd:complexType name=\"interfaceTypeName\">");
            xsdFile.println("  <xsd:attribute name=\"name\" type=\"xsd:string\"/>");
            xsdFile.println("</xsd:complexType>");
            xsdFile.println();
            xsdFile.println("<xsd:complexType name=\"constructorType\">");
            xsdFile.println("  <xsd:sequence>");
            xsdFile.println("    <xsd:element name=\"exception\" type=\"exceptionType\" minOccurs='0' maxOccurs='unbounded'/>");
            xsdFile.println("    <xsd:element name=\"doc\" type=\"xsd:string\" minOccurs='0' maxOccurs='1'/>");
            xsdFile.println("  </xsd:sequence>");
            xsdFile.println("  <xsd:attribute name=\"name\" type=\"xsd:string\"/>");
            xsdFile.println("  <xsd:attribute name=\"type\" type=\"xsd:string\" use='optional'/>");
            xsdFile.println("  <xsd:attribute name=\"src\" type=\"xsd:string\" use='optional'/>");
            xsdFile.println("  <xsd:attribute name=\"static\" type=\"xsd:boolean\"/>");
            xsdFile.println("  <xsd:attribute name=\"final\" type=\"xsd:boolean\"/>");
            xsdFile.println("  <xsd:attribute name=\"deprecated\" type=\"xsd:string\"/>");
            xsdFile.println("  <xsd:attribute name=\"visibility\" type=\"xsd:string\"/>");
            xsdFile.println("</xsd:complexType>");
            xsdFile.println();
            xsdFile.println("<xsd:complexType name=\"paramsType\">");
            xsdFile.println("  <xsd:attribute name=\"name\" type=\"xsd:string\"/>");
            xsdFile.println("  <xsd:attribute name=\"type\" type=\"xsd:string\"/>");
            xsdFile.println("</xsd:complexType>");
            xsdFile.println();
            xsdFile.println("<xsd:complexType name=\"exceptionType\">");
            xsdFile.println("  <xsd:attribute name=\"name\" type=\"xsd:string\"/>");
            xsdFile.println("  <xsd:attribute name=\"type\" type=\"xsd:string\"/>");
            xsdFile.println("</xsd:complexType>");
            xsdFile.println();
            xsdFile.println("<xsd:complexType name=\"methodType\">");
            xsdFile.println("  <xsd:sequence>");
            xsdFile.println("    <xsd:element name=\"param\" type=\"paramsType\" minOccurs='0' maxOccurs='unbounded'/>");
            xsdFile.println("    <xsd:element name=\"exception\" type=\"exceptionType\" minOccurs='0' maxOccurs='unbounded'/>");
            xsdFile.println("    <xsd:element name=\"doc\" type=\"xsd:string\" minOccurs='0' maxOccurs='1'/>");
            xsdFile.println("  </xsd:sequence>");
            xsdFile.println("  <xsd:attribute name=\"name\" type=\"xsd:string\"/>");
            xsdFile.println("  <xsd:attribute name=\"return\" type=\"xsd:string\" use='optional'/>");
            xsdFile.println("  <xsd:attribute name=\"abstract\" type=\"xsd:boolean\"/>");
            xsdFile.println("  <xsd:attribute name=\"native\" type=\"xsd:boolean\"/>");
            xsdFile.println("  <xsd:attribute name=\"synchronized\" type=\"xsd:boolean\"/>");
            xsdFile.println("  <xsd:attribute name=\"src\" type=\"xsd:string\" use='optional'/>");
            xsdFile.println("  <xsd:attribute name=\"static\" type=\"xsd:boolean\"/>");
            xsdFile.println("  <xsd:attribute name=\"final\" type=\"xsd:boolean\"/>");
            xsdFile.println("  <xsd:attribute name=\"deprecated\" type=\"xsd:string\"/>");
            xsdFile.println("  <xsd:attribute name=\"visibility\" type=\"xsd:string\"/>");
            xsdFile.println("</xsd:complexType>");
            xsdFile.println();
            xsdFile.println("<xsd:complexType name=\"fieldType\">");
            xsdFile.println("  <xsd:sequence>");
            xsdFile.println("    <xsd:element name=\"doc\" type=\"xsd:string\" minOccurs='0' maxOccurs='1'/>");
            xsdFile.println("  </xsd:sequence>");
            xsdFile.println("  <xsd:attribute name=\"name\" type=\"xsd:string\"/>");
            xsdFile.println("  <xsd:attribute name=\"type\" type=\"xsd:string\"/>");
            xsdFile.println("  <xsd:attribute name=\"transient\" type=\"xsd:boolean\"/>");
            xsdFile.println("  <xsd:attribute name=\"volatile\" type=\"xsd:boolean\"/>");
            xsdFile.println("  <xsd:attribute name=\"value\" type=\"xsd:string\" use='optional'/>");
            xsdFile.println("  <xsd:attribute name=\"src\" type=\"xsd:string\" use='optional'/>");
            xsdFile.println("  <xsd:attribute name=\"static\" type=\"xsd:boolean\"/>");
            xsdFile.println("  <xsd:attribute name=\"final\" type=\"xsd:boolean\"/>");
            xsdFile.println("  <xsd:attribute name=\"deprecated\" type=\"xsd:string\"/>");
            xsdFile.println("  <xsd:attribute name=\"visibility\" type=\"xsd:string\"/>");
            xsdFile.println("</xsd:complexType>");
            xsdFile.println();
            xsdFile.println("</xsd:schema>");
            xsdFile.close();
        } catch(IOException e) {
            System.out.println("IO Error while attempting to create " + xsdFileName);
            System.out.println("Error: " +  e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Write the options which were used to generate this XML file
     * out as XML comments.
     */
    public void logOptions() {
        outputFile.print("<!-- ");
        outputFile.print(" Command line arguments = " + Options.cmdOptions);
        outputFile.println(" -->");
    }

    /**
     * Process each package and the classes/interfaces within it.
     *
     * @param pd  an array of PackageDoc objects
     */
    public void processPackages(RootDoc root) {
        PackageDoc[] specified_pd = root.specifiedPackages();
	Map pdl = new TreeMap();
        for (int i = 0; specified_pd != null && i < specified_pd.length; i++) {
	    pdl.put(specified_pd[i].name(), specified_pd[i]);
	}

	// Classes may be specified separately, so merge their packages into the
	// list of specified packages.
        ClassDoc[] cd = root.specifiedClasses();
	// This is lists of the specific classes to document
	Map classesToUse = new HashMap();
        for (int i = 0; cd != null && i < cd.length; i++) {
	    PackageDoc cpd = cd[i].containingPackage();
	    if (cpd == null && !packagesOnly) {
		// If the RootDoc object has been created from a jar file
		// this duplicates classes, so we have to be able to disable it.
		// TODO this is still null?
		cpd = root.packageNamed("anonymous");
	    }
            String pkgName = cpd.name();
            String className = cd[i].name();
	    if (trace) System.out.println("Found package " + pkgName + " for class " + className);
	    if (!pdl.containsKey(pkgName)) {
		if (trace) System.out.println("Adding new package " + pkgName);
		pdl.put(pkgName, cpd);
	    }

	    // Keep track of the specific classes to be used for this package
	    List classes;
	    if (classesToUse.containsKey(pkgName)) {
		classes = (ArrayList) classesToUse.get(pkgName);
	    } else {
		classes = new ArrayList();
	    }
	    classes.add(cd[i]);
	    classesToUse.put(pkgName, classes);
	}

	PackageDoc[] pd = (PackageDoc[]) pdl.values().toArray(new PackageDoc[0]);
        for (int i = 0; pd != null && i < pd.length; i++) {
            String pkgName = pd[i].name();
            
            // Check for an exclude tag in the package doc block, but not
	    // in the package.htm[l] file.
            if (!shownElement(pd[i], null))
                continue;

            if (trace) System.out.println("PROCESSING PACKAGE: " + pkgName);
            outputFile.println("<package name=\"" + pkgName + "\">");

            int tagCount = pd[i].tags().length;
            if (trace) System.out.println("#tags: " + tagCount);
            
            List classList;
	    if (classesToUse.containsKey(pkgName)) {
		// Use only the specified classes in the package
		System.out.println("Using the specified classes");
		classList = (ArrayList) classesToUse.get(pkgName);
	    } else {
		// Use all classes in the package
		classList = new LinkedList(Arrays.asList(pd[i].allClasses()));
	    }
            Collections.sort(classList);
            ClassDoc[] classes = new ClassDoc[classList.size()];
            classes = (ClassDoc[])classList.toArray(classes);
            processClasses(classes, pkgName);

            addPkgDocumentation(root, pd[i], 2);

            outputFile.println("</package>");
        }
    } // processPackages
    
    /**
     * Process classes and interfaces.
     *
     * @param cd An array of ClassDoc objects.
     */
    public void processClasses(ClassDoc[] cd, String pkgName) {
        if (cd.length == 0)
            return;
        if (trace) System.out.println("PROCESSING CLASSES, number=" + cd.length);
        for (int i = 0; i < cd.length; i++) {
            String className = cd[i].name();
            if (trace) System.out.println("PROCESSING CLASS/IFC: " + className);
            // Only save the shown elements
            if (!shownElement(cd[i], classVisibilityLevel))
                continue;
            boolean isInterface = false;
            if (cd[i].isInterface())
                isInterface = true;
            if (isInterface) {
                outputFile.println("  <!-- start interface " + pkgName + "." + className + " -->");
                outputFile.print("  <interface name=\"" + className + "\"");
            } else {
                outputFile.println("  <!-- start class " + pkgName + "." + className + " -->");
                outputFile.print("  <class name=\"" + className + "\"");
            }
            // Add attributes to the class element
            Type parent = cd[i].superclassType();
            if (parent != null)
                outputFile.println(" extends=\"" + buildEmittableTypeString(parent) + "\"");
            outputFile.println("    abstract=\"" + cd[i].isAbstract() + "\"");
            addCommonModifiers(cd[i], 4);
            outputFile.println(">");
            // Process class members. (Treat inner classes as members.)
            processInterfaces(cd[i].interfaceTypes());
            processConstructors(cd[i].constructors());
            processMethods(cd[i], cd[i].methods());
            processFields(cd[i].fields());

            addDocumentation(cd[i], 4);

            if (isInterface) {
                outputFile.println("  </interface>");
                outputFile.println("  <!-- end interface " + pkgName + "." + className + " -->");
            } else {
                outputFile.println("  </class>");
                outputFile.println("  <!-- end class " + pkgName + "." + className + " -->");
            }
            // Inner classes have already been added.
            /*
              ClassDoc[] ic = cd[i].innerClasses();
              for (int k = 0; k < ic.length; k++) {
              System.out.println("Inner class " + k + ", name = " + ic[k].name());
              } 
            */
        }//for
    }//processClasses()
    
    /**
     * Add qualifiers for the program element as attributes.
     *
     * @param ped The given program element.
     */
    public void addCommonModifiers(ProgramElementDoc ped, int indent) {
        addSourcePosition(ped, indent);
        // Static and final and visibility on one line
        for (int i = 0; i < indent; i++) outputFile.print(" ");
        outputFile.print("static=\"" + ped.isStatic() + "\"");
        outputFile.print(" final=\"" + ped.isFinal() + "\"");
        // Visibility
        String visibility = null;
        if (ped.isPublic())
            visibility = "public";
        else if (ped.isProtected())
            visibility = "protected";
        else if (ped.isPackagePrivate())
            visibility = "package";
        else if (ped.isPrivate())
            visibility = "private";
        outputFile.println(" visibility=\"" + visibility + "\"");

        // Deprecation on its own line
        for (int i = 0; i < indent; i++) outputFile.print(" ");
        boolean isDeprecated = false;
        Tag[] ta = ((Doc)ped).tags("deprecated");
        if (ta.length != 0) {
            isDeprecated = true;
        }
        if (ta.length > 1) {
            System.out.println("JDiff: warning: multiple @deprecated tags found in comments for " + ped.name() + ". Using the first one only.");
            System.out.println("Text is: " + ((Doc)ped).getRawCommentText());
        }
        if (isDeprecated) {
            String text = ta[0].text(); // Use only one @deprecated tag
            if (text != null && text.compareTo("") != 0) {
                int idx = endOfFirstSentence(text);
                if (idx == 0) {
                    // No useful comment
                    outputFile.print("deprecated=\"deprecated, no comment\"");
                } else {
                    String fs = null;
                    if (idx == -1)
                        fs = text;
                    else
                        fs = text.substring(0, idx+1);
                    String st = API.hideHTMLTags(fs);
                    outputFile.print("deprecated=\"" + st + "\"");
                }
            } else {
                outputFile.print("deprecated=\"deprecated, no comment\"");
            }
        } else {
            outputFile.print("deprecated=\"not deprecated\"");
        }

    } //addQualifiers()

    /**
     * Insert the source code details, if available.
     *
     * @param ped The given program element.
     */
    public void addSourcePosition(ProgramElementDoc ped, int indent) {
        if (!addSrcInfo)
            return;
        if (JDiff.javaVersion.startsWith("1.1") || 
            JDiff.javaVersion.startsWith("1.2") || 
            JDiff.javaVersion.startsWith("1.3")) {
            return; // position() only appeared in J2SE1.4
        }
        try {
            // Could cache the method for improved performance
            Class c = ProgramElementDoc.class;
            Method m = c.getMethod("position", null);
            Object sp = m.invoke(ped, null);
            if (sp != null) {
                for (int i = 0; i < indent; i++) outputFile.print(" ");
                outputFile.println("src=\"" + sp + "\"");
            }
        } catch (NoSuchMethodException e2) {
            System.err.println("Error: method \"position\" not found");
            e2.printStackTrace();
        } catch (IllegalAccessException e4) {
            System.err.println("Error: class not permitted to be instantiated");
            e4.printStackTrace();
        } catch (InvocationTargetException e5) {
            System.err.println("Error: method \"position\" could not be invoked");
            e5.printStackTrace();
        } catch (Exception e6) {
            System.err.println("Error: ");
            e6.printStackTrace();
        }
    }

    /**
     * Process the interfaces implemented by the class.
     *
     * @param ifaces An array of ClassDoc objects
     */
    public void processInterfaces(Type[] ifaces) {
        if (trace) System.out.println("PROCESSING INTERFACES, number=" + ifaces.length);
        for (int i = 0; i < ifaces.length; i++) {
            String ifaceName = buildEmittableTypeString(ifaces[i]);
            if (trace) System.out.println("PROCESSING INTERFACE: " + ifaceName);
            outputFile.println("    <implements name=\"" + ifaceName + "\"/>");
        }//for
    }//processInterfaces()
    
    /**
     * Process the constructors in the class.
     *
     * @param ct An array of ConstructorDoc objects
     */
    public void processConstructors(ConstructorDoc[] ct) {
        if (trace) System.out.println("PROCESSING CONSTRUCTORS, number=" + ct.length);
        for (int i = 0; i < ct.length; i++) {
            String ctorName = ct[i].name();
            if (trace) System.out.println("PROCESSING CONSTRUCTOR: " + ctorName);
            // Only save the shown elements
            if (!shownElement(ct[i], memberVisibilityLevel))
                continue;
            outputFile.print("    <constructor name=\"" + ctorName + "\"");

            Parameter[] params = ct[i].parameters();
            boolean first = true;
            if (params.length != 0) {
                outputFile.print(" type=\"");
                for (int j = 0; j < params.length; j++) {
                    if (!first)
                        outputFile.print(", ");
                    emitType(params[j].type());
                    first = false;
                }
                outputFile.println("\"");
            } else
                outputFile.println();
            addCommonModifiers(ct[i], 6);
            outputFile.println(">");
            
            // Generate the exception elements if any exceptions are thrown
            processExceptions(ct[i].thrownExceptions());

            addDocumentation(ct[i], 6);

            outputFile.println("    </constructor>");
        }//for
    }//processConstructors()
    
    /**
     * Process all exceptions thrown by a constructor or method.
     *
     * @param cd An array of ClassDoc objects
     */
    public void processExceptions(ClassDoc[] cd) {
        if (trace) System.out.println("PROCESSING EXCEPTIONS, number=" + cd.length);
        for (int i = 0; i < cd.length; i++) {
            String exceptionName = cd[i].name();
            if (trace) System.out.println("PROCESSING EXCEPTION: " + exceptionName);
            outputFile.print("      <exception name=\"" + exceptionName + "\" type=\"");
            emitType(cd[i]);
            outputFile.println("\"/>");
        }//for
    }//processExceptions()
    
    /**
     * Process the methods in the class.
     *
     * @param md An array of MethodDoc objects
     */
    public void processMethods(ClassDoc cd, MethodDoc[] md) {
        if (trace) System.out.println("PROCESSING " +cd.name()+" METHODS, number = " + md.length);
        for (int i = 0; i < md.length; i++) {
            String methodName = md[i].name();
            if (trace) System.out.println("PROCESSING METHOD: " + methodName);
            // Skip <init> and <clinit>
            if (methodName.startsWith("<"))
                continue;
            // Only save the shown elements
            if (!shownElement(md[i], memberVisibilityLevel))
                continue;
            outputFile.print("    <method name=\"" + methodName + "\"");
            com.sun.javadoc.Type retType = md[i].returnType();
            if (retType.qualifiedTypeName().compareTo("void") == 0) {
                // Don't add a return attribute if the return type is void
                outputFile.println();
            } else {
                outputFile.print(" return=\"");
                emitType(retType);
                outputFile.println("\"");
            }
            outputFile.print("      abstract=\"" + md[i].isAbstract() + "\"");
            outputFile.print(" native=\"" + md[i].isNative() + "\"");
            outputFile.println(" synchronized=\"" + md[i].isSynchronized() + "\"");
            addCommonModifiers(md[i], 6);
            outputFile.println(">");
            // Generate the parameter elements, if any
            Parameter[] params = md[i].parameters();
            for (int j = 0; j < params.length; j++) {
                outputFile.print("      <param name=\"" + params[j].name() + "\"");
                outputFile.print(" type=\"");
                emitType(params[j].type());
                outputFile.println("\"/>");
            }

            // Generate the exception elements if any exceptions are thrown
            processExceptions(md[i].thrownExceptions());

            addDocumentation(md[i], 6);

            outputFile.println("    </method>");
        }//for
    }//processMethods()

    /**
     * Process the fields in the class.
     *
     * @param fd An array of FieldDoc objects
     */
    public void processFields(FieldDoc[] fd) {
        if (trace) System.out.println("PROCESSING FIELDS, number=" + fd.length);
        for (int i = 0; i < fd.length; i++) {
            String fieldName = fd[i].name();
            if (trace) System.out.println("PROCESSING FIELD: " + fieldName);
            // Only save the shown elements
            if (!shownElement(fd[i], memberVisibilityLevel))
                continue;
            outputFile.print("    <field name=\"" + fieldName + "\"");
            outputFile.print(" type=\"");
            emitType(fd[i].type());
            outputFile.println("\"");
            outputFile.print("      transient=\"" + fd[i].isTransient() + "\"");
            outputFile.println(" volatile=\"" + fd[i].isVolatile() + "\"");
/* JDK 1.4 and later */
/*
            String value = fd[i].constantValueExpression();
            if (value != null)
                outputFile.println(" value=\"" + value + "\"");
*/
            addCommonModifiers(fd[i], 6);
            outputFile.println(">");

            addDocumentation(fd[i], 6);

            outputFile.println("    </field>");

        }//for
    }//processFields()
    
    /**
     * Emit the type name. Removed any prefixed warnings about ambiguity.
     * The type maybe an array.
     *
     * @param type A Type object.
     */
    public void emitType(com.sun.javadoc.Type type) {
        String name = buildEmittableTypeString(type);
        if (name == null)
            return;
        outputFile.print(name);
    }

    /**
     * Build the emittable type name. The type may be an array and/or
     * a generic type.
     *
     * @param type A Type object
     * @return The emittable type name
     */
    private String buildEmittableTypeString(com.sun.javadoc.Type type) {
        if (type == null) {
    	    return null;
        }
      // type.toString() returns the fully qualified name of the type
      // including the dimension and the parameters we just need to
      // escape the generic parameters brackets so that the XML
      // generated is correct
      String name = type.toString().
                         replaceAll("&", "&amp;").
                         replaceAll("<", "&lt;").
                         replaceAll(">", "&gt;");
      if (name.startsWith("<<ambiguous>>")) {
          name = name.substring(13);
      }
      return name;
    }    

    /**
     * Emit the XML header.
     */
    public void emitXMLHeader() {
        outputFile.println("<?xml version=\"1.0\" encoding=\"iso-8859-1\" standalone=\"no\"?>");
        outputFile.println("<!-- Generated by the JDiff Javadoc doclet -->");
        outputFile.println("<!-- (" + JDiff.jDiffLocation + ") -->");
        outputFile.println("<!-- on " + new Date() + " -->");
        outputFile.println();
/* No need for this any longer, since doc block text is in an CDATA element
        outputFile.println("<!-- XML Schema is used, but XHTML transitional DTD is needed for nbsp -->");
        outputFile.println("<!-- entity definitions etc.-->");
        outputFile.println("<!DOCTYPE api");
        outputFile.println("     PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\"");
        outputFile.println("     \"" + baseURI + "/TR/xhtml1/DTD/xhtml1-transitional.dtd\">");
*/
        outputFile.println("<api");
        outputFile.println("  xmlns:xsi='" + baseURI + "/2001/XMLSchema-instance'");
        outputFile.println("  xsi:noNamespaceSchemaLocation='api.xsd'");
        outputFile.println("  name=\"" + apiIdentifier + "\"");
        outputFile.println("  jdversion=\"" + JDiff.version + "\">");
        outputFile.println();
    }

    /**
     * Emit the XML footer.
     */
    public void emitXMLFooter() {
        outputFile.println();
        outputFile.println("</api>");
    }

    /** 
     * Determine if the program element is shown, according to the given 
     * level of visibility. 
     *
     * @param ped The given program element.
     * @param visLevel The desired visibility level; "public", "protected",
     *   "package" or "private". If null, only check for an exclude tag.
     * @return boolean Set if this element is shown.
     */
    public boolean shownElement(Doc doc, String visLevel) {
        // If a doc block contains @exclude or a similar such tag, 
        // then don't display it.
	if (doExclude && excludeTag != null && doc != null) {
            String rct = doc.getRawCommentText();
            if (rct != null && rct.indexOf(excludeTag) != -1) {
                return false;
	    }
	}  
	if (visLevel == null) {
	    return true;
	}
	ProgramElementDoc ped = null;
	if (doc instanceof ProgramElementDoc) {
	    ped = (ProgramElementDoc)doc;
	}
        if (visLevel.compareTo("private") == 0)
            return true;
        // Show all that is not private 
        if (visLevel.compareTo("package") == 0)
            return !ped.isPrivate();
        // Show all that is not private or package
        if (visLevel.compareTo("protected") == 0)
            return !(ped.isPrivate() || ped.isPackagePrivate());
        // Show all that is not private or package or protected,
        // i.e. all that is public
        if (visLevel.compareTo("public") == 0)
            return ped.isPublic();
        return false;
    } //shownElement()
    
    /** 
     * Strip out non-printing characters, replacing them with a character 
     * which will not change where the end of the first sentence is found.
     * This character is the hash mark, '&#035;'.
     */
    public String stripNonPrintingChars(String s, Doc doc) {
        if (!stripNonPrintables)
            return s;
        char[] sa = s.toCharArray();
        for (int i = 0; i < sa.length; i++) {
            char c = sa[i];
            // TODO still have an issue with Unicode: 0xfc in java.lang.String.toUpperCase comments
//            if (Character.isDefined(c))
            if (Character.isLetterOrDigit(c))
                continue;
            // There must be a better way that is still platform independent!
            if (c == ' ' ||
                c == '.' ||
                c == ',' ||
                c == '\r' ||
                c == '\t' ||
                c == '\n' ||
                c == '!' ||
                c == '?' ||
                c == ';' ||
                c == ':' ||
                c == '[' ||
                c == ']' ||
                c == '(' ||
                c == ')' ||
                c == '~' ||
                c == '@' ||
                c == '#' ||
                c == '$' ||
                c == '%' ||
                c == '^' ||
                c == '&' ||
                c == '*' ||
                c == '-' ||
                c == '=' ||
                c == '+' ||
                c == '_' ||
                c == '|' ||
                c == '\\' ||
                c == '/' ||
                c == '\'' ||
                c == '}' ||
                c == '{' ||
                c == '"' ||
                c == '<' ||
                c == '>' ||
                c == '`'
                )
                continue;
/* Doesn't seem to return the expected values?
            int val = Character.getNumericValue(c);
//            if (s.indexOf("which is also a test for non-printable") != -1)
//                System.out.println("** Char " + i + "[" + c + "], val =" + val); //DEBUG
            // Ranges from http://www.unicode.org/unicode/reports/tr20/
            // Should really replace 0x2028 and  0x2029 with <br/>
            if (val == 0x0 ||
                inRange(val, 0x2028, 0x2029) || 
                inRange(val, 0x202A, 0x202E) || 
                inRange(val, 0x206A, 0x206F) || 
                inRange(val, 0xFFF9, 0xFFFC) || 
                inRange(val, 0xE0000, 0xE007F)) {
                if (trace) {
                    System.out.println("Warning: changed non-printing character  " + sa[i] + " in " + doc.name()); 
                }
                sa[i] = '#';
            }
*/
            // Replace the non-printable character with a printable character
            // which does not change the end of the first sentence
            sa[i] = '#';
        }
        return new String(sa);
    }

    /** Return true if val is in the range [min|max], inclusive. */
    public boolean inRange(int val, int min, int max) {
        if (val < min)
            return false;
        if (val > max)
            return false;
        return true;
    }

    /** 
     * Add at least the first sentence from a doc block to the API. This is
     * used by the report generator if no comment is provided.
     * Need to make sure that HTML tags are not confused with XML tags.
     * This could be done by stuffing the &lt; character to another string
     * or by handling HTML in the parser. This second option seems neater. Note that
     * XML expects all element tags to have either a closing "/>" or a matching
     * end element tag. Due to the difficulties of converting incorrect HTML
     * to XHTML, the first option is used.
     */
    public void addDocumentation(ProgramElementDoc ped, int indent) {
        String rct = ((Doc)ped).getRawCommentText();
        if (rct != null) {
            rct = stripNonPrintingChars(rct, (Doc)ped);
            rct = rct.trim();
            if (rct.compareTo("") != 0 && 
                rct.indexOf(Comments.placeHolderText) == -1 &&
                rct.indexOf("InsertOtherCommentsHere") == -1) {
                int idx = endOfFirstSentence(rct);
                if (idx == 0)
                    return;
                for (int i = 0; i < indent; i++) outputFile.print(" ");
                outputFile.println("<doc>");
                for (int i = 0; i < indent; i++) outputFile.print(" ");
                String firstSentence = null;
                if (idx == -1)
                    firstSentence = rct;
                else
                    firstSentence = rct.substring(0, idx+1);
                boolean checkForAts = false;
                if (checkForAts && firstSentence.indexOf("@") != -1 && 
                    firstSentence.indexOf("@link") == -1) {
                    System.out.println("Warning: @ tag seen in comment: " + 
                                       firstSentence);
                }
                String firstSentenceNoTags = API.stuffHTMLTags(firstSentence);
                outputFile.println(firstSentenceNoTags);
                for (int i = 0; i < indent; i++) outputFile.print(" ");
                outputFile.println("</doc>");
            }
        }
    }

    /** 
     * Add at least the first sentence from a doc block for a package to the API. This is
     * used by the report generator if no comment is provided.
     * The default source tree may not include the package.html files, so
     * this may be unavailable in many cases.
     * Need to make sure that HTML tags are not confused with XML tags.
     * This could be done by stuffing the &lt; character to another string
     * or by handling HTML in the parser. This second option is neater. Note that
     * XML expects all element tags to have either a closing "/>" or a matching
     * end element tag.  Due to the difficulties of converting incorrect HTML
     * to XHTML, the first option is used.
     */
    public void addPkgDocumentation(RootDoc root, PackageDoc pd, int indent) {
        String rct = null;
        String filename = pd.name();
        try {
            // See if the source path was specified as part of the
            // options and prepend it if it was.
            String srcLocation = null;
            String[][] options = root.options();
            for (int opt = 0; opt < options.length; opt++) {
                if ((options[opt][0]).compareTo("-sourcepath") == 0) {
                    srcLocation = options[opt][1];
                    break;
                }
            }
            filename = filename.replace('.', JDiff.DIR_SEP.charAt(0));
            if (srcLocation != null) {
                // Make a relative location absolute 
                if (srcLocation.startsWith("..")) {
                    String curDir = System.getProperty("user.dir");
                    while (srcLocation.startsWith("..")) {
                        srcLocation = srcLocation.substring(3);
                        int idx = curDir.lastIndexOf(JDiff.DIR_SEP);
                        curDir = curDir.substring(0, idx+1);
                    }
                    srcLocation = curDir + srcLocation;
                }
                filename = srcLocation + JDiff.DIR_SEP + filename;
            }
            // Try both ".htm" and ".html"
            filename += JDiff.DIR_SEP + "package.htm";
            File f2 = new File(filename);
            if (!f2.exists()) {
                filename += "l";
            }
            FileInputStream f = new FileInputStream(filename);
            BufferedReader d = new BufferedReader(new InputStreamReader(f));
            String str = d.readLine();
 	    // Ignore everything except the lines between <body> elements
	    boolean inBody = false;
	    while(str != null) {
                if (!inBody) {
		    if (str.toLowerCase().trim().startsWith("<body")) {
			inBody = true;
		    }
		    str = d.readLine(); // Get the next line
		    continue; // Ignore the line
		} else {
		    if (str.toLowerCase().trim().startsWith("</body")) {
			inBody = false;
			continue; // Ignore the line
		    }
		}
                if (rct == null)
                    rct = str + "\n";
                else
                    rct += str + "\n";
                str = d.readLine();
            }
        }  catch(java.io.FileNotFoundException e) {
            // If it doesn't exist, that's fine
            if (trace)
                System.out.println("No package level documentation file at '" + filename + "'");
        } catch(java.io.IOException e) {
            System.out.println("Error reading file \"" + filename + "\": " + e.getMessage());
            System.exit(5);
        }     
        if (rct != null) {
            rct = stripNonPrintingChars(rct, (Doc)pd);
            rct = rct.trim();
            if (rct.compareTo("") != 0 &&
                rct.indexOf(Comments.placeHolderText) == -1 &&
                rct.indexOf("InsertOtherCommentsHere") == -1) {
                int idx = endOfFirstSentence(rct);
                if (idx == 0)
                    return;
                for (int i = 0; i < indent; i++) outputFile.print(" ");
                outputFile.println("<doc>");
                for (int i = 0; i < indent; i++) outputFile.print(" ");
                String firstSentence = null;
                if (idx == -1)
                    firstSentence = rct;
                else
                    firstSentence = rct.substring(0, idx+1);
                String firstSentenceNoTags = API.stuffHTMLTags(firstSentence);
                outputFile.println(firstSentenceNoTags);
                for (int i = 0; i < indent; i++) outputFile.print(" ");
                outputFile.println("</doc>");
            }
        }
    }

    /** 
     * Find the index of the end of the first sentence in the given text,
     * when writing out to an XML file.
     * This is an extended version of the algorithm used by the DocCheck 
     * Javadoc doclet. It checks for @tags too.
     *
     * @param text The text to be searched.
     * @return The index of the end of the first sentence. If there is no
     *         end, return -1. If there is no useful text, return 0.
     *         If the whole doc block comment is wanted (default), return -1.
     */
    public static int endOfFirstSentence(String text) {
        return endOfFirstSentence(text, true);
    }

    /** 
     * Find the index of the end of the first sentence in the given text.
     * This is an extended version of the algorithm used by the DocCheck 
     * Javadoc doclet. It checks for &#064;tags too.
     *
     * @param text The text to be searched.
     * @param writingToXML Set to true when writing out XML.
     * @return The index of the end of the first sentence. If there is no
     *         end, return -1. If there is no useful text, return 0.
     *         If the whole doc block comment is wanted (default), return -1.
     */
    public static int endOfFirstSentence(String text, boolean writingToXML) {
        if (saveAllDocs && writingToXML)
            return -1;
	int textLen = text.length();
	if (textLen == 0)
	    return 0;
        int index = -1;
        // Handle some special cases
        int fromindex = 0;
        int ellipsis = text.indexOf(". . ."); // Handles one instance of this
        if (ellipsis != -1)
            fromindex = ellipsis + 5;
        // If the first non-whitespace character is an @, go beyond it
        int i = 0;
        while (i < textLen && text.charAt(i) == ' ') {
            i++;
        }
        if (text.charAt(i) == '@' && fromindex < textLen-1)
            fromindex = i + 1;
        // Use the brute force approach.
        index = minIndex(index, text.indexOf("? ", fromindex));
        index = minIndex(index, text.indexOf("?\t", fromindex));
        index = minIndex(index, text.indexOf("?\n", fromindex));
        index = minIndex(index, text.indexOf("?\r", fromindex));
        index = minIndex(index, text.indexOf("?\f", fromindex));
        index = minIndex(index, text.indexOf("! ", fromindex));
        index = minIndex(index, text.indexOf("!\t", fromindex));
        index = minIndex(index, text.indexOf("!\n", fromindex));
        index = minIndex(index, text.indexOf("!\r", fromindex));
        index = minIndex(index, text.indexOf("!\f", fromindex));
        index = minIndex(index, text.indexOf(". ", fromindex));
        index = minIndex(index, text.indexOf(".\t", fromindex));
        index = minIndex(index, text.indexOf(".\n", fromindex));
        index = minIndex(index, text.indexOf(".\r", fromindex));
        index = minIndex(index, text.indexOf(".\f", fromindex));
        index = minIndex(index, text.indexOf("@param", fromindex));
        index = minIndex(index, text.indexOf("@return", fromindex));
        index = minIndex(index, text.indexOf("@throw", fromindex));
        index = minIndex(index, text.indexOf("@serial", fromindex));
        index = minIndex(index, text.indexOf("@exception", fromindex));
        index = minIndex(index, text.indexOf("@deprecate", fromindex));
        index = minIndex(index, text.indexOf("@author", fromindex));
        index = minIndex(index, text.indexOf("@since", fromindex));
        index = minIndex(index, text.indexOf("@see", fromindex));
        index = minIndex(index, text.indexOf("@version", fromindex));
        if (doExclude && excludeTag != null)
            index = minIndex(index, text.indexOf(excludeTag));
        index = minIndex(index, text.indexOf("@vtexclude", fromindex));
        index = minIndex(index, text.indexOf("@vtinclude", fromindex));
        index = minIndex(index, text.indexOf("<p>", 2)); // Not at start
        index = minIndex(index, text.indexOf("<P>", 2)); // Not at start
        index = minIndex(index, text.indexOf("<blockquote", 2));  // Not at start
        index = minIndex(index, text.indexOf("<pre", fromindex)); // May contain anything!
        // Avoid the char at the start of a tag in some cases
        if (index != -1 &&  
            (text.charAt(index) == '@' || text.charAt(index) == '<')) {
            if (index != 0)
                index--;
        }
        
/* Not used for jdiff, since tags are explicitly checked for above.
        // Look for a sentence terminated by an HTML tag.
        index = minIndex(index, text.indexOf(".<", fromindex));
        if (index == -1) {
            // If period-whitespace etc was not found, check to see if
            // last character is a period,
            int endIndex = text.length()-1;
            if (text.charAt(endIndex) == '.' ||
                text.charAt(endIndex) == '?' ||
                text.charAt(endIndex) == '!') 
                index = endIndex;
        }
*/
        return index;
    }
    
    /**
     * Return the minimum of two indexes if > -1, and return -1
     * only if both indexes = -1.
     * @param i an int index
     * @param j an int index
     * @return an int equal to the minimum index > -1, or -1
     */
    public static int minIndex(int i, int j) {
        if (i == -1) return j;
        if (j == -1) return i;
        return Math.min(i,j);
    }
    
    /** 
     * The name of the file where the XML representing the API will be 
     * stored. 
     */
    public static String outputFileName = null;

    /** 
     * The identifier of the API being written out in XML, e.g. 
     * &quotSuperProduct 1.3&quot;. 
     */
    public static String apiIdentifier = null;

    /** 
     * The file where the XML representing the API will be stored. 
     */
    private static PrintWriter outputFile = null;
    
    /** 
     * The name of the directory where the XML representing the API will be 
     * stored. 
     */
    public static String outputDirectory = null;

    /** 
     * Do not display a class  with a lower level of visibility than this. 
     * Default is to display all public and protected classes.
     */
    public static String classVisibilityLevel = "protected";

    /** 
     * Do not display a member with a lower level of visibility than this. 
     * Default is to display all public and protected members 
     * (constructors, methods, fields).
     */
    public static String memberVisibilityLevel = "protected";

    /** 
     * If set, then save the entire contents of a doc block comment in the 
     * API file. If not set, then just save the first sentence. Default is 
     * that this is set.
     */
    public static boolean saveAllDocs = true;

    /** 
     * If set, exclude program elements marked with whatever the exclude tag
     * is specified as, e.g. "@exclude".
     */
    public static boolean doExclude = false;

    /** 
     * Exclude program elements marked with this String, e.g. "@exclude".
     */
    public static String excludeTag = null;

    /** 
     * The base URI for locating necessary DTDs and Schemas. By default, this 
     * is "http://www.w3.org". A typical value to use local copies of DTD files
     * might be "file:///C:/jdiff/lib"
     */
    public static String baseURI = "http://www.w3.org";

    /** 
     * If set, then strip out non-printing characters from documentation.
     * Default is that this is set.
     */
    static boolean stripNonPrintables = true;

    /** 
     * If set, then add the information about the source file and line number
     * which is available in J2SE1.4. Default is that this is not set.
     */
    static boolean addSrcInfo = false;

    /** 
     * If set, scan classes with no packages. 
     * If the source is  a jar file this may duplicates classes, so 
     * disable it using the -packagesonly option. Default is that this is 
     * not set.
     */
    static boolean packagesOnly = false;

    /** Set to enable increased logging verbosity for debugging. */
    private static boolean trace = false;

} //RootDocToXML
