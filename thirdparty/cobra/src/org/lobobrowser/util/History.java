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
 * Created on Jun 6, 2005
 */
package org.lobobrowser.util;

import java.util.*;

/**
 * @author J. H. S.
 */
public class History implements java.io.Serializable {
	private static final long serialVersionUID = 2257845000800300100L;

	private transient ArrayList historySequence;
	
	private final SortedSet historySortedSet = new TreeSet();
	private final Map historyMap = new HashMap();
	private final SortedSet historyTimedSet = new TreeSet();

	private int sequenceCapacity;
	private int commonEntriesCapacity;

	private transient int sequenceIndex;
	
	/**
	 * @param sequenceCapacity
	 * @param commonEntriesCapacity
	 */
	public History(int sequenceCapacity, int commonEntriesCapacity) {
		super();
		this.historySequence = new ArrayList();
		this.sequenceIndex = -1;
		this.sequenceCapacity = sequenceCapacity;
		this.commonEntriesCapacity = commonEntriesCapacity;
	}
	
	private void readObject(java.io.ObjectInputStream in) throws ClassNotFoundException, java.io.IOException {
		this.historySequence = new ArrayList();
		this.sequenceIndex = -1;
		in.defaultReadObject();
	}
	
	/**
	 * @return Returns the commonEntriesCapacity.
	 */
	public int getCommonEntriesCapacity() {
		return commonEntriesCapacity;
	}
	
	/**
	 * @param commonEntriesCapacity The commonEntriesCapacity to set.
	 */
	public void setCommonEntriesCapacity(int commonEntriesCapacity) {
		this.commonEntriesCapacity = commonEntriesCapacity;
	}
	/**
	 * @return Returns the sequenceCapacity.
	 */
	public int getSequenceCapacity() {
		return sequenceCapacity;
	}
	/**
	 * @param sequenceCapacity The sequenceCapacity to set.
	 */
	public void setSequenceCapacity(int sequenceCapacity) {
		this.sequenceCapacity = sequenceCapacity;
	}

	public String getCurrentItem() {
		if(this.sequenceIndex >= 0) {
			return (String) this.historySequence.get(this.sequenceIndex);
		}
		else {
			return null;
		}
	}

	public String back() {
		if(this.sequenceIndex > 0) {
			this.sequenceIndex--;
			return this.getCurrentItem();
		}
		else {
			return null;
		}
	}

	public String forward() {
		if(this.sequenceIndex + 1 < this.historySequence.size()) {
			this.sequenceIndex++;
			return this.getCurrentItem();
		}
		else {
			return null;
		}
	}
	
	public Collection getRecentItems(int maxNumItems) {
		Collection items = new LinkedList();
		Iterator i = this.historyTimedSet.iterator();
		int count = 0;
		while(i.hasNext() && count++ < maxNumItems) {
			TimedEntry entry = (TimedEntry) i.next();
			items.add(entry.value);
		}
		return items;
	}
	
	public Collection getHeadMatchItems(String item, int maxNumItems) {
		Object[] array = this.historySortedSet.toArray();
		int idx = Arrays.binarySearch(array, item);
		int startIdx = idx >= 0 ? idx : (-idx - 1);
		int count = 0;
		Collection items = new LinkedList();
		for(int i = startIdx; i < array.length && (count++ < maxNumItems); i++) {
			String potentialItem = (String) array[i];
			if(potentialItem.startsWith(item)) {
				items.add(potentialItem);
			}
			else {
				break;
			}
		}
		return items;
	}
	
	public void addAsRecentOnly(String item) {
		TimedEntry entry = (TimedEntry) this.historyMap.get(item);
		if(entry != null) {
			this.historyTimedSet.remove(entry);
			entry.touch();
			this.historyTimedSet.add(entry);
		}
		else {
			entry = new TimedEntry(item);
			this.historyTimedSet.add(entry);
			this.historyMap.put(item, entry);
			this.historySortedSet.add(item);
			if(this.historyTimedSet.size() > this.commonEntriesCapacity) {
				// Most outdated goes last
				TimedEntry entryToRemove = (TimedEntry) this.historyTimedSet.last();
				this.historyMap.remove(entryToRemove.value);
				this.historySortedSet.remove(entryToRemove.value);
				this.historyTimedSet.remove(entryToRemove);
			}
		}		
	}
	
	public void addItem(String item, boolean updateAsRecent) {
		int newIndex = this.sequenceIndex + 1;
		
		while(newIndex >= this.historySequence.size()) {
			this.historySequence.add(null);
		}
		this.historySequence.set(newIndex, item);
		this.sequenceIndex = newIndex;

		int expectedSize = newIndex + 1;
		while(this.historySequence.size() > expectedSize) {
			this.historySequence.remove(expectedSize);
		}

		while(this.historySequence.size() > this.sequenceCapacity) {
			this.historySequence.remove(0);
			this.sequenceIndex--;
		}
		
		if(updateAsRecent) {
			this.addAsRecentOnly(item);
		}
	}
	
	private class TimedEntry implements Comparable, java.io.Serializable {
		private static final long serialVersionUID = 2257845000000000200L;
		private long timestamp = System.currentTimeMillis();
		private final String value;
		
		/**
		 * @param value
		 */
		public TimedEntry(String value) {
			this.value = value;
		}
		
		public void touch() {
			this.timestamp = System.currentTimeMillis();
		}

		public boolean equals(Object obj) {
			TimedEntry other = (TimedEntry) obj;
			return other.value.equals(this.value);
		}
		
		/* (non-Javadoc)
		 * @see java.lang.Comparable#compareTo(java.lang.Object)
		 */
		public int compareTo(Object arg0) {
			if(this.equals(arg0)) {
				return 0;
			}
			TimedEntry other = (TimedEntry) arg0;
			long time1 = this.timestamp;
			long time2 = other.timestamp;
			if(time1 > time2) {
				// More recent goes first
				return -1;
			}
			else if(time2 > time1){
				return +1;
			}
			else {
				int diff = System.identityHashCode(this) - System.identityHashCode(other);
				if(diff == 0) {
					return +1;
				}
				return diff;
			}
		}
	}

}
