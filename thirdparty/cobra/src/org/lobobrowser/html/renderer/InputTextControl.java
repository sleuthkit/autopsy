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
import java.awt.event.FocusEvent;

import org.lobobrowser.html.domimpl.*;

import javax.swing.*;
import javax.swing.text.JTextComponent;

class InputTextControl extends BaseInputTextControl {
	public InputTextControl(final HTMLBaseInputElement modelNode) {
		super(modelNode);
		JTextField w = (JTextField) this.widget;
		w.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent event) {
				HtmlController.getInstance().onEnterPressed(modelNode, null);
			}
		});
	}

	/* (non-Javadoc)
	 * @see org.xamjwg.html.renderer.BaseInputTextControl#createTextField(java.lang.String)
	 */
	protected JTextComponent createTextField() {
		return new JTextField();
	}
}
