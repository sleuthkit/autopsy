package org.lobobrowser.html.domimpl;

import org.w3c.dom.html2.*;

public class HTMLOptionElementImpl extends HTMLElementImpl implements HTMLOptionElement {
	public HTMLOptionElementImpl(String name) {
		super(name, true);
	}

	public boolean getDefaultSelected() {
		return this.getAttributeAsBoolean("selected");
	}

	public boolean getDisabled() {
		return false;
	}

	public HTMLFormElement getForm() {
		return this.getForm();
	}

	public int getIndex() {
		Object parent = this.getParentNode();
		if(parent instanceof HTMLSelectElement) {
			HTMLOptionsCollectionImpl options = (HTMLOptionsCollectionImpl) ((HTMLSelectElement) parent).getOptions();
			return options.indexOf(this);
		}
		else {
			return -1;
		}
	}

	public String getLabel() {
		return this.getAttribute("label");
	}

	public boolean getSelected() {
		return this.selected;
	}

	public String getText() {
		return this.getRawInnerText(false);
	}
	
	public void setText(String value) {
		this.setTextContent(value);
	}

	public String getValue() {
		return this.getAttribute("value");
	}

	public void setDefaultSelected(boolean defaultSelected) {
		this.setAttribute("selected", defaultSelected ? "selected" : null);
	}

	public void setDisabled(boolean disabled) {
		//TODO Unsupported
	}

	public void setLabel(String label) {
		this.setAttribute("label", label);
	}

	private boolean selected;

	void setSelectedImpl(boolean selected) {
		this.selected = selected;
	}

	public void setSelected(boolean selected) {
		boolean changed = selected != this.selected;
		this.selected = selected;
		// Changing the option state changes the selected index.
		Object parent = this.getParentNode();
		if(parent instanceof HTMLSelectElementImpl) {
			HTMLSelectElementImpl parentSelect = ((HTMLSelectElementImpl) parent);
			if(changed || parentSelect.getSelectedIndex() == -1) {
				if(selected) {	
					parentSelect.setSelectedIndexImpl(this.getIndex());				
				}
				else {
					int currentIndex = parentSelect.getSelectedIndex();
					if(currentIndex != -1 && currentIndex == this.getIndex()) {
						parentSelect.setSelectedIndexImpl(-1);
					}
				}
			}
		}
	}

	public void setValue(String value) {
		this.setAttribute("value", value);
	}
	
	public String toString() {
		return "HTMLOptionElementImpl[text=" + this.getText() + ",selected=" + this.getSelected() + "]";
	}
}
