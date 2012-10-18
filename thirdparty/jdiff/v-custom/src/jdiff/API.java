package jdiff;

import java.io.*;
import java.util.*;

/**
 * The internal representation of an API. 
 * 
 * RootDoc could have been used for representing this, but 
 * you cannot serialize a RootDoc object - see
 *  http://developer.java.sun.com/developer/bugParade/bugs/4125581.html
 * You might be able use Javadoc.Main() to create another RootDoc, but the 
 * methods are package private. You can run javadoc in J2SE1.4, see:
 *  http://java.sun.com/j2se/1.4/docs/tooldocs/javadoc/standard-doclet.html#runningprogrammatically
 * but you still can't get the RootDoc object.
 *
 * The advantage of writing out an XML representation of each API is that
 * later runs of JDiff don't have to have Javadoc scan all the files again,
 * a possibly lengthy process. XML also permits other source code in 
 * languages other than Java to be scanned to produce XML, and then versions
 * of JDiff can be used to create documents describing the difference in those
 * APIs.
 * 
 * See the file LICENSE.txt for copyright details.
 * @author Matthew Doar, mdoar@pobox.com
 */
public class API {

    /** 
     * The list of all the top-level packages. 
     * Each package contains classes, each class contains members, and so on.
     */
    public List packages_; // PackageAPI[]

    /** 
     * The list of all the classes. 
     * This is used to generate the methods and fields which are inherited,
     * rather than storing them in the XML file.
     */
    public Hashtable classes_;

    /** 
     * The String which identifies this API, e.g. &quotSuperProduct 1.3&quot;.
     */
    public String name_ = null;

    /** The current package being added to during parsing. */
    public PackageAPI currPkg_ = null;
    /** The current class being added to during parsing. */
    public ClassAPI currClass_ = null;
    /** The current constructor being added to during parsing. */
    public ConstructorAPI currCtor_ = null;
    /** The current method being added to during parsing. */
    public MethodAPI currMethod_ = null;
    /** The current field being added to during parsing. */
    public FieldAPI currField_ = null;

    /** Default constructor. */
    public API() {
        packages_ = new ArrayList(); //PackageAPI[]
        classes_ = new Hashtable(); //ClassAPI
    }   
  
//
// Methods to display the contents of an API object.
//

    /** Amount by which to increment each indentation. */
    public static final int indentInc = 2;

    /** Display the contents of the API object. */
    public void dump() {
        int indent = 0;
        Iterator iter = packages_.iterator();
        while (iter.hasNext()) {
            dumpPackage((PackageAPI)(iter.next()), indent);
        }
    }

    /** 
     * Display the contents of a PackageAPI object.
     *
     * @param pkg The given PackageAPI object.
     * @param indent The number of spaces to indent the output.
     */
    public void dumpPackage(PackageAPI pkg, int indent) {
        for (int i = 0; i < indent; i++) System.out.print(" ");
        System.out.println("Package Name: " + pkg.name_);
        Iterator iter = pkg.classes_.iterator();
        while (iter.hasNext()) {
            dumpClass((ClassAPI)(iter.next()), indent + indentInc);
        }
        // Display documentation
        if (pkg.doc_ != null) {
            System.out.print("Package doc block:");
            System.out.println("\"" + pkg.doc_ + "\"");
        }
    }

    /** 
     * Display the contents of a ClassAPI object.
     *
     * @param c The given ClassAPI object.
     * @param indent The number of spaces to indent the output.
     */
    public static void dumpClass(ClassAPI c, int indent) {
        for (int i = 0; i < indent; i++) System.out.print(" ");
        if (c.isInterface_)
            System.out.println("Interface name: " + c.name_);
        else
            System.out.println("Class Name: " + c.name_);
        if (c.extends_ != null) {
            for (int i = 0; i < indent; i++) System.out.print(" ");
            System.out.println("Extends: " + c.extends_);
        }
        if (c.implements_.size() != 0) {
            for (int i = 0; i < indent; i++) System.out.print(" ");
            System.out.println("Implements: ");
            Iterator iter = c.implements_.iterator();
            while (iter.hasNext()) {
                String interfaceImpl = (String)(iter.next());
                for (int i = 0; i < indent + 2; i++) System.out.print(" ");
                System.out.println("  " + interfaceImpl);
            }
        }
        // Dump modifiers specific to a class
        if (c.isAbstract_)
            System.out.print("abstract ");
        // Dump modifiers common to all
        dumpModifiers(c.modifiers_, indent);
        // Dump ctors
        Iterator iter = c.ctors_.iterator();
        while (iter.hasNext()) {
            dumpCtor((ConstructorAPI)(iter.next()), indent + indentInc);
        }
        // Dump methods
        iter = c.methods_.iterator();
        while (iter.hasNext()) {
            dumpMethod((MethodAPI)(iter.next()), indent + indentInc);
        }
        // Dump fields
        iter = c.fields_.iterator();
        while (iter.hasNext()) {
            dumpField((FieldAPI)(iter.next()), indent + indentInc);
        }
        // Display documentation
        if (c.doc_ != null) {
            System.out.print("Class doc block:");
            System.out.println("\"" + c.doc_ + "\"");
        } else
            System.out.println();
    }

