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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import org.lobobrowser.html.domimpl.*;
import org.lobobrowser.util.gui.WrapperLayout;
import javax.swing.*;

class InputButtonControl extends BaseInputControl {
	private final JButton widget; 
	
	public InputButtonControl(final HTMLBaseInputElement modelNode) {
		super(modelNode);
		this.setLayout(WrapperLayout.getInstance());
		JButton widget = new JButton();
		widget.setContentAreaFilled(false);
		this.widget = widget;
		this.add(widget);
		widget.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent event) {
				HtmlController.getInstance().onPressed(InputButtonControl.this.controlElement, null, 0, 0);
			}
		});
	}	
	
	public void reset(int availWidth, int availHeight) {
		super.reset(availWidth, availHeight);
		RUIControl ruiControl = this.ruicontrol;
		JButton button = this.widget;
		button.setContentAreaFilled(!ruiControl.hasBackground());
		java.awt.Color foregroundColor = ruiControl.getForegroundColor();
		if(foregroundColor != null) {
			button.setForeground(foregroundColor);
		}
		HTMLInputElementImpl element = (HTMLInputElementImpl) this.controlElement;
		String text = element.getAttribute("value");
		if(text == null || text.length() == 0) {
			String type = element.getType();
			if("submit".equalsIgnoreCase(type)) {
				text = "Submit Query";
			}
			else if("reset".equalsIgnoreCase(type)) {
				text = "Reset";
			}
			else {
				text = "";
			}
		}
		button.setText(text);		
	}
	
	/* (non-Javadoc)
	 * @see org.xamjwg.html.domimpl.InputContext#click()
	 */
	public void click() {
		this.widget.doClick();
	}

	/* (non-Javadoc)
	 * @see org.xamjwg.html.domimpl.InputContext#getValue()
	 */
	public String getValue() {
		return this.widget.getText();
	}

	public void setDisabled(boolean disabled) {
		super.setDisabled(disabled);
		this.widget.setEnabled(!disabled);
	}
	
	/* (non-Javadoc)
	 * @see org.xamjwg.html.domimpl.InputContext#setValue(java.lang.String)
	 */
	public void setValue(String value) {
		this.widget.setText(value);
	}
	
	public void resetInput() {
		// nop
	}
}
