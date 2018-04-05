/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.experimental.autoingest;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.datamodel.NodeProperty;
import org.sleuthkit.autopsy.guiutils.StatusIconCellRenderer;

final class AutoIngestNode extends AbstractNode {

    @Messages({
        "AutoIngestNode.col1.text=Case Name",
        "AutoIngestNode.col2.text=File Name",
        "AutoIngestNode.col3.text=Date Created",
        "AutoIngestNode.col4.text=Priority"
    })

    AutoIngestNode(List<AutoIngestJob> jobs, AutoIngestJobType type) {
        super(Children.create(new AutoIngestNodeChildren(jobs, type), true));
    }

    static class AutoIngestNodeChildren extends ChildFactory<AutoIngestJob> {

        AutoIngestJobType autoIngestJobType;
        private final List<AutoIngestJob> jobs;

        AutoIngestNodeChildren(List<AutoIngestJob> jobList, AutoIngestJobType type) {
            this.jobs = jobList;
            autoIngestJobType = type;
        }

        @Override
        protected boolean createKeys(List<AutoIngestJob> list) {
            if (jobs != null && jobs.size() > 0) {
                list.addAll(jobs);
            }
            return true;
        }

        @Override
        protected Node createNodeForKey(AutoIngestJob key) {
            return new PendingJobNode(key, autoIngestJobType);
        }

    }

    /**
     * A node which represents a single multi user case.
     */
    static final class PendingJobNode extends AbstractNode {

        private final AutoIngestJob autoIngestJob;
        private final String caseName;
        private final Path fileName;
        private final Date dateCreated;
        private final int priority;
        private final AutoIngestJobType jobType;

        PendingJobNode(AutoIngestJob job, AutoIngestJobType type) {
            super(Children.LEAF);
            jobType = type;
            autoIngestJob = job;
            caseName = autoIngestJob.getManifest().getCaseName();
            fileName = autoIngestJob.getManifest().getDataSourcePath().getFileName();
            dateCreated = autoIngestJob.getManifest().getDateFileCreated();
            priority = autoIngestJob.getPriority();
            super.setName(caseName);
            setName(caseName);
            setDisplayName(caseName);
        }

        @Override
        protected Sheet createSheet() {
            Sheet s = super.createSheet();
            Sheet.Set ss = s.get(Sheet.PROPERTIES);
            if (ss == null) {
                ss = Sheet.createPropertiesSet();
                s.put(ss);
            }
            ss.put(new NodeProperty<>(Bundle.AutoIngestNode_col1_text(), Bundle.AutoIngestNode_col1_text(), Bundle.AutoIngestNode_col1_text(), caseName));
            switch (jobType) {
                case PENDING_JOB:
                    ss.put(new NodeProperty<>(Bundle.AutoIngestNode_col2_text(), Bundle.AutoIngestNode_col2_text(), Bundle.AutoIngestNode_col2_text(), fileName.toString()));
                    ss.put(new NodeProperty<>(Bundle.AutoIngestNode_col3_text(), Bundle.AutoIngestNode_col3_text(), Bundle.AutoIngestNode_col3_text(), dateCreated.toString()));
                    ss.put(new NodeProperty<>(Bundle.AutoIngestNode_col4_text(), Bundle.AutoIngestNode_col4_text(), Bundle.AutoIngestNode_col4_text(), Integer.toString(priority)));
                    break;
                case RUNNING_JOB:
                    AutoIngestJob.StageDetails status = autoIngestJob.getProcessingStageDetails();
                    ss.put(new NodeProperty<>("Stage", "Stage", "Stage", status.getDescription()));
                    break;
                case COMPLETED_JOB:
                    ss.put(new NodeProperty<>(Bundle.AutoIngestNode_col3_text(), Bundle.AutoIngestNode_col3_text(), Bundle.AutoIngestNode_col3_text(), dateCreated.toString()));
                    ss.put(new NodeProperty<>("Date Started", "Date Started", "Date Started", autoIngestJob.getProcessingStageStartDate()));
                    ss.put(new NodeProperty<>("Date Completed - Prop6", "Date Completed", "Date Completed - Prop6", autoIngestJob.getCompletedDate()));
                    ss.put(new NodeProperty<>("Status", "Status", "Status", autoIngestJob.getErrorsOccurred() ? StatusIconCellRenderer.Status.WARNING : StatusIconCellRenderer.Status.OK));
                    break;
                default:
            }
//            AutoIngestJob.StageDetails status = autoIngestJob.getProcessingStageDetails();
            //                   ss.put(new NodeProperty<>(Bundle.AutoIngestNode_col1_text(), Bundle.AutoIngestNode_col1_text(), Bundle.AutoIngestNode_col1_text(), caseName));
//                    ss.put(new NodeProperty<>(Bundle.AutoIngestNode_col2_text(), Bundle.AutoIngestNode_col2_text(), Bundle.AutoIngestNode_col2_text(), fileName.toString()));
//            ss.put(new NodeProperty<>("Host Name - Prop3", "Host Name - Prop3", "Host Name - Prop3", autoIngestJob.getProcessingHostName()));
//                    ss.put(new NodeProperty<>(Bundle.AutoIngestNode_col3_text(), Bundle.AutoIngestNode_col3_text(), Bundle.AutoIngestNode_col3_text(), dateCreated.toString()));
//            ss.put(new NodeProperty<>("Date Started - Prop5", "Date Started - Prop5", "Date Started - Prop5", autoIngestJob.getProcessingStageStartDate()));
//            ss.put(new NodeProperty<>("Date Completed - Prop6", "Date Completed - Prop6", "Date Completed - Prop6", autoIngestJob.getCompletedDate()));
//            ss.put(new NodeProperty<>("Stage - Prop7", "Stage - Prop7", "Stage - Prop7", status.getDescription()));
//            ss.put(new NodeProperty<>("Status - Prop8", "Status - Prop8", "Status - Prop8", autoIngestJob.getErrorsOccurred() ? StatusIconCellRenderer.Status.WARNING : StatusIconCellRenderer.Status.OK));
//            ss.put(new NodeProperty<>("Stage Time - Prop9", "Stage Time - Prop9", "Stage Time - Prop9", (Date.from(Instant.now()).getTime()) - (status.getStartDate().getTime())));
//            ss.put(new NodeProperty<>("Case Directory - Prop10", "Case Directory - Prop10", "Case Directory - Prop10", autoIngestJob.getCaseDirectoryPath()));
//            ss.put(new NodeProperty<>("Manifest Path - Prop11", "Manifest Path - Prop11", "Manifest Path - Prop11", autoIngestJob.getManifest().getFilePath()));
//                    ss.put(new NodeProperty<>(Bundle.AutoIngestNode_col4_text(), Bundle.AutoIngestNode_col4_text(), Bundle.AutoIngestNode_col4_text(), Integer.toString(priority)));
            return s;
        }

    }

    enum AutoIngestJobType {
        PENDING_JOB, //NON-NLS
        RUNNING_JOB, //NON-NLS
        COMPLETED_JOB //NON-NLS
    }
}
