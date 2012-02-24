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
 * Created on Sep 10, 2005
 */
package org.lobobrowser.html.domimpl;

import org.w3c.dom.*;

public class AttrImpl extends NodeImpl implements Attr {
	private String name;
	private String value;
	private boolean specified;
	private Element ownerElement;
	private boolean isId;
	
	/**
	 * @param name
	 * @param value
	 */
	public AttrImpl(String name, String value, boolean specified, Element owner, boolean isId) {
		super();
		this.name = name;
		this.value = value;
		this.specified = specified;
		this.ownerElement = owner;
		this.isId = isId;
	}

	/**
	 * @param name
	 */
	public AttrImpl(String name) {
		super();
		this.name = name;
		this.value = "";
		this.specified = false;
		this.ownerElement = null;
		this.isId = false;
	}

	public String getLocalName() {
		return this.name;
	}

	public String getNodeName() {
		return this.name;
	}

	public String getNodeValue() throws DOMException {
		return this.value;
	}

	public void setNodeValue(String nodeValue) throws DOMException {
		this.value = nodeValue;
	}

	public short getNodeType() {
		return Node.ATTRIBUTE_NODE; 
	}

	public String getName() {
		return this.name;
	}

	public boolean getSpecified() {
		return this.specified;
	}

	public String getValue() {
		return this.value;
	}

	public void setValue(String value) throws DOMException {
		this.value = value;
	}

	public Element getOwnerElement() {
		return this.ownerElement;
	}

	public TypeInfo getSchemaTypeInfo() {
		throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "Namespaces not supported");
	}

	public boolean isId() {
		return this.isId;
	}
	
	public void setId(boolean value) {
		this.isId = value;
	}

	protected Node createSimilarNode() {
		return new AttrImpl(this.name, this.value, this.specified, this.ownerElement, this.isId);
	}
}
