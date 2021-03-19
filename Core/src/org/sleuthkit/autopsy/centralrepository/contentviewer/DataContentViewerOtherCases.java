/*
 * Central Repository
 *
 * Copyright 2017-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.centralrepository.contentviewer;

import java.awt.Component;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.JPanel;
import org.openide.nodes.Node;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeNormalizationException;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataContentViewer;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeUtil;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationCase;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationDataSource;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifactTag;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.datamodel.TskException;

/**
 * View correlation results from other cases
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
@ServiceProvider(service = DataContentViewer.class, position = 9)
@Messages({"DataContentViewerOtherCases.title=Other Occurrences",
    "DataContentViewerOtherCases.toolTip=Displays instances of the selected file/artifact from other occurrences."})
public final class DataContentViewerOtherCases extends JPanel implements DataContentViewer {

    private static final long serialVersionUID = -1L;
    private static final Logger LOGGER = Logger.getLogger(DataContentViewerOtherCases.class.getName());
    private final OtherOccurrencesPanel otherOccurrencesPanel = new OtherOccurrencesPanel();

    /**
     * Could be null.
     */
    private AbstractFile file; //the file which the content viewer is being populated for

    /**
     * Creates new form DataContentViewerOtherCases
     */
    public DataContentViewerOtherCases() {
        initComponents();
        add(otherOccurrencesPanel);
    }

    @Override
    public String getTitle() {
        return Bundle.DataContentViewerOtherCases_title();
    }

    @Override
    public String getToolTip() {
        return Bundle.DataContentViewerOtherCases_toolTip();
    }

    @Override
    public DataContentViewer createInstance() {
        return new DataContentViewerOtherCases();
    }

    @Override
    public Component getComponent() {
        return this;
    }

    @Override
    public void resetComponent() {
        otherOccurrencesPanel.reset();
    }

    @Override
    public int isPreferred(Node node) {
        return 1;

    }

    /**
     * Get the associated BlackboardArtifact from a node, if it exists.
     *
     * @param node The node
     *
     * @return The associated BlackboardArtifact, or null
     */
    private BlackboardArtifact
            getBlackboardArtifactFromNode(Node node) {
        BlackboardArtifactTag nodeBbArtifactTag = node.getLookup().lookup(BlackboardArtifactTag.class
        );
        BlackboardArtifact nodeBbArtifact = node.getLookup().lookup(BlackboardArtifact.class
        );

        if (nodeBbArtifactTag != null) {
            return nodeBbArtifactTag.getArtifact();
        } else if (nodeBbArtifact != null) {
            return nodeBbArtifact;
        }

        return null;

    }

    /**
     * Determine what attributes can be used for correlation based on the node.
     * If EamDB is not enabled, get the default Files correlation.
     *
     * @param node The node to correlate
     *
     * @return A list of attributes that can be used for correlation
     */
    private Collection<CorrelationAttributeInstance> getCorrelationAttributesFromNode(Node node) {
        Collection<CorrelationAttributeInstance> ret = new ArrayList<>();

        // correlate on blackboard artifact attributes if they exist and supported
        BlackboardArtifact bbArtifact = getBlackboardArtifactFromNode(node);
        if (bbArtifact != null && CentralRepository.isEnabled()) {
            ret.addAll(CorrelationAttributeUtil.makeCorrAttrsForCorrelation(bbArtifact));
        }

        // we can correlate based on the MD5 if it is enabled      
        if (this.file != null && CentralRepository.isEnabled() && this.file.getSize() > 0) {
            try {

                List<CorrelationAttributeInstance.Type> artifactTypes = CentralRepository.getInstance().getDefinedCorrelationTypes();
                String md5 = this.file.getMd5Hash();
                if (md5 != null && !md5.isEmpty() && null != artifactTypes && !artifactTypes.isEmpty()) {
                    for (CorrelationAttributeInstance.Type aType : artifactTypes) {
                        if (aType.getId() == CorrelationAttributeInstance.FILES_TYPE_ID) {
                            CorrelationCase corCase = CentralRepository.getInstance().getCase(Case.getCurrentCase());
                            try {
                                ret.add(new CorrelationAttributeInstance(
                                        aType,
                                        md5,
                                        corCase,
                                        CorrelationDataSource.fromTSKDataSource(corCase, file.getDataSource()),
                                        file.getParentPath() + file.getName(),
                                        "",
                                        file.getKnown(),
                                        file.getId()));
                            } catch (CorrelationAttributeNormalizationException ex) {
                                LOGGER.log(Level.INFO, String.format("Unable to check create CorrelationAttribtueInstance for value %s and type %s.", md5, aType.toString()), ex);
                            }
                            break;
                        }
                    }
                }
            } catch (CentralRepoException | TskCoreException ex) {
                LOGGER.log(Level.SEVERE, "Error connecting to DB", ex); // NON-NLS
            }
            // If EamDb not enabled, get the Files default correlation type to allow Other Occurances to be enabled.  
        } else if (this.file != null && this.file.getSize() > 0) {
            String md5 = this.file.getMd5Hash();
            if (md5 != null && !md5.isEmpty()) {
                try {
                    final CorrelationAttributeInstance.Type fileAttributeType
                            = CorrelationAttributeInstance.getDefaultCorrelationTypes()
                                    .stream()
                                    .filter(attrType -> attrType.getId() == CorrelationAttributeInstance.FILES_TYPE_ID)
                                    .findAny()
                                    .get();
                    //The Central Repository is not enabled
                    ret.add(new CorrelationAttributeInstance(fileAttributeType, md5, null, null, "", "", TskData.FileKnown.UNKNOWN, this.file.getId()));
                } catch (CentralRepoException ex) {
                    LOGGER.log(Level.SEVERE, "Error connecting to DB", ex); // NON-NLS
                } catch (CorrelationAttributeNormalizationException ex) {
                    LOGGER.log(Level.INFO, String.format("Unable to create CorrelationAttributeInstance for value %s", md5), ex); // NON-NLS
                }
            }
        }
        return ret;
    }

    @Override
    public boolean isSupported(Node node) {

        // Is supported if one of the following is true:
        // - The central repo is enabled and the node has correlatable content
        //   (either through the MD5 hash of the associated file or through a BlackboardArtifact)
        // - The central repo is disabled and the backing file has a valid MD5 hash
        this.file = node.getLookup().lookup(AbstractFile.class);
        if (file == null) {
            return false;
        } else if (CentralRepository.isEnabled()) {
            return !getCorrelationAttributesFromNode(node).isEmpty();
        } else {
            return this.file.getSize() > 0
                    && ((this.file.getMd5Hash() != null)
                    && (!this.file.getMd5Hash().isEmpty()));
        }
    }

    @Override
    public void setNode(Node node) {

        otherOccurrencesPanel.reset(); // reset the table to empty.
        if (node == null) {
            return;
        }
        //could be null
        this.file = node.getLookup().lookup(AbstractFile.class);
        String dataSourceName = "";
        String deviceId = "";
        try {
            if (this.file != null) {
                Content dataSource = this.file.getDataSource();
                dataSourceName = dataSource.getName();
                deviceId = Case.getCurrentCaseThrows().getSleuthkitCase().getDataSource(dataSource.getId()).getDeviceId();
            }
        } catch (TskException | NoCurrentCaseException ex) {
            // do nothing. 
            // @@@ Review this behavior
        }
        otherOccurrencesPanel.populateTable(getCorrelationAttributesFromNode(node), dataSourceName, deviceId, file);

    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        setMinimumSize(new java.awt.Dimension(1000, 10));
        setOpaque(false);
        setPreferredSize(new java.awt.Dimension(1000, 63));
        setLayout(new java.awt.BorderLayout());
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
}
