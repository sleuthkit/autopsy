/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2013 - 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.stix;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import javax.swing.JPanel;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException; 
import javax.xml.bind.Unmarshaller;
import javax.xml.namespace.QName;
import org.mitre.cybox.cybox_2.ObjectType;
import org.mitre.cybox.cybox_2.Observable;
import org.mitre.cybox.cybox_2.ObservableCompositionType;
import org.mitre.cybox.cybox_2.OperatorTypeEnum;
import org.mitre.cybox.objects.AccountObjectType;
import org.mitre.cybox.objects.Address;
import org.mitre.cybox.objects.DomainName;
import org.mitre.cybox.objects.EmailMessage;
import org.mitre.cybox.objects.FileObjectType;
import org.mitre.cybox.objects.SystemObjectType;
import org.mitre.cybox.objects.URIObjectType;
import org.mitre.cybox.objects.URLHistory;
import org.mitre.cybox.objects.WindowsNetworkShare;
import org.mitre.cybox.objects.WindowsRegistryKey;
import org.mitre.stix.common_1.IndicatorBaseType;
import org.mitre.stix.indicator_2.Indicator;
import org.mitre.stix.stix_1.STIXPackage;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.autopsy.report.GeneralReportModule;
import org.sleuthkit.autopsy.report.ReportProgressPanel;
import org.sleuthkit.autopsy.report.ReportProgressPanel.ReportStatus;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 */
public class STIXReportModule implements GeneralReportModule {

    private static final Logger logger = Logger.getLogger(STIXReportModule.class.getName());
    private STIXReportModuleConfigPanel configPanel;
    private static STIXReportModule instance = null;
    private String reportPath;
    private boolean reportAllResults;

    private Map<String, ObjectType> idToObjectMap = new HashMap<String, ObjectType>();
    private Map<String, ObservableResult> idToResult = new HashMap<String, ObservableResult>();

    private List<EvalRegistryObj.RegistryFileInfo> registryFileData = null;

    private final boolean skipShortCircuit = true;

    // Hidden constructor for the report
    private STIXReportModule() {
    }

    // Get the default implementation of this report
    public static synchronized STIXReportModule getDefault() {
        if (instance == null) {
            instance = new STIXReportModule();
        }
        return instance;
    }

