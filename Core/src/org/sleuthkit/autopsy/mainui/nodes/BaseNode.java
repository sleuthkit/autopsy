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
import org.openide.util.Lookup;
import org.openide.util.WeakListeners;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.events.BlackBoardArtifactTagAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.BlackBoardArtifactTagDeletedEvent;
import org.sleuthkit.autopsy.casemodule.events.CommentChangedEvent;
import org.sleuthkit.autopsy.casemodule.events.ContentTagAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.ContentTagDeletedEvent;
import org.sleuthkit.autopsy.datamodel.NodeProperty;
import org.sleuthkit.autopsy.mainui.datamodel.BaseRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.SearchResultsDTO;
import org.sleuthkit.autopsy.mainui.nodes.actions.ActionContext;
import org.sleuthkit.autopsy.mainui.nodes.actions.ActionsFactory;
import org.sleuthkit.autopsy.mainui.sco.SCOFetcher;
import org.sleuthkit.autopsy.mainui.sco.SCOSupporter;
import org.sleuthkit.autopsy.mainui.sco.SCOUtils;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;

/**
 * A a simple starting point for nodes.
 */
abstract class BaseNode<S extends SearchResultsDTO, R extends BaseRowDTO> extends AbstractNode implements ActionContext {

    private final S results;
    private final R rowData;

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
                BlackBoardArtifactTagAddedEvent event = (BlackBoardArtifactTagAddedEvent) evt;
                Optional<BlackboardArtifact> optional = getArtifact();
                if (optional.isPresent()
                        && event.getAddedTag().getArtifact().equals(optional.get())) {
                    updateSCOColumns();
                }
            } else if (eventType.equals(Case.Events.BLACKBOARD_ARTIFACT_TAG_DELETED.toString())) {
                BlackBoardArtifactTagDeletedEvent event = (BlackBoardArtifactTagDeletedEvent) evt;
                Optional<BlackboardArtifact> optional = getArtifact();
                if (optional.isPresent() && event.getDeletedTagInfo().getArtifactID() == optional.get().getArtifactID()) {
                    updateSCOColumns();
                }
            } else if (eventType.equals(Case.Events.CONTENT_TAG_ADDED.toString())) {
                ContentTagAddedEvent event = (ContentTagAddedEvent) evt;
                Optional<Content> optional = getSourceContent();
                if (optional.isPresent() && event.getAddedTag().getContent().equals(optional.get())) {
                    updateSCOColumns();
                }

            } else if (eventType.equals(Case.Events.CONTENT_TAG_DELETED.toString())) {
                ContentTagDeletedEvent event = (ContentTagDeletedEvent) evt;
                Optional<Content> optional = getSourceContent();
                if (optional.isPresent() && event.getDeletedTagInfo().getContentID() == optional.get().getId()) {
                    updateSCOColumns();
                }
            } else if (eventType.equals(Case.Events.CR_COMMENT_CHANGED.toString())) {
                CommentChangedEvent event = (CommentChangedEvent) evt;
                Optional<Content> optional = getSourceContent();
                if (optional.isPresent() && event.getContentID() == optional.get().getId()) {
                    updateSCOColumns();
                }
            } else if (eventType.equals(Case.Events.CURRENT_CASE.toString())) {
                if (evt.getNewValue() == null) {
                    /*
                     * The case has been closed.
                     */
//                    unregisterListener();
                }
            }
        }
    };

    private PropertyChangeListener weakListener = null;

    /**
     * A pool of background tasks to run any long computation needed to populate
     * this node.
     */
    static final ExecutorService backgroundTasksPool;
    private static final Integer MAX_POOL_SIZE = 10;

    private FutureTask<String> scoFutureTask;

    static {
        //Initialize this pool only once! This will be used by every instance BaseNode
        //to do their heavy duty SCO column and translation updates.
        backgroundTasksPool = Executors.newFixedThreadPool(MAX_POOL_SIZE,
                new ThreadFactoryBuilder().setNameFormat("BaseNode-background-task-%d").build());
    }

    BaseNode(Children children, Lookup lookup, S results, R rowData) {
        super(children, lookup);
        this.results = results;
        this.rowData = rowData;

        // If the S column is there register the listeners.
        if (results.getColumns().stream().map(p -> p.getDisplayName()).collect(Collectors.toList()).contains(SCOUtils.SCORE_COLUMN_NAME)) {
            weakListener = WeakListeners.propertyChange(listener, null);
            Case.addEventTypeSubscriber(CASE_EVENTS_OF_INTEREST, weakListener);
        }
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
        return sheet;
    }

    @Override
    public Action[] getActions(boolean context) {
        return ActionsFactory.getActions(this);
    }

    private void updateSCOColumns() {
        if (scoFutureTask != null && !scoFutureTask.isDone()) {
            scoFutureTask.cancel(true);
            scoFutureTask = null;
        }

        if ((scoFutureTask == null || scoFutureTask.isDone()) && this instanceof SCOSupporter) {
            scoFutureTask = new FutureTask<>(new SCOFetcher<>(new WeakReference<>((SCOSupporter) this)), "");
            backgroundTasksPool.submit(scoFutureTask);
        }
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
}
