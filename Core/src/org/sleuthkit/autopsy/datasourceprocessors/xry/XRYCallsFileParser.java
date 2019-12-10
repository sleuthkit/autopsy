/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.datasourceprocessors.xry;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Parses XRY Calls files and creates artifacts.
 */
final class XRYCallsFileParser extends AbstractSingleKeyValueParser {

    private static final Logger logger = Logger.getLogger(XRYCallsFileParser.class.getName());

    //Pattern is in reverse due to a Java 8 bug, see calculateSecondsSinceEpoch()
    //function for more details.
    private static final DateTimeFormatter DATE_TIME_PARSER
            = DateTimeFormatter.ofPattern("O a h:m:s M/d/y");
    /**
     * All of the known XRY keys for call reports.
     */
    private static enum XRY_KEY {
        TEL("tel"),
        NAME_MATCHED("name (matched)"),
        TIME("time"),
        DIRECTION("direction"),
        CALL_TYPE("call type"),
        DURATION("duration"),
        STORAGE("storage"),
        INDEX("index"),
        NAME("name"),
        NUMBER("number");
        
        private final String name;
        XRY_KEY(String name) {
            this.name = name;
        }
        
        /**
         * Indicates if the XRY key is a recognized type.
         * 
         * @param xryKey
         * @return 
         */
        public static boolean contains(String xryKey) {
            String normalizedKey = xryKey.trim().toLowerCase();
            for(XRY_KEY keyChoice : XRY_KEY.values()) {
                if(keyChoice.name.equals(normalizedKey)) {
                    return true;
                }
            }
            
            return false;
        }
        
        /**
         * Fetches the enum type for the given XRY key.
         * 
         * It is assumed that XRY key string is recognized. Otherwise,
         * an IllegalArgumentException is thrown. Test all membership
         * with contains() before hand.
         * 
         * @param xryKey
         * @return 
         */
        public static XRY_KEY fromName(String xryKey) {
            String normalizedKey = xryKey.trim().toLowerCase();
            for(XRY_KEY keyChoice : XRY_KEY.values()) {
                if(keyChoice.name.equals(normalizedKey)) {
                    return keyChoice;
                }
            }
            
            throw new IllegalArgumentException(String.format("Key [%s] was not found."
                    + " All keys should be tested with contains.", xryKey));
        }
    }

    /**
     * All known XRY namespaces for call reports.
     */
    private static enum XRY_NAMESPACE {
        TO("to"),
        FROM("from"),
        NONE(null);
        
        private final String name;
        XRY_NAMESPACE(String name) {
            this.name = name;
        }
        
        /**
         * Indicates if the XRY namespace is a recognized type.
         * 
         * @param xryNamespace
         * @return 
         */
        public static boolean contains(String xryNamespace) {
            String normalizedNamespace = xryNamespace.trim().toLowerCase();
            for(XRY_NAMESPACE keyChoice : XRY_NAMESPACE.values()) {
                if(normalizedNamespace.equals(keyChoice.name)) {
                    return true;
                }
            }
            
            return false;
        }
        
        /**
         * Fetches the enum type for the given XRY namespace.
         * 
         * It is assumed that XRY namespace string is recognized. Otherwise,
         * an IllegalArgumentException is thrown. Test all membership
         * with contains() before hand.
         * 
         * @param xryNamespace
         * @return 
         */
        public static XRY_NAMESPACE fromName(String xryNamespace) {
            String normalizedNamespace = xryNamespace.trim().toLowerCase();
            for(XRY_NAMESPACE keyChoice : XRY_NAMESPACE.values()) {
                if(normalizedNamespace.equals(keyChoice.name)) {
                    return keyChoice;
                }
            }
            
            throw new IllegalArgumentException(String.format("Key [%s] was not found."
                    + " All keys should be tested with contains.", xryNamespace));
        }
    }

    @Override
    boolean isKey(String key) {
        return XRY_KEY.contains(key);
    }

    @Override
    boolean isNamespace(String nameSpace) {
        return XRY_NAMESPACE.contains(nameSpace);
    }

