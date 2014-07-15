/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2014 Basis Technology Corp.
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
package org.sleuthkit.autopsy.ingest;

import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.python.util.PythonInterpreter;

/**
 * Creates Java objects associated with instantiated Jython ingest module
 * classes.
 */
class JythonIngestModuleFactory extends IngestModuleFactoryAdapter {

    private PythonInterpreter interpreter;
    private String moduleDisplayName;
    private String moduleDescription;
    private String moduleVersionNumber;
    private String dataSourceIngestModuleClassName;
    private String fileIngestModuleClassName;
    private int instanceNumber;

    // RJCTODO: With this approach a Jython ingest module developer needs to
    // write either one or two ingest module classes and a few lines of XML. We could 
    // offer a Jython module import UI that allows an Autopsy user to choose a 
    // script file and an associated metadata file, which we would copy into a 
    // subdirectory (we would create a new subdirectory for each imported Jython 
    // module) of a Jython ingest modules directory created by the installer. 
    // Perhaps we could ask the Jython module developer to create an archive 
    // with the two files in it, so the user would only need to import a 
    // single file, sort of like an NBM file.
    /**
     * Constructs an ingest module factory capable of producing Java objects
     * associated with instantiated Jython ingest module classes.
     *
     * @param jythonScriptPath The full path to a Jython script with the ingest
     * module class definitions.
     * @param moduleMetadataFilePath The full path to an XML file of metadata
     * for the ingest module classes defined in the Jython script.
     * @throws
     * org.sleuthkit.autopsy.ingest.JythonIngestModuleFactory.JythonIngestModuleFactoryException
     */
    JythonIngestModuleFactory(String jythonScriptPath, String moduleMetadataFilePath) throws JythonIngestModuleFactoryException {
        executeJythonScript(jythonScriptPath);
        parseModuleMetadataFile(moduleMetadataFilePath);
    }

    private void executeJythonScript(String jythonScriptPath) throws JythonIngestModuleFactoryException {
        try {
            interpreter = new PythonInterpreter();
            interpreter.execfile(jythonScriptPath);
        } catch (Exception ex) {
            throw new JythonIngestModuleFactoryException("Error executing Jython script at " + jythonScriptPath + ": " + ex.getLocalizedMessage());
        }
    }

    private void parseModuleMetadataFile(String moduleMetadataFilePath) throws JythonIngestModuleFactoryException {
        // RJCTODO: Parse something like this:
//         <?xml version="1.0" encoding="UTF-8" standalone="no"?>
//            <IngestModule>
//              <DisplayName>Sample Jython Ingest Module</DisplayName>
//              <Description>A sample Jython ingest module.</Description>
//              <Version>1.0</Version>
//              <DataSourceIngestModuleClassName>SampleJythonDataSourceIngestModule</DataSourceIngestModule>
//              <FileIngestModuleClassName>SampleJythonFileIngestModule</FileIngestModule>
//            </IngestModule>       
        this.moduleDisplayName = "Sample Jython Ingest Module";
        this.moduleDescription = "A sample Jython ingest module";
        this.moduleVersionNumber = "1.0";
        this.fileIngestModuleClassName = "SampleJythonFileIngestModule";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getModuleDisplayName() {
        return this.moduleDisplayName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getModuleDescription() {
        return this.moduleDescription;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getModuleVersionNumber() {
        return this.moduleVersionNumber;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isDataSourceIngestModuleFactory() {
        return this.dataSourceIngestModuleClassName != null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataSourceIngestModule createDataSourceIngestModule(IngestModuleIngestJobSettings ingestOptions) {
        return (DataSourceIngestModule) createIngestModuleInstance(this.dataSourceIngestModuleClassName, DataSourceIngestModule.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isFileIngestModuleFactory() {
        return this.fileIngestModuleClassName != null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FileIngestModule createFileIngestModule(IngestModuleIngestJobSettings ingestOptions) {
        return (FileIngestModule) createIngestModuleInstance(this.fileIngestModuleClassName, FileIngestModule.class);
    }

    // RJCTODO: There just doesn't seem to be a way to make this a generic...
    private Object createIngestModuleInstance(String moduleClassName, Class clazz) {
        try {
            String instanceName = moduleClassName + "_" + instanceNumber++;
            this.interpreter.exec(instanceName + " = " + moduleClassName + "()");
            return this.interpreter.get(instanceName).__tojava__(clazz);
        } catch (Exception ex) {
            // RJCTODO: Do error handling this way? Add an exception specification to API (perhaps with deprecated version with no spec)?
            // The generated message is not user-friendly!
            StringBuilder errorMessage = new StringBuilder("Error creating instance of ");
            errorMessage.append(moduleClassName);
            errorMessage.append(" extending ");
            errorMessage.append(clazz.getSimpleName());
            errorMessage.append(": ");
            errorMessage.append(ex.toString()); // Jython exceptions apparently don't support getMessage()
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(errorMessage.toString(), NotifyDescriptor.ERROR_MESSAGE));
            return null;
        }
    }

    static class JythonIngestModuleFactoryException extends Exception {

        public JythonIngestModuleFactoryException() {
        }

        public JythonIngestModuleFactoryException(String message) {
            super(message);
        }
    }
}
