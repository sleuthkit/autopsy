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
 * Created on Dec 3, 2005
 */
package org.lobobrowser.html.renderer;

import org.lobobrowser.html.style.HtmlLength;

class VirtualCell {
	private final RTableCell actualCell;
	private final boolean isTopLeft;
	private int column;
	private int row;

	/**
	 * @param cell
	 */
	public VirtualCell(RTableCell cell, boolean isTopLeft) {
		actualCell = cell;
		this.isTopLeft = isTopLeft;
	}
	
	public boolean isTopLeft() {
		return this.isTopLeft;
	}

	/**
	 * @return Returns the column.
	 */
	public int getColumn() {
		return column;
	}



	/**
	 * @param column The column to set.
	 */
	public void setColumn(int column) {
		this.column = column;
	}



	/**
	 * @return Returns the row.
	 */
	public int getRow() {
		return row;
	}



	/**
	 * @param row The row to set.
	 */
	public void setRow(int row) {
		this.row = row;
	}



	/**
	 * @return Returns the actualCell.
	 */
	public RTableCell getActualCell() {
		return actualCell;
	}
	
	public HtmlLength getHeightLength() {
		//TODO: Does not consider cellpadding and border
		RTableCell cell = this.actualCell;
		String heightText = cell.getHeightText();
		HtmlLength length;
		try {
			length = heightText == null ? null : new HtmlLength(heightText);
		} catch(Exception err) {
			length = null;
		}
		if(length != null) {
			length.divideBy(cell.getRowSpan());
		}
		return length;
	}	

	public HtmlLength getWidthLength() {
		RTableCell cell = this.actualCell;
		String widthText = cell.getWidthText();
		HtmlLength length;
		try {
			length = widthText == null ? null : new HtmlLength(widthText);
		} catch(Exception err) {
			length = null;
		}
		if(length != null) {
			length.divideBy(cell.getColSpan());
		}
		return length;
	}	
	
//	public Dimension layoutMinWidth() {
//
//		ActualCell cell = this.actualCell;
//
//		Dimension ad = cell.layoutMinWidth();
//
//		int colspan = cell.getColSpan();
//
//		int rowspan = cell.getRowSpan();
//
//		if(colspan == 1 && rowspan == 1) {
//
//			return ad;
//
//		}
//
//		else {
//
//			return new Dimension(ad.width / colspan, ad.height / rowspan);
//
//		}
//
//	}
}
