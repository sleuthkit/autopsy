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
package org.lobobrowser.html.renderer;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Insets;
import java.awt.image.ImageObserver;
import java.awt.event.*;

import org.lobobrowser.html.domimpl.*;
import org.lobobrowser.html.style.HtmlValues;
import org.lobobrowser.util.gui.*;

class InputImageControl extends BaseInputControl implements ImageListener {
	//private JButton button;
	private boolean mouseBeingPressed;
	
	public InputImageControl(final HTMLBaseInputElement modelNode) {
		super(modelNode);
		this.setLayout(WrapperLayout.getInstance());
//		JButton button = new LocalButton();
//		this.button = button;
//		button.setMargin(RBlockViewport.ZERO_INSETS);
//		button.setBorder(null);
//		this.add(button);
		modelNode.addImageListener(this);
		this.addMouseListener(new MouseAdapter() {
			public void mousePressed(MouseEvent e) {
				mouseBeingPressed = true;
				repaint();
			}

//			public void mouseExited(MouseEvent e) {
//				mouseBeingPressed = false;
//				repaint();
//			}

			public void mouseReleased(MouseEvent e) {
				mouseBeingPressed = false;
				repaint();
				HtmlController.getInstance().onPressed(modelNode, e, e.getX(), e.getY());
			}
		});
	}

	private int valign = RElement.VALIGN_BASELINE;
	private Dimension preferredSize;
	private int declaredWidth;
	private int declaredHeight;
	private Image image;

	public void reset(int availWidth, int availHeight) {
		super.reset(availWidth, availHeight);
		HTMLElementImpl element = this.controlElement;
		int dw = HtmlValues.getOldSyntaxPixelSize(element.getAttribute("width"), availWidth, -1);
		int dh = HtmlValues.getOldSyntaxPixelSize(element.getAttribute("height"), availHeight, -1);
		this.declaredWidth = dw;
		this.declaredHeight = dh;
		this.preferredSize = this.createPreferredSize(dw, dh);
		int valign;
		String alignText = element.getAttribute("align");
		if(alignText == null) {
			valign = RElement.VALIGN_BASELINE;
		}
		else {
			alignText = alignText.toLowerCase().trim();
			if("middle".equals(alignText)) {
				valign = RElement.VALIGN_MIDDLE;
			}
			else if("absmiddle".equals(alignText)) {
				valign = RElement.VALIGN_ABSMIDDLE;
			}
			else if("top".equals(alignText)) {
				valign = RElement.VALIGN_TOP;
			}
			else if("bottom".equals(alignText)) {
				valign = RElement.VALIGN_BOTTOM;
			}
			else if("baseline".equals(alignText)) {
				valign = RElement.VALIGN_BASELINE;
			}
			else if("absbottom".equals(alignText)) {
				valign = RElement.VALIGN_ABSBOTTOM;
			}
			else {
				valign = RElement.VALIGN_BASELINE;
			}	
		}
		this.valign = valign;
	}

	public int getVAlign() {
		return this.valign;
	}
	
	public void paintComponent(Graphics g) {
		super.paintComponent(g);
		Dimension size = this.getSize();
		Insets insets = this.getInsets();
		synchronized(this) {}
		Image image = this.image;
		if(image != null) {
			g.drawImage(image, 
					insets.left, insets.top, 
					size.width - insets.left - insets.right, 
					size.height - insets.top - insets.bottom, this);
		}
		else {
			//TODO: alt
		}
		if(this.mouseBeingPressed) {
			Color over = new Color(255, 100, 100, 64);
			if(over != null) {
				Color oldColor = g.getColor();
				try {
					g.setColor(over);
					g.fillRect(0, 0, size.width, size.height);
				} finally {
					g.setColor(oldColor);
				}				
			}
		}
	}

	public Dimension getPreferredSize() {
		Dimension ps = this.preferredSize;
		return ps == null ? new Dimension(0, 0) : ps;
	}

	public Dimension createPreferredSize(int dw, int dh) {
		Image img = this.image;
		if(dw == -1) {
			dw = img == null ? -1 : img.getWidth(this);
			if(dw == -1) {
				dw = 0;
			}
		}
		if(dh == -1) {
			dh = img == null ? -1 : img.getHeight(this);			
			if(dh == -1) {
				dh = 0;
			}
		}
		return new Dimension(dw, dh);
	}

	private final boolean checkPreferredSizeChange() {
		Dimension newPs = this.createPreferredSize(this.declaredWidth, this.declaredHeight);
		Dimension ps = this.preferredSize;
		if(ps == null) {
			return true;
		}
		if(ps.width != newPs.width || ps.height != newPs.height) {
			this.preferredSize = newPs;
			return true;
		}
		else {
			return false;
		}
	}

	/* (non-Javadoc)
	 * @see java.awt.Component#imageUpdate(java.awt.Image, int, int, int, int, int)
	 */
	public boolean imageUpdate(Image img, int infoflags, int x, int y, final int w, final int h) {
		if((infoflags & ImageObserver.ALLBITS) != 0 || (infoflags & ImageObserver.FRAMEBITS) != 0) {
			EventQueue.invokeLater(new Runnable() {
				public void run() {
					if(!checkPreferredSizeChange()) {
						repaint();
					}
					else {
						ruicontrol.preferredSizeInvalidated();
					}
				}
			});
		}
		return true;
	}

	/* (non-Javadoc)
	 * @see java.awt.Component#imageUpdate(java.awt.Image, int, int, int, int, int)
	 */
	public void imageUpdate(Image img, final int w, final int h) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				if(!checkPreferredSizeChange()) {
					repaint();
				}
				else {
					ruicontrol.preferredSizeInvalidated();
				}
			}
		});
	}

	public boolean paintSelection(Graphics g, boolean inSelection, RenderableSpot startPoint, RenderableSpot endPoint) {
		return inSelection;
	}		
	
	public void imageLoaded(ImageEvent event) {
		// Implementation of ImageListener. Invoked in a request thread most likely.
		Image image = event.image;
//		ImageIcon imageIcon = new ImageIcon(image);
//		this.button.setIcon(imageIcon);
		this.image = image;
		int width = image.getWidth(this);
		int height = image.getHeight(this);
		if(width != -1 && height != -1) {
			this.imageUpdate(image, width, height);
		}
	}
	
	public void resetInput() {
		// NOP
	}
	
//	private static class LocalButton extends JButton {
//		public void revalidate() {
//			// ignore
//		}
//		
//		public void repaint() {
//			// ignore
//		}
//	}
}
