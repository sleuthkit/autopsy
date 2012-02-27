package org.lobobrowser.html.renderer;

import java.awt.*;
import org.lobobrowser.html.*;
import org.lobobrowser.html.style.*;
import org.lobobrowser.html.domimpl.*;
import org.w3c.dom.html2.*;

class BrowserFrameUIControl implements UIControl {
	//private final BrowserFrame browserFrame;
	private final Component component;
	private final HTMLElement element;
	private final BrowserFrame browserFrame;
	private RUIControl ruiControl;
	
	public BrowserFrameUIControl(HTMLElement element, BrowserFrame browserFrame) {
		this.component = browserFrame.getComponent();
		this.browserFrame = browserFrame;
		this.element = element;
	}
	
	public int getVAlign() {
		return RElement.VALIGN_BASELINE;
	}
	
	public float getAlignmentY() {
		return 0;
	}

	public Color getBackgroundColor() {
		return this.component.getBackground();
	}

	public Component getComponent() {
		return this.component;
	}

	private int availWidth;
	private int availHeight;
	
	public void reset(int availWidth, int availHeight) {
		this.availWidth = availWidth;
		this.availHeight = availHeight;
		RUIControl ruiControl = this.ruiControl;
		if(ruiControl != null) {
		    ModelNode node = ruiControl.getModelNode();
		    HTMLElement element = (HTMLElement) node;
		    RenderState renderState = node.getRenderState();		    
		    HtmlInsets insets = null;
		    String marginwidth = element.getAttribute("marginwidth");
		    String marginheight = element.getAttribute("marginheight");
		    if(marginwidth != null && marginwidth.length() != 0) {
		        if(insets == null) {
		            insets = new HtmlInsets();
		        }
		        marginwidth = marginwidth.trim();
		        if(marginwidth.endsWith("%")) {
		            int value;
		            try {
		                value = Integer.parseInt(marginwidth.substring(0, marginwidth.length() - 1));
		            } catch(NumberFormatException nfe) {
		                value = 0;
		            }
		            insets.left = value; 
		            insets.right = value;
		            insets.leftType = HtmlInsets.TYPE_PERCENT;
		            insets.rightType = HtmlInsets.TYPE_PERCENT;
		        }
		        else {
		            int value;
		            try {
		                value = Integer.parseInt(marginwidth);
		            } catch(NumberFormatException nfe) {
		                value = 0;
		            }
		            insets.left = value;
		            insets.right = value;
		            insets.leftType = HtmlInsets.TYPE_PIXELS;
		            insets.rightType = HtmlInsets.TYPE_PIXELS;
		        }
		    }
		    if(marginheight != null && marginheight.length() != 0) {
		        if(insets == null) {
		            insets = new HtmlInsets();
		        }
		        marginheight = marginheight.trim();
		        if(marginheight.endsWith("%")) {
		            int value;
		            try {
		                value = Integer.parseInt(marginheight.substring(0, marginheight.length() - 1));
		            } catch(NumberFormatException nfe) {
		                value = 0;
		            }
		            insets.top = value; 
		            insets.bottom = value;
		            insets.topType = HtmlInsets.TYPE_PERCENT;
		            insets.bottomType = HtmlInsets.TYPE_PERCENT;
		        }
		        else {
		            int value;
		            try {
		                value = Integer.parseInt(marginheight);
		            } catch(NumberFormatException nfe) {
		                value = 0;
		            }
		            insets.top = value;
		            insets.bottom = value;
		            insets.topType = HtmlInsets.TYPE_PIXELS;
		            insets.bottomType = HtmlInsets.TYPE_PIXELS;
		        }
		    }
		    Insets awtMarginInsets = insets == null ? null : insets.getSimpleAWTInsets(availWidth, availHeight);
		    int overflowX = renderState.getOverflowX();
		    int overflowY = renderState.getOverflowY();
		    if(awtMarginInsets != null) {
		        this.browserFrame.setDefaultMarginInsets(awtMarginInsets);
		    }
		    if(overflowX != RenderState.OVERFLOW_NONE) {
		        this.browserFrame.setDefaultOverflowX(overflowX);
		    }
		    if(overflowY != RenderState.OVERFLOW_NONE) {
		        this.browserFrame.setDefaultOverflowY(overflowY);
		    }
		}
	}
	
	public Dimension getPreferredSize() {
		int width = HtmlValues.getOldSyntaxPixelSize(element.getAttribute("width"), this.availWidth, 100);
		int height = HtmlValues.getOldSyntaxPixelSize(element.getAttribute("height"), this.availHeight, 100);
		return new Dimension(width, height);
	}

	public void invalidate() {
		this.component.invalidate();
	}

	public void paint(Graphics g) {
		// We actually have to paint it.
		this.component.paint(g);
	}

	public boolean paintSelection(Graphics g, boolean inSelection,
			RenderableSpot startPoint, RenderableSpot endPoint) {
		// Selection does not cross in here?
		return false;
	}

	public void setBounds(int x, int y, int width, int height) {
		this.component.setBounds(x, y, width, height);
	}

	public void setRUIControl(RUIControl ruicontrol) {
		this.ruiControl = ruicontrol;
	}
}
