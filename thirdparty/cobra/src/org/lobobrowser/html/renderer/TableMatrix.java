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
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.*;

import org.lobobrowser.html.*;
import org.lobobrowser.html.domimpl.*;
import org.lobobrowser.html.style.*;
import org.w3c.dom.Node;
import org.w3c.dom.html2.HTMLTableCellElement;
import org.w3c.dom.html2.HTMLTableRowElement;

class TableMatrix {
    //private static final NodeFilter ROWS_FILTER = new RowsFilter();
    private static final NodeFilter COLUMNS_FILTER = new ColumnsFilter();
    private final ArrayList ROWS = new ArrayList();
    private final ArrayList ALL_CELLS = new ArrayList();
    private final ArrayList ROW_ELEMENTS = new ArrayList();
    private final HTMLElementImpl tableElement;
    private final UserAgentContext parserContext;
    private final HtmlRendererContext rendererContext;
    private final FrameContext frameContext;
    private final RElement relement;
    private final RenderableContainer container;
    
    private SizeInfo[] columnSizes;
    private SizeInfo[] rowSizes;
    private int tableWidth;
    private int tableHeight;
    
    /* This is so that we can draw the lines inside the table
     * that appear when a border attribute is used.
     */
    private int hasOldStyleBorder;

    /**
	 * @param element
	 */
    public TableMatrix(HTMLElementImpl element, UserAgentContext pcontext, HtmlRendererContext rcontext, FrameContext frameContext, RenderableContainer tableAsContainer, RElement relement) {
		this.tableElement = element;
		this.parserContext = pcontext;
		this.rendererContext = rcontext;
		this.frameContext = frameContext;
		this.relement = relement;
		this.container = tableAsContainer;
	}
    
    public void finalize() throws Throwable {
    	super.finalize();
    }
    
    public int getNumRows() {
    	return this.ROWS.size();
    }
    
    public int getNumColumns() {
    	return this.columnSizes.length;
    }
	/**
	 * @return Returns the tableHeight.
	 */
	public int getTableHeight() {
		return this.tableHeight;
	}
	/**
	 * @return Returns the tableWidth.
	 */
	public int getTableWidth() {
		return this.tableWidth;
	}
	
	//private int border;
	private int cellSpacingY;
	private int cellSpacingX;
	private int widthsOfExtras;
	private int heightsOfExtras;
	private HtmlLength tableWidthLength;
		
	/**
	 * Called on every relayout. 
	 * Element children might have changed.
	 */
	public void reset(Insets insets, int availWidth, int availHeight) {
		//TODO: Incorporate into build() and calculate
		//sizes properly based on parameters.
		ROWS.clear();
		ALL_CELLS.clear();
		ROW_ELEMENTS.clear();
		//TODO: Does it need this old-style border?
		String borderText = this.tableElement.getAttribute("border");
		int border = 0;
		if(borderText != null) {
			try {
				border = Integer.parseInt(borderText);
				if(border < 0) {
					border = 0;
				}
			} catch(NumberFormatException nfe) {
				// ignore
			}
		}
		String cellSpacingText = this.tableElement.getAttribute("cellspacing");
		int cellSpacing = 1;
		if(cellSpacingText != null) {
			try {
				//TODO: cellSpacing can be a percentage as well
				cellSpacing = Integer.parseInt(cellSpacingText);
				if(cellSpacing < 0) {
					cellSpacing = 0;
				}
			} catch(NumberFormatException nfe) {
				// ignore
			}
		}
		this.cellSpacingX = cellSpacing;
		this.cellSpacingY = cellSpacing;

		this.tableWidthLength = TableMatrix.getWidthLength(this.tableElement, availWidth);

		this.populateRows();
		this.adjustForCellSpans();
		this.createSizeArrays();		
		
		// Calculate widths of extras
		SizeInfo[] columnSizes = this.columnSizes;
		int numCols = columnSizes.length;
		int widthsOfExtras = insets.left + insets.right + (numCols + 1) * cellSpacing;
		if(border > 0) {
			widthsOfExtras += (numCols * 2);
		}
		this.widthsOfExtras = widthsOfExtras;
		
		// Calculate heights of extras
		SizeInfo[] rowSizes = this.rowSizes;
		int numRows = rowSizes.length;
		int heightsOfExtras = insets.top + insets.bottom + (numRows + 1) * cellSpacing;
		if(border > 0) {
			heightsOfExtras += (numRows * 2);
		}
		this.heightsOfExtras = heightsOfExtras;
		this.hasOldStyleBorder = border > 0 ? 1 : 0;
	}
	
	public void build(int availWidth, int availHeight, boolean sizeOnly) {
		int hasBorder = this.hasOldStyleBorder;
		this.determineColumnSizes(hasBorder, this.cellSpacingX, this.cellSpacingY, availWidth);
		this.determineRowSizes(hasBorder, this.cellSpacingY, availHeight, sizeOnly);
	}

	private final HTMLTableRowElementImpl getParentRow(HTMLTableCellElementImpl cellNode) {
		org.w3c.dom.Node parentNode = cellNode.getParentNode();
		for(;;) {
			if(parentNode instanceof HTMLTableRowElementImpl) {
				return (HTMLTableRowElementImpl) parentNode;
			}
			if(parentNode instanceof HTMLTableElementImpl) {
				return null;
			}
			parentNode = parentNode.getParentNode();
		}		
	}
	
	private static HtmlLength getWidthLength(HTMLElementImpl element, int availWidth) {
		try {
			AbstractCSS2Properties props = element.getCurrentStyle();
			String widthText = props == null ? null : props.getWidth();
			if(widthText == null) {
			    String widthAttr=element.getAttribute("width");
			    if (widthAttr==null) return null;
				return new HtmlLength(widthAttr);
			} 
			else {
				return new HtmlLength(HtmlValues.getPixelSize(widthText, element.getRenderState(), 0, availWidth));
			}
		} catch(Exception err) {
			return null;
		}
	}

	private static HtmlLength getHeightLength(HTMLElementImpl element, int availHeight) {
		try {
			AbstractCSS2Properties props = element.getCurrentStyle();
			String heightText = props == null ? null : props.getHeight();
			if(heightText == null) {
			    String ha = element.getAttribute("height");
			    if(ha == null) {
			        return null;
			    }
			    else {
			        return new HtmlLength(ha);
			    }
			} 
			else {
				return new HtmlLength(HtmlValues.getPixelSize(heightText, element.getRenderState(), 0, availHeight));
			}
		} catch(Exception err) {
			return null;
		}
	}

