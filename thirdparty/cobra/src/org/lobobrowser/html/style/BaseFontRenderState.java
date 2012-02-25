package org.lobobrowser.html.style;

public class BaseFontRenderState extends RenderStateDelegator {
	private final int fontBase;
	
	public BaseFontRenderState(final RenderState prevRenderState, int fontBase) {
		super(prevRenderState);
		this.fontBase = fontBase;
	}

	public int getFontBase() {
		return this.fontBase;
	}
}
