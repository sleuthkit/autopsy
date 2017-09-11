package jdiff;

import java.io.*;
import java.util.*;

/* For SAX parsing in APIHandler */
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Handle the parsing of an XML file and the generation of an API object.
 *
 * See the file LICENSE.txt for copyright details.
 * @author Matthew Doar, mdoar@pobox.com
 */
class APIHandler extends DefaultHandler {

    /** The API object which is populated from the XML file. */
    public API api_;

    /** Default constructor. */
    public APIHandler(API api, boolean createGlobalComments) {
        api_ = api;
        createGlobalComments_ = createGlobalComments;
        tagStack = new LinkedList();
    }   

    /** If set, then check that each comment is a sentence. */
    public static boolean checkIsSentence = false;

    /** 
     * Contains the name of the current package element type
     * where documentation is being added. Also used as the level
     * at which to add documentation into an element, i.e. class-level
     * or package-level.
     */
    private String currentElement = null;

    /** If set, then create the global list of comments. */
    private boolean createGlobalComments_ = false;

    /** Set if inside a doc element. */
    private boolean inDoc = false;

    /** The current comment text being assembled. */
    private String currentText = null;

    /** The current text from deprecation, null if empty. */
    private String currentDepText = null;

    /** 
     * The stack of SingleComment objects awaiting the comment text 
     * currently being assembled. 
     */
    private LinkedList tagStack = null;

    /** Called at the start of the document. */
    public void startDocument() {
    }
    
    /** Called when the end of the document is reached. */
    public void endDocument() {
        if (trace)
            api_.dump();
        System.out.println(" finished");
    }

    /** Called when a new element is started. */
    public void startElement(java.lang.String uri, java.lang.String localName,
                             java.lang.String qName, Attributes attributes) {
	 // The change to JAXP compliance produced this change.
	if (localName.equals(""))
	    localName = qName;
        if (localName.compareTo("api") == 0) {
            String apiName = attributes.getValue("name");
            String version = attributes.getValue("jdversion"); // Not used yet
            XMLToAPI.nameAPI(apiName);
        } else if (localName.compareTo("package") == 0) {
            currentElement = localName;
            String pkgName = attributes.getValue("name");
            XMLToAPI.addPackage(pkgName);
        } else if (localName.compareTo("class") == 0) {
            currentElement = localName;
            String className = attributes.getValue("name");
            String parentName = attributes.getValue("extends");
            boolean isAbstract = false;
            if (attributes.getValue("abstract").compareTo("true") == 0)
                isAbstract = true;
            XMLToAPI.addClass(className, parentName, isAbstract, getModifiers(attributes));
        } else if (localName.compareTo("interface") == 0) {
            currentElement = localName;
            String className = attributes.getValue("name");
            String parentName = attributes.getValue("extends");
            boolean isAbstract = false;
            if (attributes.getValue("abstract").compareTo("true") == 0)
                isAbstract = true;
            XMLToAPI.addInterface(className, parentName, isAbstract, getModifiers(attributes));
        } else if (localName.compareTo("implements") == 0) {
            String interfaceName = attributes.getValue("name");
            XMLToAPI.addImplements(interfaceName);
        } else if (localName.compareTo("constructor") == 0) {
            currentElement = localName;
            String ctorType = attributes.getValue("type");
            XMLToAPI.addCtor(ctorType, getModifiers(attributes));
        } else if (localName.compareTo("method") == 0) {
            currentElement = localName;
            String methodName = attributes.getValue("name");
            String returnType = attributes.getValue("return");
            boolean isAbstract = false;
            if (attributes.getValue("abstract").compareTo("true") == 0)
                isAbstract = true;
            boolean isNative = false;
            if (attributes.getValue("native").compareTo("true") == 0)
                isNative = true;
            boolean isSynchronized = false;
            if (attributes.getValue("synchronized").compareTo("true") == 0)
                isSynchronized = true;
            XMLToAPI.addMethod(methodName, returnType, isAbstract, isNative, 
                               isSynchronized, getModifiers(attributes));
        } else if (localName.compareTo("field") == 0) {
            currentElement = localName;
            String fieldName = attributes.getValue("name");
            String fieldType = attributes.getValue("type");
            boolean isTransient = false;
            if (attributes.getValue("transient").compareTo("true") == 0)
                isTransient = true;
            boolean isVolatile = false;
            if (attributes.getValue("volatile").compareTo("true") == 0)
                isVolatile = true;
            String value = attributes.getValue("value");
            XMLToAPI.addField(fieldName, fieldType, isTransient, isVolatile, 
                              value, getModifiers(attributes));
        } else if (localName.compareTo("param") == 0) {
            String paramName = attributes.getValue("name");
            String paramType = attributes.getValue("type");
            XMLToAPI.addParam(paramName, paramType);
        } else if (localName.compareTo("exception") == 0) {
            String paramName = attributes.getValue("name");
            String paramType = attributes.getValue("type");
            XMLToAPI.addException(paramName, paramType, currentElement);
        } else if (localName.compareTo("doc") == 0) {
            inDoc = true;
            currentText = null;
        } else {
            if (inDoc) {
                // Start of an element, probably an HTML element
                addStartTagToText(localName, attributes);
            } else {
                System.out.println("Error: unknown element type: " + localName);
                System.exit(-1);
            }
        }
    }
    