	/**
	 * Populates the ROWS and ALL_CELLS collections.
	 */
	private void populateRows() {
		HTMLElementImpl te = this.tableElement;
		ArrayList rows = this.ROWS;
		ArrayList rowElements = this.ROW_ELEMENTS;
		ArrayList allCells = this.ALL_CELLS;
		Map rowElementToRowArray = new HashMap(2);
		ArrayList cellList = te.getDescendents(COLUMNS_FILTER, false);
		ArrayList currentNullRow = null;
		Iterator ci = cellList.iterator();
		while(ci.hasNext()) {
			HTMLTableCellElementImpl columnNode = (HTMLTableCellElementImpl) ci.next();
			HTMLTableRowElementImpl rowElement = this.getParentRow(columnNode);
			if(rowElement != null && rowElement.getRenderState().getDisplay() == RenderState.DISPLAY_NONE) {
				// Skip row [ 2047122 ] 
				continue;
			}
			ArrayList row;
			if(rowElement != null) {
				currentNullRow = null;
				row = (ArrayList) rowElementToRowArray.get(rowElement);
				if(row == null) {
					row = new ArrayList();
					rowElementToRowArray.put(rowElement, row);
					rows.add(row);
					rowElements.add(rowElement);
				}
			}
			else {
				// Doesn't have a TR parent. Let's add a ROW just for itself.
				// Both IE and FireFox have this behavior.
				if(currentNullRow != null) {
					row = currentNullRow;
				}
				else {
					row = new ArrayList();
					currentNullRow = row;
					rows.add(row);
					// Null TR element must be added to match.
					rowElements.add(null);
				}
			}
			RTableCell ac = (RTableCell) columnNode.getUINode();
			if(ac == null) {
				// Saved UI nodes must be reused, because they
				// can contain a collection of GUI components.
				ac = new RTableCell(columnNode, this.parserContext, this.rendererContext, this.frameContext, this.container);
				ac.setParent(this.relement);
				columnNode.setUINode(ac);
			}
			VirtualCell vc = new VirtualCell(ac, true);
			ac.setTopLeftVirtualCell(vc);
			row.add(vc);
			allCells.add(ac);			
		}
	}
	
	/**
	 * Based on colspans and rowspans, creates
	 * additional virtual cells from actual 
	 * table cells.
	 */
	private void adjustForCellSpans() {
		ArrayList rows = this.ROWS;
		int numRows = rows.size();
		for(int r = 0; r < numRows; r++) {
			ArrayList row = (ArrayList) rows.get(r);
			int numCols = row.size();
			for(int c = 0; c < numCols; c++) {
				VirtualCell vc = (VirtualCell) row.get(c);
				if(vc != null && vc.isTopLeft()) {
					RTableCell ac = vc.getActualCell();
					int colspan = ac.getColSpan();
					if(colspan < 1) {
						colspan = 1;
					}
					int rowspan = ac.getRowSpan();
					if(rowspan < 1) {
						rowspan = 1;
					}
					
					// Can't go beyond last row (Fix bug #2022584)
					int targetRows = r + rowspan;
					if(numRows < targetRows) {
						rowspan = numRows - r;
						ac.setRowSpan(rowspan);
					}
					
					numRows = rows.size();
					for(int y = 0; y < rowspan; y++) {
						if(colspan > 1 || y > 0) {
							// Get row
							int nr = r + y;
							ArrayList newRow = (ArrayList) rows.get(nr);

							// Insert missing cells in row
							int xstart = y == 0 ? 1 : 0;
														
							// Insert virtual cells, potentially
							// shifting others to the right.
							for(int cc = xstart; cc < colspan; cc++) {
								int nc = c + cc;
								while(newRow.size() < nc) {
									newRow.add(null);
								}
								newRow.add(nc, new VirtualCell(ac, false));
							}
							if(row == newRow) {
								numCols = row.size();
							}
						}
					}
				}
			}
		}

		// Adjust row and column of virtual cells
		for(int r = 0; r < numRows; r++) {
			ArrayList row = (ArrayList) rows.get(r);
			int numCols = row.size();
			for(int c = 0; c < numCols; c++) {
				VirtualCell vc = (VirtualCell) row.get(c);
				if(vc != null) {	
					vc.setColumn(c);
					vc.setRow(r);
				}
			}
		}
	}

	/**
	 * Populates the columnSizes and rowSizes arrays,
	 * setting htmlLength in each element.
	 */
	private void createSizeArrays() {
		ArrayList rows = this.ROWS;
		int numRows = rows.size();
		SizeInfo[] rowSizes = new SizeInfo[numRows];
		this.rowSizes = rowSizes;
		int numCols = 0;
		ArrayList rowElements = this.ROW_ELEMENTS;
		for(int i = 0; i < numRows; i++) {
			ArrayList row = (ArrayList) rows.get(i);
			int rs = row.size();
			if(rs > numCols) {
				numCols = rs;
			}			
			SizeInfo rowSizeInfo = new SizeInfo();
			rowSizes[i] = rowSizeInfo;
			HTMLTableRowElement rowElement;
			try {
				rowElement = (HTMLTableRowElement) rowElements.get(i);
				// Possible rowElement is null because TD does not have TR parent
			} catch(IndexOutOfBoundsException iob) {
				//Possible if rowspan expands beyond that
				rowElement = null;
			}
			//TODO: TR.height an IE quirk?
			String rowHeightText = rowElement == null ? null : rowElement.getAttribute("height");
			HtmlLength rowHeightLength = null;
			if(rowHeightText != null) {
				try {
					rowHeightLength = new HtmlLength(rowHeightText);
				} catch(Exception err) {
					// ignore
				}
			}
			if(rowHeightLength != null) {
				rowSizeInfo.htmlLength = rowHeightLength;
			}
			else {
				HtmlLength bestHeightLength = null;
				for(int x = 0; x < rs; x++) {
					VirtualCell vc = (VirtualCell) row.get(x);
					if(vc != null) {
						HtmlLength vcHeightLength = vc.getHeightLength();
						if(vcHeightLength != null && vcHeightLength.isPreferredOver(bestHeightLength)) {
							bestHeightLength = vcHeightLength;
						}
					}
				}
				rowSizeInfo.htmlLength = bestHeightLength;
			}
		}
		SizeInfo[] columnSizes = new SizeInfo[numCols];
		this.columnSizes = columnSizes;
		for(int i = 0; i < numCols; i++) {
			HtmlLength bestWidthLength = null;
			
			// Cells with colspan==1 first.
			for(int y = 0; y < numRows; y++) {
				ArrayList row = (ArrayList) rows.get(y);
				VirtualCell vc;
				try {
					vc = (VirtualCell) row.get(i);
				} catch(IndexOutOfBoundsException iob) {
					vc = null;
				}
				if(vc != null) {
					RTableCell ac = vc.getActualCell();
					if(ac.getColSpan() == 1) {
						HtmlLength vcWidthLength = vc.getWidthLength();
						if(vcWidthLength != null && vcWidthLength.isPreferredOver(bestWidthLength)) {
							bestWidthLength = vcWidthLength;
						}
					}
				}
			}
			// Now cells with colspan>1.
			if(bestWidthLength == null) {
				for(int y = 0; y < numRows; y++) {
					ArrayList row = (ArrayList) rows.get(y);
					VirtualCell vc;
					try {
						vc = (VirtualCell) row.get(i);
					} catch(IndexOutOfBoundsException iob) {
						vc = null;
					}
					if(vc != null) {
						RTableCell ac = vc.getActualCell();
						if(ac.getColSpan() > 1) {
							HtmlLength vcWidthLength = vc.getWidthLength();
							if(vcWidthLength != null && vcWidthLength.isPreferredOver(bestWidthLength)) {
								bestWidthLength = vcWidthLength;
							}
						}
					}
				}
			}
			SizeInfo colSizeInfo = new SizeInfo();
			colSizeInfo.htmlLength = bestWidthLength;
			columnSizes[i] = colSizeInfo;
		}
	}
	
