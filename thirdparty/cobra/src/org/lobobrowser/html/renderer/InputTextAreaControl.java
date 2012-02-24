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

import javax.swing.*;
import javax.swing.text.JTextComponent;

import org.lobobrowser.html.domimpl.*;
import org.lobobrowser.util.gui.WrapperLayout;

class InputTextAreaControl extends BaseInputControl {
	private final JTextComponent widget;
	
	public InputTextAreaControl(HTMLBaseInputElement modelNode) {
		super(modelNode);
		this.setLayout(WrapperLayout.getInstance());
		JTextComponent widget = this.createTextField();
		this.widget = widget;
		this.add(new JScrollPane(widget));
		
		//Note: Value attribute cannot be set in reset() method.
		//Otherwise, layout revalidation causes typed values to
		//be lost (including revalidation due to hover.)

		ElementImpl element = this.controlElement;
		String value = element.getTextContent();
		((JTextArea) widget).setLineWrap(true);
		widget.setText(value);
	}

	public void reset(int availWidth, int availHeight) {
		super.reset(availWidth, availHeight);
		ElementImpl element = this.controlElement;
		String colsStr = element.getAttribute("cols");
		if(colsStr != null) {
			try {
				this.setCols(Integer.parseInt(colsStr));
			} catch(NumberFormatException nfe) {
				// ignore
			}
		}
		String rowsStr = element.getAttribute("rows");
		if(rowsStr != null) {
			try {
				this.setRows(Integer.parseInt(rowsStr));
			} catch(NumberFormatException nfe) {
				// ignore
			}
		}		
	}
	
	protected JTextComponent createTextField() {
		return new JTextArea();
	}

	/* (non-Javadoc)
	 * @see org.xamjwg.html.renderer.BaseInputControl#getCols()
	 */
	public int getCols() {
		return this.cols;
	}

	/* (non-Javadoc)
	 * @see org.xamjwg.html.renderer.BaseInputControl#getRows()
	 */
	public int getRows() {
		return this.rows;
	}

	private int cols = -1;
	private int rows = -1;
	
	/* (non-Javadoc)
	 * @see org.xamjwg.html.renderer.BaseInputControl#setCols(int)
	 */
	public void setCols(int cols) {
		if(cols != this.cols) {
			this.cols = cols;
			this.invalidate();
		}
	}

	/* (non-Javadoc)
	 * @see org.xamjwg.html.renderer.BaseInputControl#setRows(int)
	 */
	public void setRows(int rows) {
		if(rows != this.rows) {
			this.rows = rows;
			this.invalidate();
		}
	}

	public java.awt.Dimension getPreferredSize() {
		int pw;
		int cols = this.cols;
		if(cols == -1) {
			pw = 100;
		}
		else {
			Font f = this.widget.getFont();
			FontMetrics fm = this.widget.getFontMetrics(f);
			Insets insets = this.widget.getInsets();
			pw = insets.left + insets.right + fm.charWidth('*') * cols;
		}
		int ph;
		int rows = this.rows;
		if(rows == -1) {
			ph = 100;
		}
		else {
			Font f = this.widget.getFont();
			FontMetrics fm = this.widget.getFontMetrics(f);
			Insets insets = this.widget.getInsets();
			ph = insets.top + insets.bottom + fm.getHeight() * rows;
		}
		return new java.awt.Dimension(pw, ph);
		
	}
	
	/* (non-Javadoc)
	 * @see org.xamjwg.html.renderer.BaseInputControl#getReadOnly()
	 */
	public boolean getReadOnly() {
		return !this.widget.isEditable();
	}

	/* (non-Javadoc)
	 * @see org.xamjwg.html.renderer.BaseInputControl#getValue()
	 */
	public String getValue() {
		String text = this.widget.getText();
		return org.lobobrowser.util.Strings.getCRLFString(text);
	}

	/* (non-Javadoc)
	 * @see org.xamjwg.html.renderer.BaseInputControl#setReadOnly(boolean)
	 */
	public void setReadOnly(boolean readOnly) {
		this.widget.setEditable(readOnly);
	}

	/* (non-Javadoc)
	 * @see org.xamjwg.html.renderer.BaseInputControl#setValue(java.lang.String)
	 */
	public void setValue(String value) {
		this.widget.setText(value);
	}	
	
	public void resetInput() {
		this.widget.setText("");
	}
}
