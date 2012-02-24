package org.lobobrowser.html.js;

import java.net.*;
import org.w3c.dom.Document;
import org.lobobrowser.html.*;
import org.lobobrowser.util.*;
import org.lobobrowser.js.*;
import org.mozilla.javascript.*;
import java.util.logging.*;

public class XMLHttpRequest extends AbstractScriptableDelegate {
	//TODO: See reference: http://www.xulplanet.com/references/objref/XMLHttpRequest.html
	
	private static final Logger logger = Logger.getLogger(XMLHttpRequest.class.getName());
	private final HttpRequest request;
	private final UserAgentContext pcontext;
	private final Scriptable scope;
	private final java.net.URL codeSource;
	
	public XMLHttpRequest(UserAgentContext pcontext, java.net.URL codeSource, Scriptable scope) {
		this.request = pcontext.createHttpRequest();
		this.pcontext = pcontext;
		this.scope = scope;
		this.codeSource = codeSource;
	}

	public void abort() {
		request.abort();
	}

	public String getAllResponseHeaders() {
		return request.getAllResponseHeaders();
	}

	public int getReadyState() {
		return request.getReadyState();
	}

	public byte[] getResponseBytes() {
		return request.getResponseBytes();
	}

	public String getResponseHeader(String headerName) {
		return request.getResponseHeader(headerName);
	}

	public String getResponseText() {
		return request.getResponseText();
	}

	public Document getResponseXML() {
		return request.getResponseXML();
	}

	public int getStatus() {
		return request.getStatus();
	}

	public String getStatusText() {
		return request.getStatusText();
	}

	private URL getFullURL(String relativeUrl) throws java.net.MalformedURLException {
		return Urls.createURL(this.codeSource, relativeUrl);
	}
	
	public void open(String method, String url, boolean asyncFlag, String userName, String password) throws java.io.IOException {
		request.open(method, this.getFullURL(url), asyncFlag, userName, password);
	}

	public void open(String method, String url, boolean asyncFlag, String userName) throws java.io.IOException {
		request.open(method, this.getFullURL(url), asyncFlag, userName);
	}

	public void open(String method, String url, boolean asyncFlag) throws java.io.IOException {
		request.open(method, this.getFullURL(url), asyncFlag);
	}

	public void open(String method, String url) throws java.io.IOException {
		request.open(method, this.getFullURL(url));
	}	
	
	public void send(String content) throws java.io.IOException {
		request.send(content);
	}

	private Function onreadystatechange;
	private boolean listenerAdded;
	
	public Function getOnreadystatechange() {
		synchronized(this) {
			return this.onreadystatechange;
		}
	}

	public void setOnreadystatechange(final Function value) {
		synchronized(this) {
			this.onreadystatechange = value;
			if(value != null && !this.listenerAdded) {
				this.request.addReadyStateChangeListener(new ReadyStateChangeListener() {
					public void readyStateChanged() {
						// Not called in GUI thread to ensure consistency of readyState.
						executeReadyStateChange();
					}
				});
				this.listenerAdded = true;
			}
		}
	}
	
	private void executeReadyStateChange() {
		// Not called in GUI thread to ensure consistency of readyState.
		try {
			Function f = XMLHttpRequest.this.getOnreadystatechange();
			if(f != null) {
				Context ctx = Executor.createContext(this.codeSource, this.pcontext);
				try {
					Scriptable newScope = (Scriptable) JavaScript.getInstance().getJavascriptObject(XMLHttpRequest.this, this.scope);
					f.call(ctx, newScope, newScope, new Object[0]);
				} finally {
					Context.exit();
				}
			}	
		} catch(Exception err) {
			logger.log(Level.WARNING, "Error processing ready state change.", err);
		}		
	}
	
}