	/**
	 * Determines the size of each column, and the table width.
	 * Does the following:
	 * <ol>
	 * <li>Determine tentative widths. This is done by looking
	 *     at declared column widths, any table width, and 
	 *     filling in the blanks. No rendering is done. 
	 *     The tentative
	 *     width of columns with no declared width is zero.
	 *     
	 * <li>Render all cell blocks. It uses the tentative widths from
	 *     the previous step as a desired width. The resulting width
	 *     is considered a sort of minimum. If the column
	 *     width is not defined, use a NOWRAP override flag to render.
	 *     
	 * <li>Check if cell widths are too narrow for the rendered
	 *     width. In the case of columns without a declared width,
	 *     check if they are too wide.
	 *     
	 * <li>Finally, adjust widths considering the expected max table
	 *     size. Columns are layed out again if necessary to determine
	 *     if they can really be shrunk.
	 * </ol>
	 * @param renderState
	 * @param border
	 * @param cellSpacingX
	 * @param cellSpacingY
	 * @param availWidth
	 */
	private void determineColumnSizes(int hasBorder, int cellSpacingX, int cellSpacingY, int availWidth) {
		HtmlLength tableWidthLength = this.tableWidthLength;
		int tableWidth;
		boolean widthKnown;
		if(tableWidthLength != null) {
			tableWidth = tableWidthLength.getLength(availWidth);
			widthKnown = true;
		}
		else {
			tableWidth = availWidth;
			widthKnown = false;
		}
		SizeInfo[] columnSizes = this.columnSizes;
		int widthsOfExtras = this.widthsOfExtras;
		int cellAvailWidth = tableWidth - widthsOfExtras;
		if(cellAvailWidth < 0) {
			tableWidth += (-cellAvailWidth);
			cellAvailWidth = 0;
		}
		
		// Determine tentative column widths based on specified cell widths
		
		this.determineTentativeSizes(columnSizes, widthsOfExtras, cellAvailWidth, widthKnown);
		
		// Pre-render cells. This will give the minimum width of each cell,
		// in addition to the minimum height.
		
		this.preLayout(hasBorder, cellSpacingX, cellSpacingY, widthKnown);

		// Increases column widths if they are less than minimums of each cell.
		
		this.adjustForRenderWidths(columnSizes, hasBorder, cellSpacingX, widthKnown);
		
		// Adjust for expected total width
		
		this.adjustWidthsForExpectedMax(columnSizes, cellAvailWidth, widthKnown);
	}
	
	/**
	 * This method sets the tentative actual sizes of columns (rows) based
	 * on specified witdhs (heights) if available.
	 * @param columnSizes
	 * @param widthsOfExtras
	 * @param cellAvailWidth
	 */
	private void determineTentativeSizes(SizeInfo[] columnSizes, int widthsOfExtras, int cellAvailWidth, boolean setNoWidthColumns) {
		int numCols = columnSizes.length;
		
		// Look at percentages first
		int widthUsedByPercent = 0;
		for(int i = 0; i < numCols; i++) {
			SizeInfo colSizeInfo = columnSizes[i];
			HtmlLength widthLength = colSizeInfo.htmlLength;
			if(widthLength != null && widthLength.getLengthType() == HtmlLength.LENGTH) {
				int actualSizeInt = widthLength.getLength(cellAvailWidth);
				widthUsedByPercent += actualSizeInt;
				colSizeInfo.actualSize = actualSizeInt;
			}
		}
		
		// Look at columns with absolute sizes
		int widthUsedByAbsolute = 0;
		int numNoWidthColumns = 0;
		for(int i = 0; i < numCols; i++) {
			SizeInfo colSizeInfo = columnSizes[i];
			HtmlLength widthLength = colSizeInfo.htmlLength;
			if(widthLength != null && widthLength.getLengthType() != HtmlLength.LENGTH) {
				//TODO: MULTI-LENGTH not supported
				int actualSizeInt = widthLength.getRawValue();
				widthUsedByAbsolute += actualSizeInt;
				colSizeInfo.actualSize = actualSizeInt;
			}
			else if(widthLength == null) {
				numNoWidthColumns++;
			}
		}
		
		// Tentative width of all columns without a declared
		// width is set to zero. The pre-render will determine
		// a better size.
		
//		// Assign all columns without widths now
//		int widthUsedByUnspecified = 0;
//		if(setNoWidthColumns) {
//			int remainingWidth = cellAvailWidth - widthUsedByAbsolute - widthUsedByPercent;
//			if(remainingWidth > 0) {
//				for(int i = 0; i < numCols; i++) {
//					SizeInfo colSizeInfo = columnSizes[i];
//					HtmlLength widthLength = colSizeInfo.htmlLength;
//					if(widthLength == null) {
//						int actualSizeInt = remainingWidth / numNoWidthColumns;
//						widthUsedByUnspecified += actualSizeInt;
//						colSizeInfo.actualSize = actualSizeInt;
//					}
//				}
//			}		
//		}
		
		// Contract if necessary. This is done again later, but this is
		// an optimization, as it may prevent re-layout. It is only done
		// if all columns have some kind of declared width.
		
		if(numNoWidthColumns == 0) {
			int totalWidthUsed = widthUsedByPercent + widthUsedByAbsolute;
			int difference = totalWidthUsed - cellAvailWidth;
			// See if absolutes need to be contracted
			if(difference > 0) {
				if(widthUsedByAbsolute > 0) {
					int expectedAbsoluteWidthTotal = widthUsedByAbsolute - difference;
					if(expectedAbsoluteWidthTotal < 0) {
						expectedAbsoluteWidthTotal = 0;
					}
					double ratio = (double) expectedAbsoluteWidthTotal / widthUsedByAbsolute;
					for(int i = 0; i < numCols; i++) {
						SizeInfo sizeInfo = columnSizes[i];
						HtmlLength widthLength = columnSizes[i].htmlLength;
						if(widthLength != null && widthLength.getLengthType() != HtmlLength.LENGTH) {
							int oldActualSize = sizeInfo.actualSize;
							int newActualSize = (int) Math.round(oldActualSize * ratio);
							sizeInfo.actualSize = newActualSize;
							totalWidthUsed += (newActualSize - oldActualSize);
						}
					}				
					difference = totalWidthUsed - cellAvailWidth;
				}

				// See if percentages need to be contracted
				if(difference > 0) {
					if(widthUsedByPercent > 0) {
						int expectedPercentWidthTotal = widthUsedByPercent - difference;
						if(expectedPercentWidthTotal < 0) {
							expectedPercentWidthTotal = 0;
						}
						double ratio = (double) expectedPercentWidthTotal / widthUsedByPercent;
						for(int i = 0; i < numCols; i++) {
							SizeInfo sizeInfo = columnSizes[i];
							HtmlLength widthLength = columnSizes[i].htmlLength;
							if(widthLength != null && widthLength.getLengthType() == HtmlLength.LENGTH) {
								int oldActualSize = sizeInfo.actualSize;
								int newActualSize = (int) Math.round(oldActualSize * ratio);
								sizeInfo.actualSize = newActualSize;
								totalWidthUsed += (newActualSize - oldActualSize);
							}
						}							
					}
				}		
			}
		}
	}
	
