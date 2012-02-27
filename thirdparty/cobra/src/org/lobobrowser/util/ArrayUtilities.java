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
 * Created on Apr 17, 2005
 */
package org.lobobrowser.util;

import java.util.*;

/**
 * @author J. H. S.
 */
public class ArrayUtilities {

	/**
	 * 
	 */
	private ArrayUtilities() {
		super();
	}

	public static Iterator iterator(Object[] array, int offset, int length) {
		return new ArrayIterator(array, offset, length);
	}
	
	private static class ArrayIterator implements Iterator {
		private final Object[] array;
		private final int top;
		private int offset;
		
		public ArrayIterator(Object[] array, int offset, int length) {
			this.array = array;
			this.offset = offset;
			this.top = offset + length;
		}
		
		/* (non-Javadoc)
		 * @see java.util.Iterator#hasNext()
		 */
		public boolean hasNext() {
			return this.offset < this.top;
		}
		
		/* (non-Javadoc)
		 * @see java.util.Iterator#next()
		 */
		public Object next() {
			return this.array[this.offset++];
		}
		
		/* (non-Javadoc)
		 * @see java.util.Iterator#remove()
		 */
		public void remove() {
			throw new UnsupportedOperationException();
		}
	}
}
