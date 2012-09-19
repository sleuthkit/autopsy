/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012 Basis Technology Corp.
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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Class responsible for discovery and loading ingest modules specified in
 * pipeline XML file. Maintains a singleton instance. Requires restart of
 * application for changes in XML to take effect.
 *
 * Refer to
 * http://sleuthkit.org/sleuthkit/docs/framework-docs/pipeline_config_page.html
 * for the pipeline XML fiel schema details.
 *
 * NOTE: this will be part of future IngestPipelineManager with IngestManager
 * code refactored
 */
public final class IngestModuleLoader {

    private static final String PIPELINE_CONFIG_XML = "pipeline_config.xml";
    private String absFilePath;
    private static IngestModuleLoader instance;
    //raw XML pipeline representation for validation
    private final List<XmlPipelineRaw> pipelinesXML;
    //validated pipelines with instantiated modules
    private final List<IngestModuleAbstractFile> filePipeline;
    private final List<IngestModuleImage> imagePipeline;
    private static final Logger logger = Logger.getLogger(IngestModuleLoader.class.getName());
    //for default module order if not specified/invalid
    private int currentLast = 0;
    private ClassLoader classLoader;

    private IngestModuleLoader() {
        pipelinesXML = new ArrayList<XmlPipelineRaw>();
        filePipeline = new ArrayList<IngestModuleAbstractFile>();
        imagePipeline = new ArrayList<IngestModuleImage>();
    }

    synchronized static IngestModuleLoader getDefault() throws IngestModuleLoaderException {
        if (instance == null) {
            logger.log(Level.INFO, "Creating ingest module loader instance");
            instance = new IngestModuleLoader();
            instance.init();
        }
        return instance;
    }

