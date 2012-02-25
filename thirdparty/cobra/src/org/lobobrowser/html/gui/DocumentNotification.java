package org.lobobrowser.html.gui;

import org.lobobrowser.html.domimpl.*;

class DocumentNotification {
	public static final int LOOK = 0;
	public static final int POSITION = 1;
	public static final int SIZE = 2;
	public static final int GENERIC = 3;

	public final int type;
	public final NodeImpl node;
	
	public DocumentNotification(int type, NodeImpl node) {
		this.type = type;
		this.node = node;
	}
	
	public String toString() {
		return "DocumentNotification[type=" + this.type + ",node=" + this.node + "]";
	}
}
