package org.lobobrowser.util;

import java.util.*;

public class Items {
	private Items() {
	}
	
	private static Map sourceMap = new WeakHashMap();
	
	public static Object getItem(Object source, String name) {
		Map sm = sourceMap;
		synchronized(sm) {
			Map itemMap = (Map) sm.get(source);
			if(itemMap == null) {
				return null;
			}
			return itemMap.get(name);
		}
	}
	
	public static void setItem(Object source, String name, Object value) {
		Map sm = sourceMap;
		synchronized(sm) {
			Map itemMap = (Map) sm.get(source);
			if(itemMap == null) {
				itemMap = new HashMap(1);
				sm.put(source, itemMap);
			}
			itemMap.put(name, value);
		}		
	}
} 
