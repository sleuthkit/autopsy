/** *************************************************************************
 ** This data and information is proprietary to, and a valuable trade secret
 ** of, Basis Technology Corp. It is given in confidence by Basis Technology
 ** and may only be used as permitted under the license agreement under which
 ** it has been distributed, and in no other way.
 **
 ** Copyright (c) 2023 Basis Technology Corp. All rights reserved.
 **
 ** The technical data and information provided herein are provided with
 ** `limited rights', and the computer software provided herein is provided
 ** with `restricted rights' as those terms are defined in DAR and ASPR
 ** 7-104.9(a).
 ************************************************************************** */
package com.basistech.df.cybertriage.autopsy.ctapi.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.function.Function;

/**
 * Creates default ObjectMapper
 */
public class ObjectMapperUtil {

    private static final ObjectMapperUtil instance = new ObjectMapperUtil();

    public static ObjectMapperUtil getInstance() {
        return instance;
    }

    private ObjectMapperUtil() {

    }

    public ObjectMapper getDefaultObjectMapper() {
        ObjectMapper defaultMapper = new ObjectMapper();
        // defaultMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        defaultMapper.registerModule(new JavaTimeModule());
        return defaultMapper;
    }

    
    public static class ZonedDateTimeDeserializer extends JsonDeserializer<ZonedDateTime> {

        @Override
        public ZonedDateTime deserialize(JsonParser jp, DeserializationContext dc) throws IOException, JacksonException {
            String date = jp.getText();
            try {
                LocalDateTime ldt = LocalDateTime.parse(date, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                return ZonedDateTime.of(ldt, ZoneOffset.UTC);
            } catch (DateTimeParseException ex) {
                return null;
            }
        }
    }

//    public static class MDYDateDeserializer extends JsonDeserializer<Date> {
//
//        private static final SimpleDateFormat FORMATTER = new SimpleDateFormat("MMM dd, yyyy");
//
//        @Override
//        public Date deserialize(JsonParser jp, DeserializationContext dc) throws IOException, JacksonException {
//            JsonNode node = jp.getCodec().readTree(jp);
//            String nodeText = node.asText();
//            try {
//                return FORMATTER.parse(nodeText);
//            } catch (ParseException ex) {
//                return null;
//            }
//        }
//
//    }

    public static class EpochTimeDeserializer<T> extends JsonDeserializer<T> {

        private final Function<Long, T> timeDeserializer;

        public EpochTimeDeserializer(Function<Long, T> timeDeserializer) {
            this.timeDeserializer = timeDeserializer;
        }

        @Override
        public T deserialize(JsonParser jp, DeserializationContext dc) throws IOException, JacksonException {
            JsonNode node = jp.getCodec().readTree(jp);

            Long timeVal = null;
            if (node.isNumber()) {
                timeVal = node.asLong();
            } else {
                String nodeText = node.asText();
                try {
                    timeVal = Long.parseLong(nodeText);
                } catch (NumberFormatException ex) {
                    // do nothing if can't parse as number
                }
            }

            if (timeVal != null) {
                try {
                    return timeDeserializer.apply(timeVal);
                } catch (DateTimeException ex) {
                    // do nothing if can't parse to epoch
                }
            }

            return null;
        }
    }

    public static class InstantEpochMillisDeserializer extends EpochTimeDeserializer<Instant> {

        public InstantEpochMillisDeserializer() {
            super(InstantEpochMillisDeserializer::convert);
        }

        private static Instant convert(Long longVal) {
            try {
                return Instant.ofEpochMilli(longVal);
            } catch (DateTimeException ex) {
                // do nothing if can't parse to epoch

                return null;
            }
        }
    }

    public static class InstantEpochSecsDeserializer extends EpochTimeDeserializer<Instant> {

        public InstantEpochSecsDeserializer() {
            super(InstantEpochSecsDeserializer::convert);
        }

        private static Instant convert(Long longVal) {
            try {
                return Instant.ofEpochSecond(longVal);
            } catch (DateTimeException ex) {
                // do nothing if can't parse to epoch

                return null;
            }
        }
    }
}