    /** 
     * Display the contents of the Modifiers object.
     *
     * @param c The given Modifiers object.
     * @param indent The number of spaces to indent the output.
     */
    public static void dumpModifiers(Modifiers m, int indent) {
        for (int i = 0; i < indent; i++) System.out.print(" ");
        if (m.isStatic)
            System.out.print("static ");
        if (m.isFinal)
            System.out.print("final ");
        if (m.visibility != null)
            System.out.print("visibility = " + m.visibility + " ");
        // Flush the line
        System.out.println();
    }

    /** 
     * Display the contents of a constructor.
     *
     * @param c The given constructor object.
     * @param indent The number of spaces to indent the output.
     */
    public static void dumpCtor(ConstructorAPI c, int indent) {
        for (int i = 0; i < indent; i++) System.out.print(" ");
        System.out.println("Ctor type: " + c.type_);
        // Display exceptions 
        System.out.print("exceptions: " + c.exceptions_ + " ");
        // Dump modifiers common to all
        dumpModifiers(c.modifiers_, indent);
        // Display documentation
        if (c.doc_ != null) {
            System.out.print("Ctor doc block:");
            System.out.println("\"" + c.doc_ + "\"");
        }
    }

    /** 
     * Display the contents of a MethodAPI object.
     *
     * @param m The given MethodAPI object.
     * @param indent The number of spaces to indent the output.
     */
    public static void dumpMethod(MethodAPI m, int indent) {
        if (m.inheritedFrom_ != null)
            return;
        for (int i = 0; i < indent; i++) System.out.print(" ");
        System.out.print("Method Name: " + m.name_);
        if (m.inheritedFrom_ != null)
            System.out.println(", inherited from: " + m.inheritedFrom_);
        if (m.returnType_ != null)
            System.out.println(", return type: " + m.returnType_);
        else
            System.out.println();
        // Dump modifiers specific to a method
        if (m.isAbstract_)
            System.out.print("abstract ");
        if (m.isNative_)
            System.out.print("native ");
        if (m.isSynchronized_)
            System.out.print("synchronized ");
        // Display exceptions 
        System.out.print("exceptions: " + m.exceptions_ + " ");
        // Dump modifiers common to all
        dumpModifiers(m.modifiers_, indent);
      
        Iterator iter = m.params_.iterator();
        while (iter.hasNext()) {
            dumpParam((ParamAPI)(iter.next()), indent + indentInc);
        }
        // Display documentation
        if (m.doc_ != null) {
            System.out.print("Method doc block:");
            System.out.println("\"" + m.doc_ + "\"");
        }
    }

    /** 
     * Display the contents of a field.
     * Does not show inherited fields.
     *
     * @param f The given field object.
     * @param indent The number of spaces to indent the output.
     */
    public static void dumpField(FieldAPI f, int indent) {
        if (f.inheritedFrom_ != null)
            return;
        for (int i = 0; i < indent; i++) System.out.print(" ");
        System.out.println("Field Name: " + f.name_ + ", type: " + f.type_);
        if (f.inheritedFrom_ != null)
            System.out.println(", inherited from: " + f.inheritedFrom_);
        if (f.isTransient_)
            System.out.print("transient ");
        if (f.isVolatile_)
            System.out.print("volatile ");
        // Dump modifiers common to all
        dumpModifiers(f.modifiers_, indent);
        // Display documentation
        if (f.doc_ != null)
            System.out.print("Field doc block:");
            System.out.println("\"" + f.doc_ + "\"");
    }

    /** 
     * Display the contents of a parameter.
     *
     * @param p The given parameter object.
     * @param indent The number of spaces to indent the output.
     */
    public static void dumpParam(ParamAPI p, int indent) {
        for (int i = 0; i < indent; i++) System.out.print(" ");
        System.out.println("Param Name: " + p.name_ + ", type: " + p.type_);
    }

    /** 
     * Convert all HTML tags to text by placing them inside a CDATA element.
     * Characters still have to be valid Unicode characters as defined by the 
     * parser.
     */
    public static String stuffHTMLTags(String htmlText) {
        if (htmlText.indexOf("]]>") != -1) {
            System.out.println("Warning: illegal string ]]> found in text. Ignoring the comment.");
            return "";
        }
        return "<![CDATA[" + htmlText + "]]>";
    }

