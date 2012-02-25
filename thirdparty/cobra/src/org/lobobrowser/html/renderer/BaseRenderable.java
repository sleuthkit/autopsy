package org.lobobrowser.html.renderer;

abstract class BaseRenderable implements Renderable {
	private int ordinal = 0;
	
	public int getOrdinal() {
		return this.ordinal;
	}

	public int getZIndex() {
		return 0;
	}

	public void setOrdinal(int ordinal) {
		this.ordinal = ordinal;
	}
}
