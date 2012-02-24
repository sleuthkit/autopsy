package org.lobobrowser.html;

/**
 * Provides information about the user agent (browser) driving
 * the parser and/or renderer.
 * <p>
 * A simple implementation of this interface is provided in
 * {@link org.lobobrowser.html.test.SimpleUserAgentContext}.
 * @see HtmlRendererContext#getUserAgentContext()
 * @see org.lobobrowser.html.parser.DocumentBuilderImpl#DocumentBuilderImpl(UserAgentContext)
 */
public interface UserAgentContext {
	/**
	 * Creates an instance of {@link org.lobobrowser.html.HttpRequest} which
	 * can be used by the renderer to load images, scripts, external style sheets, 
	 * and implement the Javascript XMLHttpRequest class (AJAX). 
	 */
	public HttpRequest createHttpRequest();

	/**
	 * Gets browser "code" name.
	 */
	public String getAppCodeName();

	/**
	 * Gets browser application name.
	 */
	public String getAppName();

	/**
	 * Gets browser application version.
	 */
	public String getAppVersion();

	/**
	 * Gets browser application minor version.
	 */
	public String getAppMinorVersion();

	/**
	 * Gets browser language code. See <a href="http://en.wikipedia.org/wiki/List_of_ISO_639-1_codes">ISO 639-1 codes</a>.
	 */
	public String getBrowserLanguage();

	/**
	 * Returns a boolean value indicating whether cookies are
	 * enabled in the user agent. This value is used for reporting
	 * purposes only.
	 */
	public boolean isCookieEnabled();
	
	/**
	 * Returns a boolean value indicating whether scripting
	 * is enabled in the user agent. If this value is <code>false</code>,
	 * the parser will not process scripts and Javascript element
	 * attributes will have no effect.
	 */
	public boolean isScriptingEnabled();
	
	/**
	 * Returns a boolean value indicating whether
	 * remote (non-inline) CSS documents should be loaded.
	 */
	public boolean isExternalCSSEnabled();
	
	/**
	 * Gets the name of the user's operating system.
	 */
	public String getPlatform();

	/**
	 * Should return the string used in
	 * the User-Agent header. 
	 */
	public String getUserAgent();
	
	/**
	 * Method used to implement Javascript <code>document.cookie</code> property.
	 */
	public String getCookie(java.net.URL url);

	/**
	 * Method used to implement <code>document.cookie</code> property.
	 * @param cookieSpec Specification of cookies, as they
	 *                   would appear in the Set-Cookie
	 *                   header value of HTTP.
	 */
	public void setCookie(java.net.URL url, String cookieSpec);
	
	/**
	 * Gets the security policy for scripting. Return <code>null</code>
	 * if JavaScript code is trusted.
	 */
	public java.security.Policy getSecurityPolicy();
	
	/**
	 * Gets the scripting optimization level, which is a value
	 * equivalent to Rhino's optimization level.
	 */
	public int getScriptingOptimizationLevel();
	
	/**
	 * Returns true if the current media matches the name provided.
	 * @param mediaName Media name, which
	 * may be <code>screen</code>, <code>tty</code>, etc. (See <a href="http://www.w3.org/TR/REC-html40/types.html#type-media-descriptors">HTML Specification</a>).
	 */
	public boolean isMedia(String mediaName);
	
	public String getVendor();
	
	public String getProduct();
}
