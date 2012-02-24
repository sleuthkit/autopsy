package org.lobobrowser.html.domimpl;

import org.w3c.dom.html.HTMLAppletElement;

public class HTMLAppletElementImpl extends HTMLAbstractUIElement implements
		HTMLAppletElement {
	public HTMLAppletElementImpl(String name) {
		super(name);
	}

	public String getAlign() {
		return this.getAttribute("align");
	}

	public String getAlt() {
		return this.getAttribute("alt");
	}

	public String getArchive() {
		return this.getAttribute("archive");
	}

	public String getCode() {
		return this.getAttribute("code");
	}

	public String getCodeBase() {
		return this.getAttribute("codebase");
	}

	public String getHeight() {
		return this.getAttribute("height");
	}

	public String getHspace() {
		return this.getAttribute("hspace");
	}

	public String getName() {
		return this.getAttribute("name");
	}

	public String getObject() {
		return this.getAttribute("object");
	}

	public String getVspace() {
		return this.getAttribute("vspace");
	}

	public String getWidth() {
		return this.getAttribute("width");
	}

	public void setAlign(String align) {
		this.setAttribute("align", align);
	}

	public void setAlt(String alt) {
		this.setAttribute("alt", alt);
	}

	public void setArchive(String archive) {
		this.setAttribute("archive", archive);
	}

	public void setCode(String code) {
		this.setAttribute("code", code);
	}

	public void setCodeBase(String codeBase) {
		this.setAttribute("codebase", codeBase);
	}

	public void setHeight(String height) {
		this.setAttribute("height", height);
	}

	public void setHspace(String hspace) {
		this.setAttribute("hspace", hspace);
	}

	public void setName(String name) {
		this.setAttribute("name", name);
	}

	public void setObject(String object) {
		this.setAttribute("object", object);
	}

	public void setVspace(String vspace) {
		this.setAttribute("vspace", vspace);
	}

	public void setWidth(String width) {
		this.setAttribute("width", width);
	}
}
