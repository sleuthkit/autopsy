/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.mainui.nodes;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.lang.ref.WeakReference;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.stream.Collectors;
import javax.swing.Action;
import javax.swing.SwingUtilities;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.WeakListeners;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.events.BlackBoardArtifactTagAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.BlackBoardArtifactTagDeletedEvent;
import org.sleuthkit.autopsy.casemodule.events.CommentChangedEvent;
import org.sleuthkit.autopsy.casemodule.events.ContentTagAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.ContentTagDeletedEvent;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.datamodel.NodeProperty;
import org.sleuthkit.autopsy.datamodel.utils.FileNameTransTask;
import org.sleuthkit.autopsy.mainui.datamodel.BaseRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.SearchResultsDTO;
import org.sleuthkit.autopsy.mainui.nodes.actions.ActionContext;
import org.sleuthkit.autopsy.mainui.nodes.actions.ActionsFactory;
import org.sleuthkit.autopsy.mainui.sco.SCOFetcher;
import org.sleuthkit.autopsy.mainui.sco.SCOSupporter;
import org.sleuthkit.autopsy.mainui.sco.SCOUtils;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.autopsy.directorytree.DirectoryTreeTopComponent;
import org.sleuthkit.autopsy.texttranslation.TextTranslationService;
import org.sleuthkit.datamodel.AnalysisResult;
import org.sleuthkit.datamodel.DataArtifact;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A a simple starting point for nodes.
 */
abstract class BaseNode<S extends SearchResultsDTO, R extends BaseRowDTO> extends AbstractNode implements ActionContext {

    private final S results;
    private final R rowData;
    private String translatedSourceName;

    private static final Set<Case.Events> CASE_EVENTS_OF_INTEREST = EnumSet.of(
            Case.Events.BLACKBOARD_ARTIFACT_TAG_ADDED,
            Case.Events.BLACKBOARD_ARTIFACT_TAG_DELETED,
            Case.Events.CONTENT_TAG_ADDED,
            Case.Events.CONTENT_TAG_DELETED,
            Case.Events.CR_COMMENT_CHANGED,
            Case.Events.CURRENT_CASE);

