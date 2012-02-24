package org.lobobrowser.html.test;

import java.security.Policy;
import java.util.*;
import java.util.logging.*;

import org.lobobrowser.html.HttpRequest;
import org.lobobrowser.html.UserAgentContext;

/**
 * Simple implementation of {@link org.lobobrowser.html.UserAgentContext}. 
 * This class is provided for user convenience. 
 * Usually this class should be extended in order to provide appropriate
 * user agent information and more robust content loading routines. 
 * Its setters can be called to modify certain user agent defaults.
 */
public class SimpleUserAgentContext implements UserAgentContext {
	private static final Logger logger = Logger.getLogger(SimpleUserAgentContext.class.getName());
	private static final Set mediaNames = new HashSet();
	
	static {
		// Media names claimed by this context.
		Set mn = mediaNames;
	    mn.add("screen");	
	    mn.add("tv");	
	    mn.add("tty");	
	    mn.add("all");	
	}
	
	/**
	 * This implementation returns true for certain media names,
	 * such as <code>screen</code>.
	 */
	public boolean isMedia(String mediaName) {
		return mediaNames.contains(mediaName.toLowerCase());
	}

	/**
	 * Creates a {@link org.lobobrowser.html.test.SimpleHttpRequest} instance. 
	 * The {@link org.lobobrowser.html.HttpRequest}</code> object returned by this method is
	 * used to load images, scripts, style sheets, and to implement
	 * the Javascript XMLHttpRequest class.
	 * Override if a custom mechanism to make requests is needed.
	 */
	public HttpRequest createHttpRequest() {
		return new SimpleHttpRequest(this, this.getProxy());
	}	

	private java.net.Proxy proxy = java.net.Proxy.NO_PROXY;
	
	/**
	 * Gets the connection proxy used in requests created
	 * by {@link #createHttpRequest()} by default. This implementation returns
	 * the value of a local field.
	 * @see #setProxy(java.net.Proxy)
	 */
	protected java.net.Proxy getProxy() {
		return this.proxy;
	}

	/**
	 * Sets the value of the proxy normally returned by
	 * {@link #getProxy()}.
	 * @param proxy A <code>java.net.Proxy</code> instance.
	 */
	public void setProxy(java.net.Proxy proxy) {
		this.proxy = proxy;
	}

	private String appCodeName = "Cobra";
	
	/**
	 * Returns the application "code name." This implementation
	 * returns the value of a local field.
	 * @see #setAppCodeName(String)
	 */
	public String getAppCodeName() {
		return this.appCodeName;
	}

	/**
	 * Sets the application code name normally returned by
	 * {@link #getAppCodeName()}.
	 * @param appCodeName An application "code name."
	 */
	public void setAppCodeName(String appCodeName) {
		this.appCodeName = appCodeName;
	}
	
	private String appMinorVersion = "0";
	
	/**
	 * Gets the "minor version" of the application. This implementation
	 * returns the value of a local field.
	 * @see #setAppMinorVersion(String)
	 */
	public String getAppMinorVersion() {
		return this.appMinorVersion;
	}
	
	/**
	 * Sets the value normally returned by {@link #getAppMinorVersion()}.
	 * @param appMinorVersion The application's "minor version."
	 */
	public void setAppMinorVersion(String appMinorVersion) {
		this.appMinorVersion = appMinorVersion;
	}

	private String appName = "Cobra";
		
	/**
	 * Gets the application name. This implementation returns
	 * the value of a local field.
	 * @see #setAppName(String)
	 */
	public String getAppName() {
		return this.appName;
	}

	/**
	 * Sets the value normally returned by {@link #getAppName()}.
	 * @param appName The application name.
	 */
	public void setAppName(String appName) {
		this.appName = appName;
	}

	private String appVersion = "1";
	
	/**
	 * Gets the major application version. This implementation
	 * returns the value of a local field.
	 * @see #setAppVersion(String)
	 */
	public String getAppVersion() {
		return this.appVersion;
	}

	/**
	 * Sets the value normally returned by {@link #getAppVersion()}.
	 * @param appVersion The application version.
	 */
	public void setAppVersion(String appVersion) {
		this.appVersion = appVersion;
	}
	
	/**
	 * Get the browser language. This implementation returns
	 * the language of the default locale. It may be overridden
	 * to provide a different value.
	 */
	public String getBrowserLanguage() {
		return Locale.getDefault().getLanguage();
	}

	/**
	 * Returns the value of Java property <code>os.name</code>.
	 * It may be overridden to provide a different value.
	 */
	public String getPlatform() {
		return System.getProperty("os.name");
	}

	private String userAgent = "Mozilla/4.0 (compatible; MSIE 6.0;) Cobra/Simple";
		
	/**
	 * Gets the User-Agent string. This implementation returns
	 * the value of a local field.
	 * @see #setUserAgent(String)
	 */
	public String getUserAgent() {
		return this.userAgent;
	}