	/**
	 * Contracts column sizes according to render sizes.
	 */
	private void adjustForRenderWidths(SizeInfo[] columnSizes, int hasBorder, int cellSpacing, boolean tableWidthKnown) {
		int numCols = columnSizes.length;
		for(int i = 0; i < numCols; i++) {
			SizeInfo si = columnSizes[i];
			if(si.actualSize < si.layoutSize) {
				si.actualSize = si.layoutSize;
			}
//			else if(si.htmlLength == null) {
//				// For cells without a declared width, see if
//				// their tentative width is a bit too big.
//				if(si.actualSize > si.layoutSize) {
//					si.actualSize = si.layoutSize;
//				}
//			}
		}
	}
	
	private void layoutColumn(SizeInfo[] columnSizes, SizeInfo colSize, int col, int cellSpacingX, int hasBorder) {
		SizeInfo[] rowSizes = this.rowSizes;
		ArrayList rows = this.ROWS;
		int numRows = rows.size();
		int actualSize = colSize.actualSize;
		colSize.layoutSize = 0;
		for(int row = 0; row < numRows;) {
			//SizeInfo rowSize = rowSizes[row];
			ArrayList columns = (ArrayList) rows.get(row);
			VirtualCell vc = null;
			try {
				vc = (VirtualCell) columns.get(col);
			} catch(IndexOutOfBoundsException iob) {
				vc = null;
			}
			RTableCell ac = vc == null ? null : vc.getActualCell();
			if(ac != null) {
				if(ac.getVirtualRow() == row) {
					// Only process actual cells with a row
					// beginning at the current row being processed.
					int colSpan = ac.getColSpan();
					if(colSpan > 1) {
						int firstCol = ac.getVirtualColumn();
						int cellExtras = (colSpan - 1) * (cellSpacingX + 2 * hasBorder);
						int vcActualWidth = cellExtras;
						for(int x = 0; x < colSpan; x++) {
							vcActualWidth += columnSizes[firstCol + x].actualSize;
						}
						//TODO: better height possible
						Dimension size = ac.doCellLayout(vcActualWidth, 0, true, true, true);
						int vcRenderWidth = size.width;

						int denominator = (vcActualWidth - cellExtras);
						int newTentativeCellWidth;
						if(denominator > 0) {
							newTentativeCellWidth = actualSize * (vcRenderWidth - cellExtras) / denominator;
						}
						else {
							newTentativeCellWidth = (vcRenderWidth - cellExtras) / colSpan;
						}
						if(newTentativeCellWidth > colSize.layoutSize) {
							colSize.layoutSize = newTentativeCellWidth;
						}
						int rowSpan = ac.getRowSpan();
						int vch = (size.height - (rowSpan - 1) * (this.cellSpacingY + 2 * hasBorder)) / rowSpan;
						for(int y = 0; y < rowSpan; y++) {
							if(rowSizes[row + y].minSize < vch) {
								rowSizes[row + y].minSize = vch;
							}
						}
					}
					else {
						//TODO: better height possible
						Dimension size = ac.doCellLayout(actualSize, 0, true, true, true);
						if(size.width > colSize.layoutSize) {
							colSize.layoutSize = size.width;
						}
						int rowSpan = ac.getRowSpan();
						int vch = (size.height - (rowSpan - 1) * (this.cellSpacingY + 2 * hasBorder)) / rowSpan;
						for(int y = 0; y < rowSpan; y++) {
							if(rowSizes[row + y].minSize < vch) {
								rowSizes[row + y].minSize = vch;
							}
						}
					}
				}
			}
			//row = (ac == null ? row + 1 : ac.getVirtualRow() + ac.getRowSpan());
			row++;
		}
	}
	
	private int adjustWidthsForExpectedMax(SizeInfo[] columnSizes, int cellAvailWidth, boolean expand) {
		int hasBorder = this.hasOldStyleBorder;
		int cellSpacingX = this.cellSpacingX;
		int currentTotal = 0;
		int numCols = columnSizes.length;
		for(int i = 0; i < numCols; i++) {
			currentTotal += columnSizes[i].actualSize;
		}
		int difference = currentTotal - cellAvailWidth;
		if(difference > 0 || (difference < 0 && expand)) {
			// First, try to contract/expand columns with no width
			int noWidthTotal = 0;
			int numNoWidth = 0;
			for(int i = 0; i < numCols; i++) {
				if(columnSizes[i].htmlLength == null) {
					numNoWidth++;
					noWidthTotal += columnSizes[i].actualSize;
				}
			}
			if(noWidthTotal > 0) {
				//TODO: This is not shrinking correctly.				
				int expectedNoWidthTotal = noWidthTotal - difference;
				if(expectedNoWidthTotal < 0) {
					expectedNoWidthTotal = 0;
				}
				double ratio = (double) expectedNoWidthTotal / noWidthTotal;
				int noWidthCount = 0;
				for(int i = 0; i < numCols; i++) {
					SizeInfo sizeInfo = columnSizes[i];
					if(sizeInfo.htmlLength == null) {
						int oldActualSize = sizeInfo.actualSize;
						int newActualSize;
						if(++noWidthCount == numNoWidth) {
							// Last column without a width.							
							int currentDiff = currentTotal - cellAvailWidth;
							newActualSize = oldActualSize - currentDiff;
							if(newActualSize < 0) {
								newActualSize = 0;
							}
						}
						else {
							newActualSize = (int) Math.round(oldActualSize * ratio);
						}
						sizeInfo.actualSize = newActualSize;
						if(newActualSize < sizeInfo.layoutSize) {
							// See if it actually fits.
							this.layoutColumn(columnSizes, sizeInfo, i, cellSpacingX, hasBorder);
							if(newActualSize < sizeInfo.layoutSize) {
								// Didn't fit.
								newActualSize = sizeInfo.layoutSize;
								sizeInfo.actualSize = newActualSize;
							}
						}
						currentTotal += (newActualSize - oldActualSize);
					}
				}
				difference = currentTotal - cellAvailWidth;
			}
			
			// See if absolutes need to be contracted
			if(difference > 0 || (difference < 0 && expand)) {
				int absoluteWidthTotal = 0;
				for(int i = 0; i < numCols; i++) {
					HtmlLength widthLength = columnSizes[i].htmlLength;
					if(widthLength != null && widthLength.getLengthType() != HtmlLength.LENGTH) {
						absoluteWidthTotal += columnSizes[i].actualSize;
					}
				}
				if(absoluteWidthTotal > 0) {
					int expectedAbsoluteWidthTotal = absoluteWidthTotal - difference;
					if(expectedAbsoluteWidthTotal < 0) {
						expectedAbsoluteWidthTotal = 0;
					}
					double ratio = (double) expectedAbsoluteWidthTotal / absoluteWidthTotal;
					for(int i = 0; i < numCols; i++) {
						SizeInfo sizeInfo = columnSizes[i];
						HtmlLength widthLength = columnSizes[i].htmlLength;
						if(widthLength != null && widthLength.getLengthType() != HtmlLength.LENGTH) {
							int oldActualSize = sizeInfo.actualSize;
							int newActualSize = (int) Math.round(oldActualSize * ratio);
							sizeInfo.actualSize = newActualSize;
							if(newActualSize < sizeInfo.layoutSize) {
								// See if it actually fits.
								this.layoutColumn(columnSizes, sizeInfo, i, cellSpacingX, hasBorder);
								if(newActualSize < sizeInfo.layoutSize) {
									// Didn't fit.
									newActualSize = sizeInfo.layoutSize;
									sizeInfo.actualSize = newActualSize;
								}
							}
							currentTotal += (newActualSize - oldActualSize);
						}
					}				
					difference = currentTotal - cellAvailWidth;
				}
				
				// See if percentages need to be contracted
				if(difference > 0 || (difference < 0 && expand)) {
					int percentWidthTotal = 0;
					for(int i = 0; i < numCols; i++) {
						HtmlLength widthLength = columnSizes[i].htmlLength;
						if(widthLength != null && widthLength.getLengthType() == HtmlLength.LENGTH) {
							percentWidthTotal += columnSizes[i].actualSize;
						}
					}
					if(percentWidthTotal > 0) {
						int expectedPercentWidthTotal = percentWidthTotal - difference;
						if(expectedPercentWidthTotal < 0) {
							expectedPercentWidthTotal = 0;
						}
						double ratio = (double) expectedPercentWidthTotal / percentWidthTotal;
						for(int i = 0; i < numCols; i++) {
							SizeInfo sizeInfo = columnSizes[i];
							HtmlLength widthLength = columnSizes[i].htmlLength;
							if(widthLength != null && widthLength.getLengthType() == HtmlLength.LENGTH) {
								int oldActualSize = sizeInfo.actualSize;
								int newActualSize = (int) Math.round(oldActualSize * ratio);
								sizeInfo.actualSize = newActualSize;
								if(newActualSize < sizeInfo.layoutSize) {
									// See if it actually fits.
									this.layoutColumn(columnSizes, sizeInfo, i, cellSpacingX, hasBorder);
									if(newActualSize < sizeInfo.layoutSize) {
										// Didn't fit.
										newActualSize = sizeInfo.layoutSize;
										sizeInfo.actualSize = newActualSize;
									}
								}									
								currentTotal += (newActualSize - oldActualSize);
							}
						}							
					}
				}
			}		
		}
		return currentTotal;
	}
	