    private final PropertyChangeListener listener = new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            String eventType = evt.getPropertyName();
            if (eventType.equals(Case.Events.BLACKBOARD_ARTIFACT_TAG_ADDED.toString())) {
                if(BaseNode.this instanceof SCOSupporter) {
                    BlackBoardArtifactTagAddedEvent event = (BlackBoardArtifactTagAddedEvent) evt;
                    Optional<BlackboardArtifact> optional = getArtifact();
                    if (optional.isPresent()
                            && event.getAddedTag().getArtifact().equals(optional.get())) {
                        updateSCOColumns();
                    }
                }
            } else if (eventType.equals(Case.Events.BLACKBOARD_ARTIFACT_TAG_DELETED.toString())) {
                if(BaseNode.this instanceof SCOSupporter) {
                    BlackBoardArtifactTagDeletedEvent event = (BlackBoardArtifactTagDeletedEvent) evt;
                    Optional<BlackboardArtifact> optional = getArtifact();
                    if (optional.isPresent() && event.getDeletedTagInfo().getArtifactID() == optional.get().getArtifactID()) {
                        updateSCOColumns();
                    }
                }
            } else if (eventType.equals(Case.Events.CONTENT_TAG_ADDED.toString())) {
                if(BaseNode.this instanceof SCOSupporter) {
                    ContentTagAddedEvent event = (ContentTagAddedEvent) evt;
                    Optional<Content> optional = ((SCOSupporter)BaseNode.this).getContent();
                    if (optional.isPresent() && event.getAddedTag().getContent().equals(optional.get())) {
                        updateSCOColumns();
                    }
                }

            } else if (eventType.equals(Case.Events.CONTENT_TAG_DELETED.toString())) {
                if(BaseNode.this instanceof SCOSupporter) {
                    ContentTagDeletedEvent event = (ContentTagDeletedEvent) evt;
                    Optional<Content> optional = ((SCOSupporter)BaseNode.this).getContent();
                    if (optional.isPresent() && event.getDeletedTagInfo().getContentID() == optional.get().getId()) {
                        updateSCOColumns();
                    }
                }
            } else if (eventType.equals(Case.Events.CR_COMMENT_CHANGED.toString())) {
                if(BaseNode.this instanceof SCOSupporter) {
                    CommentChangedEvent event = (CommentChangedEvent) evt;
                    
                    if (shouldUpdateSCOColumns(event.getContentID())) {
                       updateSCOColumns();
                    }
                }
            } else if (eventType.equals(Case.Events.CURRENT_CASE.toString())) {
                if (evt.getNewValue() == null) {
                    /*
                     * The case has been closed.
                     */
                    unregisterListeners();
                }
            }
        }
    };

    private PropertyChangeListener weakListener = null;

    /**
     * A pool of background tasks to run any long computation needed to populate
     * this node.
     */
    private final ExecutorService backgroundTasksPool;

    private FutureTask<String> scoFutureTask;

    BaseNode(Children children, Lookup lookup, S results, R rowData, ExecutorService backgroundTasksPool) {
        super(children, lookup);
        this.results = results;
        this.rowData = rowData;
        this.backgroundTasksPool = backgroundTasksPool;

        // If the S column is there register the listeners.
        if (results.getColumns().stream().map(p -> p.getDisplayName()).collect(Collectors.toList()).contains(SCOUtils.SCORE_COLUMN_NAME)) {
            weakListener = WeakListeners.propertyChange(listener, null);
            Case.addEventTypeSubscriber(CASE_EVENTS_OF_INTEREST, weakListener);
        }
    }
    
    private void unregisterListeners() {
        Case.removeEventTypeSubscriber(CASE_EVENTS_OF_INTEREST, weakListener);
    }

    /**
     * Returns the SearchResultDTO object.
     *
     * @return
     */
    S getSearchResultsDTO() {
        return results;
    }

    /**
     * Returns the RowDTO for this node.
     *
     * @return A RowDTO object.
     */
    R getRowDTO() {
        return rowData;
    }

    @Override
    protected Sheet createSheet() {
        Sheet sheet = ContentNodeUtil.setSheet(super.createSheet(), results.getColumns(), rowData.getCellValues());
        updateSCOColumns();
        startTranslationTask();
        return sheet;
    }

    @Override
    public Action[] getActions(boolean context) {
        return ActionsFactory.getActions(this);
    }
    
    protected boolean shouldUpdateSCOColumns(long eventObjId) {
        if (BaseNode.this instanceof SCOSupporter) {
            Optional<Content> optional = ((SCOSupporter) BaseNode.this).getContent();
            return optional.isPresent() && eventObjId == optional.get().getId();
            
        }
        return false;
    }
    
    private void updateSCOColumns() {
        if (scoFutureTask != null && !scoFutureTask.isDone()) {
            scoFutureTask.cancel(true);
            scoFutureTask = null;
        }

        if (backgroundTasksPool != null && (scoFutureTask == null || scoFutureTask.isDone()) && this instanceof SCOSupporter) {
            scoFutureTask = new FutureTask<>(new SCOFetcher<>(new WeakReference<>((SCOSupporter) this)), "");
            backgroundTasksPool.submit(scoFutureTask);
        }
    }
    
    private void startTranslationTask() {
        if (TextTranslationService.getInstance().hasProvider() && UserPreferences.displayTranslatedFileNames()) {
            /*
             * If machine translation is configured, add the original name of
             * the of the source content of the artifact represented by this
             * node to the sheet.
             */

            if (translatedSourceName == null) {
                PropertyChangeListener listener = new PropertyChangeListener() {
                    @Override
                    public void propertyChange(PropertyChangeEvent evt) {
                         String eventType = evt.getPropertyName();
                        if (eventType.equals(FileNameTransTask.getPropertyName())) {
                            displayTranslation(evt.getOldValue().toString(), evt.getNewValue().toString());
                        }
                    }
                };
                /*
                 * NOTE: The task makes its own weak reference to the listener.
                 */
                // use first cell value for display name
                String displayName = rowData.getCellValues().size() > 0
                        ? rowData.getCellValues().get(0).toString()
                        : "";
                new FileNameTransTask(displayName, this, listener).submit();
            }
        }
    }
    
    // These strings need to be consistent with what is in FileSystemColumnUtils
    @NbBundle.Messages({
        "BaseNode_columnKeys_originalName_name=Original Name",
        "BaseNode_columnKeys_originalName_displayName=Original Name",
        "BaseNode_columnKeys_originalName_description=Original Name",
    })
    private void displayTranslation(String originalName, String translatedSourceName) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                BaseNode.this.translatedSourceName = translatedSourceName;
                setDisplayName(translatedSourceName);
                setShortDescription(originalName);
                updateSheet(Collections.singletonList(new NodeProperty<>(
                    Bundle.BaseNode_columnKeys_originalName_name(),
                    Bundle.BaseNode_columnKeys_originalName_displayName(),
                    Bundle.BaseNode_columnKeys_originalName_description(),
                    originalName)));
            }
        });
        
    }    

    /**
     * Updates the values of the properties in the current property sheet with
     * the new properties being passed in. Only if that property exists in the
     * current sheet will it be applied. That way, we allow for subclasses to
     * add their own (or omit some!) properties and we will not accidentally
     * disrupt their UI.
     *
     * Race condition if not synchronized. Only one update should be applied at
     * a time.
     *
     * @param newProps New file property instances to be updated in the current
     *                 sheet.
     */
    protected synchronized void updateSheet(List<NodeProperty<?>> newProps) {
        SwingUtilities.invokeLater(() -> {
            /*
             * Refresh ONLY those properties in the sheet currently. Subclasses
             * may have only added a subset of our properties or their own
             * properties.
             */
            Sheet visibleSheet = this.getSheet();
            Sheet.Set visibleSheetSet = visibleSheet.get(Sheet.PROPERTIES);
            Property<?>[] visibleProps = visibleSheetSet.getProperties();
            for (NodeProperty<?> newProp : newProps) {
                for (int i = 0; i < visibleProps.length; i++) {
                    if (visibleProps[i].getName().equals(newProp.getName())) {
                        visibleProps[i] = newProp;
                    }
                }
            }
            visibleSheetSet.put(visibleProps);
            visibleSheet.put(visibleSheetSet);
            //setSheet() will notify Netbeans to update this node in the UI.
            this.setSheet(visibleSheet);
        });
    }

    @Override
    public Action getPreferredAction() {
        return DirectoryTreeTopComponent.getOpenChildAction(getName());
    }
    
    protected ExecutorService getTaskPool() {
        return backgroundTasksPool;
    }
}
