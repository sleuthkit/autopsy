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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.logging.*;

import org.lobobrowser.html.*;
import org.lobobrowser.html.style.*;
import org.lobobrowser.js.*;
import org.lobobrowser.util.*;
import org.w3c.dom.*;

public abstract class NodeImpl extends AbstractScriptableDelegate implements Node, ModelNode {
	private static final NodeImpl[] EMPTY_ARRAY = new NodeImpl[0];
	private static final RenderState INVALID_RENDER_STATE = new StyleSheetRenderState(null);
	protected static final Logger logger = Logger.getLogger(NodeImpl.class.getName());
	protected UINode uiNode;	
	protected ArrayList nodeList;	
	protected volatile Document document;
	
	/**
	 * A tree lock is less deadlock-prone than
	 * a node-level lock. This is assigned in
	 * setOwnerDocument.
	 */
	protected volatile Object treeLock = this;
	
	public NodeImpl() {
		super();
	}

	public void setUINode(UINode uiNode) {
		// Called in GUI thread always.
		this.uiNode = uiNode;
	}
	
	public UINode getUINode() {
		// Called in GUI thread always.
		return this.uiNode;
	}
	
	/**
	 * Tries to get a UINode associated with the current node.
	 * Failing that, it tries ancestors recursively. This method
	 * will return the closest <i>block-level</i> renderer node, if any.
	 */
	public UINode findUINode() {
		// Called in GUI thread always.
		UINode uiNode = this.uiNode;
		if(uiNode != null) {
			return uiNode;
		}
		NodeImpl parentNode = (NodeImpl) this.getParentNode();
		return parentNode == null ? null : parentNode.findUINode();
	}
		
	public Node appendChild(Node newChild)
			throws DOMException {
		synchronized(this.treeLock) {
			ArrayList nl = this.nodeList;
			if(nl == null) {
				nl = new ArrayList(3);
				this.nodeList = nl;
			}
			nl.add(newChild);
			if(newChild instanceof NodeImpl) {
				((NodeImpl) newChild).setParentImpl(this);
			}
		}
				
		if(!this.notificationsSuspended) {
			this.informStructureInvalid();
		}
		return newChild;
	}
	
	protected void removeAllChildren() {
		synchronized(this.treeLock) {
			this.removeAllChildrenImpl();
		}		
	}

	protected void removeAllChildrenImpl() {
		synchronized(this.treeLock) {
			ArrayList nl = this.nodeList;
			if(nl != null) {
				nl.clear();
				//this.nodeList = null;
			}
		}		
		if(!this.notificationsSuspended) {
			this.informStructureInvalid();
		}
	}

	protected NodeList getNodeList(NodeFilter filter) {
		Collection collection = new ArrayList();
		synchronized(this.treeLock) {
			this.appendChildrenToCollectionImpl(filter, collection);
		}		
		return new NodeListImpl(collection);
	}

	public NodeImpl[] getChildrenArray() {
		ArrayList nl = this.nodeList;
		synchronized(this.treeLock) {
			return nl == null ? null : (NodeImpl[]) nl.toArray(NodeImpl.EMPTY_ARRAY);
		}
	}
	
	int getChildCount() {
		ArrayList nl = this.nodeList;
		synchronized(this.treeLock) {
			return nl == null ? 0 : nl.size();
		}
	}
	
	private ChildHTMLCollection childrenCollection;	
	public ChildHTMLCollection getChildren() {
		// Method required by JavaScript
		synchronized(this) {
			ChildHTMLCollection collection = this.childrenCollection;
			if(collection == null) {
				collection = new ChildHTMLCollection(this);
				this.childrenCollection = collection;
			}
			return collection;
		}
	}
	
	/**
	 * Creates an <code>ArrayList</code> of descendent nodes that 
	 * the given filter condition.
	 */
	public ArrayList getDescendents(NodeFilter filter, boolean nestIntoMatchingNodes) {
		ArrayList al = new ArrayList();
		synchronized(this.treeLock) {
			this.extractDescendentsArrayImpl(filter, al, nestIntoMatchingNodes);
		}
		return al;
	}
	