    /**
     * @param baseReportDir path to save the report
     * @param progressPanel panel to update the report's progress
     */
    @Override
    @Messages({"STIXReportModule.srcModuleName.text=STIX Report"})
    public void generateReport(String baseReportDir, ReportProgressPanel progressPanel) {
        // Start the progress bar and setup the report
        progressPanel.setIndeterminate(false);
        progressPanel.start();
        progressPanel.updateStatusLabel(NbBundle.getMessage(this.getClass(), "STIXReportModule.progress.readSTIX"));        
        reportPath = baseReportDir + getRelativeFilePath();
        File reportFile = new File(reportPath);
        // Check if the user wants to display all output or just hits
        reportAllResults = configPanel.getShowAllResults();

        // Keep track of whether any errors occur during processing
        boolean hadErrors = false;

        // Process the file/directory name entry
        String stixFileName = configPanel.getStixFile();

        if (stixFileName == null) {
            logger.log(Level.SEVERE, "STIXReportModuleConfigPanel.stixFile not initialized "); //NON-NLS
            MessageNotifyUtil.Message.error(
                    NbBundle.getMessage(this.getClass(), "STIXReportModule.notifyErr.noFildDirProvided"));
            progressPanel.complete(ReportStatus.ERROR);
            progressPanel.updateStatusLabel(
                    NbBundle.getMessage(this.getClass(), "STIXReportModule.progress.noFildDirProvided"));
            new File(baseReportDir).delete();
            return;
        }
        if (stixFileName.isEmpty()) {
            logger.log(Level.SEVERE, "No STIX file/directory provided "); //NON-NLS
            MessageNotifyUtil.Message.error(
                    NbBundle.getMessage(this.getClass(), "STIXReportModule.notifyErr.noFildDirProvided"));
            progressPanel.complete(ReportStatus.ERROR);
            progressPanel.updateStatusLabel(
                    NbBundle.getMessage(this.getClass(), "STIXReportModule.progress.noFildDirProvided"));
            new File(baseReportDir).delete();
            return;
        }
        File stixFile = new File(stixFileName);

        if (!stixFile.exists()) {
            logger.log(Level.SEVERE, String.format("Unable to open STIX file/directory %s", stixFileName)); //NON-NLS
            MessageNotifyUtil.Message.error(NbBundle.getMessage(this.getClass(),
                    "STIXReportModule.notifyMsg.unableToOpenFileDir",
                    stixFileName));
            progressPanel.complete(ReportStatus.ERROR);
            progressPanel.updateStatusLabel(
                    NbBundle.getMessage(this.getClass(), "STIXReportModule.progress.couldNotOpenFileDir", stixFileName));
            new File(baseReportDir).delete();
            return;
        }

        try (BufferedWriter output = new BufferedWriter(new FileWriter(reportFile))) {
            // Store the path
            ModuleSettings.setConfigSetting("STIX", "defaultPath", stixFileName); //NON-NLS

            // Create array of stix file(s)
            File[] stixFiles;
            if (stixFile.isFile()) {
                stixFiles = new File[1];
                stixFiles[0] = stixFile;
            } else {
                stixFiles = stixFile.listFiles();
            }

            // Set the length of the progress bar - we increment twice for each file
            progressPanel.setMaximumProgress(stixFiles.length * 2 + 1);

            // Process each STIX file
            for (File file : stixFiles) {
                if (progressPanel.getStatus() == ReportStatus.CANCELED) {
                    return;
                }
                try {
                    processFile(file.getAbsolutePath(), progressPanel, output);
                } catch (TskCoreException | JAXBException ex) {
                    String errMsg = String.format("Unable to process STIX file %s", file);
                    logger.log(Level.SEVERE, errMsg, ex); //NON-NLS
                    MessageNotifyUtil.Notify.show("STIXReportModule", //NON-NLS
                            errMsg,
                            MessageNotifyUtil.MessageType.ERROR);
                    hadErrors = true;
                    break;
                }
                // Clear out the ID maps before loading the next file
                idToObjectMap = new HashMap<String, ObjectType>();
                idToResult = new HashMap<String, ObservableResult>();
            }

            // Set the progress bar to done. If any errors occurred along the way, modify
            // the "complete" message to indicate this.
            Case.getOpenCase().addReport(reportPath, Bundle.STIXReportModule_srcModuleName_text(), "");
            if (hadErrors) {
                progressPanel.complete(ReportStatus.ERROR);
                progressPanel.updateStatusLabel(
                        NbBundle.getMessage(this.getClass(), "STIXReportModule.progress.completedWithErrors"));
            } else {
                progressPanel.complete(ReportStatus.COMPLETE);
            }
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Unable to complete STIX report.", ex); //NON-NLS
            MessageNotifyUtil.Notify.show("STIXReportModule", //NON-NLS
                    NbBundle.getMessage(this.getClass(),
                            "STIXReportModule.notifyMsg.unableToOpenReportFile"),
                    MessageNotifyUtil.MessageType.ERROR);
            progressPanel.complete(ReportStatus.ERROR);
            progressPanel.updateStatusLabel(
                    NbBundle.getMessage(this.getClass(), "STIXReportModule.progress.completedWithErrors"));
        } catch (TskCoreException | NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Unable to add report to database.", ex);
        }
    }

    /**
     * Process a STIX file.
     *
     * @param stixFile      - Name of the file
     * @param progressPanel - Progress panel (for updating)
     * @param output
     *
     * @throws JAXBException
     * @throws TskCoreException
     */
    private void processFile(String stixFile, ReportProgressPanel progressPanel, BufferedWriter output) throws
            JAXBException, TskCoreException {

        // Load the STIX file
        STIXPackage stix;
        stix = loadSTIXFile(stixFile);

        printFileHeader(stixFile, output);

        // Save any observables listed up front
        processObservables(stix);
        progressPanel.increment();

        // Make copies of the registry files
        registryFileData = EvalRegistryObj.copyRegistryFiles();

        // Process the indicators
        processIndicators(stix, output);
        progressPanel.increment();

    }

    /**
     * Load a STIX-formatted XML file into a STIXPackage object.
     *
     * @param stixFileName Name of the STIX file to unmarshal
     *
     * @return Unmarshalled file contents
     *
     * @throws JAXBException
     */
    private STIXPackage loadSTIXFile(String stixFileName) throws JAXBException {
        // Create STIXPackage object from xml.
        File file = new File(stixFileName);
        JAXBContext jaxbContext = JAXBContext.newInstance("org.mitre.stix.stix_1:org.mitre.stix.common_1:org.mitre.stix.indicator_2:" //NON-NLS
                + "org.mitre.cybox.objects:org.mitre.cybox.cybox_2:org.mitre.cybox.common_2"); //NON-NLS
        Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
        STIXPackage stix = (STIXPackage) jaxbUnmarshaller.unmarshal(file);
        return stix;
    }
    
