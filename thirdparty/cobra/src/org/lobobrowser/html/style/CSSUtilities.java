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

package org.lobobrowser.html.style;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.logging.*;
import java.security.*;

import org.w3c.css.sac.InputSource;
import org.w3c.dom.css.CSSStyleSheet;
import org.w3c.dom.stylesheets.*;
import org.lobobrowser.html.*;
import org.lobobrowser.html.domimpl.HTMLDocumentImpl;
import org.lobobrowser.util.*;

import com.steadystate.css.dom.CSSStyleSheetImpl;
import com.steadystate.css.parser.CSSOMParser;

public class CSSUtilities {
	private static final Logger logger = Logger.getLogger(CSSUtilities.class.getName());

	private CSSUtilities() {
	}

	public static String preProcessCss(String text) {
		try {
			BufferedReader reader = new BufferedReader(new StringReader(text));
			String line;
			StringBuffer sb = new StringBuffer();
			String pendingLine = null;
			// Only last line should be trimmed.
			while((line = reader.readLine()) != null) {
				String tline = line.trim();
				if(tline.length() != 0) {
					if(pendingLine != null) {
						sb.append(pendingLine);
						sb.append("\r\n");
						pendingLine = null;
					}
					if(tline.startsWith("//")) {
						pendingLine = line;
						continue;
					}
					sb.append(line);
					sb.append("\r\n");
				}
			}
			return sb.toString();
		} catch(IOException ioe) {
			// not possible
			throw new IllegalStateException(ioe.getMessage());
		}
	}
	
	public static InputSource getCssInputSourceForStyleSheet(String text, String scriptURI) {
		java.io.Reader reader = new StringReader(text);
		InputSource is = new InputSource(reader);
		is.setURI(scriptURI);
		return is;
	}
		
	public static CSSStyleSheet parse(org.w3c.dom.Node ownerNode, String href, HTMLDocumentImpl doc, String baseUri, boolean considerDoubleSlashComments) throws MalformedURLException {
		UserAgentContext bcontext = doc.getUserAgentContext();
		final HttpRequest request = bcontext.createHttpRequest();
		URL baseURL = new URL(baseUri);
		URL scriptURL = Urls.createURL(baseURL, href);
		final String scriptURI = scriptURL == null ? href : scriptURL.toExternalForm();
		// Perform a synchronous request
		SecurityManager sm = System.getSecurityManager();
		if(sm == null) {
			try {
				request.open("GET", scriptURI, false);
				request.send(null);
			} catch(java.io.IOException thrown) {
				logger.log(Level.WARNING, "parse()", thrown);
			}

		}
		else {
			// Code might have restrictions on loading items from elsewhere.
			AccessController.doPrivileged(new PrivilegedAction() {
				public Object run() {
					try {
						request.open("GET", scriptURI, false);								
						request.send(null);
					} catch(java.io.IOException thrown) {
						logger.log(Level.WARNING, "parse()", thrown);
					}
					return null;
				}
			});
		}
		int status = request.getStatus();
		if(status != 200 && status != 0) {
			logger.warning("Unable to parse CSS. URI=[" + scriptURI + "]. Response status was " + status + ".");
			return null;
		}
		
		String text = request.getResponseText();
		if(text != null && !"".equals(text)) {
			String processedText = considerDoubleSlashComments ? preProcessCss(text) : text;
			CSSOMParser parser = new CSSOMParser();
			InputSource is = getCssInputSourceForStyleSheet(processedText, scriptURI);
			is.setURI(scriptURI);
			try {
				CSSStyleSheetImpl sheet = (CSSStyleSheetImpl) parser.parseStyleSheet(is);
				sheet.setHref(scriptURI);
				sheet.setOwnerNode(ownerNode);
				return sheet;
			} catch(Throwable err) {					
				logger.log(Level.WARNING,"Unable to parse CSS. URI=[" + scriptURI + "].", err);
				return null;
			}
		}		
		else {
			return null;
		}
	}
	
	public static boolean matchesMedia(String mediaValues, UserAgentContext rcontext) {
		if(mediaValues == null || mediaValues.length() == 0) {
			return true;
		}
		if(rcontext == null) {
			return false;
		}
		StringTokenizer tok = new StringTokenizer(mediaValues, ",");
		while(tok.hasMoreTokens()) {
			String token = tok.nextToken().trim();
			String mediaName = Strings.trimForAlphaNumDash(token);
			if(rcontext.isMedia(mediaName)) {
				return true;
			}
		}
		return false;
	}

	public static boolean matchesMedia(MediaList mediaList, UserAgentContext rcontext) {
		if(mediaList == null) {
			return true;
		}
		int length = mediaList.getLength();
		if(length == 0) {
			return true;
		}
		if(rcontext == null) {
			return false;
		}
		for(int i = 0; i < length; i++) {
			String mediaName = mediaList.item(i);
			if(rcontext.isMedia(mediaName)) {
				return true;
			}
		}
		return false;
	}

}
