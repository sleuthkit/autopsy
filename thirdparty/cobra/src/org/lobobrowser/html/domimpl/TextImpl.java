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
 * Created on Sep 4, 2005
 */
package org.lobobrowser.html.domimpl;

import org.w3c.dom.*;
import org.lobobrowser.util.*;

public class TextImpl extends CharacterDataImpl implements Text {
	public TextImpl() {
		this("");
	}

	public TextImpl(String text) {
		this.text = text;
	}

	
	/* (non-Javadoc)
	 * @see org.w3c.dom.html2.Text#isElementContentWhitespace()
	 */
	public boolean isElementContentWhitespace() {
		String t = this.text;
		return t == null || t.trim().equals("");
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.html2.Text#replaceWholeText(java.lang.String)
	 */
	public Text replaceWholeText(String content) throws DOMException {
		NodeImpl parent = (NodeImpl) this.getParentNode();
		if(parent == null) {
			throw new DOMException(DOMException.HIERARCHY_REQUEST_ERR, "Text node has no parent");
		}
		return parent.replaceAdjacentTextNodes(this, content);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.html2.Text#splitText(int)
	 */
	public Text splitText(int offset) throws DOMException {
		NodeImpl parent = (NodeImpl) this.getParentNode();
		if(parent == null) {
			throw new DOMException(DOMException.HIERARCHY_REQUEST_ERR, "Text node has no parent");
		}
		String t = this.text;
		if(offset < 0 || offset > t.length()) {
			throw new DOMException(DOMException.INDEX_SIZE_ERR, "Bad offset: " + offset);
		}
		String content1 = t.substring(0, offset);
		String content2 = t.substring(offset);
		this.text = content1;
		TextImpl newNode = new TextImpl(content2);
		newNode.setOwnerDocument(this.document);
		return (Text) parent.insertAfter(newNode, this);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.html2.Text#getwholeText()
	 */
	public String getWholeText() {
		NodeImpl parent = (NodeImpl) this.getParentNode();
		if(parent == null) {
			throw new DOMException(DOMException.HIERARCHY_REQUEST_ERR, "Text node has no parent");
		}
		return parent.getTextContent();
	}

	/* (non-Javadoc)
	 * @see org.xamjwg.html.domimpl.NodeImpl#getlocalName()
	 */
	public String getLocalName() {
		return null;
	}

	/* (non-Javadoc)
	 * @see org.xamjwg.html.domimpl.NodeImpl#getnodeName()
	 */
	public String getNodeName() {
		return "#text";
	}
	
	

	/* (non-Javadoc)
	 * @see org.xamjwg.html.domimpl.NodeImpl#getnodeType()
	 */
	public short getNodeType() {
		return Node.TEXT_NODE;
	}

	/* (non-Javadoc)
	 * @see org.xamjwg.html.domimpl.NodeImpl#getnodeValue()
	 */
	public String getNodeValue() throws DOMException {
		return this.text;
	}

	/* (non-Javadoc)
	 * @see org.xamjwg.html.domimpl.NodeImpl#setnodeValue(java.lang.String)
	 */
	public void setNodeValue(String nodeValue) throws DOMException {
		this.text = nodeValue;
	}

	public void setTextContent(String textContent) throws DOMException {
		this.text = textContent;
	}

	protected Node createSimilarNode() {
		return new TextImpl(this.text);
	}

	public String toString() {
		String text = this.text;
		int textLength = text == null ? 0 : text.length();
		return "#text[length=" + textLength + ",value=\"" + Strings.truncate(text, 64) + "\",renderState=" + this.getRenderState() + "]";
	}
}
