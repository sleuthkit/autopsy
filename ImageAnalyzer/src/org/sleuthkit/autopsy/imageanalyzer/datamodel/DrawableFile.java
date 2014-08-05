/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013 Basis Technology Corp.
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
package org.sleuthkit.autopsy.imageanalyzer.datamodel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.util.Pair;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imageanalyzer.EurekaModule;
import static org.sleuthkit.autopsy.imageanalyzer.datamodel.DrawableAttribute.AttributeName.WIDTH;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import static org.sleuthkit.datamodel.BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.BYTE;
import static org.sleuthkit.datamodel.BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.DOUBLE;
import static org.sleuthkit.datamodel.BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.INTEGER;
import static org.sleuthkit.datamodel.BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.LONG;
import static org.sleuthkit.datamodel.BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.STRING;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.SleuthkitItemVisitor;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * @TODO: There is something I don't understand or have done wrong about
 * implementing this class,as it is unreadable by
 * {@link ReadContentInputStream}. As a work around I kept a reference to the
 * original {@link AbstractFile} to use when reading the image. -jm
 */
public abstract class DrawableFile<T extends AbstractFile> extends AbstractFile {

    public static DrawableFile create(AbstractFile abstractFileById, boolean b) {
        if (EurekaModule.isVideoFile(abstractFileById)) {
            return new VideoFile(abstractFileById, b);
        } else {
            return new ImageFile(abstractFileById, b);
        }
    }

    public static DrawableFile create(Long id, boolean b) throws TskCoreException, IllegalStateException {

        AbstractFile abstractFileById = Case.getCurrentCase().getSleuthkitCase().getAbstractFileById(id);
        if (EurekaModule.isVideoFile(abstractFileById)) {
            return new VideoFile(abstractFileById, b);
        } else {
            return new ImageFile(abstractFileById, b);
        }
    }

    private String drawablePath;

    abstract public boolean isVideo();

    protected T file;

    private final SimpleBooleanProperty analyzed;

    private final SimpleObjectProperty<Category> category = new SimpleObjectProperty<>(null);

    private final ObservableList<String> hashHitSetNames = FXCollections.observableArrayList();

    public ObservableList<String> getHashHitSetNames() {
        return hashHitSetNames;
    }

    private String make;

    private String model;

    protected DrawableFile(T file, Boolean analyzed) {
        /* @TODO: the two 'new Integer(0).shortValue()' values and null are
         * placeholders because the super constructor expects values i can't get
         * easily at the moment */

        super(file.getSleuthkitCase(), file.getId(), file.getAttrType(), file.getAttrId(), file.getName(), file.getType(), file.getMetaAddr(), (int) file.getMetaSeq(), file.getDirType(), file.getMetaType(), null, new Integer(0).shortValue(), file.getSize(), file.getCtime(), file.getCrtime(), file.getAtime(), file.getMtime(), new Integer(0).shortValue(), file.getUid(), file.getGid(), file.getMd5Hash(), file.getKnown(), file.getParentPath());
        this.analyzed = new SimpleBooleanProperty(analyzed);
        this.file = file;
        updateHashSetHits();
    }

    @Override
    public boolean isRoot() {
        return false;
    }

    @Override
    public <T> T accept(SleuthkitItemVisitor<T> v) {

        return file.accept(v);
    }

    @Override
    public <T> T accept(ContentVisitor<T> v) {
        return file.accept(v);
    }

    @Override
    public List<Content> getChildren() throws TskCoreException {
        return new ArrayList<>();
    }

    @Override
    public List<Long> getChildrenIds() throws TskCoreException {
        return new ArrayList<>();
    }

    public ObservableList<Pair<DrawableAttribute, ? extends Object>> getAttributesList() {
        final ObservableList<Pair<DrawableAttribute, ? extends Object>> attributeList = FXCollections.observableArrayList();
        for (DrawableAttribute attr : DrawableAttribute.getValues()) {
            attributeList.add(new Pair<>(attr, getValueOfAttribute(attr)));
        }
        return attributeList;
    }

    public Object getValueOfAttribute(DrawableAttribute attr) {
        switch (attr.attrName) {
            case OBJ_ID:
                return file.getId();
            case PATH:
                return getDrawablePath();
            case NAME:
                return getName();
            case CREATED_TIME:
                return getCrtimeAsDate();
            case MODIFIED_TIME:
                return getMtimeAsDate();
            case MAKE:
                if (make == null) {
                    make = WordUtils.capitalizeFully((String) getValueOfBBAttribute(BlackboardArtifact.ARTIFACT_TYPE.TSK_METADATA_EXIF, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DEVICE_MAKE));
                }
                return make;
            case MODEL:
                if (model == null) {
                    model = WordUtils.capitalizeFully((String) getValueOfBBAttribute(BlackboardArtifact.ARTIFACT_TYPE.TSK_METADATA_EXIF, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DEVICE_MODEL));
                }
                return model;

            case CATEGORY:

                updateCategory();

                return category.get();

            case TAGS:
                try {
                    List<ContentTag> contentTagsByContent = Case.getCurrentCase().getServices().getTagsManager().getContentTagsByContent(this);
                    Set<TagName> values = new HashSet<>();

                    for (ContentTag ct : contentTagsByContent) {
                        values.add(ct.getName());
                    }
                    return new ArrayList<>(values);

                } catch (TskCoreException ex) {
                    Logger.getAnonymousLogger().log(Level.WARNING, "problem looking up " + attr.getDisplayName() + " for " + file.getName(), ex);
                    return new ArrayList<>();
                }

            case ANALYZED:
                return analyzed.get();
            case HASHSET:
                updateHashSetHits();
                return hashHitSetNames;
            case WIDTH:
                return getWidth();
            case HEIGHT:
                return getHeight();

            default:
                throw new UnsupportedOperationException("DrawableFile.getValueOfAttribute does not yet support " + attr.getDisplayName());

        }
    }

