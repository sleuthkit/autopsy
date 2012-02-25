package org.lobobrowser.html.domimpl;

import org.w3c.dom.Document;
import org.w3c.dom.html2.HTMLFormElement;
import org.w3c.dom.html2.HTMLObjectElement;

public class HTMLObjectElementImpl extends HTMLAbstractUIElement implements
		HTMLObjectElement {
	public HTMLObjectElementImpl(String name) {
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

	public String getName() {
		return this.getAttribute("name");
	}

	public String getObject() {
		return this.getAttribute("object");
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

	public void setName(String name) {
		this.setAttribute("name", name);
	}

	public void setObject(String object) {
		this.setAttribute("object", object);
	}

	public void setWidth(String width) {
		this.setAttribute("width", width);
	}

	public String getBorder() {
		return this.getAttribute("border");
	}

	public String getCodeType() {
		return this.getAttribute("codetype");
	}

	public Document getContentDocument() {
		return this.getOwnerDocument();
	}

	public String getData() {
		return this.getAttribute("data");
	}

	public boolean getDeclare() {
		return "declare".equalsIgnoreCase(this.getAttribute("declare"));
	}

	public HTMLFormElement getForm() {
		return (HTMLFormElement) this.getAncestorForJavaClass(HTMLFormElement.class);
	}

	public int getHspace() {
		try {
			return Integer.parseInt(this.getAttribute("hspace"));
		} catch(Exception err) {
			return 0;
		}
	}

	public String getStandby() {
		return this.getAttribute("standby");
	}

	public int getTabIndex() {
		try {
			return Integer.parseInt(this.getAttribute("tabindex"));
		} catch(Exception err) {
			return 0;
		}
	}

	public String getType() {
		return this.getAttribute("type");
	}

	public String getUseMap() {
		return this.getAttribute("usemap");
	}

	public int getVspace() {
		try {
			return Integer.parseInt(this.getAttribute("vspace"));
		} catch(Exception err) {
			return 0;
		}
	}

	public void setBorder(String border) {
		this.setAttribute("border", border);
	}

	public void setCodeType(String codeType) {
		this.setAttribute("codetype", codeType);
	}

	public void setData(String data) {
		this.setAttribute("data", data);
	}

	public void setDeclare(boolean declare) {
		this.setAttribute("declare", declare ? "declare" : null);
	}

	public void setHspace(int hspace) {
		this.setAttribute("hspace", String.valueOf(hspace));
	}

	public void setStandby(String standby) {
		this.setAttribute("standby", standby);
	}

	public void setTabIndex(int tabIndex) {
		this.setAttribute("tabindex", String.valueOf(tabIndex));
	}

	public void setType(String type) {
		this.setAttribute("type", type);
	}

	public void setUseMap(String useMap) {
		this.setAttribute("usemap", useMap);		
	}

	public void setVspace(int vspace) {
		this.setAttribute("vspace", String.valueOf(vspace));
	}	
}