	/**
	 * This method renders each cell using already set actual column widths.
	 * It sets minimum row heights based on this.
	 */
	private final void preLayout(int hasBorder, int cellSpacingX, int cellSpacingY, boolean tableWidthKnown) {
		//TODO: Fix for table without width that has a subtable with width=100%.
		//TODO: Maybe it can be addressed when NOWRAP is implemented.
		//TODO: Maybe it's possible to eliminate this pre-layout altogether.
		
		SizeInfo[] colSizes = this.columnSizes;
		SizeInfo[] rowSizes = this.rowSizes;

		// Initialize minSize in rows
		int numRows = rowSizes.length;
		for(int i = 0; i < numRows; i++) {
			rowSizes[i].minSize = 0;
		}
		
		// Initialize layoutSize in columns
		int numCols = colSizes.length;
		for(int i = 0; i < numCols; i++) {
			colSizes[i].layoutSize = 0;
		}
		
		ArrayList allCells = this.ALL_CELLS;
		int numCells = allCells.size();
		for(int i = 0; i < numCells; i++) {
			RTableCell cell = (RTableCell) allCells.get(i);
			int col = cell.getVirtualColumn();
			int colSpan = cell.getColSpan();
			int cellsTotalWidth;
			int cellsUsedWidth;
			boolean widthDeclared = false;
			if(colSpan > 1) {
				cellsUsedWidth = 0;
				for(int x = 0; x < colSpan; x++) {
					SizeInfo colSize = colSizes[col + x];
					if(colSize.htmlLength != null) {
						widthDeclared = true;
					}
					cellsUsedWidth += colSize.actualSize;
				}
				cellsTotalWidth = cellsUsedWidth + (colSpan - 1) * (cellSpacingX + 2 * hasBorder);
			}
			else {
				SizeInfo colSize = colSizes[col];
				if(colSize.htmlLength != null) {
					widthDeclared = true;
				}
				cellsUsedWidth = cellsTotalWidth = colSize.actualSize;
			}

			//TODO: A tentative height could be used here: Height of
			//table divided by number of rows.

			java.awt.Dimension size;
			RenderThreadState state = RenderThreadState.getState();
			boolean prevOverrideNoWrap = state.overrideNoWrap;
			try {
				if(!prevOverrideNoWrap) {
					state.overrideNoWrap = !widthDeclared;
				}
				size = cell.doCellLayout(cellsTotalWidth, 0, true, true, true);
			} finally {
				state.overrideNoWrap = prevOverrideNoWrap;
			}
			// Set render widths
			int cellLayoutWidth = size.width;
			if(colSpan > 1) {
				if(cellsUsedWidth > 0) {
					double ratio = (double) cellLayoutWidth / cellsUsedWidth;
					for(int x = 0; x < colSpan; x++) {
						SizeInfo si = colSizes[col + x];
						int newLayoutSize = (int) Math.round(si.actualSize * ratio);
						if(si.layoutSize < newLayoutSize) {
							si.layoutSize = newLayoutSize;
						}
					}					
				}
				else {
					int newLayoutSize = cellLayoutWidth / colSpan;
					for(int x = 0; x < colSpan; x++) {
						SizeInfo si = colSizes[col + x];
						if(si.layoutSize < newLayoutSize) {
							si.layoutSize = newLayoutSize;
						}
					}										
				}
			}
			else {
				SizeInfo colSizeInfo = colSizes[col];
				if(colSizeInfo.layoutSize < cellLayoutWidth) {
					colSizeInfo.layoutSize = cellLayoutWidth;
				}
			}
			
			// Set minimum heights
			int actualCellHeight = size.height;
			int row = cell.getVirtualRow();
			int rowSpan = cell.getRowSpan();
			if(rowSpan > 1) {
				int vch = (actualCellHeight - (rowSpan - 1) * (cellSpacingY + 2 * hasBorder)) / rowSpan;
				for(int y = 0; y < rowSpan; y++) {
					if(rowSizes[row + y].minSize < vch) {
						rowSizes[row + y].minSize = vch;
					}
				}
			}
			else {
				if(rowSizes[row].minSize < actualCellHeight) {
					rowSizes[row].minSize = actualCellHeight;
				}				
			}
		}
	}

	private void determineRowSizes(int hasBorder, int cellSpacing, int availHeight, boolean sizeOnly) {
		HtmlLength tableHeightLength = TableMatrix.getHeightLength(this.tableElement, availHeight);
		int tableHeight;
		SizeInfo[] rowSizes = this.rowSizes;
		int numRows = rowSizes.length;
		int heightsOfExtras = this.heightsOfExtras;
		if(tableHeightLength != null) {
			tableHeight = tableHeightLength.getLength(availHeight);
			this.determineRowSizesFixedTH(hasBorder, cellSpacing, availHeight, tableHeight, sizeOnly);
		}
		else {
			tableHeight = heightsOfExtras;
			for(int row = 0; row < numRows; row++) {
				tableHeight += rowSizes[row].minSize;
			}
			this.determineRowSizesFlexibleTH(hasBorder, cellSpacing, availHeight, sizeOnly);
		}
	}
	