    public List<? extends Object> getValuesOfBBAttribute(BlackboardArtifact.ARTIFACT_TYPE artType, BlackboardAttribute.ATTRIBUTE_TYPE attrType) {
        ArrayList<Object> vals = new ArrayList<>();
        try {
            //why doesn't file.getArtifacts() work?
            //TODO: this seams like overkill, use a more targeted query
            ArrayList<BlackboardArtifact> artifacts = getAllArtifacts();

            for (BlackboardArtifact artf : artifacts) {
                if (artf.getArtifactTypeID() == artType.getTypeID()) {
                    for (BlackboardAttribute attr : artf.getAttributes()) {
                        if (attr.getAttributeTypeID() == attrType.getTypeID()) {

                            switch (attr.getValueType()) {
                                case BYTE:
                                    vals.add(attr.getValueBytes());
                                case DOUBLE:
                                    vals.add(attr.getValueDouble());
                                case INTEGER:
                                    vals.add(attr.getValueInt());
                                case LONG:
                                    vals.add(attr.getValueLong());
                                case STRING:
                                    vals.add(attr.getValueString());
                            }
                        }
                    }
                }
            }
        } catch (TskCoreException ex) {
            Logger.getAnonymousLogger().log(Level.WARNING, "problem looking up {0}/{1}" + " " + " for {2}", new Object[]{artType.getDisplayName(), attrType.getDisplayName(), getName()});
        }

        return vals;
    }

    private Object getValueOfBBAttribute(BlackboardArtifact.ARTIFACT_TYPE artType, BlackboardAttribute.ATTRIBUTE_TYPE attrType) {
        try {

            //why doesn't file.getArtifacts() work?
            //TODO: this seams like overkill, use a more targeted query
            ArrayList<BlackboardArtifact> artifacts = file.getArtifacts(artType);// getAllArtifacts();

            for (BlackboardArtifact artf : artifacts) {
                if (artf.getArtifactTypeID() == artType.getTypeID()) {
                    for (BlackboardAttribute attr : artf.getAttributes()) {
                        if (attr.getAttributeTypeID() == attrType.getTypeID()) {
                            switch (attr.getValueType()) {
                                case BYTE:
                                    return attr.getValueBytes();
                                case DOUBLE:
                                    return attr.getValueDouble();
                                case INTEGER:
                                    return attr.getValueInt();
                                case LONG:
                                    return attr.getValueLong();
                                case STRING:
                                    return attr.getValueString();
                            }
                        }
                    }
                }
            }
        } catch (TskCoreException ex) {
            Logger.getAnonymousLogger().log(Level.WARNING, "problem looking up {0}/{1}" + " " + " for {2}", new Object[]{artType.getDisplayName(), attrType.getDisplayName(), getName()});
        }
        return "";
    }

    public String getMake() {
        return getValueOfAttribute(DrawableAttribute.MAKE).toString();
    }

    public String getModel() {
        return getValueOfAttribute(DrawableAttribute.MODEL).toString();
    }

    public void setCategory(Category category) {
        categoryProperty().set(category);

    }

    public Category getCategory() {
        return (Category) getValueOfAttribute(DrawableAttribute.CATEGORY);
    }

    public SimpleObjectProperty<Category> categoryProperty() {
        return category;
    }

    public void updateCategory() {
        try {
            List<ContentTag> contentTagsByContent = Case.getCurrentCase().getServices().getTagsManager().getContentTagsByContent(this);
            Category cat = null;
            for (ContentTag ct : contentTagsByContent) {
                if (ct.getName().getDisplayName().startsWith(Category.CATEGORY_PREFIX)) {
                    cat = Category.fromDisplayName(ct.getName().getDisplayName());
                    break;
                }
            }
            if (cat == null) {
                category.set(Category.ZERO);
            } else {
                category.set(cat);
            }
        } catch (TskCoreException ex) {
            Logger.getLogger(DrawableFile.class.getName()).log(Level.WARNING, "problem looking up category for file " + this.getName(), ex);
        }
    }

    public abstract Node getFullsizeDisplayNode();

    public abstract Image getIcon();

    public void setAnalyzed(Boolean analyzed) {
        this.analyzed.set(analyzed);
    }

    public boolean isAnalyzed() {
        return analyzed.get();
    }

    public T getAbstractFile() {
        return this.file;
    }

    private void updateHashSetHits() {
        hashHitSetNames.setAll((Collection<? extends String>) getValuesOfBBAttribute(BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME));
    }

    abstract Number getWidth();

    abstract Number getHeight();

    private static final String[] SLASH = new String[]{"/"};

    private static final String[] DOUBLE_SLASH = new String[]{"//"};

    public String getDrawablePath() {
        if (drawablePath != null) {
            return drawablePath;
        } else {
            try {
                drawablePath = StringUtils.removeEnd(getUniquePath(), getName());
//                drawablePath = StringUtils.replaceEachRepeatedly(drawablePath, DOUBLE_SLASH, SLASH);
                return drawablePath;
            } catch (TskCoreException ex) {
                Logger.getLogger(DrawableFile.class.getName()).log(Level.WARNING, "failed to get drawablePath from " + getName());
                return "";
            }
        }
    }

    private long getRootID() throws TskCoreException {

        Content myParent = getParent();
        long id = -1;

        while (myParent != null) {
            id = myParent.getId();
            myParent = myParent.getParent();
        }

        return id;
    }
}