    /** Called when the end of an element is reached. */
    public void endElement(java.lang.String uri, java.lang.String localName, 
                           java.lang.String qName) {
	if (localName.equals(""))
	    localName = qName;
        // Deal with the end of doc blocks
        if (localName.compareTo("doc") == 0) {
            inDoc = false;
            // Add the assembled comment text to the appropriate current
            // program element, as determined by currentElement.
            addTextToComments();
        } else if (inDoc) {
            // An element was found inside the HTML text
            addEndTagToText(localName);
        } else if (currentElement.compareTo("constructor") == 0 && 
                   localName.compareTo("constructor") == 0) {
            currentElement = "class";
        } else if (currentElement.compareTo("method") == 0 && 
                   localName.compareTo("method") == 0) {
            currentElement = "class";
        } else if (currentElement.compareTo("field") == 0 && 
                   localName.compareTo("field") == 0) {
            currentElement = "class";
        } else if (currentElement.compareTo("class") == 0 ||
                   currentElement.compareTo("interface") == 0) {
            // Feature request 510307 and bug 517383: duplicate comment ids.
            // The end of a member element leaves the currentElement at the 
            // "class" level, but the next class may in fact be an interface
            // and so the currentElement here will be "interface".
            if (localName.compareTo("class") == 0 || 
                localName.compareTo("interface") == 0) {
                currentElement = "package";
            }
        }
    }

    /** Called to process text. */
    public void characters(char[] ch, int start, int length) {
         if (inDoc) {
            String chunk = new String(ch, start, length);
            if (currentText == null)
                currentText = chunk;
            else
                currentText += chunk;
         }
    }

    /** 
     * Trim the current text, check it is a sentence and add it to the
     * current program element. 
     */
    public void addTextToComments() {
        // Eliminate any whitespace at each end of the text.
        currentText = currentText.trim();        
        // Convert any @link tags to HTML links.
        if (convertAtLinks) {
            currentText = Comments.convertAtLinks(currentText, currentElement, 
                                                  api_.currPkg_, api_.currClass_);
        }
        // Check that it is a sentence
        if (checkIsSentence && !currentText.endsWith(".") && 
            currentText.compareTo(Comments.placeHolderText) != 0) {
            System.out.println("Warning: text of comment does not end in a period: " + currentText);
        }
        // The construction of the commentID assumes that the 
        // documentation is the final element to be parsed. The format matches
        // the format used in the report generator to look up comments in the
        // the existingComments object.
        String commentID = null;
        // Add this comment to the current API element.
        if (currentElement.compareTo("package") == 0) {
            api_.currPkg_.doc_ = currentText;
            commentID = api_.currPkg_.name_;
        } else if (currentElement.compareTo("class") == 0 ||
                   currentElement.compareTo("interface") == 0) {
            api_.currClass_.doc_ = currentText;
            commentID = api_.currPkg_.name_ + "." + api_.currClass_.name_;
        } else if (currentElement.compareTo("constructor") == 0) {
            api_.currCtor_.doc_ = currentText;
            commentID = api_.currPkg_.name_ + "." + api_.currClass_.name_ +
                ".ctor_changed(";
            if (api_.currCtor_.type_.compareTo("void") == 0)
                commentID = commentID + ")";
            else
                commentID = commentID + api_.currCtor_.type_ + ")";
        } else if (currentElement.compareTo("method") == 0) {
            api_.currMethod_.doc_ = currentText;
            commentID = api_.currPkg_.name_ + "." + api_.currClass_.name_ +
                "." + api_.currMethod_.name_ + "_changed(" + 
                api_.currMethod_.getSignature() + ")";
        } else if (currentElement.compareTo("field") == 0) {
            api_.currField_.doc_ = currentText;
            commentID = api_.currPkg_.name_ + "." + api_.currClass_.name_ +
                "." + api_.currField_.name_;
        }            
        // Add to the list of possible comments for use when an
        // element has changed (not removed or added).
        if (createGlobalComments_ && commentID != null) {
            String ct = currentText;
            // Use any deprecation text as the possible comment, ignoring 
            // any other comment text.
            if (currentDepText != null) {
                ct = currentDepText;
                currentDepText = null; // Never reuse it. Bug 469794
            }
            String ctOld = (String)(Comments.allPossibleComments.put(commentID, ct));
            if (ctOld != null) {
                System.out.println("Error: duplicate comment id: " + commentID);
                System.exit(5);
            }
        }
    }