	private void determineRowSizesFixedTH(int hasBorder, int cellSpacing, int availHeight, int tableHeight, boolean sizeOnly) {
		SizeInfo[] rowSizes = this.rowSizes;
		int numRows = rowSizes.length;
		int heightsOfExtras = this.heightsOfExtras;
		int cellAvailHeight = tableHeight - heightsOfExtras;
		if(cellAvailHeight < 0) {
			cellAvailHeight = 0;
		}
		
		// Look at percentages first
		
		int heightUsedbyPercent = 0;
		int otherMinSize = 0;
		for(int i = 0; i < numRows; i++) {
			SizeInfo rowSizeInfo = rowSizes[i];
			HtmlLength heightLength = rowSizeInfo.htmlLength;
			if(heightLength != null && heightLength.getLengthType() == HtmlLength.LENGTH) {
				int actualSizeInt = heightLength.getLength(cellAvailHeight);
				if(actualSizeInt < rowSizeInfo.minSize) {
					actualSizeInt = rowSizeInfo.minSize;
				}
				heightUsedbyPercent += actualSizeInt;
				rowSizeInfo.actualSize = actualSizeInt;
			}
			else {
				otherMinSize += rowSizeInfo.minSize;
			}
		}
		
		// Check if rows with percent are bigger than they should be
		
		if(heightUsedbyPercent + otherMinSize > cellAvailHeight) {
			double ratio = (double) (cellAvailHeight - otherMinSize) / heightUsedbyPercent; 
			for(int i = 0; i < numRows; i++) {
				SizeInfo rowSizeInfo = rowSizes[i];
				HtmlLength heightLength = rowSizeInfo.htmlLength;
				if(heightLength != null && heightLength.getLengthType() == HtmlLength.LENGTH) {
					int actualSize = rowSizeInfo.actualSize;
					int prevActualSize = actualSize;
					int newActualSize = (int) Math.round(prevActualSize * ratio);
					if(newActualSize < rowSizeInfo.minSize) {
						newActualSize = rowSizeInfo.minSize;
					}
					heightUsedbyPercent += (newActualSize - prevActualSize);
					rowSizeInfo.actualSize = newActualSize;
				}
			}				
		}
		
		// Look at rows with absolute sizes
		
		int heightUsedByAbsolute = 0;
		int noHeightMinSize = 0;
		int numNoHeightColumns = 0;
		for(int i = 0; i < numRows; i++) {
			SizeInfo rowSizeInfo = rowSizes[i];
			HtmlLength heightLength = rowSizeInfo.htmlLength;
			if(heightLength != null && heightLength.getLengthType() != HtmlLength.LENGTH) {
				//TODO: MULTI-LENGTH not supported
				int actualSizeInt = heightLength.getRawValue();
				if(actualSizeInt < rowSizeInfo.minSize) {
					actualSizeInt = rowSizeInfo.minSize;
				}
				heightUsedByAbsolute += actualSizeInt;
				rowSizeInfo.actualSize = actualSizeInt;
			}
			else if(heightLength == null) {
				numNoHeightColumns++;
				noHeightMinSize += rowSizeInfo.minSize;
			}
		}
		
		// Check if absolute sizing is too much
		
		if(heightUsedByAbsolute + heightUsedbyPercent + noHeightMinSize > cellAvailHeight) {
			double ratio = (double) (cellAvailHeight - noHeightMinSize - heightUsedbyPercent) / heightUsedByAbsolute;
			for(int i = 0; i < numRows; i++) {
				SizeInfo rowSizeInfo = rowSizes[i];
				HtmlLength heightLength = rowSizeInfo.htmlLength;
				if(heightLength != null && heightLength.getLengthType() != HtmlLength.LENGTH) {
					int actualSize = rowSizeInfo.actualSize;
					int prevActualSize = actualSize;
					int newActualSize = (int) Math.round(prevActualSize * ratio);
					if(newActualSize < rowSizeInfo.minSize) {
						newActualSize = rowSizeInfo.minSize;
					}
					heightUsedByAbsolute += (newActualSize - prevActualSize);
					rowSizeInfo.actualSize = newActualSize;
				}
			}									
		}
		
		// Assign all rows without heights now
		
		int remainingHeight = cellAvailHeight - heightUsedByAbsolute - heightUsedbyPercent;
		int heightUsedByRemaining = 0;
		for(int i = 0; i < numRows; i++) {
			SizeInfo rowSizeInfo = rowSizes[i];
			HtmlLength heightLength = rowSizeInfo.htmlLength;
			if(heightLength == null) {
				int actualSizeInt = remainingHeight / numNoHeightColumns;
				if(actualSizeInt < rowSizeInfo.minSize) {
					actualSizeInt = rowSizeInfo.minSize;
				}
				heightUsedByRemaining += actualSizeInt;
				rowSizeInfo.actualSize = actualSizeInt;
			}
		}

		// Calculate actual table width

		int totalUsed = heightUsedByAbsolute + heightUsedbyPercent + heightUsedByRemaining;
		if(totalUsed >= cellAvailHeight) {
			this.tableHeight = totalUsed + heightsOfExtras;
		}
		else {
			// Rows too short; expand them
			double ratio = (double) cellAvailHeight / totalUsed;
			for(int i = 0; i < numRows; i++) {
				SizeInfo rowSizeInfo = rowSizes[i];
				int actualSize = rowSizeInfo.actualSize;
				rowSizeInfo.actualSize = (int) Math.round(actualSize * ratio);
			}
			this.tableHeight = tableHeight;
		}
		
		//TODO:
		// This final render is probably unnecessary. Avoid exponential rendering
		// by setting a single height of subcell. Verify that IE only sets height
		// of subcells when height of row or table are specified.
		
		this.finalRender(hasBorder, cellSpacing, sizeOnly);
	}

	private void determineRowSizesFlexibleTH(int hasBorder, int cellSpacing, int availHeight, boolean sizeOnly) {
		SizeInfo[] rowSizes = this.rowSizes;
		int numRows = rowSizes.length;
		int heightsOfExtras = this.heightsOfExtras;
		
		// Look at rows with absolute sizes		
		int heightUsedByAbsolute = 0;
		int percentSum = 0;
		for(int i = 0; i < numRows; i++) {
			SizeInfo rowSizeInfo = rowSizes[i];
			HtmlLength heightLength = rowSizeInfo.htmlLength;
			if(heightLength != null && heightLength.getLengthType() == HtmlLength.PIXELS) {
				//TODO: MULTI-LENGTH not supported
				int actualSizeInt = heightLength.getRawValue();
				if(actualSizeInt < rowSizeInfo.minSize) {
					actualSizeInt = rowSizeInfo.minSize;
				}
				heightUsedByAbsolute += actualSizeInt;
				rowSizeInfo.actualSize = actualSizeInt;
			}
			else if(heightLength != null && heightLength.getLengthType() == HtmlLength.LENGTH) {
				percentSum += heightLength.getRawValue();
			}
		}
			
		// Look at rows with no specified heights		
		int heightUsedByNoSize = 0;
		
		// Set sizes to in row height
		for(int i = 0; i < numRows; i++) {
			SizeInfo rowSizeInfo = rowSizes[i];
			HtmlLength widthLength = rowSizeInfo.htmlLength;
			if(widthLength == null) {
				int actualSizeInt = rowSizeInfo.minSize;
				heightUsedByNoSize += actualSizeInt;
				rowSizeInfo.actualSize = actualSizeInt;
			}
		}
		
		// Calculate actual total cell width
		int expectedTotalCellHeight = (int) Math.round((heightUsedByAbsolute + heightUsedByNoSize) / (1 - (percentSum / 100.0)));
		
		// Set widths of columns with percentages		
		int heightUsedByPercent = 0;
		for(int i = 0; i < numRows; i++) {
			SizeInfo rowSizeInfo = rowSizes[i];
			HtmlLength heightLength = rowSizeInfo.htmlLength;
			if(heightLength != null && heightLength.getLengthType() == HtmlLength.LENGTH) {
				int actualSizeInt = heightLength.getLength(expectedTotalCellHeight);
				if(actualSizeInt < rowSizeInfo.minSize) {
					actualSizeInt = rowSizeInfo.minSize;
				}
				heightUsedByPercent += actualSizeInt;
				rowSizeInfo.actualSize = actualSizeInt;
			}
		}
						
		// Set width of table
		this.tableHeight = heightUsedByAbsolute + heightUsedByNoSize + heightUsedByPercent + heightsOfExtras;
		
		// Do a final render to set actual cell sizes		
		this.finalRender(hasBorder, cellSpacing, sizeOnly);
	}

