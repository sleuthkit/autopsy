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
 * Created on Oct 15, 2005
 */
package org.lobobrowser.html.domimpl;

import org.w3c.dom.DOMException;
import org.w3c.dom.DocumentType;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public class DocumentTypeImpl extends NodeImpl implements DocumentType {
	private final String qualifiedName;
	private final String publicId;
	private final String systemId;
	
	public DocumentTypeImpl(String qname, String publicId, String systemId) {
		super();
		this.qualifiedName = qname;
		this.publicId = publicId;
		this.systemId = systemId;
	}

	public String getLocalName() {
		return null;
	}

	public String getNodeName() {
		return this.getName();
	}

	public String getNodeValue() throws DOMException {
		return null;
	}

	public void setNodeValue(String nodeValue) throws DOMException {
		// nop
	}

	public short getNodeType() {
		return org.w3c.dom.Node.DOCUMENT_TYPE_NODE;
	}

	public String getName() {
		return this.qualifiedName;
	}

	public NamedNodeMap getEntities() {
		//TODO: DOCTYPE declared entities
		return null;
	}

	public NamedNodeMap getNotations() {
		//TODO: DOCTYPE notations
		return null;
	}

	public String getPublicId() {
		return this.publicId;
	}

	public String getSystemId() {
		return this.systemId;
	}

	public String getInternalSubset() {
		//TODO: DOCTYPE internal subset
		return null;
	}

	protected Node createSimilarNode() {
		return new DocumentTypeImpl(this.qualifiedName, this.publicId, this.systemId);
	}
}