    /** 
     * Add the start tag to the current comment text. 
     */
    public void addStartTagToText(String localName, Attributes attributes) {
        // Need to insert the HTML tag into the current text
        String currentHTMLTag = localName;
        // Save the tag in a stack
        tagStack.add(currentHTMLTag);
        String tag = "<" + currentHTMLTag;
        // Now add all the attributes into the current text
        int len = attributes.getLength();
        for (int i = 0; i < len; i++) {
            String name = attributes.getLocalName(i);
            String value = attributes.getValue(i);
            tag += " " + name + "=\"" + value+ "\"";
        }

        // End the tag
        if (Comments.isMinimizedTag(currentHTMLTag)) {
            tag += "/>";
        } else {
            tag += ">";
        }
        // Now insert the HTML tag into the current text
        if (currentText == null)
            currentText = tag;
        else
            currentText += tag;
    }

    /** 
     * Add the end tag to the current comment text. 
     */
    public void addEndTagToText(String localName) {
        // Close the current HTML tag
        String currentHTMLTag = (String)(tagStack.removeLast());
        if (!Comments.isMinimizedTag(currentHTMLTag))
            currentText += "</" + currentHTMLTag + ">";
    }

    /** Extra modifiers which are common to all program elements. */
    public Modifiers getModifiers(Attributes attributes) {
        Modifiers modifiers = new Modifiers();
        modifiers.isStatic = false;
        if (attributes.getValue("static").compareTo("true") == 0)
            modifiers.isStatic = true;
        modifiers.isFinal = false;
        if (attributes.getValue("final").compareTo("true") == 0)
            modifiers.isFinal = true;
        modifiers.isDeprecated = false;
        String cdt = attributes.getValue("deprecated");
        if (cdt.compareTo("not deprecated") == 0) {
            modifiers.isDeprecated = false;
            currentDepText = null;
        } else if (cdt.compareTo("deprecated, no comment") == 0) {
            modifiers.isDeprecated = true;
            currentDepText = null;
        } else {
            modifiers.isDeprecated = true;
            currentDepText = API.showHTMLTags(cdt);
        }
        modifiers.visibility = attributes.getValue("visibility");
        return modifiers;
    }

    public void warning(SAXParseException e) {
        System.out.println("Warning (" + e.getLineNumber() + "): parsing XML API file:" + e);
        e.printStackTrace();
    }

    public void error(SAXParseException e) {
        System.out.println("Error (" + e.getLineNumber() + "): parsing XML API file:" + e);
        e.printStackTrace();
        System.exit(1);
    }
    
    public void fatalError(SAXParseException e) {
        System.out.println("Fatal Error (" + e.getLineNumber() + "): parsing XML API file:" + e);
        e.printStackTrace();
        System.exit(1);
    }    

    /** 
     * If set, then attempt to convert @link tags to HTML links. 
     * A few of the HTML links may be broken links.
     */
    private static boolean convertAtLinks = true;

    /** Set to enable increased logging verbosity for debugging. */
    private static boolean trace = false;

}