	/**
	 * This method renders each cell using already set actual column widths.
	 * It sets minimum row heights based on this.
	 */
	private final void finalRender(int hasBorder, int cellSpacing, boolean sizeOnly) {
		// finalRender needs to adjust actualSize of columns and rows
		// given that things might change as we render one last time.
		ArrayList allCells = this.ALL_CELLS;
		SizeInfo[] colSizes = this.columnSizes;
		SizeInfo[] rowSizes = this.rowSizes;
		int numCells = allCells.size();
		for(int i = 0; i < numCells; i++) {
			RTableCell cell = (RTableCell) allCells.get(i);
			int col = cell.getVirtualColumn();
			int colSpan = cell.getColSpan();
			int totalCellWidth;
			if(colSpan > 1) {
				totalCellWidth = (colSpan - 1) * (cellSpacing + 2 * hasBorder);
				for(int x = 0; x < colSpan; x++) {
					totalCellWidth += colSizes[col + x].actualSize;
				}
			}
			else {
				totalCellWidth = colSizes[col].actualSize;
			}
			int row = cell.getVirtualRow();
			int rowSpan = cell.getRowSpan();
			int totalCellHeight;
			if(rowSpan > 1) {
				totalCellHeight = (rowSpan - 1) * (cellSpacing + 2 * hasBorder);
				for(int y = 0; y < rowSpan; y++) {
					totalCellHeight += rowSizes[row + y].actualSize;
				}
			}
			else {
				totalCellHeight = rowSizes[row].actualSize;
			}
			Dimension size = cell.doCellLayout(totalCellWidth, totalCellHeight, true, true, sizeOnly); 
			if(size.width > totalCellWidth) {
				if(colSpan == 1) {
					colSizes[col].actualSize = size.width;
				}
				else {
					colSizes[col].actualSize += (size.width - totalCellWidth);
				}
			}
			if(size.height > totalCellHeight) {
				if(rowSpan == 1) {
					rowSizes[row].actualSize = size.height;
				}
				else {
					rowSizes[row].actualSize += (size.height - totalCellHeight);
				}
			}			
		}
	}

//	public final void adjust() {
//	    // finalRender needs to adjust actualSize of columns and rows
//	    // given that things might change as we render one last time.
//	    int hasBorder = this.hasOldStyleBorder;
//	    int cellSpacingX = this.cellSpacingX;
//	    int cellSpacingY = this.cellSpacingY;
//	    ArrayList allCells = this.ALL_CELLS;
//	    SizeInfo[] colSizes = this.columnSizes;
//	    SizeInfo[] rowSizes = this.rowSizes;
//	    int numCells = allCells.size();
//	    for(int i = 0; i < numCells; i++) {
//	        RTableCell cell = (RTableCell) allCells.get(i);
//	        int col = cell.getVirtualColumn();
//	        int colSpan = cell.getColSpan();
//	        int totalCellWidth;
//	        if(colSpan > 1) {
//	            totalCellWidth = (colSpan - 1) * (cellSpacingX + 2 * hasBorder);
//	            for(int x = 0; x < colSpan; x++) {
//	                totalCellWidth += colSizes[col + x].actualSize;
//	            }
//	        }
//	        else {
//	            totalCellWidth = colSizes[col].actualSize;
//	        }
//	        int row = cell.getVirtualRow();
//	        int rowSpan = cell.getRowSpan();
//	        int totalCellHeight;
//	        if(rowSpan > 1) {
//	            totalCellHeight = (rowSpan - 1) * (cellSpacingY + 2 * hasBorder);
//	            for(int y = 0; y < rowSpan; y++) {
//	                totalCellHeight += rowSizes[row + y].actualSize;
//	            }
//	        }
//	        else {
//	            totalCellHeight = rowSizes[row].actualSize;
//	        }
//	        cell.adjust();
//	        Dimension size = cell.getSize();
//	        if(size.width > totalCellWidth) {
//	            if(colSpan == 1) {
//	                colSizes[col].actualSize = size.width;
//	            }
//	            else {
//	                colSizes[col].actualSize += (size.width - totalCellWidth);
//	            }
//	        }
//	        if(size.height > totalCellHeight) {
//	            if(rowSpan == 1) {
//	                rowSizes[row].actualSize = size.height;
//	            }
//	            else {
//	                rowSizes[row].actualSize += (size.height - totalCellHeight);
//	            }
//	        }           
//	    }
//	}

	/**
	 * Sets bounds of each cell's component, and sumps up table width
	 * and height.
	 */
	public final void doLayout(Insets insets) {
		
		// Set row offsets
		
		SizeInfo[] rowSizes = this.rowSizes;
		int numRows = rowSizes.length;
		int yoffset = insets.top;
		int cellSpacingY = this.cellSpacingY;
		int hasBorder = this.hasOldStyleBorder;
		for(int i = 0; i < numRows; i++) {
			yoffset += cellSpacingY;
			yoffset += hasBorder;
			SizeInfo rowSizeInfo = rowSizes[i];
			rowSizeInfo.offset = yoffset;
			yoffset += rowSizeInfo.actualSize;
			yoffset += hasBorder;
		}
		this.tableHeight = yoffset + cellSpacingY + insets.bottom;
		
		// Set colum offsets 
		
		SizeInfo[] colSizes = this.columnSizes;
		int numColumns = colSizes.length; 
		int xoffset = insets.left;
		int cellSpacingX = this.cellSpacingX;
		for(int i = 0; i < numColumns; i++) {
			xoffset += cellSpacingX;
			xoffset += hasBorder;
			SizeInfo colSizeInfo = colSizes[i];
			colSizeInfo.offset = xoffset;
			xoffset += colSizeInfo.actualSize;
			xoffset += hasBorder;
		}
		this.tableWidth = xoffset + cellSpacingX + insets.right;
		
		// Set offsets of each cell
		
		ArrayList allCells = this.ALL_CELLS;
		int numCells = allCells.size();
		for(int i = 0; i < numCells; i++) {
			RTableCell cell = (RTableCell) allCells.get(i);
			cell.setCellBounds(colSizes, rowSizes, hasBorder, cellSpacingX, cellSpacingY);
		}		
	}
	
