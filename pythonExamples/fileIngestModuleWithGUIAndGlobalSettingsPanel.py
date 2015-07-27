# Sample module in the public domain. Feel free to use this as a template
# for your modules (and you can remove this header and take complete credit
# and liability)
#
# Contact: Brian Carrier [carrier <at> sleuthkit [dot] org]
#
# This is free and unencumbered software released into the public domain.
#
# Anyone is free to copy, modify, publish, use, compile, sell, or
# distribute this software, either in source code form or as a compiled
# binary, for any purpose, commercial or non-commercial, and by any
# means.
#
# In jurisdictions that recognize copyright laws, the author or authors
# of this software dedicate any and all copyright interest in the
# software to the public domain. We make this dedication for the benefit
# of the public at large and to the detriment of our heirs and
# successors. We intend this dedication to be an overt act of
# relinquishment in perpetuity of all present and future rights to this
# software under copyright law.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
# EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
# MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
# IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
# OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
# ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
# OTHER DEALINGS IN THE SOFTWARE.

import jarray
from java.io import File
from java.lang import System
from javax.swing import JCheckBox
from javax.swing import JButton
from javax.swing import BoxLayout
from javax.xml.parsers import DocumentBuilderFactory
from javax.xml.parsers import DocumentBuilder
from javax.xml.parsers import ParserConfigurationException
from org.sleuthkit.autopsy.casemodule import Case
from org.sleuthkit.autopsy.casemodule.services import Services
from org.sleuthkit.autopsy.ingest import DataSourceIngestModule
from org.sleuthkit.autopsy.ingest import FileIngestModule
from org.sleuthkit.autopsy.ingest import IngestMessage
from org.sleuthkit.autopsy.ingest import IngestModule
from org.sleuthkit.autopsy.ingest import IngestModuleFactoryAdapter
from org.sleuthkit.autopsy.ingest import IngestModuleIngestJobSettings
from org.sleuthkit.autopsy.ingest import IngestModuleIngestJobSettingsPanel
from org.sleuthkit.autopsy.ingest import IngestServices
from org.sleuthkit.autopsy.ingest import IngestModuleGlobalSettingsPanel
from org.sleuthkit.datamodel import BlackboardArtifact
from org.sleuthkit.datamodel import BlackboardAttribute
from org.sleuthkit.datamodel import ReadContentInputStream
from org.sleuthkit.autopsy.coreutils import Logger
from java.lang import IllegalArgumentException
from org.sleuthkit.autopsy.coreutils import PlatformUtil
from org.sleuthkit.autopsy.coreutils import XMLUtil

# Sample factory that defines basic functionality and features of the module
# It implements IngestModuleFactoryAdapter which is a no-op implementation of
# IngestModuleFactory.
class SampleJythonIngestModuleFactory(IngestModuleFactoryAdapter):
    def __init__(self):
        self.settings = None

    def getModuleDisplayName(self):
        return "Sample Jython(GUI) ingest module"

    def getModuleDescription(self):
        return "Sample Jython Ingest Module with GUI example code"

    def getModuleVersionNumber(self):
        return "1.0"

    def getDefaultIngestJobSettings(self):
        return SampleIngestModuleSettings()

    def hasIngestJobSettingsPanel(self):
        return True

    def getIngestJobSettingsPanel(self, settings):
        if not isinstance(settings, SampleIngestModuleSettings):
            raise IllegalArgumentException("Expected settings argument to be instanceof SampleIngestModuleSettings")
        self.settings = settings
        return SampleIngestModuleSettingsPanel(self.settings)

    # Return true if module wants to get called for each file
    def isFileIngestModuleFactory(self):
        return True

    # can return null if isFileIngestModuleFactory returns false
    def createFileIngestModule(self, ingestOptions):
        return SampleJythonFileIngestModule(self.settings)

    def hasGlobalSettingsPanel(self):
        return True

    def getGlobalSettingsPanel(self):
        globalSettingsPanel = SampleIngestModuleGlobalSettingsPanel();
        return globalSettingsPanel


