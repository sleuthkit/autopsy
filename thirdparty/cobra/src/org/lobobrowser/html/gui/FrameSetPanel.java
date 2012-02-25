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
 * Created on Jan 29, 2006
 */
package org.lobobrowser.html.gui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.StringTokenizer;

import javax.swing.*;
import java.util.logging.*;

import org.lobobrowser.html.*;
import org.lobobrowser.html.domimpl.*;
import org.lobobrowser.html.renderer.NodeRenderer;
import org.lobobrowser.html.style.HtmlLength;
import org.lobobrowser.util.gui.WrapperLayout;

/**
 * A Swing panel used to render FRAMESETs only. It is
 * used by {@link HtmlPanel} when a document is determined
 * to be a FRAMESET.
 * @see HtmlPanel
 * @see HtmlBlockPanel
 */
public class FrameSetPanel extends JComponent implements NodeRenderer {
	private static final Logger logger = Logger.getLogger(FrameSetPanel.class.getName());
	
	public FrameSetPanel() {
		super();
		this.setLayout(WrapperLayout.getInstance());
		//TODO: This should be a temporary preferred size
		this.setPreferredSize(new Dimension(600, 400));
	}

	private HtmlLength[] getLengths(String spec) {
		if(spec == null) {
			return new HtmlLength[] { new HtmlLength("1*") };
		}
		StringTokenizer tok = new StringTokenizer(spec, ",");
		ArrayList lengths = new ArrayList();
		while(tok.hasMoreTokens()) {
			String token = tok.nextToken().trim();
			try {
				lengths.add(new HtmlLength(token));
			} catch(Exception err) {
				logger.warning("Frame rows or cols value [" + spec + "] is invalid.");
			}			
		}
		return (HtmlLength[]) lengths.toArray(HtmlLength.EMPTY_ARRAY);
	}
	
	private HTMLElementImpl[] getSubFrames(HTMLElementImpl parent) {
		NodeImpl[] children = parent.getChildrenArray();
		ArrayList subFrames = new ArrayList();
		for(int i = 0; i < children.length; i++) {
			NodeImpl child = children[i];
			if(child instanceof HTMLElementImpl) {
				String nodeName = child.getNodeName();
				if("FRAME".equalsIgnoreCase(nodeName) || "FRAMESET".equalsIgnoreCase(nodeName)) {
					subFrames.add(child);
				}
			}
		}
		return (HTMLElementImpl[]) subFrames.toArray(new HTMLElementImpl[0]);
	}

	private HTMLElementImpl rootNode;
	
	/**
	 * Sets the FRAMESET node and invalidates the component
	 * so it can be rendered immediately in the GUI thread.
	 */
	public void setRootNode(NodeImpl node) {
		// Method expected to be called in the GUI thread.
		if(!(node instanceof HTMLElementImpl)) {
			throw new IllegalArgumentException("node=" + node);
		}
		HTMLElementImpl element = (HTMLElementImpl) node;
		this.rootNode = element;
		HtmlRendererContext context = element.getHtmlRendererContext();
		this.htmlContext = context;
		this.domInvalid = true;
		this.invalidate();
		this.validateAll();
		this.repaint();
	}
	
	protected void validateAll() {
		Component toValidate = this;
		for(;;) {
			Container parent = toValidate.getParent();
			if(parent == null || parent.isValid()) {
				break;
			}
			toValidate = parent;
		}
		toValidate.validate();
	}

	public final void processDocumentNotifications(DocumentNotification[] notifications) {
		// Called in the GUI thread.
		if(notifications.length > 0) {
			// Not very efficient, but it will do.
			this.domInvalid = true;
			this.invalidate();
			if(this.isVisible()) {
				this.validate();
				this.repaint();
			}
		}
	}
	
	private HtmlRendererContext htmlContext;
	private Component[] frameComponents;
	private boolean domInvalid = true;
	
	public void setBounds(int x, int y, int w, int h) {
		super.setBounds(x, y, w, h);
	}
	
