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
 * Created on Jan 15, 2006
 */
package org.lobobrowser.html.domimpl;

import java.util.ArrayList;

import org.lobobrowser.html.FormInput;
import org.lobobrowser.html.js.Executor;
import org.mozilla.javascript.Function;
import org.w3c.dom.Node;
import org.w3c.dom.html2.HTMLFormElement;

public abstract class HTMLBaseInputElement extends HTMLAbstractUIElement {
	public HTMLBaseInputElement(String name) {
		super(name);
	}
	
	protected InputContext inputContext;
	protected String deferredValue;
	
	public void setInputContext(InputContext ic) {
		String dv = null;
		synchronized(this) {
			this.inputContext = ic;
			if(ic != null) {
				dv = this.deferredValue;
			}
		}
		if(dv != null) {
			ic.setValue(dv);
		}	
	}

	public String getDefaultValue() {
		return this.getAttribute("defaultValue");
	}

	public void setDefaultValue(String defaultValue) {
		this.setAttribute("defaultValue", defaultValue);
	}

	public HTMLFormElement getForm() {
		Node parent = this.getParentNode();
		while(parent != null && !(parent instanceof HTMLFormElement)) {
			parent = parent.getParentNode();
		}
		return (HTMLFormElement) parent;
	}

	public void submitForm(FormInput[] extraFormInputs) {
		HTMLFormElementImpl form = (HTMLFormElementImpl) this.getForm();
		if(form != null) {
			form.submit(extraFormInputs);
		}
	}

	public void resetForm() {
		HTMLFormElement form = this.getForm();
		if (form != null) {
			form.reset();
		}
	}

	public String getAccept() {
		return this.getAttribute("accept");
	}

	public void setAccept(String accept) {
		this.setAttribute("accept", accept);
	}

	public String getAccessKey() {
		return this.getAttribute("accessKey");
	}

	public void setAccessKey(String accessKey) {
		this.setAttribute("accessKey", accessKey);
	}

	public String getAlign() {
		return this.getAttribute("align");
	}

	public void setAlign(String align) {
		this.setAttribute("align", align);
	}

	public String getAlt() {
		return this.getAttribute("alit");
	}

	public void setAlt(String alt) {
		this.setAttribute("alt", alt);
	}

	public boolean getDisabled() {
		InputContext ic = this.inputContext;
		return ic == null ? false : ic.getDisabled();
	}

	public void setDisabled(boolean disabled) {
		InputContext ic = this.inputContext;
		if(ic != null) {
			ic.setDisabled(disabled);
		}
	}

	public String getName() {
		//TODO: Should this return valid of "id"?
		return this.getAttribute("name");
	}

	public void setName(String name) {
		this.setAttribute("name", name);
	}

	public boolean getReadOnly() {
		InputContext ic = this.inputContext;
		return ic == null ? false : ic.getReadOnly();
	}

	public void setReadOnly(boolean readOnly) {
		InputContext ic = this.inputContext;
		if(ic != null) {
			ic.setReadOnly(readOnly);
		}
	}

	public int getTabIndex() {
		InputContext ic = this.inputContext;
		return ic == null ? 0 : ic.getTabIndex();
	}

	public void setTabIndex(int tabIndex) {
		InputContext ic = this.inputContext;
		if(ic != null) {
			ic.setTabIndex(tabIndex);
		}
	}

	public String getValue() {
		InputContext ic = this.inputContext;
		if(ic != null) {
			//Note: Per HTML Spec, setValue does not set attribute.
			return ic.getValue();
		}
		else {
			String dv = this.deferredValue;
			if(dv != null) {
				return dv;
			}
			else {
				String val = this.getAttribute("value");
				return val == null ? "" : val;
			}
		}
	}

	protected java.io.File getFileValue() {
		InputContext ic = this.inputContext;
		if(ic != null) {
			return ic.getFileValue();
		}
		else {
			return null;
		}
	}