    /**
     * validate raw pipeline, set valid to true member on pipeline and modules
     * if valid log if invalid
     *
     * @throws IngestModuleLoaderException
     */
    private void validate() throws IngestModuleLoaderException {
        for (XmlPipelineRaw pRaw : pipelinesXML) {
            boolean pipelineErrors = false;

            //check pipelineType
            String pipelineType = pRaw.type;

            XmlPipelineRaw.PIPELINE_TYPE pType = null;

            try {
                pType = XmlPipelineRaw.getPipelineType(pipelineType);
            } catch (IllegalArgumentException e) {
                pipelineErrors = true;
                logger.log(Level.SEVERE, "Unknown pipeline type: " + pipelineType);

            }
            //ordering store
            Map<Integer, Integer> orderings = new HashMap<Integer, Integer>();

            for (XmlModuleRaw pMod : pRaw.modules) {
                boolean moduleErrors = false;

                //record ordering for validation
                int order = pMod.order;
                if (orderings.containsKey(order)) {
                    orderings.put(order, orderings.get(order) + 1);
                } else {
                    orderings.put(order, 1);
                }

                //check pipelineType
                String modType = pMod.type;
                if (!modType.equals(XmlModuleRaw.MODULE_TYPE.PLUGIN.toString())) {
                    moduleErrors = true;
                    logger.log(Level.SEVERE, "Unknown module type: " + modType);
                }

                //classes exist and interfaces implemented
                String location = pMod.location;
                try {
                    //netbeans uses custom class loader, otherwise can't load classes from other modules

                    final Class<?> moduleClass = Class.forName(location, false, classLoader);
                    final Type[] intfs = moduleClass.getGenericInterfaces();

                    if (intfs.length != 0 && pType != null) {
                        //check if one of the module interfaces matches the pipeline type
                        boolean interfaceFound = false;
                        Class moduleMeta = ((IngestModuleMapping) pType).getIngestModuleInterface();
                        String moduleIntNameCan = moduleMeta.getCanonicalName();
                        String[] moduleIntNameTok = moduleIntNameCan.split(" ");
                        String moduleIntName = moduleIntNameTok[moduleIntNameTok.length - 1];
                        for (Type intf : intfs) {
                            String intNameCan = intf.toString();
                            String[] intNameCanTok = intNameCan.split(" ");
                            String intName = intNameCanTok[intNameCanTok.length - 1];
                            if (intName.equals(moduleIntName)) {
                                interfaceFound = true;
                                break;
                            }
                        }

                        if (interfaceFound == false) {
                            moduleErrors = true;
                            logger.log(Level.WARNING, "Module class: " + location
                                    + " does not implement correct interface: " + moduleMeta.getName()
                                    + " required for pipeline: " + pType.toString()
                                    + ", module will not be active.");
                        }
                    } else {
                        moduleErrors = true;
                        logger.log(Level.WARNING, "Module class: " + location + " does not implement any interface, module will not be active.");
                    }

                    //if file module: check if has public static getDefault()
                    if (pType == XmlPipelineRaw.PIPELINE_TYPE.FILE_ANALYSIS) {
                        try {
                            Method getDefaultMethod = moduleClass.getMethod("getDefault");
                            int modifiers = getDefaultMethod.getModifiers();
                            if (!(Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers))) {
                                moduleErrors = true;
                                logger.log(Level.WARNING, "Module class: " + location + " does not implement public static getDefault() singleton method.");
                            }
                            if (!getDefaultMethod.getReturnType().equals(moduleClass)) {
                                logger.log(Level.WARNING, "Module class: " + location + " getDefault() singleton method should return the module class instance: " + moduleClass.getName());
                            }

                        } catch (NoSuchMethodException ex) {
                            Exceptions.printStackTrace(ex);
                        } catch (SecurityException ex) {
                            Exceptions.printStackTrace(ex);
                        }
                    } //if image module: check if has public constructor with no args
                    else if (pType == XmlPipelineRaw.PIPELINE_TYPE.IMAGE_ANALYSIS) {
                        try {
                            Constructor<?> constr = moduleClass.getConstructor();
                            int modifiers = constr.getModifiers();
                            if (!Modifier.isPublic(modifiers)) {
                                moduleErrors = true;
                                logger.log(Level.WARNING, "Module class: " + location + " lacks a public default constructor.");
                            }
                        } catch (NoSuchMethodException ex) {
                            moduleErrors = true;
                            logger.log(Level.WARNING, "Module class: " + location + " lacks a public default constructor.");
                        } catch (SecurityException ex) {
                            moduleErrors = true;
                            logger.log(Level.WARNING, "Module class: " + location + " lacks a public default constructor.");
                        }
                    }

                } catch (ClassNotFoundException ex) {
                    moduleErrors = true;
                    logger.log(Level.WARNING, "Module class: " + location + " not found, module will not be active.");
                }


                //validate ordering
                for (int o : orderings.keySet()) {
                    int count = orderings.get(o);
                    if (count > 1) {
                        pipelineErrors = true;
                        logger.log(Level.SEVERE, "Pipeline " + pipelineType + " invalid non-unique ordering of modules, order: " + o);
                    }
                }

                pMod.valid = !moduleErrors;
                logger.log(Level.INFO, "Module " + pMod.location + " valid: " + pMod.valid);
            } //end module

            pRaw.valid = !pipelineErrors;
            logger.log(Level.INFO, "Pipeline " + pType + " valid: " + pRaw.valid);
        } //end pipeline

    }

    /**
     * Autodiscover ingest modules that are not in the config XML and add them
     * to the end of the config
     *
     * @throws IngestModuleLoaderException
     */
    private void autodiscover() throws IngestModuleLoaderException {
    }

    /**
     * Instantiate valid pipeline and modules and keep the references
     *
     * @throws IngestModuleLoaderException
     */
    private void instantiate() throws IngestModuleLoaderException {

        //clear current
        filePipeline.clear();
        imagePipeline.clear();

        autodiscover();

        for (XmlPipelineRaw pRaw : pipelinesXML) {
            if (pRaw.valid == false) {
                //skip invalid pipelines
                continue;
            }

            //sort modules by order parameter, in case XML order is different
            Collections.sort(pRaw.modules, new Comparator<XmlModuleRaw>() {
                @Override
                public int compare(XmlModuleRaw o1, XmlModuleRaw o2) {
                    return Integer.valueOf(o1.order).compareTo(Integer.valueOf(o2.order));
                }
            });

            //check pipelineType, add  to right pipeline collection
            XmlPipelineRaw.PIPELINE_TYPE pType = XmlPipelineRaw.getPipelineType(pRaw.type);

            for (XmlModuleRaw pMod : pRaw.modules) {
                try {
                    if (pMod.valid == false) {
                        //skip invalid modules
                        continue;
                    }

                    //add to right pipeline
                    switch (pType) {
                        case FILE_ANALYSIS:
                            IngestModuleAbstractFile fileModuleInstance = null;
                            final Class<IngestModuleAbstractFile> fileModuleClass =
                                    (Class<IngestModuleAbstractFile>) Class.forName(pMod.location, true, classLoader);
                            try {
                                Method getDefaultMethod = fileModuleClass.getMethod("getDefault");
                                if (getDefaultMethod != null) {
                                    fileModuleInstance = (IngestModuleAbstractFile) getDefaultMethod.invoke(null);
                                }
                            } catch (NoSuchMethodException ex) {
                                logger.log(Level.SEVERE, "Validated module, but not public getDefault() found: " + pMod.location);
                            } catch (SecurityException ex) {
                                logger.log(Level.SEVERE, "Validated module, but not public getDefault() found: " + pMod.location);
                            } catch (IllegalAccessException ex) {
                                logger.log(Level.SEVERE, "Validated module, but not public getDefault() found: " + pMod.location);
                            } catch (InvocationTargetException ex) {
                                logger.log(Level.SEVERE, "Validated module, but not public getDefault() found: " + pMod.location);
                            }
                            //final IngestModuleAbstract fileModuleInstance =
                            //      getNewIngestModuleInstance(fileModuleClass);
                            if (fileModuleInstance != null) {
                                //set arguments
                                fileModuleInstance.setArguments(pMod.arguments);
                            }
                            filePipeline.add(fileModuleInstance);
                            break;
                        case IMAGE_ANALYSIS:
                            final Class<IngestModuleImage> imageModuleClass =
                                    (Class<IngestModuleImage>) Class.forName(pMod.location, true, classLoader);

                            try {
                                Constructor<IngestModuleImage> constr = imageModuleClass.getConstructor();
                                IngestModuleImage imageModuleInstance = constr.newInstance();

                                if (imageModuleInstance != null) {
                                    //set arguments
                                    imageModuleInstance.setArguments(pMod.arguments);
                                    imagePipeline.add((IngestModuleImage) imageModuleInstance);
                                }

                            } catch (NoSuchMethodException ex) {
                                logger.log(Level.SEVERE, "Validated module, should not happen.", ex);
                            } catch (SecurityException ex) {
                                logger.log(Level.SEVERE, "Validated module, should not happen.", ex);
                            } catch (InstantiationException ex) {
                                logger.log(Level.SEVERE, "Validated module, should not happen.", ex);
                            } catch (IllegalAccessException ex) {
                                logger.log(Level.SEVERE, "Validated module, should not happen.", ex);
                            } catch (IllegalArgumentException ex) {
                                logger.log(Level.SEVERE, "Validated module, should not happen.", ex);
                            } catch (InvocationTargetException ex) {
                                logger.log(Level.SEVERE, "Validated module, should not happen.", ex);
                            }


                            break;
                        default:
                            logger.log(Level.SEVERE, "Unexpected pipeline type to add module to: " + pType);
                    }


                } catch (ClassNotFoundException ex) {
                    logger.log(Level.SEVERE, "Validated module, but could not load (shouldn't happen): " + pMod.location);
                }
            }

        }

    }

    /**
     * Get a new instance of the module or null if could not be created
     *
     * @param module existing module to get an instance of
     * @return new module instance or null if could not be created
     */
    IngestModuleAbstract getNewIngestModuleInstance(IngestModuleAbstract module) {
        try {
            IngestModuleAbstract newInstance = module.getClass().newInstance();
            //copy arguments from the registered "template" module 
            newInstance.setArguments(module.getArguments());
            return newInstance;
        } catch (InstantiationException e) {
            logger.log(Level.SEVERE, "Cannot instantiate module: " + module.getName(), e);
            return null;
        } catch (IllegalAccessException e) {
            logger.log(Level.SEVERE, "Cannot instantiate module: " + module.getName(), e);
            return null;
        }

    }

    private IngestModuleAbstract getNewIngestModuleInstance(Class<IngestModuleAbstract> moduleClass) {
        try {
            IngestModuleAbstract newInstance = moduleClass.newInstance();
            return newInstance;
        } catch (InstantiationException e) {
            logger.log(Level.SEVERE, "Cannot instantiate module: " + moduleClass.getName(), e);
            return null;
        } catch (IllegalAccessException e) {
            logger.log(Level.SEVERE, "Cannot instantiate module: " + moduleClass.getName(), e);
            return null;
        }

    }

    private Document loadDoc() {
        DocumentBuilderFactory builderFactory =
                DocumentBuilderFactory.newInstance();

        Document ret = null;


        try {
            DocumentBuilder builder = builderFactory.newDocumentBuilder();
            ret = builder.parse(
                    new FileInputStream(absFilePath));
        } catch (ParserConfigurationException e) {
            logger.log(Level.SEVERE, "Error loading pipeline configuration: can't initialize parser.", e);

        } catch (SAXException e) {
            logger.log(Level.SEVERE, "Error loading pipeline configuration: can't parse XML.", e);

        } catch (IOException e) {
            //error reading file
            logger.log(Level.SEVERE, "Error loading pipeline configuration: can't read file.", e);

        }
        return ret;

    }

    /**
     * Load XML into raw pipeline representation
     *
     * @throws IngestModuleLoaderException
     */
    private void loadRawPipeline() throws IngestModuleLoaderException {
        final Document doc = loadDoc();
        if (doc == null) {
            throw new IngestModuleLoaderException("Could not load pipeline config XML: " + this.absFilePath);
        }
        Element root = doc.getDocumentElement();
        if (root == null) {
            String msg = "Error loading pipeline configuration: invalid file format.";
            logger.log(Level.SEVERE, msg);
            throw new IngestModuleLoaderException(msg);
        }
        NodeList pipelineNodes = root.getElementsByTagName(XmlPipelineRaw.XML_PIPELINE_EL);
        int numPipelines = pipelineNodes.getLength();
        if (numPipelines == 0) {
            throw new IngestModuleLoaderException("No pipelines found in the pipeline configuration: " + absFilePath);
        }
        for (int pipelineNum = 0; pipelineNum < numPipelines; ++pipelineNum) {
            //process pipelines
            Element pipelineEl = (Element) pipelineNodes.item(pipelineNum);
            final String pipelineType = pipelineEl.getAttribute(XmlPipelineRaw.XML_PIPELINE_TYPE_ATTR);
            logger.log(Level.INFO, "Found pipeline type: " + pipelineType);

            XmlPipelineRaw pipelineRaw = new XmlPipelineRaw();
            pipelineRaw.type = pipelineType;
            this.pipelinesXML.add(pipelineRaw);

            //process modules
            NodeList modulesNodes = pipelineEl.getElementsByTagName(XmlModuleRaw.XML_MODULE_EL);
            int numModules = modulesNodes.getLength();
            if (numModules == 0) {
                logger.log(Level.WARNING, "Pipeline: " + pipelineType + " has no modules defined.");
            }
            for (int moduleNum = 0; moduleNum < numModules; ++moduleNum) {
                //process modules
                Element moduleEl = (Element) modulesNodes.item(moduleNum);
                final String moduleType = moduleEl.getAttribute(XmlModuleRaw.XML_MODULE_TYPE_ATTR);
                final String moduleOrder = moduleEl.getAttribute(XmlModuleRaw.XML_MODULE_ORDER_ATTR);
                final String moduleLoc = moduleEl.getAttribute(XmlModuleRaw.XML_MODULE_LOC_ATTR);
                final String moduleArgs = moduleEl.getAttribute(XmlModuleRaw.XML_MODULE_ARGS_ATTR);
                XmlModuleRaw module = new XmlModuleRaw();
                module.arguments = moduleArgs;
                module.location = moduleLoc;
                try {
                    module.order = Integer.parseInt(moduleOrder);
                } catch (NumberFormatException e) {
                    logger.log(Level.WARNING, "Invalid module order, need integer: " + moduleOrder);
                    module.order = Integer.MAX_VALUE - (currentLast++);
                }
                module.type = moduleType;
                pipelineRaw.modules.add(module);
            }

        }

    }

    /**
     * Load and validate XML pipeline, autodiscover and instantiate the pipeline
     * modules Can be called multiple times to refresh the view of modules
     *
     * @throws IngestModuleLoaderException
     */
    public synchronized void init() throws IngestModuleLoaderException {
        absFilePath = PlatformUtil.getUserDirectory() + File.separator + PIPELINE_CONFIG_XML;
        classLoader = Lookup.getDefault().lookup(ClassLoader.class);

        try {
            boolean extracted = PlatformUtil.extractResourceToUserDir(IngestModuleLoader.class, PIPELINE_CONFIG_XML);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error copying default pipeline configuration to user dir ", ex);
        }

        //load the pipeline config
        loadRawPipeline();

        validate();

        instantiate();


    }

    /**
     * Get loaded file modules
     *
     * @return file modules loaded
     */
    public List<IngestModuleAbstractFile> getAbstractFileIngestModules() {
        return filePipeline;
    }

    /**
     * Get loaded image modules
     *
     * @return image modules loaded
     */
    public List<IngestModuleImage> getImageIngestModules() {
        return imagePipeline;
    }

    //pipeline XML representation
    private static final class XmlPipelineRaw {

        enum PIPELINE_TYPE implements IngestModuleMapping {

            FILE_ANALYSIS {
                @Override
                public String toString() {
                    return "FileAnalysis";
                }

                @Override
                public Class getIngestModuleInterface() {
                    return IngestModuleAbstractFile.class;
                }
            },
            IMAGE_ANALYSIS {
                @Override
                public String toString() {
                    return "ImageAnalysis";
                }

                @Override
                public Class getIngestModuleInterface() {
                    return IngestModuleImage.class;
                }
            },;
        }

        /**
         * get pipeline type for string mapping to type toString() method
         *
         * @param s string equals to one of the types toString() representation
         * @return matching type
         */
        static PIPELINE_TYPE getPipelineType(String s) throws IllegalArgumentException {
            PIPELINE_TYPE[] types = PIPELINE_TYPE.values();
            for (int i = 0; i < types.length; ++i) {
                if (types[i].toString().equals(s)) {
                    return types[i];
                }
            }
            throw new IllegalArgumentException("No PIPELINE_TYPE for string: " + s);
        }
        private static final String XML_PIPELINE_EL = "PIPELINE";
        private static final String XML_PIPELINE_TYPE_ATTR = "type";
        String type;
        List<XmlModuleRaw> modules = new ArrayList<XmlModuleRaw>();
        boolean valid = false; // if passed validation
    }

    private static final class XmlModuleRaw {

        enum MODULE_TYPE {

            PLUGIN {
                @Override
                public String toString() {
                    return "plugin";
                }
            },;
        }
        //XML tags and attributes
        private static final String XML_MODULE_EL = "MODULE";
        private static final String XML_MODULE_ORDER_ATTR = "order";
        private static final String XML_MODULE_TYPE_ATTR = "type";
        private static final String XML_MODULE_LOC_ATTR = "location";
        private static final String XML_MODULE_ARGS_ATTR = "arguments";
        int order;
        String type;
        String location;
        String arguments;
        boolean valid = false; // if passed validation
    }
}

/**
 * Exception thrown when errors occur while loading modules
 */
class IngestModuleLoaderException extends Throwable {

    public IngestModuleLoaderException(String message) {
        super(message);
    }

    public IngestModuleLoaderException(String message, Throwable cause) {
        super(message, cause);
    }
}

/**
 * Implements mapping of a type to ingest module interface type
 */
interface IngestModuleMapping {

    /**
     * Get ingest module interface mapped to that type
     *
     * @return ingest module interface meta type
     */
    public Class getIngestModuleInterface();
}
