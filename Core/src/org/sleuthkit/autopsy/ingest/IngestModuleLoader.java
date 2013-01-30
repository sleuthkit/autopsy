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

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.openide.modules.ModuleInfo;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;
import org.reflections.Reflections;
import org.reflections.scanners.ResourcesScanner;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.coreutils.XMLUtil;
import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Class responsible for discovery and loading ingest modules specified in
 * pipeline XML file. Maintains a singleton instance. Requires restart of
 * application for changes in XML to take effect.
 *
 * Supports module auto-discovery from system-wide and user-dir wide jar files.
 * Discovered modules are validated, and if valid, they are added to end of
 * configuration and saved in the XML.
 *
 * If module is removed/uninstalled, it will remain in the XML file, but it will
 * not load because it will fail the validation.
 *
 * Get a handle to the object by calling static getDefault() method. The
 * singleton instance will initialize itself the first time - it will load XML
 * and autodiscover currently present ingest modules in the jar classpath..
 *
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
    private static final String XSDFILE = "PipelineConfigSchema.xsd";
    private String absFilePath;
    private static IngestModuleLoader instance;
    //raw XML pipeline representation for validation
    private final List<IngestModuleLoader.XmlPipelineRaw> pipelinesXML;
    //validated pipelines with instantiated modules
    private final List<IngestModuleAbstractFile> filePipeline;
    private final List<IngestModuleImage> imagePipeline;
    private static final Logger logger = Logger.getLogger(IngestModuleLoader.class.getName());
    private ClassLoader classLoader;
    private PropertyChangeSupport pcs;
    private static final String ENCODING = "UTF-8";
    private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";
    private SimpleDateFormat dateFormatter;
    //used to specify default unique module order of autodiscovered modules
    //if not specified
    private int numModDiscovered = 0;
    private static String CUR_MODULES_DISCOVERED_SETTING = "curModulesDiscovered";

    //events supported
    enum Event {

        ModulesReloaded
    };

    private IngestModuleLoader() {
        pipelinesXML = new ArrayList<IngestModuleLoader.XmlPipelineRaw>();
        filePipeline = new ArrayList<IngestModuleAbstractFile>();
        imagePipeline = new ArrayList<IngestModuleImage>();
        dateFormatter = new SimpleDateFormat(DATE_FORMAT);

        String numModDiscoveredStr = ModuleSettings.getConfigSetting(IngestManager.MODULE_PROPERTIES, CUR_MODULES_DISCOVERED_SETTING);
        if (numModDiscoveredStr != null) {
            try {
                numModDiscovered = Integer.valueOf(numModDiscoveredStr);
            } catch (NumberFormatException e) {
                numModDiscovered = 0;
                logger.log(Level.WARNING, "Could not parse numModDiscovered setting, defaulting to 0", e);
            }
        }

        pcs = new PropertyChangeSupport(this);
        registerModulesChange();
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
     * Add a listener to listen for modules reloaded events such as when new
     * modules have been added / removed / reconfigured
     *
     * @param l listener to add
     */
    void addModulesReloadedListener(PropertyChangeListener l) {
        pcs.addPropertyChangeListener(l);
    }

    /**
     * Remove a listener to listen for modules reloaded events such as when new
     * modules have been added / removed / reconfigured
     *
     * @param l listener to remove
     */
    void removeModulesReloadedListener(PropertyChangeListener l) {
        pcs.removePropertyChangeListener(l);
    }

    /**
     * validate raw pipeline, set valid to true member on pipeline and modules
     * if valid log if invalid
     *
     * valid pipeline: valid pipeline type, modules have unique ordering
     *
     * valid module: module class exists, module can be loaded, module
     * implements correct interface, module has proper methods and modifiers to
     * create an instance
     *
     * @throws IngestModuleLoaderException
     */
    private void validate() throws IngestModuleLoaderException {
        for (IngestModuleLoader.XmlPipelineRaw pRaw : pipelinesXML) {
            boolean pipelineErrors = false;

            //check pipelineType
            String pipelineType = pRaw.type;

            IngestModuleLoader.XmlPipelineRaw.PIPELINE_TYPE pType = null;

            try {
                pType = IngestModuleLoader.XmlPipelineRaw.getPipelineType(pipelineType);
            } catch (IllegalArgumentException e) {
                pipelineErrors = true;
                logger.log(Level.SEVERE, "Unknown pipeline type: " + pipelineType);

            }
            //ordering store
            Map<Integer, Integer> orderings = new HashMap<Integer, Integer>();

            for (IngestModuleLoader.XmlModuleRaw pMod : pRaw.modules) {
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
                if (!modType.equals(IngestModuleLoader.XmlModuleRaw.MODULE_TYPE.PLUGIN.toString())) {
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
                        Class<?> moduleMeta = ((IngestModuleMapping) pType).getIngestModuleInterface();
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
                    if (pType == IngestModuleLoader.XmlPipelineRaw.PIPELINE_TYPE.FILE_ANALYSIS) {
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
                    else if (pType == IngestModuleLoader.XmlPipelineRaw.PIPELINE_TYPE.IMAGE_ANALYSIS) {
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

                } catch (LinkageError le) {
                    moduleErrors = true;
                    logger.log(Level.WARNING, "Module class: " + location + " has unresolved symbols, module will not be active.", le);
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

    private Set<URL> getJarPaths(String modulesDir) {
        Set<URL> urls = new HashSet<URL>();

        final File modulesDirF = new File(modulesDir);
        FilenameFilter jarFilter = new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return dir.equals(modulesDirF) && name.endsWith(".jar");
            }
        };
        File[] dirJars = modulesDirF.listFiles(jarFilter);
        if (dirJars != null) {
            //modules dir exists
            for (int i = 0; i < dirJars.length; ++i) {
                String urlPath = "file:/" + dirJars[i].getAbsolutePath();
                try {
                    urlPath = URLDecoder.decode(urlPath, ENCODING);
                } catch (UnsupportedEncodingException ex) {
                    logger.log(Level.SEVERE, "Could not decode file path. ", ex);
                }

                try {
                    urls.add(new URL(urlPath));
                    logger.log(Level.INFO, "JAR: " + urlPath);
                } catch (MalformedURLException ex) {
                    logger.log(Level.WARNING, "Invalid URL: " + urlPath, ex);
                }
            }
        }

        /*
         * netbeans way, but not public API
         org.openide.filesystems.Repository defaultRepository = Repository.getDefault();
         FileSystem masterFilesystem = defaultRepository.getDefaultFileSystem();
         org.netbeans.core.startup.ModuleSystem moduleSystem = new org.netbeans.core.startup.ModuleSystem(masterFilesystem);
         List<File> jars = moduleSystem.getModuleJars();
         for (File jar : jars) {
         logger.log(Level.INFO, " JAR2: " + jar.getAbsolutePath());
         }
         //org.netbeans.ModuleManager moduleManager = moduleSystem.getManager();
         */

        return urls;
    }

    /**
     * Get jar paths of autodiscovered modules
     *
     * @param moduleInfos to look into to discover module jar paths
     * @return
     */
    private Set<URL> getJarPaths(Collection<? extends ModuleInfo> moduleInfos) {
        Set<URL> urls = new HashSet<URL>();

        //TODO lookup module jar file paths by "seed" class or resource, using the module loader
        //problem: we don't have a reliable "seed" class in every moduke
        //and loading by Bundle.properties resource does not seem to work with the module class loader
        //for now hardcoding jar file locations

        /*
         for (ModuleInfo moduleInfo : moduleInfos) {

         if (moduleInfo.isEnabled() == false) {
         continue;
         }

         String basePackageName = moduleInfo.getCodeNameBase();
         if (basePackageName.startsWith("org.netbeans")
         || basePackageName.startsWith("org.openide")) {
         //skip
         continue;
         }

            
         ClassLoader moduleClassLoader = moduleInfo.getClassLoader();
            
         URL modURL = moduleClassLoader.getResource(basePackageName);
         logger.log(Level.INFO, "GOT MOD URL1 : " + modURL);

         modURL = moduleClassLoader.getParent().getResource(basePackageName);
         logger.log(Level.INFO, "GOT MOD URL2 : " + modURL);
            
         modURL = classLoader.getResource(basePackageName);
         logger.log(Level.INFO, "GOT MOD URL3 : " + modURL);
         } */
        /*
         URL modURL = moduleClassLoader.getParent().getResource("Bundle.properties");
         //URL modURL = classLoader.getResource(basePackageName);
         logger.log(Level.INFO, "GOT MOD URL : " + modURL);

         modURL = moduleClassLoader.getResource(basePackageName + ".Bundle.properties");
         //URL modURL = classLoader.getResource(basePackageName);
         logger.log(Level.INFO, "GOT MOD URL : " + modURL);

         modURL = moduleClassLoader.getResource("Bundle");
         //URL modURL = classLoader.getResource(basePackageName);
         logger.log(Level.INFO, "GOT MOD URL : " + modURL);

         Class<?> modClass;
         try {
         modClass = classLoader.loadClass(basePackageName + ".Installer");
         URL modURL2 = modClass.getProtectionDomain().getCodeSource().getLocation();
         logger.log(Level.INFO, "GOT MOD URL2 : " + modURL2);
         } catch (ClassNotFoundException ex) {
         //  Exceptions.printStackTrace(ex);
         }
         try {
         Class<?> moduleBundleClass =
         Class.forName(basePackageName, false, classLoader);
         URL modURL3 = moduleBundleClass.getProtectionDomain().getCodeSource().getLocation();
         logger.log(Level.INFO, "GOT MOD URL3 : " + modURL3);
         } catch (ClassNotFoundException ex) {
         // Exceptions.printStackTrace(ex);
         }


         URL urltry;
         try {
         urltry = moduleClassLoader.loadClass("Bundle").getProtectionDomain().getCodeSource().getLocation();
         logger.log(Level.INFO, "GOT TRY URL : " + urltry);
         } catch (ClassNotFoundException ex) {
         // Exceptions.printStackTrace(ex);
         }

         }
         * */

        //core modules
        urls.addAll(getJarPaths(PlatformUtil.getInstallModulesPath()));

        //user modules
        urls.addAll(getJarPaths(PlatformUtil.getUserModulesPath()));

        return urls;
    }

    /**
     * Auto-discover ingest modules in all platform modules that are "enabled"
     * If discovered ingest module is not already in XML config, add it do
     * config and add to in-memory pipeline.
     *
     * @throws IngestModuleLoaderException
     */
    @SuppressWarnings("unchecked")
    private void autodiscover() throws IngestModuleLoaderException {

        Collection<? extends ModuleInfo> moduleInfos = Lookup.getDefault().lookupAll(ModuleInfo.class);
        logger.log(Level.INFO, "Autodiscovery, found #platform modules: " + moduleInfos.size());

        Set<URL> urls = getJarPaths(moduleInfos);

        for (final ModuleInfo moduleInfo : moduleInfos) {
            if (moduleInfo.isEnabled()) {
                String basePackageName = moduleInfo.getCodeNameBase();
                if (basePackageName.startsWith("org.netbeans")
                        || basePackageName.startsWith("org.openide")) {
                    //skip
                    continue;
                }

                logger.log(Level.INFO, "Module enabled: " + moduleInfo.getDisplayName() + " " + basePackageName
                        + " Build version: " + moduleInfo.getBuildVersion()
                        + " Spec version: " + moduleInfo.getSpecificationVersion()
                        + " Impl version: " + moduleInfo.getImplementationVersion());

                ConfigurationBuilder cb = new ConfigurationBuilder();
                cb.filterInputsBy(new FilterBuilder().include(FilterBuilder.prefix(basePackageName)));
                cb.setUrls(urls);
                cb.setScanners(new SubTypesScanner(), new ResourcesScanner());
                Reflections reflections = new Reflections(cb);

                Set<?> fileModules = reflections.getSubTypesOf(IngestModuleAbstractFile.class);
                Iterator<?> it = fileModules.iterator();
                while (it.hasNext()) {
                    logger.log(Level.INFO, "Found file ingest module in: " + basePackageName + ": " + it.next().toString());
                }

                Set<?> imageModules = reflections.getSubTypesOf(IngestModuleImage.class);
                it = imageModules.iterator();
                while (it.hasNext()) {
                    logger.log(Level.INFO, "Found image ingest module in: " + basePackageName + ": " + it.next().toString());
                }

                //find out which modules to add
                //TODO check which modules to remove (which modules were uninstalled)
                boolean modulesChanged = false;

                it = fileModules.iterator();
                while (it.hasNext()) {
                    boolean exists = false;
                    Class<IngestModuleAbstractFile> foundClass = (Class<IngestModuleAbstractFile>) it.next();

                    for (IngestModuleLoader.XmlPipelineRaw rawP : pipelinesXML) {
                        if (!rawP.type.equals(IngestModuleLoader.XmlPipelineRaw.PIPELINE_TYPE.FILE_ANALYSIS.toString())) {
                            continue; //skip
                        }

                        for (IngestModuleLoader.XmlModuleRaw rawM : rawP.modules) {
                            //logger.log(Level.INFO, "CLASS NAME : " + foundClass.getName());
                            if (foundClass.getName().equals(rawM.location)) {
                                exists = true;
                                break;
                            }
                        }
                        if (exists == true) {
                            break;
                        }
                    }

                    if (exists == false) {
                        logger.log(Level.INFO, "Discovered a new file module to load: " + foundClass.getName());
                        //ADD MODULE
                        addModuleToRawPipeline(foundClass, IngestModuleLoader.XmlPipelineRaw.PIPELINE_TYPE.FILE_ANALYSIS);
                        modulesChanged = true;
                    }

                }

                it = imageModules.iterator();
                while (it.hasNext()) {
                    boolean exists = false;
                    Class<IngestModuleImage> foundClass = (Class<IngestModuleImage>) it.next();

                    for (IngestModuleLoader.XmlPipelineRaw rawP : pipelinesXML) {
                        if (!rawP.type.equals(IngestModuleLoader.XmlPipelineRaw.PIPELINE_TYPE.IMAGE_ANALYSIS.toString())) {
                            continue; //skip
                        }


                        for (IngestModuleLoader.XmlModuleRaw rawM : rawP.modules) {
                            //logger.log(Level.INFO, "CLASS NAME : " + foundClass.getName());
                            if (foundClass.getName().equals(rawM.location)) {
                                exists = true;
                                break;
                            }
                        }
                        if (exists == true) {
                            break;
                        }
                    }

                    if (exists == false) {
                        logger.log(Level.INFO, "Discovered a new image module to load: " + foundClass.getName());
                        //ADD MODULE 
                        addModuleToRawPipeline(foundClass, IngestModuleLoader.XmlPipelineRaw.PIPELINE_TYPE.IMAGE_ANALYSIS);
                        modulesChanged = true;
                    }

                }

                if (modulesChanged) {
                    save();
                    pcs.firePropertyChange(IngestModuleLoader.Event.ModulesReloaded.toString(), 0, 1);
                }

                /*
                 //Enumeration<URL> resources = moduleClassLoader.getResources(basePackageName);
                 Enumeration<URL> resources = classLoader.getResources(basePackageName);
                 while (resources.hasMoreElements()) {
                 System.out.println(resources.nextElement());
                 } */

            } else {
                //logger.log(Level.INFO, "Module disabled: " + moduleInfo.getDisplayName() );
            }
        }

    }

    /**
     * Set a new order of the module
     *
     * @param pipeLineType pipeline type where the module to reorder is present
     * @param moduleLocation loaded module name (location), fully qualified
     * class path
     * @param newOrder new order to set
     */
    void setModuleOrder(IngestModuleLoader.XmlPipelineRaw.PIPELINE_TYPE pipeLineType, String moduleLocation, int newOrder) throws IngestModuleLoaderException {
        throw new IngestModuleLoaderException("Not yet implemented");
    }

    /**
     * add autodiscovered module to raw pipeline to be validated and
     * instantiated
     *
     * @param moduleClass
     * @param pipelineType
     */
    private void addModuleToRawPipeline(Class<?> moduleClass, IngestModuleLoader.XmlPipelineRaw.PIPELINE_TYPE pipelineType) throws IngestModuleLoaderException {
        String moduleLocation = moduleClass.getName();

        IngestModuleLoader.XmlModuleRaw modRaw = new IngestModuleLoader.XmlModuleRaw();
        modRaw.arguments = ""; //default, no arguments
        modRaw.location = moduleLocation;
        modRaw.order = Integer.MAX_VALUE - (numModDiscovered++); //add to end
        modRaw.type = IngestModuleLoader.XmlModuleRaw.MODULE_TYPE.PLUGIN.toString();
        modRaw.valid = false; //to be validated

        //save the current numModDiscovered
        ModuleSettings.setConfigSetting(IngestManager.MODULE_PROPERTIES, CUR_MODULES_DISCOVERED_SETTING, Integer.toString(numModDiscovered));

        //find the pipeline of that type
        IngestModuleLoader.XmlPipelineRaw pipeline = null;
        for (IngestModuleLoader.XmlPipelineRaw rawP : this.pipelinesXML) {
            if (rawP.type.equals(pipelineType.toString())) {
                pipeline = rawP;
                break;
            }
        }
        if (pipeline == null) {
            throw new IngestModuleLoaderException("Could not find expected pipeline of type: " + pipelineType.toString() + ", cannot add autodiscovered module: " + moduleLocation);
        } else {
            pipeline.modules.add(modRaw);
            logger.log(Level.INFO, "Added a new module " + moduleClass.getName() + " to pipeline " + pipelineType.toString());
        }
    }

    /**
     * Register a listener for module install/uninstall //TODO ensure that
     * module is actually loadable when Lookup event is fired
     */
    private void registerModulesChange() {
        final Lookup.Result<ModuleInfo> result =
                Lookup.getDefault().lookupResult(ModuleInfo.class);
        result.addLookupListener(new LookupListener() {
            @Override
            public void resultChanged(LookupEvent event) {
                try {
                    logger.log(Level.INFO, "Module change occured, reloading.");
                    init();
                } catch (IngestModuleLoaderException ex) {
                    logger.log(Level.SEVERE, "Error reloading the module loader. ", ex);
                }
            }
        });
    }

    /**
     * Save the current in memory pipeline config, including autodiscovered
     * modules
     *
     * @throws IngestModuleLoaderException
     */
    public void save() throws IngestModuleLoaderException {
        DocumentBuilderFactory dbfac = DocumentBuilderFactory.newInstance();

        try {
            DocumentBuilder docBuilder = dbfac.newDocumentBuilder();
            Document doc = docBuilder.newDocument();


            Comment comment = doc.createComment("Saved by: " + getClass().getName()
                    + " on: " + dateFormatter.format(System.currentTimeMillis()));
            doc.appendChild(comment);
            Element rootEl = doc.createElement(IngestModuleLoader.XmlPipelineRaw.XML_PIPELINE_ROOT);
            doc.appendChild(rootEl);

            for (IngestModuleLoader.XmlPipelineRaw rawP : this.pipelinesXML) {
                Element pipelineEl = doc.createElement(IngestModuleLoader.XmlPipelineRaw.XML_PIPELINE_EL);
                pipelineEl.setAttribute(IngestModuleLoader.XmlPipelineRaw.XML_PIPELINE_TYPE_ATTR, rawP.type);
                rootEl.appendChild(pipelineEl);

                for (IngestModuleLoader.XmlModuleRaw rawM : rawP.modules) {
                    Element moduleEl = doc.createElement(IngestModuleLoader.XmlModuleRaw.XML_MODULE_EL);

                    moduleEl.setAttribute(IngestModuleLoader.XmlModuleRaw.XML_MODULE_LOC_ATTR, rawM.location);
                    moduleEl.setAttribute(IngestModuleLoader.XmlModuleRaw.XML_MODULE_TYPE_ATTR, rawM.type);
                    moduleEl.setAttribute(IngestModuleLoader.XmlModuleRaw.XML_MODULE_ORDER_ATTR, Integer.toString(rawM.order));
                    moduleEl.setAttribute(IngestModuleLoader.XmlModuleRaw.XML_MODULE_TYPE_ATTR, rawM.type);

                    pipelineEl.appendChild(moduleEl);
                }
            }

            XMLUtil.saveDoc(IngestModuleLoader.class, absFilePath, ENCODING, doc);
            logger.log(Level.INFO, "Pipeline configuration saved to: " + this.absFilePath);
        } catch (ParserConfigurationException e) {
            logger.log(Level.SEVERE, "Error saving pipeline config XML: can't initialize parser.", e);
        }

    }

    /**
     * Instantiate valid pipeline and modules and store the module object
     * references
     *
     * @throws IngestModuleLoaderException
     */
    @SuppressWarnings("unchecked")
    private void instantiate() throws IngestModuleLoaderException {

        //clear current
        filePipeline.clear();
        imagePipeline.clear();

        //add autodiscovered modules to pipelinesXML
        autodiscover();

        //validate all modules: from XML + just autodiscovered

        validate();

        for (IngestModuleLoader.XmlPipelineRaw pRaw : pipelinesXML) {
            if (pRaw.valid == false) {
                //skip invalid pipelines
                continue;
            }

            //sort modules by order parameter, in case XML order is different
            Collections.sort(pRaw.modules, new Comparator<IngestModuleLoader.XmlModuleRaw>() {
                @Override
                public int compare(IngestModuleLoader.XmlModuleRaw o1, IngestModuleLoader.XmlModuleRaw o2) {
                    return Integer.valueOf(o1.order).compareTo(Integer.valueOf(o2.order));
                }
            });

            //check pipelineType, add  to right pipeline collection
            IngestModuleLoader.XmlPipelineRaw.PIPELINE_TYPE pType = IngestModuleLoader.XmlPipelineRaw.getPipelineType(pRaw.type);

            for (IngestModuleLoader.XmlModuleRaw pMod : pRaw.modules) {
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
                                logger.log(Level.WARNING, "Validated module, but not public getDefault() found: " + pMod.location);
                                pMod.valid = false; //prevent from trying to load again
                            } catch (SecurityException ex) {
                                logger.log(Level.WARNING, "Validated module, but not public getDefault() found: " + pMod.location);
                                pMod.valid = false; //prevent from trying to load again
                            } catch (IllegalAccessException ex) {
                                logger.log(Level.WARNING, "Validated module, but not public getDefault() found: " + pMod.location);
                                pMod.valid = false; //prevent from trying to load again
                            } catch (InvocationTargetException ex) {
                                logger.log(Level.WARNING, "Validated module, but not public getDefault() found: " + pMod.location);
                                pMod.valid = false; //prevent from trying to load again
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
                                    imagePipeline.add(imageModuleInstance);
                                }

                            } catch (NoSuchMethodException ex) {
                                logger.log(Level.WARNING, "Validated module, could not initialize, check for bugs in the module: " + pMod.location, ex);
                                pMod.valid = false;
                            } catch (SecurityException ex) {
                                logger.log(Level.WARNING, "Validated module, could not initialize, check for bugs in the module: " + pMod.location, ex);
                                pMod.valid = false;
                            } catch (InstantiationException ex) {
                                logger.log(Level.WARNING, "Validated module, could not initialize, check for bugs in the module: " + pMod.location, ex);
                                pMod.valid = false; //prevent from trying to load again
                            } catch (IllegalAccessException ex) {
                                logger.log(Level.WARNING, "Validated module, could not initialize, check for bugs in the module: " + pMod.location, ex);
                                pMod.valid = false; //prevent from trying to load again
                            } catch (IllegalArgumentException ex) {
                                logger.log(Level.WARNING, "Validated module, could not initialize, check for bugs in the module: " + pMod.location, ex);
                                pMod.valid = false; //prevent from trying to load again
                            } catch (InvocationTargetException ex) {
                                logger.log(Level.WARNING, "Validated module, could not initialize, check for bugs in the module: " + pMod.location, ex);
                                pMod.valid = false; //prevent from trying to load again
                            }


                            break;
                        default:
                            logger.log(Level.SEVERE, "Unexpected pipeline type to add module to: " + pType);
                    }


                } catch (ClassNotFoundException ex) {
                    logger.log(Level.SEVERE, "Validated module, but could not load (shouldn't happen): " + pMod.location);
                }
            }

        } //end instantiating modules in XML



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

    /**
     * Load XML into raw pipeline representation
     *
     * @throws IngestModuleLoaderException
     */
    private void loadRawPipeline() throws IngestModuleLoaderException {
        final Document doc = XMLUtil.loadDoc(IngestModuleLoader.class, absFilePath, XSDFILE);
        if (doc == null) {
            throw new IngestModuleLoaderException("Could not load pipeline config XML: " + this.absFilePath);
        }
        Element root = doc.getDocumentElement();
        if (root == null) {
            String msg = "Error loading pipeline configuration: invalid file format.";
            logger.log(Level.SEVERE, msg);
            throw new IngestModuleLoaderException(msg);
        }
        NodeList pipelineNodes = root.getElementsByTagName(IngestModuleLoader.XmlPipelineRaw.XML_PIPELINE_EL);
        int numPipelines = pipelineNodes.getLength();
        if (numPipelines == 0) {
            throw new IngestModuleLoaderException("No pipelines found in the pipeline configuration: " + absFilePath);
        }
        for (int pipelineNum = 0; pipelineNum < numPipelines; ++pipelineNum) {
            //process pipelines
            Element pipelineEl = (Element) pipelineNodes.item(pipelineNum);
            final String pipelineType = pipelineEl.getAttribute(IngestModuleLoader.XmlPipelineRaw.XML_PIPELINE_TYPE_ATTR);
            logger.log(Level.INFO, "Found pipeline type: " + pipelineType);

            IngestModuleLoader.XmlPipelineRaw pipelineRaw = new IngestModuleLoader.XmlPipelineRaw();
            pipelineRaw.type = pipelineType;
            this.pipelinesXML.add(pipelineRaw);

            //process modules
            NodeList modulesNodes = pipelineEl.getElementsByTagName(IngestModuleLoader.XmlModuleRaw.XML_MODULE_EL);
            int numModules = modulesNodes.getLength();
            if (numModules == 0) {
                logger.log(Level.WARNING, "Pipeline: " + pipelineType + " has no modules defined.");
            }
            for (int moduleNum = 0; moduleNum < numModules; ++moduleNum) {
                //process modules
                Element moduleEl = (Element) modulesNodes.item(moduleNum);
                final String moduleType = moduleEl.getAttribute(IngestModuleLoader.XmlModuleRaw.XML_MODULE_TYPE_ATTR);
                final String moduleOrder = moduleEl.getAttribute(IngestModuleLoader.XmlModuleRaw.XML_MODULE_ORDER_ATTR);
                final String moduleLoc = moduleEl.getAttribute(IngestModuleLoader.XmlModuleRaw.XML_MODULE_LOC_ATTR);
                final String moduleArgs = moduleEl.getAttribute(IngestModuleLoader.XmlModuleRaw.XML_MODULE_ARGS_ATTR);
                IngestModuleLoader.XmlModuleRaw module = new IngestModuleLoader.XmlModuleRaw();
                module.arguments = moduleArgs;
                module.location = moduleLoc;
                try {
                    module.order = Integer.parseInt(moduleOrder);
                } catch (NumberFormatException e) {
                    logger.log(Level.WARNING, "Invalid module order, need integer: " + moduleOrder + ", adding to end of the list");
                    module.order = Integer.MAX_VALUE - (numModDiscovered++);
                    //save the current numModDiscovered
                    ModuleSettings.setConfigSetting(IngestManager.MODULE_PROPERTIES, CUR_MODULES_DISCOVERED_SETTING, Integer.toString(numModDiscovered));

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
        absFilePath = PlatformUtil.getUserConfigDirectory() + File.separator + PIPELINE_CONFIG_XML;
        ClassLoader parentClassLoader = Lookup.getDefault().lookup(ClassLoader.class);
        classLoader = new CustomClassLoader(parentClassLoader);

        try {
            boolean extracted = PlatformUtil.extractResourceToUserConfigDir(IngestModuleLoader.class, PIPELINE_CONFIG_XML);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error copying default pipeline configuration to user dir ", ex);
        }

        //load the pipeline config
        loadRawPipeline();

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
        static IngestModuleLoader.XmlPipelineRaw.PIPELINE_TYPE getPipelineType(String s) throws IllegalArgumentException {
            IngestModuleLoader.XmlPipelineRaw.PIPELINE_TYPE[] types = IngestModuleLoader.XmlPipelineRaw.PIPELINE_TYPE.values();
            for (int i = 0; i < types.length; ++i) {
                if (types[i].toString().equals(s)) {
                    return types[i];
                }
            }
            throw new IllegalArgumentException("No PIPELINE_TYPE for string: " + s);
        }
        private static final String XML_PIPELINE_ROOT = "PIPELINE_CONFIG";
        private static final String XML_PIPELINE_EL = "PIPELINE";
        private static final String XML_PIPELINE_TYPE_ATTR = "type";
        String type;
        List<IngestModuleLoader.XmlModuleRaw> modules = new ArrayList<IngestModuleLoader.XmlModuleRaw>();
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
    public Class<?> getIngestModuleInterface();
}

/**
 * Custom class loader that attempts to force class resolution / linkage validation at loading
 */
class CustomClassLoader extends ClassLoader {
    private static final Logger logger = Logger.getLogger(CustomClassLoader.class.getName());

    CustomClassLoader(ClassLoader parent) {
        super(parent);
    }


    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        logger.log(Level.INFO, "Custom loading class: " + name);
        
        Class<?> cl = super.loadClass(name, true);
                
        return cl;
    }
    
}
