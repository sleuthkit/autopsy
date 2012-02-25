package org.lobobrowser.html.js;

import java.awt.*;

import org.lobobrowser.js.*;

public class Screen extends AbstractScriptableDelegate {
	private final GraphicsEnvironment graphicsEnvironment;
	private final GraphicsDevice graphicsDevice;
	
	/**
	 * @param context
	 */
	Screen() {
		super();
	    if (GraphicsEnvironment.isHeadless()) {
	        this.graphicsEnvironment = null;
	        this.graphicsDevice = null;
	    }
	    else {
	    	this.graphicsEnvironment = GraphicsEnvironment.getLocalGraphicsEnvironment();
	    	this.graphicsDevice = this.graphicsEnvironment.getDefaultScreenDevice();
	    }
	}

	public int getHeight() {
		GraphicsDevice gd = this.graphicsDevice;
		return gd == null ? 0 : gd.getDisplayMode().getHeight();
	}

	public int getPixelDepth() {
		return this.getColorDepth();
	}

	public int getWidth() {
		GraphicsEnvironment ge = this.graphicsEnvironment;
		if(ge == null) {
			return 0;
		}
		GraphicsDevice gd = ge.getDefaultScreenDevice();
		return gd.getDisplayMode().getWidth();
	}

	public int getAvailHeight() {
		GraphicsEnvironment ge = this.graphicsEnvironment;
		if(ge == null) {
			return 0;
		}
		return ge.getMaximumWindowBounds().height;
	}

	public int getAvailWidth() {
		GraphicsEnvironment ge = this.graphicsEnvironment;
		if(ge == null) {
			return 0;
		}
		return ge.getMaximumWindowBounds().width;
	}

	public int getColorDepth() {
		GraphicsDevice gd = this.graphicsDevice;
		if(gd == null) {
			return 0;
		}
		return gd.getDisplayMode().getBitDepth();
	}
}
