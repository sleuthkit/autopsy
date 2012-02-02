package org.sleuthkit.autopsy.datamodel;

import java.util.Map;

public class KeyValue implements StringContent{
    Map<String, Object> map;
    int id;
    String name;

    /**
     * 
     * @param map must iterate it keys and values in a consistent order
     * (use of LinkedHashMap is recommended)
     * @param id an arbitrary id representing the type of the thing
     */
    public KeyValue(String name, Map<String, Object> map, int id) {
        this.name = name;
        this.map = map;
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public Map<String, Object> getMap() {
        return map;
    }

    public String getName() {
        return name;
    }

    @Override
    public String getString() {
        StringBuilder buffer = new StringBuilder();
        buffer.append(name);
        buffer.append("\n");
        for(Map.Entry<String, Object> entry : map.entrySet()){
            buffer.append(entry.getKey()); 
            buffer.append(": ");
            buffer.append(entry.getValue().toString());
            buffer.append("\n");
        }
        return buffer.toString();
    }
}