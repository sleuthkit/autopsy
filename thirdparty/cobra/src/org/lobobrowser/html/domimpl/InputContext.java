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
package org.lobobrowser.html.domimpl;

public interface InputContext {
	public boolean getChecked();
	public void setChecked(boolean checked);
	public boolean getDisabled();
	public void setDisabled(boolean disabled);
	public int getMaxLength();
	public void setMaxLength(int maxLength);
	public String getName();
	public void setName(String name);
	public boolean getReadOnly();
	public void setReadOnly(boolean readOnly);
	public int getControlSize();
	public void setControlSize(int size);
	public int getTabIndex();
	public void setTabIndex(int tabIndex);
	public String getValue();
	public String[] getValues();
	public void setValue(String value);
	public void blur();
	public void focus();
	public void select();
	public void click();
	public int getRows();
	public int getCols();
	public void setRows(int rows);
	public void setCols(int cols);
	public int getSelectedIndex();
	public void setSelectedIndex(int value);
	public int getVisibleSize();
	public void setVisibleSize(int value);
	public java.io.File getFileValue();
	public void resetInput();
}
