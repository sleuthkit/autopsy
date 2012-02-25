package org.lobobrowser.html;

import java.awt.*;

/**
 * This interface should be implemented to provide
 * OBJECT, EMBED or APPLET functionality.
 */
public interface HtmlObject {
	public Component getComponent();
	public void suspend();
	public void resume();
	public void destroy();
	
	/**
	 * Called as the object is layed out, either
	 * the first time it's layed out or whenever
	 * the DOM changes. This is where the object
	 * should reset its state based on element
	 * children or attributes and possibly change
	 * its preferred size if appropriate.
	 */
	public void reset(int availableWidth, int availableHeight);
}
