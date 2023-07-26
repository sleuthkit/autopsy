/*
 * Autopsy Forensic Browser
 *
 * Copyright 2023 Basis Technology Corp.
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
package com.basistech.df.cybertriage.autopsy.ctapi.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.Locale;
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
        defaultMapper.registerModule(new JavaTimeModule());
        return defaultMapper;
    }

    public static class UTCBaseZonedDateTimeDeserializer extends JsonDeserializer<ZonedDateTime> {

        private final DateTimeFormatter formatter;

        public UTCBaseZonedDateTimeDeserializer(DateTimeFormatter formatter) {
            this.formatter = formatter;
        }

        @Override
        public ZonedDateTime deserialize(JsonParser jp, DeserializationContext dc) throws IOException, JacksonException {
            String date = jp.getText();
            if (date == null) {
                return null;
            }

            try {
                LocalDateTime ldt = LocalDateTime.parse(date, formatter);
                return ZonedDateTime.of(ldt, ZoneOffset.UTC);
            } catch (DateTimeParseException ex) {
                return null;
            }
        }
    }

    public static class ZonedDateTimeDeserializer extends UTCBaseZonedDateTimeDeserializer {

        public ZonedDateTimeDeserializer() {
            super(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
    }

    public static class MDYDateDeserializer extends JsonDeserializer<ZonedDateTime> {

        private final DateTimeFormatter formatter;

        public MDYDateDeserializer() {
            this.formatter = new DateTimeFormatterBuilder()
                    .parseCaseInsensitive()
                    .appendPattern("MMM d, [uuuu][uu]")
                    .toFormatter(Locale.ENGLISH);
        }

        @Override
        public ZonedDateTime deserialize(JsonParser jp, DeserializationContext dc) throws IOException, JacksonException {
            String date = jp.getText();
            if (date == null) {
                return null;
            }

            try {
                LocalDate ld = LocalDate.parse(date, formatter);
                LocalDateTime ldt = ld.atStartOfDay();
                return ZonedDateTime.of(ldt, ZoneOffset.UTC);
            } catch (DateTimeParseException ex) {
                return null;
            }
        }
    }

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
