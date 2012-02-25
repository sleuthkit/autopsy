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

import org.lobobrowser.html.*;
import org.w3c.dom.DOMException;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;

public class DOMImplementationImpl implements DOMImplementation {
	private final UserAgentContext context;
	
	public DOMImplementationImpl(UserAgentContext context) {
		this.context = context;
	}

	public boolean hasFeature(String feature, String version) {
		return "HTML".equals(feature) && "2.0".compareTo(version) <= 0;
	}

	public DocumentType createDocumentType(String qualifiedName,
			String publicId, String systemId) throws DOMException {
		return new DocumentTypeImpl(qualifiedName, publicId, systemId);
	}

	public Document createDocument(String namespaceURI, String qualifiedName,
			DocumentType doctype) throws DOMException {
		return new HTMLDocumentImpl(this.context);
	}

	public Object getFeature(String feature, String version) {
		if("HTML".equals(feature) && "2.0".compareTo(version) <= 0) {
			return this;
		}
		else {
			return null;
		}
	}
}