	public final void paint(Graphics g, Dimension size) {
		ArrayList allCells = this.ALL_CELLS;
		int numCells = allCells.size();
		for(int i = 0; i < numCells; i++) {
			RTableCell cell = (RTableCell) allCells.get(i);
			// Should clip table cells, just in case.
			Graphics newG = g.create(cell.x, cell.y, cell.width, cell.height);
			try {
				cell.paint(newG);
			} finally {
				newG.dispose();
			}
		}		

		if(this.hasOldStyleBorder > 0) {
//			// Paint table border
//			
//			int tableWidth = this.tableWidth;
//			int tableHeight = this.tableHeight;
//			g.setColor(Color.BLACK); //TODO: Actual border color
//			int x = insets.left;
//			int y = insets.top;
//			for(int i = 0; i < border; i++) {
//				g.drawRect(x + i, y + i, tableWidth - i * 2 - 1, tableHeight - i * 2 - 1);
//			}

			// Paint cell borders
			
			g.setColor(Color.GRAY);
			for(int i = 0; i < numCells; i++) {
				RTableCell cell = (RTableCell) allCells.get(i);
				int cx = cell.getX() - 1;
				int cy = cell.getY() - 1;
				int cwidth = cell.getWidth() + 1;
				int cheight = cell.getHeight() + 1;
				g.drawRect(cx, cy, cwidth, cheight);
			}					
		}
	}

//	public boolean paintSelection(Graphics g, boolean inSelection, RenderableSpot startPoint, RenderableSpot endPoint) {
//		ArrayList allCells = this.ALL_CELLS;
//		int numCells = allCells.size();
//		for(int i = 0; i < numCells; i++) {
//			RTableCell cell = (RTableCell) allCells.get(i);
//			Rectangle bounds = cell.getBounds();
//			int offsetX = bounds.x;
//			int offsetY = bounds.y;
//			g.translate(offsetX, offsetY);
//			try {
//				boolean newInSelection = cell.paintSelection(g, inSelection, startPoint, endPoint);
//				if(inSelection && !newInSelection) {
//					return false;
//				}
//				inSelection = newInSelection;
//			} finally {
//				g.translate(-offsetX, -offsetY);
//			}
//		}
//		return inSelection;
//	}
//
//	public boolean extractSelectionText(StringBuffer buffer, boolean inSelection, RenderableSpot startPoint, RenderableSpot endPoint) {
//		ArrayList allCells = this.ALL_CELLS;
//		int numCells = allCells.size();
//		for(int i = 0; i < numCells; i++) {
//			RTableCell cell = (RTableCell) allCells.get(i);
//			boolean newInSelection = cell.extractSelectionText(buffer, inSelection, startPoint, endPoint);
//			if(inSelection && !newInSelection) {
//				return false;
//			}
//			inSelection = newInSelection;
//		}
//		return inSelection;
//	}

	/* (non-Javadoc)
	 * @see org.xamjwg.html.renderer.BoundableRenderable#getRenderablePoint(int, int)
	 */
	public RenderableSpot getLowestRenderableSpot(int x, int y) {
		ArrayList allCells = this.ALL_CELLS;
		int numCells = allCells.size();
		for(int i = 0; i < numCells; i++) {
			RTableCell cell = (RTableCell) allCells.get(i);
			Rectangle bounds = cell.getBounds();
			if(bounds.contains(x, y)) {
				RenderableSpot rp = cell.getLowestRenderableSpot(x - bounds.x, y - bounds.y);
				if(rp != null) {
					return rp;
				}
			}
		}
		return null;
	}

	/* (non-Javadoc)
	 * @see org.xamjwg.html.renderer.BoundableRenderable#onMouseClick(java.awt.event.MouseEvent, int, int)
	 */
	public boolean onMouseClick(MouseEvent event, int x, int y) {
		ArrayList allCells = this.ALL_CELLS;
		int numCells = allCells.size();
		for(int i = 0; i < numCells; i++) {
			RTableCell cell = (RTableCell) allCells.get(i);
			Rectangle bounds = cell.getBounds();
			if(bounds.contains(x, y)) {
				if(!cell.onMouseClick(event, x - bounds.x, y - bounds.y)) {
					return false;
				}
				break;
			}
		}
		return true;
	}

	public boolean onDoubleClick(MouseEvent event, int x, int y) {
		ArrayList allCells = this.ALL_CELLS;
		int numCells = allCells.size();
		for(int i = 0; i < numCells; i++) {
			RTableCell cell = (RTableCell) allCells.get(i);
			Rectangle bounds = cell.getBounds();
			if(bounds.contains(x, y)) {
				if(!cell.onDoubleClick(event, x - bounds.x, y - bounds.y)) {
					return false;
				}
				break;
			}
		}
		return true;
	}

	private BoundableRenderable armedRenderable;
	
	/* (non-Javadoc)
	 * @see org.xamjwg.html.renderer.BoundableRenderable#onMouseDisarmed(java.awt.event.MouseEvent)
	 */
	public boolean onMouseDisarmed(MouseEvent event) {
		BoundableRenderable ar = this.armedRenderable;
		if(ar != null) {
			this.armedRenderable = null;
			return ar.onMouseDisarmed(event);
		}
		else {
			return true;
		}
	}

	/* (non-Javadoc)
	 * @see org.xamjwg.html.renderer.BoundableRenderable#onMousePressed(java.awt.event.MouseEvent, int, int)
	 */
	public boolean onMousePressed(MouseEvent event, int x, int y) {
		ArrayList allCells = this.ALL_CELLS;
		int numCells = allCells.size();
		for(int i = 0; i < numCells; i++) {
			RTableCell cell = (RTableCell) allCells.get(i);
			Rectangle bounds = cell.getBounds();
			if(bounds.contains(x, y)) {
				if(!cell.onMousePressed(event, x - bounds.x, y - bounds.y)) {
					this.armedRenderable = cell;
					return false;
				}
				break;
			}
		}
		return true;
	}

	/* (non-Javadoc)
	 * @see org.xamjwg.html.renderer.BoundableRenderable#onMouseReleased(java.awt.event.MouseEvent, int, int)
	 */
	public boolean onMouseReleased(MouseEvent event, int x, int y) {
		ArrayList allCells = this.ALL_CELLS;
		int numCells = allCells.size();
		boolean found = false;
		for(int i = 0; i < numCells; i++) {
			RTableCell cell = (RTableCell) allCells.get(i);
			Rectangle bounds = cell.getBounds();
			if(bounds.contains(x, y)) {
				found = true;
		    	BoundableRenderable oldArmedRenderable = this.armedRenderable;
		    	if(oldArmedRenderable != null && cell != oldArmedRenderable) {
		    		oldArmedRenderable.onMouseDisarmed(event);
		    		this.armedRenderable = null;
		    	}
				if(!cell.onMouseReleased(event, x - bounds.x, y - bounds.y)) {
					return false;
				}
				break;
			}
		}
		if(!found) {
	    	BoundableRenderable oldArmedRenderable = this.armedRenderable;
	    	if(oldArmedRenderable != null) {
	    		oldArmedRenderable.onMouseDisarmed(event);
	    		this.armedRenderable = null;
	    	}
		}
		return true;
	}

	public Iterator getRenderables() {
		return this.ALL_CELLS.iterator();
	}

	private static class RowsFilter implements NodeFilter {
    	public final boolean accept(Node node) {
    		return (node instanceof HTMLTableRowElement);
    	}
    }

    private static class ColumnsFilter implements NodeFilter {
    	public final boolean accept(Node node) {
    		return (node instanceof HTMLTableCellElement);
    	}
    }
    
    public static class SizeInfo {
    	public HtmlLength htmlLength;
    	public int actualSize;
    	public int layoutSize;
    	public int minSize;
    	public int offset;
    }
}