	/**
	 * Sets the value normally returned by {@link #getUserAgent()}.
	 * @param userAgent A User-Agent string.
	 */
	public void setUserAgent(String userAgent) {
		this.userAgent = userAgent;
	}

	/**
	 * This implementation returns true if and only if
	 * <code>java.net.CookieHandler.getDefault()</code>
	 * is returning a non-null value. The method may
	 * be overridden to provide an alternative means
	 * of determining cookie enabling state.
	 */
	public boolean isCookieEnabled() {
		return java.net.CookieHandler.getDefault() != null;
	}

	/**
	 * This implementation uses the default <code>java.net.CookieHandler</code>,
	 * if any, to get cookie information for the given URL. If no cookie handler
	 * is available, this method returns the empty string.
	 */
	public String getCookie(java.net.URL url) {
		java.net.CookieHandler handler = java.net.CookieHandler.getDefault();
		if(handler == null) {
			return "";
		}
		Map results;
		try {
			results = handler.get(url.toURI(), new HashMap());
		} catch(Exception err) {
			logger.log(Level.WARNING, "getCookie()", err);
			return "";
		}
		if(results == null) {
			return "";
		}
		StringBuffer buffer = new StringBuffer();
		Iterator i = results.entrySet().iterator();
		boolean firstTime = true;
		while(i.hasNext()) {
			Map.Entry entry = (Map.Entry) i.next();
			String key = (String) entry.getKey();
			if("Cookie".equalsIgnoreCase(key) || "Cookie2".equalsIgnoreCase(key)) {
				List list = (List) entry.getValue();
				Iterator li = list.iterator();
				while(li.hasNext()) {
					String value = (String) li.next();
					if(firstTime) {
						firstTime = false;
					}
					else {
						buffer.append("; ");
					}
					buffer.append(value);
				}				
			}
		}
		return buffer.toString();
	}

	private boolean scriptingEnabled = true;
	
	/**
	 * Determines whether scripting should be enabled. This
	 * implementation returns the value of a local field defaulting
	 * to <code>true</code>.
	 * @see #setScriptingEnabled(boolean)
	 */
	public boolean isScriptingEnabled() {
		return this.scriptingEnabled;
	}

	/**
	 * Sets the value normally returned by {@link #isScriptingEnabled()}.
	 * @param enable A boolean value.
	 */
	public void setScriptingEnabled(boolean enable) {
		this.scriptingEnabled = enable;
	}
	
	/**
	 * This method uses the default CookieHandler, if one is available,
	 * to set a cookie value.
	 */
	public void setCookie(java.net.URL url, String cookieSpec) {
		java.net.CookieHandler handler = java.net.CookieHandler.getDefault();
		if(handler == null) {
			return;
		}
		Map headers = new HashMap(2);
		headers.put("Set-Cookie", Collections.singletonList(cookieSpec));
		try {
			handler.put(url.toURI(), headers);
		} catch(Exception err) {
			logger.log(Level.WARNING, "setCookie()", err);
		}
	}

	/**
	 * Returns <code>null</code>. This method must be overridden
	 * if JavaScript code is untrusted.
	 */
	public Policy getSecurityPolicy() {
		return null;
	}

	private int scriptingOptimizationLevel = -1;
	
	/**
	 * Gets the Rhino optimization level. This implementation returns
	 * the value of a local field defaulting to <code>-1</code>.
	 * @see #setScriptingOptimizationLevel(int)
	 */
	public int getScriptingOptimizationLevel() {
		return this.scriptingOptimizationLevel;
	}

	/**
	 * Sets the value normally returned by {@link #getScriptingOptimizationLevel()}.
	 * @param level A Rhino optimization level.
	 */
	public void setScriptingOptimizationLevel(int level) {
		this.scriptingOptimizationLevel = level;
	}

	private String vendor = "The Lobo Project";
	
	public String getVendor() {
		return this.vendor;
	}

	public void setVendor(String vendor) {
		this.vendor = vendor;
	}

	private String product = "Cobra";
	
	public String getProduct() {
		return this.product;
	}

	public void setProduct(String product) {
		this.product = product;
	}

	private boolean externalCSSEnabled = true;

	/**
	 * Determines whether external CSS loading should be enabled.
	 * This implementation returns the value of a local field
	 * defaulting to <code>true</code>.
	 * @see #setExternalCSSEnabled(boolean)
	 */
	public boolean isExternalCSSEnabled() {
		return this.externalCSSEnabled;
	}
	
	/**
	 * Sets the value normally returned by {@link #isExternalCSSEnabled()}.
	 * @param enabled A boolean value.
	 */
	public void setExternalCSSEnabled(boolean enabled) {
		this.externalCSSEnabled = enabled;
	}
}
