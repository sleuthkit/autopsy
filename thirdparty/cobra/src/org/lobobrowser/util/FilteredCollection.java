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
 * Created on Oct 8, 2005
 */
package org.lobobrowser.util;

import java.util.*;

public class FilteredCollection implements Collection {
	private final ObjectFilter filter;
	private final Collection sourceCollection;
	
	public FilteredCollection(Collection sourceCollection, ObjectFilter filter) {
		this.filter = filter;
		this.sourceCollection = sourceCollection;
	}

	public int size() {
		int count = 0;
		Iterator i = this.sourceCollection.iterator();
		while(i.hasNext()) {
			if(this.filter.decode(i.next()) != null) {
				count++;
			}
		}
		return count;
	}

	public boolean isEmpty() {
		Iterator i = this.sourceCollection.iterator();
		while(i.hasNext()) {
			if(this.filter.decode(i.next()) != null) {
				return false;
			}
		}
		return true;
	}

	public boolean contains(Object o) {
		return this.sourceCollection.contains(this.filter.encode(o));
	}

	public Iterator iterator() {
		final Iterator sourceIterator = this.sourceCollection.iterator();
		return new Iterator() {
			private Boolean hasNext;
			private Object next;
			
			private void scanNext() {
				while(sourceIterator.hasNext()) {
					Object item = filter.decode(sourceIterator.next());
					if(item != null) {
						hasNext = Boolean.TRUE;
						this.next = item;
					}
				}
				hasNext = Boolean.FALSE;
			}
			
			/* (non-Javadoc)
			 * @see java.util.Iterator#hasNext()
			 */
			public boolean hasNext() {
				if(this.hasNext == null) {
					scanNext();
				}
				return this.hasNext.booleanValue();
			}

			/* (non-Javadoc)
			 * @see java.util.Iterator#next()
			 */
			public Object next() {
				if(this.hasNext == null) {
					scanNext();
				}
				if(Boolean.FALSE.equals(this.hasNext)) {
					throw new NoSuchElementException();
				}
				Object next = this.next;
				this.hasNext = null;
				return next;
			}

			/* (non-Javadoc)
			 * @see java.util.Iterator#remove()
			 */
			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}

	public Object[] toArray() {
		return this.toArray(new Object[0]);
	}

	public Object[] toArray(Object[] a) {
		Collection bucket = new ArrayList();
		Iterator i = this.sourceCollection.iterator();
		while(i.hasNext()) {
			Object item = this.filter.decode(i.next()); 
			if(item != null) {
				bucket.add(item);
			}
		}
		return bucket.toArray(a);
	}

	public boolean add(Object o) {
		return this.sourceCollection.add(this.filter.encode(o));
	}

	public boolean remove(Object o) {
		return this.sourceCollection.remove(this.filter.encode(o));
	}

	public boolean containsAll(Collection c) {
		Iterator i = c.iterator();
		while(i.hasNext()) {
			if(!this.contains(i.next())) {
				return false;
			}
		}
		return true;
	}

	public boolean addAll(Collection c) {
		boolean result = false;
		Iterator i = c.iterator();
		while(i.hasNext()) {
			if(this.add(i.next())) {
				result = true;
			}
		}
		return result;
	}

	public boolean removeAll(Collection c) {
		boolean result = false;
		Iterator i = c.iterator();
		while(i.hasNext()) {
			if(this.remove(i.next())) {
				result = true;
			}
		}
		return result;
	}

	public boolean retainAll(Collection c) {
		boolean result = false;
		Object[] values = this.toArray();
		for(int i = 0; i < values.length; i++) {
			if(!c.contains(values[i])) {
				if(this.remove(values[i])) {
					result = true;
				}
			}
		}
		return result;
	}

	public void clear() {
		Object[] values = this.toArray();
		for(int i = 0; i < values.length; i++) {
			this.sourceCollection.remove(this.filter.encode(values[i]));
		}
	}
}
