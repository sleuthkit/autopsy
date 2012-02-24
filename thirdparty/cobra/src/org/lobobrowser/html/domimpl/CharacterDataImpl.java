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

import org.w3c.dom.*;

public abstract class CharacterDataImpl extends NodeImpl implements
		CharacterData {
	protected volatile String text;
	
	public CharacterDataImpl() {
		super();
	}
	
	public CharacterDataImpl(String text) {
		this.text = text;
	}
	
	public String getClassName() {
		return "HTMLCharacterData";
	}

	public String getTextContent() throws DOMException {
		return this.text;
	}

	public void setTextContent(String textContent) throws DOMException {
		this.text = textContent;
		if(!this.notificationsSuspended) {
			this.informInvalid();
		}
	}

	/* (non-Javadoc)
	 * @see org.xamjwg.html.domimpl.NodeImpl#cloneNode(boolean)
	 */
	public Node cloneNode(boolean deep) {
		CharacterDataImpl newNode = (CharacterDataImpl) super.cloneNode(deep);
		newNode.setData(this.getData());
		return newNode;
	}


	public void appendData(String arg) throws DOMException {
		this.text += arg;
		if(!this.notificationsSuspended) {
			this.informInvalid();
		}
	}

	public void deleteData(int offset, int count)
			throws DOMException {
		StringBuffer buffer = new StringBuffer(this.text);
		StringBuffer result = buffer.delete(offset, offset + count);
		this.text = result.toString();
		if(!this.notificationsSuspended) {
			this.informInvalid();
		}
	}

	public String getData() throws DOMException {
		return this.text;
	}

	public int getLength() {
		return this.text.length();
	}

	public void insertData(int offset, String arg)
			throws DOMException {
		StringBuffer buffer = new StringBuffer(this.text);
		StringBuffer result = buffer.insert(offset, arg);
		this.text = result.toString();
		if(!this.notificationsSuspended) {
			this.informInvalid();
		}
	}

	public void replaceData(int offset, int count, String arg)
			throws DOMException {
		StringBuffer buffer = new StringBuffer(this.text);
		StringBuffer result = buffer.replace(offset, offset + count, arg);
		this.text = result.toString();
		if(!this.notificationsSuspended) {
			this.informInvalid();
		}
	}

	public void setData(String data) throws DOMException {
		this.text = data;
		if(!this.notificationsSuspended) {
			this.informInvalid();
		}
	}

	public String substringData(int offset, int count)
			throws DOMException {
		return this.text.substring(offset, offset + count);
	}

	public String toString() {
		String someText = this.text;
		int length = someText.length();
		if(someText != null && someText.length() > 32) {
			someText = someText.substring(0, 29) + "...";
		}
		return this.getNodeName() + "[length=" + length + ",text=" + someText + "]";
	}
	
}