class SampleIngestModuleGlobalSettingsPanel(IngestModuleGlobalSettingsPanel):
    '''
    This is a global settings panel. It demonstrates how a global settings panel is created and how settings are written
     to an XML file saved in the config directory.
    '''
    logger = Logger.getLogger("SampleIngestModuleGlobalSettingsPanel")
    global_config_file_name = "SampleIngestModuleGlobalSettingsPanelSettings.xml"
    global_config_path = PlatformUtil.getUserConfigDirectory() + "\\" + global_config_file_name
    ROOT_ELEMENT = "SampleIngestModuleGlobalSettingsPanel"
    SET_ELEMENT = "Checkbox"
    SET_ATTRIBUTE = "isSet"
    ENCODING = "UTF-8"

    def __init__(self):
        self.setLayout(BoxLayout(self, BoxLayout.Y_AXIS))
        self.checkbox = JCheckBox("Flag inside the Global Settings Panel", actionPerformed=self.checkBoxEvent)
        self.add(self.checkbox)
        self.customizeComponents()

    def checkBoxEvent(self, event):
        self.saveSettings()

    def customizeComponents(self):
        self.load()

    def saveSettings(self):
        if self.checkbox.isSelected():
            self.is_global_checkbox_set = "True"
            self.logger.info("The checkbox in global settings panel is set.")
        else:
            # empty string evaluates to False.
            self.is_global_checkbox_set = ""
            self.logger.info("The checkbox in global settings panel is not set.")
        self.writeSettingsToDisk()

    def load(self):
        self.loadSettingsFromDisk()

    # settings are written to the disk in XML format in the config directory.
    def writeSettingsToDisk(self):
        document_builder_factory_instance = DocumentBuilderFactory.newInstance()
        try:
            document_builder = document_builder_factory_instance.newDocumentBuilder()
            doc = document_builder.newDocument()
            root_element = doc.createElement(self.ROOT_ELEMENT)
            set_element = doc.createElement(self.SET_ELEMENT)
            set_element.setAttribute(self.SET_ATTRIBUTE, self.is_global_checkbox_set)
            root_element.appendChild(set_element)
            doc.appendChild(root_element)
            XMLUtil.saveDoc(SampleIngestModuleGlobalSettingsPanel, self.global_config_path, self.ENCODING, doc)
            self.logger.info("Saved global settings to the disk.")
        except Exception as e:
            self.logger.info("Unable to save global settings to the disk.\n" + str(e))
            pass

    # Load settings from XML file stored in the config directory.
    def loadSettingsFromDisk(self):
        doc = XMLUtil.loadDoc(SampleIngestModuleGlobalSettingsPanel, self.global_config_path)
        if doc == None:
            self.logger.info("Unable to load global settings from the disk.")
            return
        root = doc.getDocumentElement()
        if root == None:
            self.logger.info("Unable to read settings from global settings file.")
            return
        set_element_list = root.getElementsByTagName(self.SET_ELEMENT)
        if set_element_list == None:
            return
        # since we expect only one element (<checkbox isSet=?>),
        # we get the item at the 0th index of the set_element_list.
        set_element = set_element_list.item(0)
        if set_element == None:
            return
        self.is_global_checkbox_set = set_element.getAttribute(self.SET_ATTRIBUTE)
        self.checkbox.setSelected(bool(self.is_global_checkbox_set))


class SampleJythonFileIngestModule(FileIngestModule):
    '''
        File-level ingest module.  One gets created per thread. Looks at the
        attributes of the passed in file. if you don't need a file-level module,
        delete this class.
    '''

    def __init__(self, settings):
        self.local_settings = settings

    def startUp(self, context):
        # Used to verify if the GUI checkbox event been recorded or not.
        logger = Logger.getLogger("SampleJythonFileIngestModule")
        if self.local_settings.getFlag():
            logger.info("flag is set")
        else:
            logger.info("flag is not set")
        pass

    def process(self, file):
        # If the file has a txt extension, post an artifact to the blackboard.
        if file.getName().find("test") != -1:
            art = file.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT)
            att = BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID(),
                                      "Sample Jython File Ingest Module", "Text Files")
            art.addAttribute(att)

            # Read the contents of the file.
            inputStream = ReadContentInputStream(file)
            buffer = jarray.zeros(1024, "b")
            totLen = 0
            len = inputStream.read(buffer)
            while (len != -1):
                totLen = totLen + len
                len = inputStream.read(buffer)

            # Send the size of the file to the ingest messages in box.
            msgText = "Size of %s is %d bytes" % ((file.getName(), totLen))
            message = IngestMessage.createMessage(IngestMessage.MessageType.DATA, "Sample Jython File IngestModule",
                                                  msgText)
            ingestServices = IngestServices.getInstance().postMessage(message)

        return IngestModule.ProcessResult.OK

    def shutDown(self):
        pass


class SampleIngestModuleSettings(IngestModuleIngestJobSettings):
    '''
    This is a sample demonstrating ingest module settings which are serialized to the disk.
    This sample setting store the state of the flag which can be set/unset using the ingest module settings panel.
    '''
    serialVersionUID = 1L

    def __init__(self):
        self.flag = False

    def getVersionNumber(self):
        return serialVersionUID

    def getFlag(self):
        return self.flag

    def setFlag(self, flag):
        self.flag = flag


class SampleIngestModuleSettingsPanel(IngestModuleIngestJobSettingsPanel):
    '''
    This is a sample demonstrating ingest module settings panel. It has a checkbox which can be set/unset.
    It used the deserialized ingest modules settings to set the initial state of the checkbox. The current state of the
    flag is serialized to ingest module settings on the disk.
    '''
    # self.settings instance variable not used. Rather, self.local_settings is used.
    # https://wiki.python.org/jython/UserGuide#javabean-properties
    # Jython Introspector generates a property - 'settings' on the basis
    # of getSettings() defined in this class. Since only getter function
    # is present, it creates a read-only 'settings' property. This auto-
    # generated read-only property overshadows the instance-variable -
    # 'settings'

    def checkBoxEvent(self, event):
        if self.checkbox.isSelected():
            self.local_settings.setFlag(True)
        else:
            self.local_settings.setFlag(False)

    def initComponents(self):
        self.setLayout(BoxLayout(self, BoxLayout.Y_AXIS))
        self.checkbox = JCheckBox("Flag", actionPerformed=self.checkBoxEvent)
        self.add(self.checkbox)

    def customizeComponents(self):
        self.checkbox.setSelected(self.local_settings.getFlag())

    def __init__(self, settings):
        self.local_settings = settings
        self.initComponents()
        self.customizeComponents()

    def getSettings(self):
        return self.local_settings