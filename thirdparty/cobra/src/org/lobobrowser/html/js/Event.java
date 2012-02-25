/*
    GNU LESSER GENERAL PUBLIC LICENSE
    Copyright (C) 2006 The XAMJ Project

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
package org.lobobrowser.html.js;

import org.w3c.dom.html2.*;
import java.awt.event.*;
import org.lobobrowser.js.*;

public class Event extends AbstractScriptableDelegate {
	private boolean cancelBubble;
	private HTMLElement fromElement, toElement;
	private int leafX, leafY;
	private boolean returnValue;
	private HTMLElement srcElement;
	private String type;
	private final java.awt.event.InputEvent inputEvent;
	
	public Event(String type, HTMLElement srcElement, java.awt.event.InputEvent mouseEvent, int leafX, int leafY) {
		this.type = type;
		this.srcElement = srcElement;
		this.leafX = leafX;
		this.leafY = leafY;
		this.inputEvent = mouseEvent;
	}
	
	public Event(String type, HTMLElement srcElement, java.awt.event.KeyEvent keyEvent) {
		this.type = type;
		this.srcElement = srcElement;
		this.inputEvent = keyEvent;
	}

	public Event(String type, HTMLElement srcElement) {
		this.type = type;
		this.srcElement = srcElement;
		this.inputEvent = null;
	}

	public boolean getAltKey() {
		InputEvent ie = this.inputEvent;
		return ie == null ? false : ie.isAltDown();
	}
	
	public boolean getShiftKey() {
		InputEvent ie = this.inputEvent;
		return ie == null ? false : ie.isShiftDown();
	}
	
	public boolean getCtrlKey() {
		InputEvent ie = this.inputEvent;
		return ie == null ? false : ie.isControlDown();
	}
	
	public int getButton() {
		InputEvent ie = this.inputEvent;
		if(ie instanceof MouseEvent) {
			return ((MouseEvent) ie).getButton();
		}
		else {
			return 0;
		}
	}

	public boolean isCancelBubble() {
		return cancelBubble;
	}

	public void setCancelBubble(boolean cancelBubble) {
		this.cancelBubble = cancelBubble;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}
	
	public int getClientX() {
		InputEvent ie = this.inputEvent;
		if(ie instanceof MouseEvent) {
			return ((MouseEvent) ie).getX();
		}
		else {
			return 0;
		}		
	}
	
	public int getClientY() {
		InputEvent ie = this.inputEvent;
		if(ie instanceof MouseEvent) {
			return ((MouseEvent) ie).getY();
		}
		else {
			return 0;
		}		
	}

	public int getKeyCode() {
		InputEvent ie = this.inputEvent;
		if(ie instanceof KeyEvent) {
			return ((KeyEvent) ie).getKeyCode();
		}
		else {
			return 0;
		}		
	}

//	public int getOffsetX() {
//		// Despite advertising that it returns an element-relative offset,
//		// IE doesn't do this.
//		//TODO: Must be relative to top viewport.
//		return this.getClientX() - 2;
//	}
//
//	public int getOffsetY() {
//		// Despite advertising that it returns an element-relative offset,
//		// IE doesn't do this.
//		//TODO: Must be relative to top viewport.
//		return this.getClientY() - 2;
//	}

	public boolean isReturnValue() {
		return returnValue;
	}

	public void setReturnValue(boolean returnValue) {
		this.returnValue = returnValue;
	}

	public HTMLElement getSrcElement() {
		return srcElement;
	}

	public void setSrcElement(HTMLElement srcElement) {
		this.srcElement = srcElement;
	}

	public HTMLElement getFromElement() {
		return fromElement;
	}

	public void setFromElement(HTMLElement fromElement) {
		this.fromElement = fromElement;
	}

	public HTMLElement getToElement() {
		return toElement;
	}

	public void setToElement(HTMLElement toElement) {
		this.toElement = toElement;
	}

	public int getLeafX() {
		return leafX;
	}

	public void setLeafX(int leafX) {
		this.leafX = leafX;
	}

	public int getLeafY() {
		return leafY;
	}

	public void setLeafY(int leafY) {
		this.leafY = leafY;
	}
}
