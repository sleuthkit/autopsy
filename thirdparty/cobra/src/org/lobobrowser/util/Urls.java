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
 * Created on Jun 12, 2005
 */
package org.lobobrowser.util;

import java.net.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.*;

public class Urls {
	private static final Logger logger = Logger.getLogger(Urls.class.getName());
	public static final DateFormat PATTERN_RFC1123 = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);

	static {
		DateFormat df = PATTERN_RFC1123;
		df.setTimeZone(TimeZone.getTimeZone("GMT"));
	}
	
	private Urls() {
		super();
	}

	/** Whether the URL refers to a resource in the local file system. */
	public static boolean isLocal(java.net.URL url) {
		if(isLocalFile(url)) {
			return true;
		}
		String protocol = url.getProtocol();
		if("jar".equalsIgnoreCase(protocol)) {
			String path = url.getPath();
			int emIdx = path.lastIndexOf('!');
			String subUrlString = emIdx == -1 ? path : path.substring(0, emIdx);
			try {
				URL subUrl = new URL(subUrlString);
				return isLocal(subUrl);
			} catch(java.net.MalformedURLException mfu) {
				return false;
			}
		}
		else {
			return false;
		}
	}
	
	/** Whether the URL is a file in the local file system. */
	public static boolean isLocalFile(java.net.URL url) {
		String scheme = url.getProtocol();
		return "file".equalsIgnoreCase(scheme) && !hasHost(url);
	}

	public static boolean hasHost(java.net.URL url) {
		String host = url.getHost();
		return host != null && !"".equals(host);
	}

	/**
	 * Creates an absolute URL in a manner equivalent to
	 * major browsers. 
	 */
	public static URL createURL(URL baseUrl, String relativeUrl) throws java.net.MalformedURLException {
		return new URL(baseUrl, relativeUrl);
	}	
	
	/**
	 * Returns the time when the document should be considered expired.
	 * The time will be zero if the document always needs to be revalidated.
	 * It will be <code>null</code> if no expiration time is specified.
	 */
	public static Long getExpiration(URLConnection connection, long baseTime) {
		String cacheControl = connection.getHeaderField("Cache-Control");
		if(cacheControl != null) {
			StringTokenizer tok = new StringTokenizer(cacheControl, ",");
			while(tok.hasMoreTokens()) {
				String token = tok.nextToken().trim().toLowerCase();
				if("must-revalidate".equals(token)) {
					return new Long(0);
				}
				else if(token.startsWith("max-age")) {
					int eqIdx = token.indexOf('=');
					if(eqIdx != -1) {
						String value = token.substring(eqIdx+1).trim();
						int seconds;
						try {
							seconds = Integer.parseInt(value);
							return new Long(baseTime + seconds * 1000);
						} catch(NumberFormatException nfe) {
							logger.warning("getExpiration(): Bad Cache-Control max-age value: " + value);
							// ignore
						}
					}
				}
			}
		}
		String expires = connection.getHeaderField("Expires");
		if(expires != null) {
			try {
				synchronized(PATTERN_RFC1123) {
					Date expDate = PATTERN_RFC1123.parse(expires);
					return new Long(expDate.getTime());
				}
			} catch(java.text.ParseException pe) {
				int seconds;
				try {
					seconds = Integer.parseInt(expires);
					return new Long(baseTime + seconds * 1000);
				} catch(NumberFormatException nfe) {
					logger.warning("getExpiration(): Bad Expires header value: " + expires);
				}
			}
		}
		return null;
	}
	
	public static List getHeaders(URLConnection connection) {
		// Random access index recommended.
		List headers = new ArrayList();
		for(int n = 0; ; n++) {
			String value = connection.getHeaderField(n);
			if(value == null) {
			    break;
			}
            // Key may be null for n == 0.
            String key = connection.getHeaderFieldKey(n);
			if(key != null) {
			    headers.add(new NameValuePair(key, value));
			}
		}
		return headers;
	}

	public static URL guessURL(URL baseURL, String spec)
			throws MalformedURLException {
		URL finalURL;
		try {
			if (baseURL != null) {
				int colonIdx = spec.indexOf(':');
				String newProtocol = colonIdx == -1 ? null : spec.substring(0,
						colonIdx);
				if (newProtocol != null
						&& !newProtocol.equalsIgnoreCase(baseURL.getProtocol())) {
					baseURL = null;
				}
			}
			finalURL = createURL(baseURL, spec);
		} catch (MalformedURLException mfu) {
			spec = spec.trim();
			int idx = spec.indexOf(':');
			if (idx == -1) {
				int slashIdx = spec.indexOf('/');
				if (slashIdx == 0) {
					// A file, absolute
					finalURL = new URL("file:" + spec);
				} else {
					if (slashIdx == -1) {
						// No slash, no colon, must be host.
						finalURL = new URL(baseURL, "http://" + spec);
					} else {
						String possibleHost = spec.substring(0, slashIdx)
								.toLowerCase();
						if (Domains.isLikelyHostName(possibleHost)) {
							finalURL = new URL(baseURL, "http://" + spec);
						} else {
							finalURL = new URL(baseURL, "file:" + spec);
						}
					}
				}
			} else {
				if (idx == 1) {
					// Likely a drive
					finalURL = new URL(baseURL, "file:" + spec);
				} else {
					throw mfu;
				}
			}
		}
		if (!"".equals(finalURL.getHost())
				&& finalURL.toExternalForm().indexOf(' ') != -1) {
			throw new MalformedURLException("There are blanks in the URL: "
					+ finalURL.toExternalForm());
		}
		return finalURL;
	}

	public static URL guessURL(String spec) throws MalformedURLException {
		return guessURL(null, spec);
	}
	
	public static String getCharset(URLConnection connection) {
		String contentType = connection.getContentType();
		if(contentType == null) {
			return getDefaultCharset(connection);
		}
		StringTokenizer tok = new StringTokenizer(contentType, ";");
		if(tok.hasMoreTokens()) {
			tok.nextToken();
			while(tok.hasMoreTokens()) {
				String assignment = tok.nextToken().trim();
				int eqIdx = assignment.indexOf('=');
				if(eqIdx != -1) {
					String varName = assignment.substring(0, eqIdx).trim();
					if("charset".equalsIgnoreCase(varName)) {
						String varValue = assignment.substring(eqIdx+1);
						return Strings.unquote(varValue.trim());
					}
				}
			}
		}
		return getDefaultCharset(connection);
	}
	
    private static String getDefaultCharset(URLConnection connection) {
    	URL url = connection.getURL();
    	if(Urls.isLocalFile(url)) {
    		String charset = System.getProperty("file.encoding");
    		return charset == null ? "ISO-8859-1" : charset;
    	}
    	else {
    		return "ISO-8859-1";
    	}
    }
    
    public static String getNoRefForm(URL url) {
    	String host = url.getHost();
    	int port = url.getPort();
    	String portText = port == -1 ? "" : ":" + port;
    	String userInfo = url.getUserInfo();
    	String userInfoText = userInfo == null || userInfo.length() == 0 ? "" : userInfo + "@";
    	String hostPort = host == null || host.length() == 0 ? "" : "//" + userInfoText + host + portText;
    	return url.getProtocol() + ":" + hostPort + url.getFile();
    }

    /**
     * Comparison that does not consider Ref.
     * @param url1
     * @param url2
     */
    public static boolean sameNoRefURL(URL url1, URL url2) {
    	return Objects.equals(url1.getHost(), url2.getHost()) &&
    	       Objects.equals(url1.getProtocol(), url2.getProtocol()) &&
    	       url1.getPort() == url2.getPort() &&
    	       Objects.equals(url1.getFile(), url2.getFile()) &&
    	       Objects.equals(url1.getUserInfo(), url2.getUserInfo());
    }
}