    @Override
    Optional<BlackboardAttribute> makeAttribute(String nameSpace, String key, String value) {
        XRY_KEY xryKey = XRY_KEY.fromName(key);
        XRY_NAMESPACE xryNamespace = XRY_NAMESPACE.NONE;
        if(XRY_NAMESPACE.contains(nameSpace)) {
            xryNamespace = XRY_NAMESPACE.fromName(nameSpace);
        }

        switch (xryKey) {
            case DIRECTION:
                return Optional.of(new BlackboardAttribute(
                        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DIRECTION, 
                        PARSER_NAME, value));
            case NAME_MATCHED:
                return Optional.of(new BlackboardAttribute(
                        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME, 
                        PARSER_NAME, value));
            case NUMBER:
                return Optional.of(new BlackboardAttribute(
                        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER, 
                        PARSER_NAME, value));
            case TEL:
                //Apply the namespace
                switch (xryNamespace) {
                    case FROM:
                        return Optional.of(new BlackboardAttribute(
                                BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_FROM,
                                PARSER_NAME, value));
                    case TO:
                        return Optional.of(new BlackboardAttribute(
                                BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_TO,
                                PARSER_NAME, value));
                    default:
                        return Optional.of(new BlackboardAttribute(
                                BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER,
                                PARSER_NAME, value));
                }
            case TIME:
                try {
                    //Tranform value to seconds since epoch
                    long dateTimeSinceEpoch = calculateSecondsSinceEpoch(value);
                    return Optional.of(new BlackboardAttribute(
                            BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_START, 
                            PARSER_NAME, dateTimeSinceEpoch));
                } catch (DateTimeParseException ex) {
                    logger.log(Level.WARNING, String.format("[XRY DSP] Assumption"
                            + " about the date time formatting of call logs is "
                            + "not right. Here is the value [ %s ]", value), ex);
                    return Optional.empty();
                }
            case DURATION:
            case STORAGE:
            case INDEX:
            case CALL_TYPE:
                //Ignore for now, don't need more data.
                return Optional.empty();
            case NAME:
                logger.log(Level.WARNING, String.format("[XRY DSP] Key [%s] was "
                    + "recognized but more examples of its values are needed "
                    + "to make a decision on an appropriate TSK attribute. "
                        + "Here is the value [%s].", key, value));
                return Optional.empty();
            default:
                throw new IllegalArgumentException(String.format("Key [ %s ] "
                        + "passed the isKey() test but was not matched. There is"
                        + " likely a typo in the  code.", key));
        }
    }

    @Override
    void makeArtifact(List<BlackboardAttribute> attributes, Content parent) throws TskCoreException {
        BlackboardArtifact artifact = parent.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_CALLLOG);
        artifact.addAttributes(attributes);
    }

    /**
     * Removes the locale from the date time value.
     *
     * Locale in this case being (Device) or (Network).
     *
     * @param dateTime XRY datetime value to be sanitized.
     * @return A purer date time value.
     */
    private String removeDateTimeLocale(String dateTime) {
        int index = dateTime.indexOf('(');
        if (index == -1) {
            return dateTime;
        }

        return dateTime.substring(0, index);
    }

    /**
     * Parses the date time value and calculates seconds since epoch.
     *
     * @param dateTime
     * @return
     */
    private long calculateSecondsSinceEpoch(String dateTime) {
        String dateTimeWithoutLocale = removeDateTimeLocale(dateTime).trim();
        /**
         * The format of time in XRY Messages reports is of the form:
         * 
         *      1/3/1990 1:23:54 AM UTC+4
         * 
         * In our current version of Java (openjdk-1.8.0.222), there is
         * a bug with having the timezone offset (UTC+4 or GMT-7) at the 
         * end of the date time input. This is fixed in later versions
         * of the JDK (9 and beyond). Rather than update the JDK to
         * accommodate this, the components of the date time string are reversed:
         * 
         *      UTC+4 AM 1:23:54 1/3/1990
         * 
         * The java time package will correctly parse this date time format.
         */
        String reversedDateTime = reverseOrderOfDateTimeComponents(dateTimeWithoutLocale);
        /**
         * Furthermore, the DateTimeFormatter's timezone offset letter ('O') does
         * not recognized UTC but recognizes GMT. According to 
         * https://en.wikipedia.org/wiki/Coordinated_Universal_Time,
         * GMT only differs from UTC by at most 1 second and so substitution
         * will only introduce a trivial amount of error.
         */
        String reversedDateTimeWithGMT = reversedDateTime.replace("UTC", "GMT");
        ZonedDateTime zonedDateTime = ZonedDateTime.parse(reversedDateTimeWithGMT, DATE_TIME_PARSER);
        return zonedDateTime.toEpochSecond();
    }
    
    /**
     * Reverses the order of the date time components.
     * 
     * Example: 
     *  1/3/1990 1:23:54 AM UTC+4 
     * becomes
     *  UTC+4 AM 1:23:54 1/3/1990
     * 
     * @param dateTime
     * @return
     */
    private String reverseOrderOfDateTimeComponents(String dateTime) {
        StringBuilder reversedDateTime = new StringBuilder(dateTime.length());
        String[] dateTimeComponents = dateTime.split(" ");
        for (String component : dateTimeComponents) {
            reversedDateTime.insert(0, " ").insert(0, component);
        }
        return reversedDateTime.toString().trim();
    }
}