	/**
	 * This method is invoked by AWT in the GUI thread
	 * to lay out the component. This implementation 
	 * is an override.
	 */
	public void doLayout() {
		if(this.domInvalid) {
			this.domInvalid = false;
			this.removeAll();
			HtmlRendererContext context = this.htmlContext;
			if(context != null) {
				HTMLElementImpl element = (HTMLElementImpl) this.rootNode;
				String rows = element.getAttribute("rows");
				String cols = element.getAttribute("cols");
				HtmlLength[] rowLengths = this.getLengths(rows);
				HtmlLength[] colLengths = this.getLengths(cols);
				HTMLElementImpl[] subframes = this.getSubFrames(element);
				Component[] frameComponents = new Component[subframes.length];
				this.frameComponents = frameComponents;
				for(int i = 0; i < subframes.length; i++) {
					HTMLElementImpl frameElement = subframes[i];
					if(frameElement != null && "FRAMESET".equalsIgnoreCase(frameElement.getTagName())) {
						FrameSetPanel fsp = new FrameSetPanel();
						fsp.setRootNode(frameElement);
						frameComponents[i] = fsp;
					}
					else {
						if(frameElement instanceof FrameNode) {
							BrowserFrame frame = context.createBrowserFrame();
							((FrameNode) frameElement).setBrowserFrame(frame);
							String src = frameElement.getAttribute("src");
							if(src != null) {
								java.net.URL url;
								try {
									url = frameElement.getFullURL(src);
									if(url != null) {
										frame.loadURL(url);
									}
								} catch(MalformedURLException mfu) {
									logger.warning("Frame URI=[" + src + "] is malformed.");
								}
							}
							frameComponents[i] = frame.getComponent();				
						}					
						else {
							frameComponents[i] = new JPanel();
						}
					}

				}
				HtmlLength[] rhl = rowLengths;
				HtmlLength[] chl = colLengths;
				Component[] fc = this.frameComponents;
				if(rhl != null && chl != null && fc != null) {
					Dimension size = this.getSize();
					Insets insets = this.getInsets();
					int width = size.width - insets.left - insets.right;
					int height = size.height - insets.left - insets.right;
					int[] absColLengths = this.getAbsoluteLengths(chl, width);
					int[] absRowLengths = this.getAbsoluteLengths(rhl, height);
					this.add(this.getSplitPane(this.htmlContext, absColLengths, 0, absColLengths.length, absRowLengths, 0, absRowLengths.length, fc));
				}
			}
		}
		super.doLayout();		
	}
	
	private int[] getAbsoluteLengths(HtmlLength[] htmlLengths, int totalSize) {
		int[] absLengths = new int[htmlLengths.length];
		int totalSizeNonMulti = 0;
		int sumMulti = 0;
		for(int i = 0; i < htmlLengths.length; i++) {
			HtmlLength htmlLength = htmlLengths[i];
			int lengthType = htmlLength.getLengthType();
			if(lengthType == HtmlLength.PIXELS) {
				int absLength = htmlLength.getRawValue();
				totalSizeNonMulti += absLength;
				absLengths[i] = absLength;
			}
			else if(lengthType == HtmlLength.LENGTH) {
				int absLength = htmlLength.getLength(totalSize);
				totalSizeNonMulti += absLength;
				absLengths[i] = absLength;				
			}
			else {
				sumMulti += htmlLength.getRawValue();
			}
		}
		int remaining = totalSize - totalSizeNonMulti;
		if(remaining > 0 && sumMulti > 0) {
			for(int i = 0; i < htmlLengths.length; i++) {
				HtmlLength htmlLength = htmlLengths[i];
				if(htmlLength.getLengthType() == HtmlLength.MULTI_LENGTH) {
					int absLength = (remaining * htmlLength.getRawValue()) / sumMulti;
					absLengths[i] = absLength;
				}
			}				
		}
		return absLengths;
	}
	
	private Component getSplitPane(HtmlRendererContext context, int[] colLengths, int firstCol, int numCols, int[] rowLengths, int firstRow, int numRows, Component[] frameComponents) {
		if(numCols == 1) {
			int frameindex = colLengths.length * firstRow + firstCol;
			Component topComponent = frameindex < frameComponents.length ? frameComponents[frameindex] : null;
			if(numRows == 1) {
				return topComponent;
			}
			else {
			    Component bottomComponent = this.getSplitPane(context, colLengths, firstCol, numCols, rowLengths, firstRow + 1, numRows - 1, frameComponents);
				JSplitPane sp = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topComponent, bottomComponent);
				sp.setDividerLocation(rowLengths[firstRow]);
				return sp;
			}
		}
		else {
			Component rightComponent = this.getSplitPane(context, colLengths, firstCol + 1, numCols - 1, rowLengths, firstRow, numRows, frameComponents);
			Component leftComponent = this.getSplitPane(context, colLengths, firstCol, 1, rowLengths, firstRow, numRows, frameComponents);
			JSplitPane sp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftComponent, rightComponent);
			sp.setDividerLocation(colLengths[firstCol]);
			return sp;
		}
	}
}