    /**
     * Convert all HTML tags to text by stuffing text into the HTML tag
     * to stop it being an HTML or XML tag. E.g. &quot;<code>foo</code>&quot;
     * becomes &quot;lEsS_tHaNcode>foolEsS_tHaN/code>&quot;. Replace all &lt; 
     * characters
     * with the string "lEsS_tHaN". Also replace &amp; character with the  
     * string "aNd_cHaR" to avoid text entities. Also replace &quot; 
     * character with the  
     * string "qUoTe_cHaR".
     */
    public static String hideHTMLTags(String htmlText) {
        StringBuffer sb = new StringBuffer(htmlText);
        int i = 0;
        while (i < sb.length()) {
            if (sb.charAt(i) == '<') {
                sb.setCharAt(i ,'l');
                sb.insert(i+1, "EsS_tHaN");
            } else if (sb.charAt(i) == '&') {
                sb.setCharAt(i ,'a');
                sb.insert(i+1, "Nd_cHaR");
            } else if (sb.charAt(i) == '"') {
                sb.setCharAt(i ,'q');
                sb.insert(i+1, "uote_cHaR");
            }
            i++;
        }
        return sb.toString();
    }

    /** 
     * Convert text with stuffed HTML tags ("lEsS_tHaN", etc) into HTML text.
     */
    public static String showHTMLTags(String text) {
        StringBuffer sb = new StringBuffer(text);
        StringBuffer res = new StringBuffer();
        int len = sb.length();
        res.setLength(len);
        int i = 0;
        int resIdx = 0;
        while (i < len) {
            char c = sb.charAt(i);
            if (len - i > 8 && c == 'l' && 
                sb.charAt(i+1) == 'E' &&
                sb.charAt(i+2) == 's' &&
                sb.charAt(i+3) == 'S' &&
                sb.charAt(i+4) == '_' &&
                sb.charAt(i+5) == 't' &&
                sb.charAt(i+6) == 'H' &&
                sb.charAt(i+7) == 'a' &&
                sb.charAt(i+8) == 'N') {
                res.setCharAt(resIdx ,'<');
                i += 8;
            } else if (len - i > 9 && c == 'q' && 
                sb.charAt(i+1) == 'U' &&
                sb.charAt(i+2) == 'o' &&
                sb.charAt(i+3) == 'T' &&
                sb.charAt(i+4) == 'e' &&
                sb.charAt(i+5) == '_' &&
                sb.charAt(i+6) == 'c' &&
                sb.charAt(i+7) == 'H' &&
                sb.charAt(i+8) == 'a' &&
                sb.charAt(i+9) == 'R') {
                res.setCharAt(resIdx ,'"');
                i += 9;
            } else if (len - i > 7 && c == 'a' && 
                sb.charAt(i+1) == 'N' &&
                sb.charAt(i+2) == 'd' &&
                sb.charAt(i+3) == '_' &&
                sb.charAt(i+4) == 'c' &&
                sb.charAt(i+5) == 'H' &&
                sb.charAt(i+6) == 'a' &&
                sb.charAt(i+7) == 'R') {
                res.setCharAt(resIdx ,'&');
                i += 7;
            } else {
                res.setCharAt(resIdx, c);
            }
            i++;
            resIdx++;
        }
        res.setLength(resIdx);
        return res.toString();
    }

    /** 
     * <b>NOT USED</b>. 
     *
     * Replace all instances of <p> with <p/>. Just for the small number
     * of HMTL tags which don't require a matching end tag.
     * Also make HTML conform to the simple HTML requirements such as 
     * no double hyphens. Double hyphens are replaced by - and the character
     * entity for a hyphen.
     *
     * Cases where this fails and has to be corrected in the XML by hand: 
     *  Attributes' values missing their double quotes , e.g. size=-2
     *  Mangled HTML tags e.g. &lt;ttt> 
     *
     * <p><b>NOT USED</b>. There is often too much bad HTML in
     * doc blocks to try to handle every case correctly. Better just to
     * stuff the *lt; and &amp: characters with stuffHTMLTags(). Though
     * the resulting XML is not as elegant, it does the job with less
     * intervention by the user.
     */
    public static String convertHTMLTagsToXHTML(String htmlText) {
        StringBuffer sb = new StringBuffer(htmlText);
        int i = 0;
        boolean inTag = false;
        String tag = null;
        // Needs to re-evaluate this length at each loop
        while (i < sb.length()) {
            char c = sb.charAt(i);
            if (inTag) {
                if (c == '>') {
                    // OPTION Could fail at or fix some errorneous tags here
                    // Make the best guess as to whether this tag is terminated
                    if (Comments.isMinimizedTag(tag) &&
                        htmlText.indexOf("</" + tag + ">", i) == -1)
                        sb.insert(i, "/");
                    inTag = false;
                } else {
                    // OPTION could also make sure that attribute values are
                    // surrounded by quotes.
                    tag += c;
                }
            }
            if (c == '<') {
                inTag = true;
                tag = "";
            }
            // -- is not allowed in XML, but !-- is part of an comment,
            // and --> is also part of a comment
            if (c == '-' && i > 0 && sb.charAt(i-1) == '-') {
                if (!(i > 1 && sb.charAt(i-2) == '!')) { 
                    sb.setCharAt(i, '&');
                    sb.insert(i+1, "#045;");
                    i += 5;
                }
            }
            i++;
        }
        if (inTag) {
            // Oops. Someone forgot to close their HTML tag, e.g. "<code."
            // Close it for them.
            sb.insert(i, ">");
        }
        return sb.toString();
    }
}