	public void setValue(String value) {
		InputContext ic = null;
		synchronized(this) {
			ic = this.inputContext;
			if(ic == null) {
				this.deferredValue = value;
			}
		}
		if(ic != null) {
			ic.setValue(value);
		}
	}

	public void blur() {
		InputContext ic = this.inputContext;
		if(ic != null) {
			ic.blur();
		}
	}

	public void focus() {
		InputContext ic = this.inputContext;
		if(ic != null) {
			ic.focus();
		}
	}

	public void select() {
		InputContext ic = this.inputContext;
		if(ic != null) {
			ic.select();
		}
	}

	/* (non-Javadoc)
	 * @see org.xamjwg.html.domimpl.HTMLElementImpl#assignAttributeField(java.lang.String, java.lang.String)
	 */
	protected void assignAttributeField(String normalName, String value) {
		if("value".equals(normalName)) {
			InputContext ic = this.inputContext;
			if(ic != null) {
				ic.setValue(value);
			}
		}
		else if("src".equals(normalName)) {
			this.loadImage(value);
		}
		else {
			super.assignAttributeField(normalName, value);
		}
	}
		
	private Function onload;
	
	public Function getOnload() {
		return this.getEventFunction(this.onload, "onload");
	}

	public void setOnload(Function onload) {
		this.onload = onload;
	}

	private java.awt.Image image = null;
	private String imageSrc;
	
	private void loadImage(String src) {
		HTMLDocumentImpl document = (HTMLDocumentImpl) this.document;
		if(document != null) {
			synchronized(this.imageListeners) {
				this.imageSrc = src;
				this.image = null;
			}
			if(src != null) {
				document.loadImage(src, new LocalImageListener(src));
			}
		}
	}
	
	public final java.awt.Image getImage() {
		synchronized(this.imageListeners) {
			return this.image;
		}
	}
	
	private final ArrayList imageListeners = new ArrayList(1);
	
	/**
	 * Adds a listener of image loading events.
	 * The listener gets called right away if there's already
	 * an image.
	 * @param listener
	 */
	public void addImageListener(ImageListener listener) {
		ArrayList l = this.imageListeners;
		java.awt.Image currentImage;
		synchronized(l) {
			currentImage = this.image;
			l.add(listener);
		}
		if(currentImage != null) {
			// Call listener right away if there's already an
			// image; holding no locks.
			listener.imageLoaded(new ImageEvent(this, currentImage));
			// Should not call onload handler here. That's taken
			// care of otherwise.
		}
	}

	public void removeImageListener(ImageListener listener) {
		ArrayList l = this.imageListeners;
		synchronized(l) {
			l.remove(l);
		}
	}
	
	void resetInput() {
		InputContext ic = this.inputContext;
		if(ic != null) {
			ic.resetInput();
		}
	}
	
	private void dispatchEvent(String expectedImgSrc, ImageEvent event) {
		ArrayList l = this.imageListeners;
		ImageListener[] listenerArray;
		synchronized(l) {
			if(!expectedImgSrc.equals(this.imageSrc)) {
				return;
			}				
			this.image = event.image;
			// Get array of listeners while holding lock.
			listenerArray = (ImageListener[]) l.toArray(ImageListener.EMPTY_ARRAY);
		}
		int llength = listenerArray.length;
		for(int i = 0; i < llength; i++) {
			// Inform listener, holding no lock.
			listenerArray[i].imageLoaded(event);
		}
		Function onload = this.getOnload(); 
		if(onload != null) {
			//TODO: onload event object?
			Executor.executeFunction(HTMLBaseInputElement.this, onload, null);
		}
	}
	
	private class LocalImageListener implements ImageListener {
		private final String expectedImgSrc;

		public LocalImageListener(String imgSrc) {
			this.expectedImgSrc = imgSrc;
		}
		
		public void imageLoaded(ImageEvent event) {
			dispatchEvent(this.expectedImgSrc, event);
		}
	}	

}
