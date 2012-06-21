/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.thunderbirdparser;

import java.io.Serializable;
import java.text.DateFormat;
import java.text.DateFormatSymbols;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import org.apache.tika.metadata.*;

/**
 *
 * @author arivera
 */
public class ThunderbirdMetadata implements CreativeCommons, DublinCore, Geographic, HttpHeaders,
        IPTC, Message, MSOffice, ClimateForcast, TIFF, TikaMetadataKeys, TikaMimeKeys,
        Serializable {
    
    private int strArrCount = 0;

    /** Serial version UID */
    private static final long serialVersionUID = 5623926545693153182L;

    /**
     * A map of all metadata attributes.
     */
    private Map<String, ArrayList<String>> metadata = null;

    /**
     * The UTC time zone. Not sure if {@link TimeZone#getTimeZone(String)}
     * understands "UTC" in all environments, but it'll fall back to GMT
     * in such cases, which is in practice equivalent to UTC.
     */
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    /**
     * Custom time zone used to interpret date values without a time
     * component in a way that most likely falls within the same day
     * regardless of in which time zone it is later interpreted. For
     * example, the "2012-02-17" date would map to "2012-02-17T12:00:00Z"
     * (instead of the default "2012-02-17T00:00:00Z"), which would still
     * map to "2012-02-17" if interpreted in say Pacific time (while the
     * default mapping would result in "2012-02-16" for UTC-8).
     */
    private static final TimeZone MIDDAY = TimeZone.getTimeZone("GMT-12:00");

    /**
     * Some parsers will have the date as a ISO-8601 string
     *  already, and will set that into the Metadata object.
     * So we can return Date objects for these, this is the
     *  list (in preference order) of the various ISO-8601
     *  variants that we try when processing a date based
     *  property.
     */
    private static final DateFormat[] iso8601InputFormats = new DateFormat[] {
        // yyyy-mm-ddThh...
        createDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", UTC),   // UTC/Zulu
        createDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", null),    // With timezone
        createDateFormat("yyyy-MM-dd'T'HH:mm:ss", null),     // Without timezone
        // yyyy-mm-dd hh...
        createDateFormat("yyyy-MM-dd' 'HH:mm:ss'Z'", UTC),   // UTC/Zulu
        createDateFormat("yyyy-MM-dd' 'HH:mm:ssZ", null),    // With timezone
        createDateFormat("yyyy-MM-dd' 'HH:mm:ss", null),     // Without timezone
        // Date without time, set to Midday UTC
        createDateFormat("yyyy-MM-dd", MIDDAY),              // Normal date format
        createDateFormat("yyyy:MM:dd", MIDDAY),              // Image (IPTC/EXIF) format
    };

    private static DateFormat createDateFormat(String format, TimeZone timezone) {
        SimpleDateFormat sdf =
            new SimpleDateFormat(format, new DateFormatSymbols(Locale.US));
        if (timezone != null) {
            sdf.setTimeZone(timezone);
        }
        return sdf;
    }

    /**
     * Parses the given date string. This method is synchronized to prevent
     * concurrent access to the thread-unsafe date formats.
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-495">TIKA-495</a>
     * @param date date string
     * @return parsed date, or <code>null</code> if the date can't be parsed
     */
    private static synchronized Date parseDate(String date) {
        // Java doesn't like timezones in the form ss+hh:mm
        // It only likes the hhmm form, without the colon
        int n = date.length();
        if (date.charAt(n - 3) == ':'
            && (date.charAt(n - 6) == '+' || date.charAt(n - 6) == '-')) {
            date = date.substring(0, n - 3) + date.substring(n - 2);
        }

        // Try several different ISO-8601 variants
        for (DateFormat format : iso8601InputFormats) {
            try {
                return format.parse(date);
            } catch (ParseException ignore) {
            }
        }
        return null;
    }

    /**
     * Returns a ISO 8601 representation of the given date. This method is
     * synchronized to prevent concurrent access to the thread-unsafe date
     * formats.
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-495">TIKA-495</a>
     * @param date given date
     * @return ISO 8601 date string
     */
    private static String formatDate(Date date) {
        Calendar calendar = GregorianCalendar.getInstance(UTC, Locale.US);
        calendar.setTime(date);
        return String.format(
                "%04d-%02d-%02dT%02d:%02d:%02dZ",
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH),
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                calendar.get(Calendar.SECOND));
    }

    /**
     * Constructs a new, empty metadata.
     */
    public ThunderbirdMetadata() {
        metadata = new HashMap<String, ArrayList<String>>();
    }

    /**
     * Returns true if named value is multivalued.
     * 
     * @param name
     *          name of metadata
     * @return true is named value is multivalued, false if single value or null
     */
    public boolean isMultiValued(final String name) {
        return metadata.get(name) != null && metadata.get(name).size() > 1;
    }

    /**
     * Returns an array of the names contained in the metadata.
     * 
     * @return Metadata names
     */
    public ArrayList<String> names() {
        return new ArrayList<String>(metadata.keySet());//.toArray(new String[metadata.keySet().size()]);
    }

    /**
     * Get the value associated to a metadata name. If many values are assiociated
     * to the specified name, then the first one is returned.
     * 
     * @param name
     *          of the metadata.
     * @return the value associated to the specified metadata name.
     */
    public String get(final String name) {
        ArrayList<String> values = metadata.get(name);
        if (values == null) {
            return null;
        } else {
            return values.get(0);
        }
    }

    /**
     * Returns the value (if any) of the identified metadata property.
     *
     * @since Apache Tika 0.7
     * @param property property definition
     * @return property value, or <code>null</code> if the property is not set
     */
    public String get(Property property) {
        return get(property.getName());
    }
    
    /**
     * Returns the value of the identified Integer based metadata property.
     * 
     * @since Apache Tika 0.8
     * @param property simple integer property definition
     * @return property value as a Integer, or <code>null</code> if the property is not set, or not a valid Integer
     */
    public Integer getInt(Property property) {
        if(property.getPropertyType() != Property.PropertyType.SIMPLE) {
            return null;
        }
        if(property.getValueType() != Property.ValueType.INTEGER) {
            return null;
        }
        
        String v = get(property);
        if(v == null) {
            return null;
        }
        try {
            return Integer.valueOf(v);
        } catch(NumberFormatException e) {
            return null;
        }
    }

    /**
     * Returns the value of the identified Date based metadata property.
     * 
     * @since Apache Tika 0.8
     * @param property simple date property definition
     * @return property value as a Date, or <code>null</code> if the property is not set, or not a valid Date
     */
    public Date getDate(Property property) {
        if(property.getPropertyType() != Property.PropertyType.SIMPLE) {
            return null;
        }
        if(property.getValueType() != Property.ValueType.DATE) {
            return null;
        }
        
        String v = get(property);
        if (v != null) {
            return parseDate(v);
        } else {
            return null;
        }
    }

    /**
     * Get the values associated to a metadata name.
     * 
     * @param name
     *          of the metadata.
     * @return the values associated to a metadata name.
     */
    public ArrayList<String> getValues(final String name) {
        return _getValues(name);
    }

    private ArrayList<String> _getValues(final String name) {
        ArrayList<String> values = metadata.get(name);
        if (values == null) {
            values = new ArrayList<String>();
        }
        return values;
    }

    /**
     * Add a metadata name/value mapping. Add the specified value to the list of
     * values associated to the specified metadata name.
     * 
     * @param name
     *          the metadata name.
     * @param value
     *          the metadata value.
     */
    public void add(final String name, final String value) {
        ArrayList<String> values = metadata.get(name);
        if (values == null) {
            set(name, value);
        } else {
            //ArrayList<String> newValues = new ArrayList<String>();//new String[values.size() + 1];
            //System.arraycopy(values, 0, newValues, 0, values.size());
            //newValues[newValues.length - 1] = value;
            values.add(value);
            metadata.put(name, values);
        }
    }

    /**
     * Copy All key-value pairs from properties.
     * 
     * @param properties
     *          properties to copy from
     */
    @SuppressWarnings("unchecked")
    public void setAll(Properties properties) {
        ArrayList<String> propArr = new ArrayList<String>();
        Enumeration<String> names =
            (Enumeration<String>) properties.propertyNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            propArr.add(properties.getProperty(name) );
            metadata.put(name, propArr);
        }
    }

    /**
     * Set metadata name/value. Associate the specified value to the specified
     * metadata name. If some previous values were associated to this name, they
     * are removed.
     * 
     * @param name
     *          the metadata name.
     * @param value
     *          the metadata value.
     */
    public void set(String name, String value) {
        ArrayList<String> strArr = this.metadata.get(name);
        
        if(strArr != null)
        {
            metadata.put(name, strArr);
        }
        else
        {
            strArr = new ArrayList<String>();
            strArr.add(value);
            metadata.put(name,strArr);
        }
        ++strArrCount;
    }

    /**
     * Sets the value of the identified metadata property.
     *
     * @since Apache Tika 0.7
     * @param property property definition
     * @param value    property value
     */
    public void set(Property property, String value) {
        set(property.getName(), value);
    }

    /**
     * Sets the integer value of the identified metadata property.
     *
     * @since Apache Tika 0.8
     * @param property simple integer property definition
     * @param value    property value
     */
    public void set(Property property, int value) {
        if(property.getPropertyType() != Property.PropertyType.SIMPLE) {
            throw new PropertyTypeException(Property.PropertyType.SIMPLE, property.getPropertyType());
        }
        if(property.getValueType() != Property.ValueType.INTEGER) {
            throw new PropertyTypeException(Property.ValueType.INTEGER, property.getValueType());
        }
        set(property.getName(), Integer.toString(value));
    }

    /**
     * Sets the real or rational value of the identified metadata property.
     *
     * @since Apache Tika 0.8
     * @param property simple real or simple rational property definition
     * @param value    property value
     */
    public void set(Property property, double value) {
        if(property.getPropertyType() != Property.PropertyType.SIMPLE) {
            throw new PropertyTypeException(Property.PropertyType.SIMPLE, property.getPropertyType());
        }
        if(property.getValueType() != Property.ValueType.REAL &&
              property.getValueType() != Property.ValueType.RATIONAL) {
            throw new PropertyTypeException(Property.ValueType.REAL, property.getValueType());
        }
        set(property.getName(), Double.toString(value));
    }

    /**
     * Sets the date value of the identified metadata property.
     *
     * @since Apache Tika 0.8
     * @param property simple integer property definition
     * @param date     property value
     */
    public void set(Property property, Date date) {
        if(property.getPropertyType() != Property.PropertyType.SIMPLE) {
            throw new PropertyTypeException(Property.PropertyType.SIMPLE, property.getPropertyType());
        }
        if(property.getValueType() != Property.ValueType.DATE) {
            throw new PropertyTypeException(Property.ValueType.DATE, property.getValueType());
        }
        set(property.getName(), formatDate(date));
    }

    /**
     * Remove a metadata and all its associated values.
     * 
     * @param name
     *          metadata name to remove
     */
    public void remove(String name) {
        metadata.remove(name);
    }

    /**
     * Returns the number of metadata names in this metadata.
     * 
     * @return number of metadata names
     */
    public int size() {
        return metadata.size();
    }

    public boolean equals(Object o) {

        if (o == null) {
            return false;
        }

        ThunderbirdMetadata other = null;
        try {
            other = (ThunderbirdMetadata) o;
        } catch (ClassCastException cce) {
            return false;
        }

        if (other.size() != size()) {
            return false;
        }

        ArrayList<String> names = names();
        for (String str : names)
        {//int i = 0; i < names.length; i++) {
            ArrayList<String> otherValues = other._getValues(str);
            ArrayList<String> thisValues = _getValues(str);
            if (otherValues.size() != thisValues.size()) {
                return false;
            }
            for (int j = 0; j < otherValues.size(); j++) {
                if (!otherValues.get(j).equals(thisValues.get(j))) {
                    return false;
                }
            }
        }
        return true;
    }

    public String toString() {
        StringBuffer buf = new StringBuffer();
        ArrayList<String> names = names();
        for (int i = 0; i < names.size(); i++) {
            ArrayList<String> values = _getValues(names.get(i));
            for (int j = 0; j < values.size(); j++) {
                buf.append(names.get(i)).append("=").append(values.get(j)).append(" ");
            }
        }
        return buf.toString();
    }
}
