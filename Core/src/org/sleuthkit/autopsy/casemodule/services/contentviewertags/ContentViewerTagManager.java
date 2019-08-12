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
package org.sleuthkit.autopsy.casemodule.services.contentviewertags;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A per case Autopsy service that manages the addition of content viewer tags
 * to the case database. This manager is also responsible for serializing and
 * deserializing instances of your tag data objects for persistence and
 * retrieval.
 */
public class ContentViewerTagManager {

    //Used to convert Java beans into the physical representation that will be stored
    //in the database.
    private static final ObjectMapper SERIALIZER = new ObjectMapper();

    public static final String TABLE_NAME = "beta_tag_app_data";
    public static final String TABLE_SCHEMA_SQLITE = "(app_data_id INTEGER PRIMARY KEY, "
            + "content_tag_id INTEGER NOT NULL, app_data TEXT NOT NULL, "
            + "FOREIGN KEY(content_tag_id) REFERENCES content_tags(tag_id))";
    public static final String TABLE_SCHEMA_POSTGRESQL = "(app_data_id BIGSERIAL PRIMARY KEY, "
            + "content_tag_id INTEGER NOT NULL, app_data TEXT NOT NULL, "
            + "FOREIGN KEY(content_tag_id) REFERENCES content_tags(tag_id))";

    private static final String INSERT_TAG_DATA = "(content_tag_id, app_data) VALUES (%d, '%s')";
    private static final String UPDATE_TAG_DATA = "SET content_tag_id = %d, app_data = '%s' WHERE app_data_id = %d";
    private static final String SELECT_TAG_DATA = "* FROM " + TABLE_NAME + " WHERE content_tag_id = %d";
    private static final String DELETE_TAG_DATA = "WHERE app_data_id = %d";

    /**
     * Creates and saves a new ContentViewerTag in the case database. The
     * generic tag data instance T will be automatically serialized into a
     * storable format.
     *
     * @param contentTag ContentTag that this ContentViewerTag is associated
     * with (1:1).
     * @param tagDataBean Data instance that contains the tag information to be
     * persisted.
     * @return An instance of a ContentViewerTag of type T, which contains all
     * the stored information.
     *
     * @throws SerializationException Thrown if the tag data instance T could
     * not be serialized into a storable format.
     * @throws TskCoreException Thrown if this operation did not successfully
     * persist in the case database.
     * @throws NoCurrentCaseException Thrown if invocation of this method occurs
     * when no case is open.
     */
    public static <T> ContentViewerTag<T> saveTag(ContentTag contentTag, T tagDataBean) 
            throws SerializationException, TskCoreException, NoCurrentCaseException {
        try {
            long contentTagId = contentTag.getId();
            String serialAppData = SERIALIZER.writeValueAsString(tagDataBean);
            String insertTemplateInstance = String.format(INSERT_TAG_DATA,
                    contentTagId, serialAppData);
            long insertId = Case.getCurrentCaseThrows()
                    .getSleuthkitCase()
                    .getCaseDbAccessManager()
                    .insert(TABLE_NAME, insertTemplateInstance);
            return new ContentViewerTag<>(insertId, contentTag, tagDataBean);
        } catch (JsonProcessingException ex) {
            throw new SerializationException("Unable to convert object instance into a storable format", ex);
        }
    }

    /**
     * Updates the ContentViewerTag instance with the new tag data T and
     * persists the changes to the case database.
     *
     * @param oldTag ContentViewerTag instance to be updated
     * @param tagDataBean Data instance that contains the updated information to
     * be persisted.
     *
     * @throws SerializationException Thrown if the tag data instance T could
     * not be serialized into a storable format.
     * @throws TskCoreException Thrown if this operation did not successfully
     * persist in the case database.
     * @throws NoCurrentCaseException Thrown if invocation of this method occurs
     * when no case is open.
     */
    public static <T> ContentViewerTag<T> updateTag(ContentViewerTag<T> oldTag, T tagDataBean)
            throws SerializationException, TskCoreException, NoCurrentCaseException {
        try {
            String serialAppData = SERIALIZER.writeValueAsString(tagDataBean);
            String updateTemplateInstance = String.format(UPDATE_TAG_DATA,
                    oldTag.getContentTag().getId(), serialAppData, oldTag.getId());
            Case.getCurrentCaseThrows()
                    .getSleuthkitCase()
                    .getCaseDbAccessManager()
                    .update(TABLE_NAME, updateTemplateInstance);
            return new ContentViewerTag<>(oldTag.getId(), oldTag.getContentTag(), tagDataBean);
        } catch (JsonProcessingException ex) {
            throw new SerializationException("Unable to convert object instance into a storable format", ex);
        }
    }

