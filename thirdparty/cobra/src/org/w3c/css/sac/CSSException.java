/*
 * Copyright (c) 1999 World Wide Web Consortium
 * (Massachusetts Institute of Technology, Institut National de Recherche
 *  en Informatique et en Automatique, Keio University).
 * All Rights Reserved. http://www.w3.org/Consortium/Legal/
 *
 * The original version of this interface comes from SAX :
 * http://www.megginson.com/SAX/
 *
 * $Id: CSSException.java,v 1.5 2008/01/03 13:35:38 xamjadmin Exp $
 */
package org.w3c.css.sac;

/**
 * @version $Revision: 1.5 $
 * @author  Philippe Le Hegaret
 */
public class CSSException extends RuntimeException {
    /**
     * this error is unspecified.
     */    
    public static short SAC_UNSPECIFIED_ERR   = 0;

    /**
     * If the operation is not supported
     */    
    public static short SAC_NOT_SUPPORTED_ERR = 1;

    /**
     * If an invalid or illegal string is specified
     */    
    public static short SAC_SYNTAX_ERR        = 2;

    protected short     code;

    /**
     * Creates a new CSSException
     */
    public CSSException() {
    }

    /**
     * Creates a new CSSException
     */
    public CSSException(String s) {
    	super(s);
    	this.code = SAC_UNSPECIFIED_ERR;
    }
    
    /**
     * Creates a new CSSException with an embeded exception.
     * @param a the embeded exception.
     */
    public CSSException(Exception e) {
    	super(e);
    	this.code = SAC_UNSPECIFIED_ERR;
    }

    /**
     * Creates a new CSSException with a specific code.
     * @param a the embeded exception.
     */
    public CSSException(short code) {
        this.code = code;
    }

    /**
     * Creates a new CSSException with an embeded exception and a specified
     * message.
     * @param code the specified code.
     * @param e the embeded exception.  
     */
    public CSSException(short code, String s, Exception e) {
    	super(s, e);
    	this.code = code;
    }

    /**
     * Returns the detail message of this throwable object. 
     *
     * @return the detail message of this Throwable, or null if this Throwable
     *         does not have a detail message.  
     */
    public String getMessage() {
    	return super.getMessage();
    }

    /**
     * returns the error code for this exception.
     */    
    public short getCode() {
    	return code;
    }

    /**
     * Returns the internal exception if any, null otherwise.
     */    
    public Exception getException() {
    	Throwable cause = this.getCause();
    	return cause instanceof Exception ? (Exception) cause : null;
    }
}
