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
 * Created on Jan 15, 2006
 */
package org.lobobrowser.html.renderer;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Insets;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;

import org.lobobrowser.html.domimpl.*;
import org.lobobrowser.util.gui.WrapperLayout;

abstract class BaseInputTextControl extends BaseInputControl {
	private static final float DEFAULT_FONT_SIZE = 14.0f;
	protected final JTextComponent widget;
	
	public BaseInputTextControl(final HTMLBaseInputElement modelNode) {
		super(modelNode);
		this.setLayout(WrapperLayout.getInstance());
		JTextComponent widget = this.createTextField();
		Font font = widget.getFont();
		widget.setFont(font.deriveFont(DEFAULT_FONT_SIZE));
		widget.setDocument(new LimitedDocument());

		//Note: Value attribute cannot be set in reset() method.
		//Otherwise, layout revalidation causes typed values to
		//be lost (including revalidation due to hover.)
		ElementImpl element = this.controlElement;
	    String value = element.getAttribute("value");
		widget.setText(value);

		this.widget = widget; 
		this.add(widget);
	}
	
	public void reset(int availWidth, int availHeight) {
		super.reset(availWidth, availHeight);
		String maxLengthText = this.controlElement.getAttribute("maxlength");
		if(maxLengthText != null) {
			try {
				this.maxLength = Integer.parseInt(maxLengthText);
			} catch(NumberFormatException nfe) {
				// ignore
			}
		}
		
	}
	
	protected abstract JTextComponent createTextField();
	
	private int maxLength = -1;
	
	/* (non-Javadoc)
	 * @see org.xamjwg.html.domimpl.InputContext#getMaxLength()
	 */
	public int getMaxLength() {
		return this.maxLength;
	}

	/* (non-Javadoc)
	 * @see org.xamjwg.html.domimpl.InputContext#getReadOnly()
	 */
	public boolean getReadOnly() {
		return !this.widget.isEditable();
	}

	/* (non-Javadoc)
	 * @see org.xamjwg.html.domimpl.InputContext#getValue()
	 */
	public String getValue() {
		return this.widget.getText();
	}

	/* (non-Javadoc)
	 * @see org.xamjwg.html.domimpl.InputContext#select()
	 */
	public void select() {
		this.widget.selectAll();
	}

	/* (non-Javadoc)
	 * @see org.xamjwg.html.domimpl.InputContext#setDisabled(boolean)
	 */
	public void setDisabled(boolean disabled) {
		super.setDisabled(disabled);
		this.widget.setEnabled(!disabled);
	}

	/* (non-Javadoc)
	 * @see org.xamjwg.html.domimpl.InputContext#setMaxLength(int)
	 */
	public void setMaxLength(int maxLength) {
		this.maxLength = maxLength;
	}

	/* (non-Javadoc)
	 * @see org.xamjwg.html.domimpl.InputContext#setReadOnly(boolean)
	 */
	public void setReadOnly(boolean readOnly) {
		this.widget.setEditable(!readOnly);
	}

	/* (non-Javadoc)
	 * @see org.xamjwg.html.domimpl.InputContext#setValue(java.lang.String)
	 */
	public void setValue(String value) {
		this.widget.setText(value);
	}

	public java.awt.Dimension getPreferredSize() {
		int size = this.size;
		JTextComponent widget = this.widget;
		FontMetrics fm = widget.getFontMetrics(widget.getFont());
		Insets insets = widget.getInsets();
		int pw, ph;
		if(size == -1) {
			pw = 100; 
		}
		else {
			pw = insets.left + insets.right + fm.charWidth('0') * size;
		}
		ph = fm.getHeight() + insets.top + insets.bottom;
		return new java.awt.Dimension(pw, ph);
	}
	
	public void resetInput() {
		this.widget.setText("");
	}
	
	/**
	 * Implements maxlength functionality.
	 */
	private class LimitedDocument extends javax.swing.text.PlainDocument {
		/* (non-Javadoc)
		 * @see javax.swing.text.PlainDocument#insertString(int, java.lang.String, javax.swing.text.AttributeSet)
		 */
		public void insertString(int offs, String str, AttributeSet a) throws BadLocationException {
			int max = BaseInputTextControl.this.maxLength;
			if(max != -1) {
				int docLength = this.getLength();
				if(docLength >= max) {
					return;
				}
				int strLen = str.length();
				if(docLength + strLen > max) {
					String shorterStr = str.substring(0, max - docLength);
					super.insertString(offs, shorterStr, a);
				}
				else {
					super.insertString(offs, str, a);
				}
			}
			else {
				super.insertString(offs, str, a);
			}
		}		
	}
}
