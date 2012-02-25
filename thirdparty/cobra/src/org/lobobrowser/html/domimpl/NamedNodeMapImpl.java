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
 * Created on Sep 3, 2005
 */
package org.lobobrowser.html.domimpl;

import org.lobobrowser.js.*;
import org.w3c.dom.*;
import java.util.*;

public class NamedNodeMapImpl extends AbstractScriptableDelegate implements NamedNodeMap {
	//Note: class must be public for reflection to work.
	private final Map attributes = new HashMap(); 
	private final ArrayList attributeList = new ArrayList();
	
	public NamedNodeMapImpl(Element owner, Map attribs) {
		Iterator i = attribs.entrySet().iterator();
		while(i.hasNext()) {
			Map.Entry entry = (Map.Entry) i.next();
			String name = (String) entry.getKey();
			String value = (String) entry.getValue();
			//TODO: "specified" attributes
			Attr attr = new AttrImpl(name, value, true, owner, "ID".equals(name));
			this.attributes.put(name, attr);
			this.attributeList.add(attr);
		}
	}

	public int getLength() {
		return this.attributeList.size();
	}

	public Node getNamedItem(String name) {
		return (Node) this.attributes.get(name);
	}

	/**
	 * @param name
	 */
	public Node namedItem(String name) {
		// Method needed for Javascript indexing.
		return this.getNamedItem(name);
	}
	
	public Node getNamedItemNS(String namespaceURI,
			String localName) throws DOMException {
		throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "No namespace support");
	}

	public Node item(int index) {
		try {
			return (Node) this.attributeList.get(index);
		} catch(IndexOutOfBoundsException iob) {
			return null;
		}
	}

	public Node removeNamedItem(String name) throws DOMException {
		return (Node) this.attributes.remove(name);
	}

	public Node removeNamedItemNS(String namespaceURI,
			String localName) throws DOMException {
		throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "No namespace support");
	}

	public Node setNamedItem(Node arg) throws DOMException {
		Object prevValue = this.attributes.put(arg.getNodeName(), arg);
		if(prevValue != null) {
			this.attributeList.remove(prevValue);
		}
		this.attributeList.add(arg);
		return arg;
	}

	public Node setNamedItemNS(Node arg) throws DOMException {
		throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "No namespace support");
	}
}