    /**
     * Do the initial processing of the list of observables. For each
     * observable, save it in a map using the ID as key.
     *
     * @param stix STIXPackage
     */
    private void processObservables(STIXPackage stix) {
        if (stix.getObservables() != null) {
            List<Observable> obs = stix.getObservables().getObservables();
            for (Observable o : obs) {
                if (o.getId() != null) {
                    saveToObjectMap(o);
                }
            }
        }
    }

    /**
     * Process all STIX indicators and save results to output file and create
     * artifacts.
     *
     * @param stix STIXPackage
     * @param output
     */
    private void processIndicators(STIXPackage stix, BufferedWriter output) throws TskCoreException {
        if (stix.getIndicators() != null) {
            List<IndicatorBaseType> s = stix.getIndicators().getIndicators();
            for (IndicatorBaseType t : s) {
                if (t instanceof Indicator) {
                    Indicator ind = (Indicator) t;
                    if (ind.getObservable() != null) {
                        if (ind.getObservable().getObject() != null) {
                            ObservableResult result = evaluateSingleObservable(ind.getObservable(), "");
                            if (result.isTrue() || reportAllResults) {
                                writeResultsToFile(ind, result.getDescription(), result.isTrue(), output);
                            }
                            if (result.isTrue()) {
                                saveResultsAsArtifacts(ind, result);
                            }
                        } else if (ind.getObservable().getObservableComposition() != null) {
                            ObservableResult result = evaluateObservableComposition(ind.getObservable().getObservableComposition(), "  ");

                            if (result.isTrue() || reportAllResults) {
                                writeResultsToFile(ind, result.getDescription(), result.isTrue(), output);
                            }
                            if (result.isTrue()) {
                                saveResultsAsArtifacts(ind, result);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Create the artifacts saved in the observable result.
     *
     * @param ind
     * @param result
     *
     * @throws TskCoreException
     */
    private void saveResultsAsArtifacts(Indicator ind, ObservableResult result) throws TskCoreException {

        if (result.getArtifacts() == null) {
            return;
        }

        // Count of how many artifacts have been created for this indicator. 
        int count = 0;

        for (StixArtifactData s : result.getArtifacts()) {

            // Figure out what name to use for this indicator. If it has a title, 
            // use that. Otherwise use the ID. If both are missing, use a
            // generic heading.
            if (ind.getTitle() != null) {
                s.createArtifact(ind.getTitle());
            } else if (ind.getId() != null) {
                s.createArtifact(ind.getId().toString());
            } else {
                s.createArtifact("Unnamed indicator(s)"); //NON-NLS
            }

            // Trying to protect against the case where we end up with tons of artifacts
            // for a single observable because the condition was not restrictive enough
            count++;
            if (count > 1000) {
                MessageNotifyUtil.Notify.show("STIXReportModule", //NON-NLS
                        NbBundle.getMessage(this.getClass(),
                                "STIXReportModule.notifyMsg.tooManyArtifactsgt1000",
                                ind.getId()),
                        MessageNotifyUtil.MessageType.INFO);
                break;
            }
        }

    }

    /**
     * Write the full results string to the output file.
     *
     * @param ind       - Used to get the title, ID, and description of the
     *                  indicator
     * @param resultStr - Full results for this indicator
     * @param found     - true if the indicator was found in datasource(s)
     * @param output
     */
    private void writeResultsToFile(Indicator ind, String resultStr, boolean found, BufferedWriter output) {
        if (output != null) {
            try {
                if (found) {
                    output.write("----------------\r\n"
                            + "Found indicator:\r\n"); //NON-NLS
                } else {
                    output.write("-----------------------\r\n"
                            + "Did not find indicator:\r\n"); //NON-NLS
                }
                if (ind.getTitle() != null) {
                    output.write("Title: " + ind.getTitle() + "\r\n"); //NON-NLS
                } else {
                    output.write("\r\n");
                }
                if (ind.getId() != null) {
                    output.write("ID: " + ind.getId() + "\r\n"); //NON-NLS
                }

                if (ind.getDescription() != null) {
                    String desc = ind.getDescription().getValue();
                    desc = desc.trim();
                    output.write("Description: " + desc + "\r\n"); //NON-NLS
                }
                output.write("\r\nObservable results:\r\n" + resultStr + "\r\n\r\n"); //NON-NLS
            } catch (IOException ex) {
                logger.log(Level.SEVERE, String.format("Error writing to STIX report file %s", reportPath), ex); //NON-NLS
            }
        }
    }

    /**
     * Write the a header for the current file to the output file.
     *
     * @param a_fileName
     * @param output
     */
    private void printFileHeader(String a_fileName, BufferedWriter output) {
        if (output != null) {
            try {
                char[] chars = new char[a_fileName.length() + 8];
                Arrays.fill(chars, '#');
                String header = new String(chars);
                output.write("\r\n" + header);
                output.write("\r\n");
                output.write("### " + a_fileName + " ###\r\n");
                output.write(header + "\r\n\r\n");
            } catch (IOException ex) {
                logger.log(Level.SEVERE, String.format("Error writing to STIX report file %s", reportPath), ex); //NON-NLS
            }

        }

    }

    /**
     * Use the ID or ID ref to create a key into the observable map.
     *
     * @param obs
     *
     * @return
     */
    private String makeMapKey(Observable obs) {
        QName idQ;
        if (obs.getId() != null) {
            idQ = obs.getId();
        } else if (obs.getIdref() != null) {
            idQ = obs.getIdref();
        } else {
            return "";
        }

        return idQ.getLocalPart();
    }

    /**
     * Save an observable in the object map.
     *
     * @param obs
     */
    private void saveToObjectMap(Observable obs) {

        if (obs.getObject() != null) {
            idToObjectMap.put(makeMapKey(obs), obs.getObject());
        }
    }

    /**
     * Evaluate an observable composition. Can be called recursively.
     *
     * @param comp    The observable composition object to evaluate
     * @param spacing Used to formatting the output
     *
     * @return The status of the composition
     *
     * @throws TskCoreException
     */
    private ObservableResult evaluateObservableComposition(ObservableCompositionType comp, String spacing) throws TskCoreException {
        if (comp.getOperator() == null) {
            throw new TskCoreException("No operator found in composition"); //NON-NLS
        }

        if (comp.getObservables() != null) {
            List<Observable> obsList = comp.getObservables();

            // Split based on the type of composition (AND vs OR)
            if (comp.getOperator() == OperatorTypeEnum.AND) {
                ObservableResult result = new ObservableResult(OperatorTypeEnum.AND, spacing);
                for (Observable o : obsList) {

                    ObservableResult newResult; // The combined result for the composition
                    if (o.getObservableComposition() != null) {
                        newResult = evaluateObservableComposition(o.getObservableComposition(), spacing + "  ");
                        if (result == null) {
                            result = newResult;
                        } else {
                            result.addResult(newResult, OperatorTypeEnum.AND);
                        }
                    } else {
                        newResult = evaluateSingleObservable(o, spacing + "  ");
                        if (result == null) {
                            result = newResult;
                        } else {
                            result.addResult(newResult, OperatorTypeEnum.AND);
                        }
                    }

                    if ((!skipShortCircuit) && !result.isFalse()) {
                        // For testing purposes (and maybe in general), may not want to short-circuit
                        return result;
                    }
                }
                // At this point, all comparisions should have been true (or indeterminate)
                if (result == null) {
                    // This really shouldn't happen, but if we have an empty composition,
                    // indeterminate seems like a reasonable result
                    return new ObservableResult("", "", spacing, ObservableResult.ObservableState.INDETERMINATE, null);
                }

                return result;

            } else {
                ObservableResult result = new ObservableResult(OperatorTypeEnum.OR, spacing);
                for (Observable o : obsList) {

                    ObservableResult newResult;// The combined result for the composition

                    if (o.getObservableComposition() != null) {
                        newResult = evaluateObservableComposition(o.getObservableComposition(), spacing + "  ");
                        if (result == null) {
                            result = newResult;
                        } else {
                            result.addResult(newResult, OperatorTypeEnum.OR);
                        }
                    } else {
                        newResult = evaluateSingleObservable(o, spacing + "  ");
                        if (result == null) {
                            result = newResult;
                        } else {
                            result.addResult(newResult, OperatorTypeEnum.OR);
                        }
                    }

                    if ((!skipShortCircuit) && result.isTrue()) {
                        // For testing (and maybe in general), may not want to short-circuit
                        return result;
                    }
                }
                // At this point, all comparisions were false (or indeterminate)
                if (result == null) {
                    // This really shouldn't happen, but if we have an empty composition,
                    // indeterminate seems like a reasonable result
                    return new ObservableResult("", "", spacing, ObservableResult.ObservableState.INDETERMINATE, null);
                }

                return result;
            }
        } else {
            throw new TskCoreException("No observables found in list"); //NON-NLS
        }
    }

    /**
     * Evaluate one observable and return the result. This is at the end of the
     * observable composition tree and will not be called recursively.
     *
     * @param obs     The observable object to evaluate
     * @param spacing For formatting the output
     *
     * @return The status of the observable
     *
     * @throws TskCoreException
     */
    private ObservableResult evaluateSingleObservable(Observable obs, String spacing) throws TskCoreException {

        // If we've already calculated this one, return the saved value
        if (idToResult.containsKey(makeMapKey(obs))) {
            return idToResult.get(makeMapKey(obs));
        }

        if (obs.getIdref() == null) {

            // We should have the object data right here (as opposed to elsewhere in the STIX file). 
            // Save it to the map.
            if (obs.getId() != null) {
                saveToObjectMap(obs);
            }

            if (obs.getObject() != null) {

                ObservableResult result = evaluateObject(obs.getObject(), spacing, makeMapKey(obs));
                idToResult.put(makeMapKey(obs), result);
                return result;
            }
        }

        if (idToObjectMap.containsKey(makeMapKey(obs))) {
            ObservableResult result = evaluateObject(idToObjectMap.get(makeMapKey(obs)), spacing, makeMapKey(obs));
            idToResult.put(makeMapKey(obs), result);
            return result;
        }

        throw new TskCoreException("Error loading/finding object for observable " + obs.getIdref()); //NON-NLS
    }

    /**
     * Evaluate a STIX object.
     *
     *
     * @param obj     The object to evaluate against the datasource(s)
     * @param spacing For formatting the output
     * @param id
     *
     * @return
     */
    private ObservableResult evaluateObject(ObjectType obj, String spacing, String id) {

        EvaluatableObject evalObj;

        if (obj.getProperties() instanceof FileObjectType) {
            evalObj = new EvalFileObj((FileObjectType) obj.getProperties(), id, spacing);
        } else if (obj.getProperties() instanceof Address) {
            evalObj = new EvalAddressObj((Address) obj.getProperties(), id, spacing);
        } else if (obj.getProperties() instanceof URIObjectType) {
            evalObj = new EvalURIObj((URIObjectType) obj.getProperties(), id, spacing);
        } else if (obj.getProperties() instanceof EmailMessage) {
            evalObj = new EvalEmailObj((EmailMessage) obj.getProperties(), id, spacing);
        } else if (obj.getProperties() instanceof WindowsNetworkShare) {
            evalObj = new EvalNetworkShareObj((WindowsNetworkShare) obj.getProperties(), id, spacing);
        } else if (obj.getProperties() instanceof AccountObjectType) {
            evalObj = new EvalAccountObj((AccountObjectType) obj.getProperties(), id, spacing);
        } else if (obj.getProperties() instanceof SystemObjectType) {
            evalObj = new EvalSystemObj((SystemObjectType) obj.getProperties(), id, spacing);
        } else if (obj.getProperties() instanceof URLHistory) {
            evalObj = new EvalURLHistoryObj((URLHistory) obj.getProperties(), id, spacing);
        } else if (obj.getProperties() instanceof DomainName) {
            evalObj = new EvalDomainObj((DomainName) obj.getProperties(), id, spacing);
        } else if (obj.getProperties() instanceof WindowsRegistryKey) {
            evalObj = new EvalRegistryObj((WindowsRegistryKey) obj.getProperties(), id, spacing, registryFileData);
        } else {
            // Try to get the object type as a string
            String type = obj.getProperties().toString();
            type = type.substring(0, type.indexOf("@"));
            if ((type.lastIndexOf(".") + 1) < type.length()) {
                type = type.substring(type.lastIndexOf(".") + 1);
            }
            return new ObservableResult(id, type + " not supported", //NON-NLS
                    spacing, ObservableResult.ObservableState.INDETERMINATE, null);
        }

        // Evalutate the object
        return evalObj.evaluate();
    }

    @Override
    public String getName() {
        String name = NbBundle.getMessage(this.getClass(), "STIXReportModule.getName.text");
        return name;
    }

    @Override
    public String getRelativeFilePath() {
        return "stix.txt"; //NON-NLS
    }

    @Override
    public String getDescription() {
        String desc = NbBundle.getMessage(this.getClass(), "STIXReportModule.getDesc.text");
        return desc;
    }

    @Override
    public JPanel getConfigurationPanel() {
        configPanel = new STIXReportModuleConfigPanel();
        return configPanel;
    }

}