	/**
	 * Extracts all descendents that match the filter, except those 
	 * descendents of nodes that match the filter.
	 * @param filter
	 * @param al
	 */
	private void extractDescendentsArrayImpl(NodeFilter filter, ArrayList al, boolean nestIntoMatchingNodes) {
		ArrayList nl = this.nodeList;
		if(nl != null) {
			Iterator i = nl.iterator();
			while(i.hasNext()) {
				NodeImpl n = (NodeImpl) i.next();
				if(filter.accept(n)) {
					al.add(n);
					if(nestIntoMatchingNodes) {
						n.extractDescendentsArrayImpl(filter, al, nestIntoMatchingNodes);						
					}
				}
				else if(n.getNodeType() == Node.ELEMENT_NODE) {
					n.extractDescendentsArrayImpl(filter, al, nestIntoMatchingNodes);
				}
			}
		}
	}

	private void appendChildrenToCollectionImpl(NodeFilter filter, Collection collection) {
		ArrayList nl = this.nodeList;
		if(nl != null) {
			Iterator i = nl.iterator();
			while(i.hasNext()) {
				NodeImpl node = (NodeImpl) i.next();
				if(filter.accept(node)) {
					collection.add(node);
				}
				node.appendChildrenToCollectionImpl(filter, collection);
			}
		}
	}

	/**
	 * Should create a node with some cloned properties, like the node name,
	 * but not attributes or children.
	 */
	protected abstract Node createSimilarNode();
	
	public Node cloneNode(boolean deep) {
		try {
			Node newNode = this.createSimilarNode();
			NodeList children = this.getChildNodes();
			int length = children.getLength();
			for(int i = 0; i < length; i++) {
				Node child = (Node) children.item(i);
				Node newChild = deep ? child.cloneNode(deep) : child;
				newNode.appendChild(newChild);
			}
			if(newNode instanceof Element) {
				Element elem = (Element) newNode;
				NamedNodeMap nnmap = this.getAttributes();
				if(nnmap != null) {
					int nnlength = nnmap.getLength();
					for(int i = 0; i < nnlength; i++) {
						Attr attr = (Attr) nnmap.item(i);
						elem.setAttributeNode((Attr) attr.cloneNode(true));
					}
				}
			}
			
			synchronized(this){
				if (userDataHandlers != null && userData != null) {
					for(Iterator handlers = userDataHandlers.entrySet().iterator();handlers.hasNext();){
						Map.Entry entry = (Map.Entry) handlers.next();    				
						UserDataHandler handler = (UserDataHandler)entry.getValue();     				
						handler.handle(UserDataHandler.NODE_CLONED,(String) entry.getKey(),userData.get(entry.getKey()), this, newNode);    				     				
					}
				}
			}
						
			return newNode;
		} catch(Exception err) {
			throw new IllegalStateException(err.getMessage());
		}
	}

	private int getNodeIndex() {
		NodeImpl parent = (NodeImpl) this.getParentNode();
		return parent == null ? -1 : parent.getChildIndex(this);
	}
	
	int getChildIndex(Node child) {
		synchronized(this.treeLock) {
			ArrayList nl = this.nodeList;
			return nl == null ? -1 : nl.indexOf(child);
		}
	}

	Node getChildAtIndex(int index) {
		synchronized(this.treeLock) {
			ArrayList nl = this.nodeList;
			try {
				return nl == null ? null : (Node) nl.get(index);
			} catch(IndexOutOfBoundsException iob) {
				this.warn("getChildAtIndex(): Bad index=" + index + " for node=" + this + ".");
				return null;
			}
		}		
	}
	
	private boolean isAncestorOf(Node other) {
		NodeImpl parent = (NodeImpl) other.getParentNode();
		if(parent == this) {
			return true;
		}
		else if(parent == null) {
			return false;
		}
		else {
			return this.isAncestorOf(parent);
		}
	}
	
