/*
 * HtmlProcessingInstruction.java
 * Selima Prague FBI Project
 * 5th-March-2008
 */
package org.lobobrowser.html.domimpl;

import org.lobobrowser.html.domimpl.NodeImpl;
import org.w3c.dom.DOMException;
import org.w3c.dom.Node;
import org.w3c.dom.ProcessingInstruction;

/**
 * HTML DOM object representing processing instruction as per HTML 4.0 specification.
 * 
 * @author vitek
 */
public class HTMLProcessingInstruction extends NodeImpl 
	implements ProcessingInstruction, Cloneable
{
	String target;
	String data;
	
	public HTMLProcessingInstruction(String target,String data)
	{
		this.target = target;
		this.data = data;
	}
	
	protected Node createSimilarNode()
	{
		return (Node) clone();
	}

	public String getLocalName()
	{
		return target;
	}

	public String getNodeName()
	{
		return target;
	}

	public short getNodeType()
	{
		return Node.PROCESSING_INSTRUCTION_NODE;
	}

	public String getNodeValue() throws DOMException
	{
		return data;
	}

	public void setNodeValue(String nodeValue) throws DOMException
	{
		this.data = nodeValue;
	}

	public String getData()
	{
		return data;
	}

	public String getTarget()
	{
		return target;
	}

	public void setData(String data) throws DOMException
	{
		this.data = data;
	}

	public Object clone()
	{
		try
		{
			return (HTMLProcessingInstruction) super.clone();
		} 
		catch (CloneNotSupportedException e)
		{
			throw new IllegalStateException(e);
		}
	}
	
	public String toString()
	{
		return "<?"+target+" "+data+">";
	}
}
