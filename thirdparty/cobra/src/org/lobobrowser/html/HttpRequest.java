/*
    GNU LESSER GENERAL PUBLIC LICENSE
    Copyright (C) 2006 The Lobo Project

    This library is free software; you can redistribute it and/or
    modify it under the terms of the GNU Lesser General Public
    License as published by the Free Software Foundation; either
    version 2.1 of the License, or (at your option) any later version.

    This library is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
    Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public
    License along with this library; if not, write to the Free Software
    Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA

    Contact info: lobochief@users.sourceforge.net
*/
/*
 * Created on Nov 13, 2005
 */
package org.lobobrowser.html;

import java.awt.Image;
import java.net.URL;

import org.w3c.dom.Document;

/**
 * The <code>HttpRequest</code> interface should
 * be implemented to provide web request capabilities. It is
 * used a similar manner to <code>XMLHttpRequest</code> in
 * Javascript (AJAX). Normally, a listener will be added
 * by calling {@link #addReadyStateChangeListener(ReadyStateChangeListener)},
 * the method {@link #open(String, URL, boolean, String, String) open}
 * will be called, and finally, {@link #send(String)} will be called to 
 * complete the request.  
 * @see UserAgentContext#createHttpRequest()
 */
public interface HttpRequest {
	/**
	 * The uninitialized request state.
	 */
	public static final int STATE_UNINITIALIZED = 0;
	
	/**
	 * The loading request state. The <code>open</code> method
	 * has been called, but a response has not been received yet.
	 */
	public static final int STATE_LOADING = 1;
	
	/**
	 * The loaded request state. Headers and status are now available.
	 */
	public static final int STATE_LOADED = 2;
	
	/**
	 * The interactive request state. Downloading response. 
	 */
	public static final int STATE_INTERACTIVE = 3;
	
	/**
	 * The complete request state. All operations are finished.
	 */
	public static final int STATE_COMPLETE = 4;

	/**
	 * Gets the state of the request, a value
	 * between 0 and 4.
	 * @return A value corresponding to one of the STATE* constants in this class.
	 */
	public int getReadyState();
	
	/**
	 * Gets the request response as text.
	 */
	public String getResponseText();
	
	/**
	 * Gets the request response as an XML DOM.
	 */
	public Document getResponseXML();
	
	/**
	 * Gets the request response as an AWT image.
	 */
	public Image getResponseImage();
	
	/**
	 * Gets the request response bytes.
	 */
	public byte[] getResponseBytes();
	
	/**
	 * Gets the status of the response. Note that this
	 * can be 0 for file requests in addition to 200
	 * for successful HTTP requests.
	 */
	public int getStatus();
	
	/**
	 * Gets the status text of the request, e.g. "OK" for 200.
	 */
	public String getStatusText();
	
	/**
	 * Aborts an ongoing request.
	 */
	public void abort();
	
	/**
	 * Gets a string with all the response headers.
	 */
	public String getAllResponseHeaders();
	
	/**
	 * Gets a response header value.
	 * @param headerName The name of the header.
	 */
	public String getResponseHeader(String headerName);
	
	/**
	 * Starts an asynchronous request.
	 * @param method The request method.
	 * @param url The destination URL.
	 */
	public void open(String method, String url) throws java.io.IOException;
	
	/**
	 * Opens an asynchronous request.
	 * @param method The request method.
	 * @param url The destination URL.
	 */
	public void open(String method, URL url) throws java.io.IOException;
	
	/**
	 * Opens an request.
	 * @param method The request method.
	 * @param url The destination URL.
	 * @param asyncFlag Whether the request is asynchronous.
	 */
	public void open(String method, URL url, boolean asyncFlag) throws java.io.IOException;

	/**
	 * Opens a request.
	 * @param method The request method.
	 * @param url The destination URL.
	 * @param asyncFlag Whether the request should be asynchronous.
	 */
	public void open(String method, String url, boolean asyncFlag) throws java.io.IOException;
	
	/**
	 * Opens a request.
	 * @param method The request method.
	 * @param url The destination URL.
	 * @param asyncFlag Whether the request should be asynchronous.
	 * @param userName The HTTP authentication user name.
	 */
	public void open(String method, java.net.URL url, boolean asyncFlag, String userName) throws java.io.IOException;

	/**
	 * Opens a request.
	 * @param method The request method.
	 * @param url The destination URL.
	 * @param asyncFlag Whether the request should be asynchronous.
	 * @param userName The HTTP authentication user name.
	 * @param password The HTTP authentication password.
	 */
	public void open(String method, java.net.URL url, boolean asyncFlag, String userName, String password) throws java.io.IOException;

	/**
	 * Sends POST content if any. 
	 * @param content POST content or <code>null</code> for GET requests.
	 * @throws java.io.IOException
	 */
	public void send(String content) throws java.io.IOException;
	
	/**
	 * Adds a listener of ReadyState changes. The listener should be invoked
	 * even in the case of errors.
	 * @param listener An instanceof of {@link org.lobobrowser.html.ReadyStateChangeListener}
	 */
	public void addReadyStateChangeListener(ReadyStateChangeListener listener);
}