	public short compareDocumentPosition(Node other)
			throws DOMException {
		Node parent = this.getParentNode();
		if(!(other instanceof NodeImpl)) {
			throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "Unknwon node implementation");
		}
		if(parent != null && parent == other.getParentNode()) {
			int thisIndex = this.getNodeIndex();
			int otherIndex = ((NodeImpl) other).getNodeIndex();
			if(thisIndex == -1 || otherIndex == -1) {
				return Node.DOCUMENT_POSITION_IMPLEMENTATION_SPECIFIC;
			}
			if(thisIndex < otherIndex) {
				return Node.DOCUMENT_POSITION_FOLLOWING;
			}
			else {
				return Node.DOCUMENT_POSITION_PRECEDING;
			}
		}
		else if(this.isAncestorOf(other)) {
			return Node.DOCUMENT_POSITION_CONTAINED_BY;
		}
		else if(((NodeImpl) other).isAncestorOf(this)) {
			return Node.DOCUMENT_POSITION_CONTAINS;
		}
		else {
			return Node.DOCUMENT_POSITION_DISCONNECTED;
		}
	}

	public NamedNodeMap getAttributes() {
		return null;
	}
	
	public Document getOwnerDocument() {
		return this.document;
	}

	void setOwnerDocument(Document value) {
	    this.document = value;
	    this.treeLock = value == null ? this : (Object) value;
	}
	
	void setOwnerDocument(Document value, boolean deep) {
		this.document = value;
	    this.treeLock = value == null ? this : (Object) value;
		if(deep) {
			synchronized(this.treeLock) {
				ArrayList nl = this.nodeList;
				if(nl != null) {
					Iterator i = nl.iterator();
					while(i.hasNext()) {
						NodeImpl child = (NodeImpl) i.next();
						child.setOwnerDocument(value, deep);
					}
				}
			}
		}
	}
	
	void visitImpl(NodeVisitor visitor) {
		try {
		    visitor.visit(this);
		} catch(SkipVisitorException sve) {
			return;
		} catch(StopVisitorException sve) {
			throw sve;
		}
		ArrayList nl = this.nodeList;
		if(nl != null) {
			Iterator i = nl.iterator();
			while(i.hasNext()) {
				NodeImpl child = (NodeImpl) i.next();
				try {
					// Call with child's synchronization
					child.visit(visitor);
				} catch(StopVisitorException sve) {
					throw sve;
				}
			}
		}
	}
	
	void visit(NodeVisitor visitor) {
		synchronized(this.treeLock) {
			this.visitImpl(visitor);
		}
	}
	
	public Node insertBefore(Node newChild, Node refChild)
			throws DOMException {
		synchronized(this.treeLock) {
			ArrayList nl = this.nodeList;
			int idx = nl == null ? -1 : nl.indexOf(refChild);
			if(idx == -1) {
				throw new DOMException(DOMException.NOT_FOUND_ERR, "refChild not found");
			}
			nl.add(idx, newChild);
			if(newChild instanceof NodeImpl) {
				((NodeImpl) newChild).setParentImpl(this);
			}
		}
		if(!this.notificationsSuspended) {
			this.informStructureInvalid();
		}
		return newChild;
	}

	protected Node insertAt(Node newChild, int idx)
	throws DOMException {
		synchronized(this.treeLock) {
			ArrayList nl = this.nodeList;
			if(nl == null) {
				nl = new ArrayList();
				this.nodeList = nl;
			}
			nl.add(idx, newChild);
			if(newChild instanceof NodeImpl) {
				((NodeImpl) newChild).setParentImpl(this);
			}
		}
		if(!this.notificationsSuspended) {
			this.informStructureInvalid();
		}
		return newChild;
	}

	public Node replaceChild(Node newChild, Node oldChild)
			throws DOMException {
		synchronized(this.treeLock) {
			ArrayList nl = this.nodeList;
			int idx = nl == null ? -1 : nl.indexOf(oldChild);
			if(idx == -1) {
				throw new DOMException(DOMException.NOT_FOUND_ERR, "oldChild not found");
			}
			nl.set(idx, newChild);
		}
		if(!this.notificationsSuspended) {
			this.informStructureInvalid();
		}
		return newChild;
	}

	public Node removeChild(Node oldChild)
			throws DOMException {
		synchronized(this.treeLock) {
			ArrayList nl = this.nodeList;
			if(nl == null || !nl.remove(oldChild)) {
				throw new DOMException(DOMException.NOT_FOUND_ERR, "oldChild not found");
			}
		}
		if(!this.notificationsSuspended) {
			this.informStructureInvalid();
		}
		return oldChild;
	}

	public Node removeChildAt(int index)
	throws DOMException {
		try { 
			synchronized(this.treeLock) {
				ArrayList nl = this.nodeList;
				if(nl == null) {
					throw new DOMException(DOMException.INDEX_SIZE_ERR, "Empty list of children");
				}
				Node n = (Node) nl.remove(index);
				if (n == null) {
					throw new DOMException(DOMException.INDEX_SIZE_ERR, "No node with that index");
				}
				return n;
			}
		} finally {
			if(!this.notificationsSuspended) {
				this.informStructureInvalid();
			}
		}
	}

	public boolean hasChildNodes() {
		synchronized(this.treeLock) {
			ArrayList nl = this.nodeList;
			return nl != null && !nl.isEmpty();
		}
	}

	public String getBaseURI() {
		Document document = this.document;
		return document == null ? null : document.getBaseURI();
	}

	public NodeList getChildNodes() {
		synchronized(this.treeLock) {
			ArrayList nl = this.nodeList;
			return new NodeListImpl(nl == null ? Collections.EMPTY_LIST : nl);
		}
	}

	public Node getFirstChild() {
		synchronized(this.treeLock) {
			ArrayList nl = this.nodeList;
			try {
				return nl == null ? null : (Node) nl.get(0);
			} catch(IndexOutOfBoundsException iob) {
				return null;
			}
		}
	}

	public Node getLastChild() {
		synchronized(this.treeLock) {
			ArrayList nl = this.nodeList;
			try {
				return nl == null ? null : (Node) nl.get(nl.size() - 1);
			} catch(IndexOutOfBoundsException iob) {
				return null;
			}
		}
	}

	private Node getPreviousTo(Node node) {
		synchronized(this.treeLock) {
			ArrayList nl = this.nodeList;
			int idx = nl == null ? -1 : nl.indexOf(node);
			if(idx == -1) {
				throw new DOMException(DOMException.NOT_FOUND_ERR, "node not found");
			}
			try {
				return (Node) nl.get(idx-1);
			} catch(IndexOutOfBoundsException iob) {
				return null;
			}
		}
	}
	
	private Node getNextTo(Node node) {
		synchronized(this.treeLock) {
			ArrayList nl = this.nodeList;
			int idx = nl == null ? -1 : nl.indexOf(node);
			if(idx == -1) {
				throw new DOMException(DOMException.NOT_FOUND_ERR, "node not found");
			}
			try {
				return (Node) nl.get(idx+1);
			} catch(IndexOutOfBoundsException iob) {
				return null;
			}
		}
	}

	public Node getPreviousSibling() {
		NodeImpl parent = (NodeImpl) this.getParentNode();
		return parent == null ? null : parent.getPreviousTo(this);
	}

	public Node getNextSibling() {
		NodeImpl parent = (NodeImpl) this.getParentNode();
		return parent == null ? null : parent.getNextTo(this);
	}

	public Object getFeature(String feature, String version) {
		//TODO What should this do?
		return null;
	}

	private Map userData;
	//TODO: Inform handlers on cloning, etc.
	private Map userDataHandlers;
	protected volatile boolean notificationsSuspended = false;
	
	public Object setUserData(String key, Object data,
			UserDataHandler handler) {
		if(org.lobobrowser.html.parser.HtmlParser.MODIFYING_KEY.equals(key)) {
			boolean ns = (Boolean.TRUE == data);
			this.notificationsSuspended = ns;
			if(!ns) {
				this.informNodeLoaded();
			}
		}
		//here we spent some effort preventing our maps from growing too much
		synchronized(this) {
			if(handler != null) {
				if(this.userDataHandlers == null) {
					this.userDataHandlers = new HashMap();
				}	
				else if (handler == null){
					this.userDataHandlers.remove(key);
				}
				if (handler != null)
					this.userDataHandlers.put(key,handler);
			}
						
			Map userData = this.userData;
			if (data != null){
				if(userData == null) {
					userData = new HashMap();
					this.userData = userData;
				}
				return userData.put(key, data);
			}
			else if (userData != null)
				return userData.remove(key);
			else
				return null;
		}
	}

	public Object getUserData(String key) {
		synchronized(this) {
			Map ud = this.userData;
			return ud == null ? null : ud.get(key);
		}
	}

	public abstract String getLocalName();

	public boolean hasAttributes() {
		return false;
	}

	public String getNamespaceURI() {
		return null;
	}

	public abstract String getNodeName();

	public abstract String getNodeValue() throws DOMException;

	private volatile String prefix;
	
	public String getPrefix() {
		return this.prefix;
	}

	public void setPrefix(String prefix) throws DOMException {
		this.prefix = prefix;
	}

	public abstract void setNodeValue(String nodeValue) throws DOMException;

	public abstract short getNodeType();

	/**
	 * Gets the text content of this node
	 * and its descendents.
	 */
	public String getTextContent() throws DOMException {
		StringBuffer sb = new StringBuffer();
		synchronized(this.treeLock) {
			ArrayList nl = this.nodeList;
			if(nl != null) {
				Iterator i = nl.iterator();
				while(i.hasNext()) {
					Node node = (Node) i.next();
					short type = node.getNodeType();
					switch(type) {
					case Node.CDATA_SECTION_NODE:
					case Node.TEXT_NODE:
					case Node.ELEMENT_NODE:
						String textContent = node.getTextContent();
						if(textContent != null) {
							sb.append(textContent);
						}
						break;
					default:
						break;
					}
				}
			}
		}
		return sb.toString();
	}

	public void setTextContent(String textContent) throws DOMException {
		synchronized(this.treeLock) {
			this.removeChildrenImpl(new TextFilter());
			if(textContent != null && !"".equals(textContent)) {
				TextImpl t = new TextImpl(textContent);
				t.setOwnerDocument(this.document);
				t.setParentImpl(this);
				ArrayList nl = this.nodeList;
				if(nl == null) {
					nl = new ArrayList();
					this.nodeList = nl;
				}
				nl.add(t);
			}
		}
		if(!this.notificationsSuspended) {
			this.informStructureInvalid();
		}
	}
	
	protected void removeChildren(NodeFilter filter) {
		synchronized(this.treeLock) {
			this.removeChildrenImpl(filter);
		}
		if(!this.notificationsSuspended) {
			this.informStructureInvalid();
		}
	}
	
	protected void removeChildrenImpl(NodeFilter filter) {
		ArrayList nl = this.nodeList;
		if(nl != null) {
			int len = nl.size();
			for(int i = len; --i >= 0;) {
				Node node = (Node) nl.get(i);
				if(filter.accept(node)) {
					nl.remove(i);
				}
			}
		}
	}
	
	public Node insertAfter(Node newChild, Node refChild) {
		synchronized(this.treeLock) {
			ArrayList nl = this.nodeList;
			int idx = nl == null ? -1 : nl.indexOf(refChild);
			if(idx == -1) {
				throw new DOMException(DOMException.NOT_FOUND_ERR, "refChild not found");
			}
			nl.add(idx+1, newChild);
			if(newChild instanceof NodeImpl) {
				((NodeImpl) newChild).setParentImpl(this);
			}
		}
		if(!this.notificationsSuspended) {
			this.informStructureInvalid();
		}
		return newChild;
	}

	public Text replaceAdjacentTextNodes(Text node, String textContent) {
		try {
			synchronized(this.treeLock) {
				ArrayList nl = this.nodeList;
				if(nl == null) {
					throw new DOMException(DOMException.NOT_FOUND_ERR, "Node not a child");				
				}
				int idx = nl.indexOf(node);
				if(idx == -1) {
					throw new DOMException(DOMException.NOT_FOUND_ERR, "Node not a child");
				}
				int firstIdx = idx;
				List toDelete = new LinkedList();
				for(int adjIdx = idx; --adjIdx >= 0;) {
					Object child = this.nodeList.get(adjIdx);
					if(child instanceof Text) {
						firstIdx = adjIdx;
						toDelete.add(child);
					}
				}
				int length = this.nodeList.size();
				for(int adjIdx = idx; ++adjIdx < length;) {
					Object child = this.nodeList.get(adjIdx);
					if(child instanceof Text) {
						toDelete.add(child);
					}
				}
				this.nodeList.removeAll(toDelete);
				TextImpl textNode = new TextImpl(textContent); 
				textNode.setOwnerDocument(this.document);
				textNode.setParentImpl(this);
				this.nodeList.add(firstIdx, textNode);
				return textNode;
			}		
		} finally {
			if(!this.notificationsSuspended) {
				this.informStructureInvalid();
			}
		}
	}

	public Text replaceAdjacentTextNodes(Text node) {
		try {
			synchronized(this.treeLock) {
				ArrayList nl = this.nodeList;
				if(nl == null) {
					throw new DOMException(DOMException.NOT_FOUND_ERR, "Node not a child");				
				}
				int idx = nl.indexOf(node);
				if(idx == -1) {
					throw new DOMException(DOMException.NOT_FOUND_ERR, "Node not a child");
				}
				StringBuffer textBuffer = new StringBuffer();
				int firstIdx = idx;
				List toDelete = new LinkedList();
				for(int adjIdx = idx; --adjIdx >= 0;) {
					Object child = this.nodeList.get(adjIdx);
					if(child instanceof Text) {
						firstIdx = adjIdx;
						toDelete.add(child);
						textBuffer.append(((Text) child).getNodeValue());
					}
				}
				int length = this.nodeList.size();
				for(int adjIdx = idx; ++adjIdx < length;) {
					Object child = this.nodeList.get(adjIdx);
					if(child instanceof Text) {
						toDelete.add(child);
						textBuffer.append(((Text) child).getNodeValue());
					}
				}
				this.nodeList.removeAll(toDelete);
				TextImpl textNode = new TextImpl(textBuffer.toString()); 
				textNode.setOwnerDocument(this.document);
				textNode.setParentImpl(this);
				this.nodeList.add(firstIdx, textNode);
				return textNode;
			}		
		} finally {
			if(!this.notificationsSuspended) {
				this.informStructureInvalid();
			}
		}
	}
		
	protected volatile Node parentNode;
	
	public Node getParentNode() {
		// Should it be synchronized? Could have side-effects.
		return this.parentNode;
	}
	
	public boolean isSameNode(Node other) {
		return this == other;
	}

	public boolean isSupported(String feature, String version) {
		return ("HTML".equals(feature) && version.compareTo("4.01") <= 0);
	}

	public String lookupNamespaceURI(String prefix) {
		return null;
	}

	public boolean equalAttributes(Node arg) {
		return false;
	}
	
	public boolean isEqualNode(Node arg) {
		return arg instanceof NodeImpl &&
			this.getNodeType() == arg.getNodeType() &&
			Objects.equals(this.getNodeName(), arg.getNodeName()) &&
			Objects.equals(this.getNodeValue(), arg.getNodeValue()) &&
			Objects.equals(this.getLocalName(), arg.getLocalName()) &&
			Objects.equals(this.nodeList, ((NodeImpl) arg).nodeList) &&
			this.equalAttributes(arg);
	}

	public boolean isDefaultNamespace(String namespaceURI) {
		return namespaceURI == null;
	}

	public String lookupPrefix(String namespaceURI) {
		return null;
	}

	public void normalize() {
		synchronized(this.treeLock) {
			ArrayList nl = this.nodeList;
			if(nl != null) {
				Iterator i = nl.iterator();
				List textNodes = new LinkedList();
				boolean prevText = false;
				while(i.hasNext()) {
					Node child = (Node) i.next();
					if(child.getNodeType() == Node.TEXT_NODE) {
						if(!prevText) {
							prevText = true;
							textNodes.add(child);
						}
					}
					else {
						prevText = false;
					}
				}
				i = textNodes.iterator();
				while(i.hasNext()) {
					Text text = (Text) i.next();
					this.replaceAdjacentTextNodes(text);
				}
			}
		}		
		if(!this.notificationsSuspended) {
			this.informStructureInvalid();
		}
	}
	
	public String toString() {
		return this.getNodeName();
	}
	
	public UserAgentContext getUserAgentContext() {
		Object doc = this.document;
		if(doc instanceof HTMLDocumentImpl) {
			return ((HTMLDocumentImpl) doc).getUserAgentContext();
		}
		else {
			return null;
		}
	}
	
	public HtmlRendererContext getHtmlRendererContext() {
		Object doc = this.document;
		if(doc instanceof HTMLDocumentImpl) {
			return ((HTMLDocumentImpl) doc).getHtmlRendererContext();
		}
		else {
			return null;
		}
	}

	final void setParentImpl(Node parent) {
		// Call holding treeLock.
		this.parentNode = parent;
	}
	
	//----- ModelNode implementation
	
	/* (non-Javadoc)
	 * @see org.xamjwg.html.renderer.RenderableContext#getAlignmentX()
	 */
	public float getAlignmentX() {
		//TODO: Removable method?
		return 0.5f;
	}

	/* (non-Javadoc)
	 * @see org.xamjwg.html.renderer.RenderableContext#getAlignmentY()
	 */
	public float getAlignmentY() {
		return 0.5f;
	}

	/* (non-Javadoc)
	 * @see org.xamjwg.html.renderer.RenderableContext#getFullURL(java.lang.String)
	 */
	public URL getFullURL(String spec) throws MalformedURLException {
		Object doc = this.document;
		if(doc instanceof HTMLDocumentImpl) {
			return ((HTMLDocumentImpl) doc).getFullURL(spec);
		}
		else {
			return new java.net.URL(spec);
		}
	}
	
	public URL getDocumentURL() {
		Object doc = this.document;
		if(doc instanceof HTMLDocumentImpl) {
			return ((HTMLDocumentImpl) doc).getDocumentURL();
		}
		else {
			return null;
		}		
	}
	
	/* (non-Javadoc)
	 * @see org.xamjwg.html.renderer.RenderableContext#getDocumentItem(java.lang.String)
	 */
	public Object getDocumentItem(String name) {
		org.w3c.dom.Document document = this.document;
		return document == null ? null : document.getUserData(name);
	}
	
	/* (non-Javadoc)
	 * @see org.xamjwg.html.renderer.RenderableContext#setDocumentItem(java.lang.String, java.lang.Object)
	 */
	public void setDocumentItem(String name, Object value) {
		org.w3c.dom.Document document = this.document;
		if(document == null) {
			return;
		}
		document.setUserData(name, value, null);
	}	

	/* (non-Javadoc)
	 * @see org.xamjwg.html.renderer.RenderableContext#isEqualOrDescendentOf(org.xamjwg.html.renderer.RenderableContext)
	 */
	public final boolean isEqualOrDescendentOf(ModelNode otherContext) {
		if(otherContext == this) {
			return true;
		}
		Object parent = this.getParentNode();
		if(parent instanceof HTMLElementImpl) {
			return ((HTMLElementImpl) parent).isEqualOrDescendentOf(otherContext);
		}
		else {
			return false;
		}
	}
	
	public final ModelNode getParentModelNode() {
		return (ModelNode) this.parentNode;
	}

	public void warn(String message, Throwable err) {
		logger.log(Level.WARNING, message, err);
	}
	
	public void warn(String message) {
		logger.log(Level.WARNING, message);
	}

	public void informSizeInvalid() {
		HTMLDocumentImpl doc = (HTMLDocumentImpl) this.document;
		if(doc != null) {
			doc.sizeInvalidated(this);
		}
	}
	
	public void informLookInvalid() {
		this.forgetRenderState();
		HTMLDocumentImpl doc = (HTMLDocumentImpl) this.document;
		if(doc != null) {
			doc.lookInvalidated(this);
		}
	}
	
	public void informPositionInvalid() {		
		HTMLDocumentImpl doc = (HTMLDocumentImpl) this.document;
		if(doc != null) {
			doc.positionInParentInvalidated(this);
		}
	}
	
	public void informInvalid() {
		// This is called when an attribute or child changes.
		this.forgetRenderState();
		HTMLDocumentImpl doc = (HTMLDocumentImpl) this.document;
		if(doc != null) {
			doc.invalidated(this);
		}
	}

	public void informStructureInvalid() {
		// This is called when an attribute or child changes.
		this.forgetRenderState();
		HTMLDocumentImpl doc = (HTMLDocumentImpl) this.document;
		if(doc != null) {
			doc.structureInvalidated(this);
		}
	}

	protected void informNodeLoaded() {
		// This is called when an attribute or child changes.
		this.forgetRenderState();
		HTMLDocumentImpl doc = (HTMLDocumentImpl) this.document;
		if(doc != null) {
			doc.nodeLoaded(this);
		}
	}

	protected void informExternalScriptLoading() {
		// This is called when an attribute or child changes.
		this.forgetRenderState();
		HTMLDocumentImpl doc = (HTMLDocumentImpl) this.document;
		if(doc != null) {
			doc.externalScriptLoading(this);
		}
	}

	public void informLayoutInvalid() {
		// This is called by the style properties object.
		this.forgetRenderState();
		HTMLDocumentImpl doc = (HTMLDocumentImpl) this.document;
		if(doc != null) {
			doc.invalidated(this);
		}
	}

	public void informDocumentInvalid() {
		// This is called when an attribute or child changes.
		HTMLDocumentImpl doc = (HTMLDocumentImpl) this.document;
		if(doc != null) {
			doc.allInvalidated(true);
		}
	}

	private RenderState renderState = INVALID_RENDER_STATE;
	
	public RenderState getRenderState() {
		// Generally called from the GUI thread, except for
		// offset properties.
		RenderState rs;
		synchronized(this.treeLock) {
			rs = this.renderState;
			if(rs != INVALID_RENDER_STATE) {
				return rs;
			}
			Object parent = this.parentNode;
			if(parent != null || this instanceof Document) {
				RenderState prs = this.getParentRenderState(parent);
				rs = this.createRenderState(prs);
				this.renderState = rs;
				return rs;
			}
			else {
				// Return null without caching.
				// Scenario is possible due to Javascript.
				return null;
			}
		}
	}

	protected final RenderState getParentRenderState(Object parent) {
		if(parent instanceof NodeImpl) {
			return ((NodeImpl) parent).getRenderState();
		}
		else {
			return null;
		}
	}
	
	protected RenderState createRenderState(RenderState prevRenderState) {
		return prevRenderState;
	}
	
	protected void forgetRenderState() {
		synchronized(this.treeLock) {
			if(this.renderState != INVALID_RENDER_STATE) {
				this.renderState = INVALID_RENDER_STATE;
				// Note that getRenderState() "validates"
				// ancestor states as well.
				java.util.ArrayList nl = this.nodeList;
				if(nl != null) {
					Iterator i = nl.iterator();
					while(i.hasNext()) {
						((NodeImpl) i.next()).forgetRenderState();
					}				
				}
			}
		}
	}

	public String getInnerHTML() {
		StringBuffer buffer = new StringBuffer();
		synchronized(this) {
			this.appendInnerHTMLImpl(buffer);
		}
		return buffer.toString();
	}
	
	protected void appendInnerHTMLImpl(StringBuffer buffer) {
		ArrayList nl = this.nodeList;
		int size;
		if (nl != null && (size = nl.size()) > 0) {
			for (int i = 0; i < size; i++) {
				Node child = (Node) nl.get(i);
				if (child instanceof HTMLElementImpl) {
					((HTMLElementImpl) child).appendOuterHTMLImpl(buffer);
				}
				else if(child instanceof Comment) {
					buffer.append("<!--" + ((Comment) child).getTextContent() + "-->");
				}
				else if (child instanceof Text) {
					String text = ((Text) child).getTextContent();
					String encText = this.htmlEncodeChildText(text);
					buffer.append(encText);
				}
				else if (child instanceof ProcessingInstruction) {
					buffer.append(child.toString());
				}
			}
		}
	}
	
	protected String htmlEncodeChildText(String text) {
		return Strings.strictHtmlEncode(text, false);
	}

	/**
	 * Attempts to convert the subtree starting at this point to a close text
	 * representation. BR elements are converted to line breaks, and so forth.
	 */
	public String getInnerText() {
		StringBuffer buffer = new StringBuffer();
		synchronized(this.treeLock) {
			this.appendInnerTextImpl(buffer);
		}
		return buffer.toString();
	}

	protected void appendInnerTextImpl(StringBuffer buffer) {
		ArrayList nl = this.nodeList;
		if (nl == null) {
			return;
		}
		int size = nl.size();
		if (size == 0) {
			return;
		}
		for (int i = 0; i < size; i++) {
			Node child = (Node) nl.get(i);
			if (child instanceof ElementImpl) {
				((ElementImpl) child).appendInnerTextImpl(buffer);
			}
			if(child instanceof Comment) {
				// skip
			}
			else if (child instanceof Text) {
				buffer.append(((Text) child).getTextContent());
			}
		}
	}
}