    /**
     * Retrieves a ContentViewerTag instance that is associated with the
     * specified ContentTag. The Java class T that represents the technical
     * details of the tag should be passed so that automatic binding can take
     * place.
     *
     * @param contentTag ContentTag that this ContentViewerTag is associated
     * with (1:1)
     * @param clazz Generic class that will be instantiated and filled in with
     * data.
     * @return ContentViewerTag with an instance of T as a member variable or
     * null if the content tag does not have an associated ContentViewerTag of
     * type T.
     *
     * @throws TskCoreException Thrown if this operation did not successfully
     * persist in the case database.
     * @throws NoCurrentCaseException Thrown if invocation of this method occurs
     * when no case is open.
     */
    public static <T> ContentViewerTag<T> getTag(ContentTag contentTag, Class<T> clazz) throws TskCoreException, NoCurrentCaseException {
        String selectTemplateInstance = String.format(SELECT_TAG_DATA, contentTag.getId());
        final ResultWrapper<ContentViewerTag<T>> result = new ResultWrapper<>();
        Case.getCurrentCaseThrows()
                .getSleuthkitCase()
                .getCaseDbAccessManager()
                .select(selectTemplateInstance, (ResultSet rs) -> {
                    try {
                        if (rs.next()) {
                            long tagId = rs.getLong(1);
                            String appDetails = rs.getString(3);
                            try {
                                T instance = SERIALIZER.readValue(appDetails, clazz);
                                result.setResult(new ContentViewerTag<>(tagId, contentTag, instance));
                            } catch (IOException ex) {
                                //Databind for type T failed. Not a system error
                                //but rather a logic error on the part of the caller.
                                result.setResult(null);
                            }
                        }
                    } catch (SQLException ex) {
                        result.setException(ex);
                    }
                });

        if (result.hasException()) {
            throw new TskCoreException("Unable to select tag from case db", result.getException());
        }

        return result.getResult();
    }

    /**
     * Wrapper for holding state in the CaseDbAccessQueryCallback.
     * CaseDbAccessQueryCallback has no support for exception handling.
     *
     * @param <T>
     */
    private static class ResultWrapper<T> {

        private T result;
        private SQLException ex = null;

        public void setResult(T result) {
            this.result = result;
        }

        public void setException(SQLException ex) {
            this.ex = ex;
        }

        public boolean hasException() {
            return this.ex != null;
        }

        public SQLException getException() {
            return ex;
        }

        public T getResult() {
            return result;
        }
    }

    /**
     * Deletes the content viewer tag with the specified id.
     *
     * @param contentViewerTag ContentViewerTag to delete
     * @throws TskCoreException Thrown if this operation did not successfully
     * persist in the case database.
     * @throws NoCurrentCaseException Thrown if invocation of this method occurs
     * when no case is open.
     */
    public static <T> void deleteTag(ContentViewerTag<T> contentViewerTag) throws TskCoreException, NoCurrentCaseException {
        String deleteTemplateInstance = String.format(DELETE_TAG_DATA, contentViewerTag.getId());
        Case.getCurrentCaseThrows()
                .getSleuthkitCase()
                .getCaseDbAccessManager()
                .delete(TABLE_NAME, deleteTemplateInstance);
    }

    /**
     * This class represents a stored tag in the case database. It is a wrapper
     * for the tag id, the attached Content tag object, and the Java bean
     * instance that describes the technical details for reconstructing the tag.
     *
     * @param <T> Generic class type that will be instantiated and filled in
     * with data.
     */
    public static class ContentViewerTag<T> {

        private final long id;
        private final ContentTag contentTag;
        private final T details;

        private ContentViewerTag(long id, ContentTag contentTag, T details) {
            this.id = id;
            this.contentTag = contentTag;
            this.details = details;
        }

        public long getId() {
            return id;
        }

        public ContentTag getContentTag() {
            return contentTag;
        }

        public T getDetails() {
            return details;
        }
    }

    /**
     * System exception thrown in the event that class instance T could not be
     * properly serialized.
     */
    public static class SerializationException extends Exception {

        public SerializationException(String message, Exception source) {
            super(message, source);
        }
    }

    //Prevent this class from being instantiated.
    private ContentViewerTagManager() {
    }
}
