package org.lobobrowser.html.style;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;


public class AlignXRenderState extends RenderStateDelegator {
	private final int alignXPercent;
	
	public AlignXRenderState(final RenderState prevRenderState, int alignXPercent) {
		super(prevRenderState);
		this.alignXPercent = alignXPercent;
	}

	public int getAlignXPercent() {
		return this.alignXPercent;
	}
}
