"""
    pyexcel.plugins
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    Public interface for plugins

    :copyright: (c) 2015-2020 by Onni Software Ltd.
    :license: New BSD License
"""
import types
from itertools import product

from pyexcel import constants as constants
from lml.plugin import PluginInfo, PluginInfoChain
from pyexcel._compact import is_string
from pyexcel.exceptions import FileTypeNotSupported
from pyexcel.internal.plugins import PARSER, RENDERER


class SourceInfo(PluginInfo):
    """Plugin description for a source"""

    def __init__(self, absolute_import_path, **keywords):
        PluginInfo.__init__(self, "source", absolute_import_path, **keywords)

    def tags(self):
        target_action_list = product(self.targets, self.actions)
        for target, action in target_action_list:
            yield "%s-%s" % (target, action)

    def is_my_business(self, action, **keywords):
        """
        Check if incoming keywords match the parameters in source plugins
        """
        statuses = [_has_field(field, keywords) for field in self.fields]
        results = [status for status in statuses if status is False]
        return len(results) == 0


class FileSourceInfo(SourceInfo):
    """Plugin description for a file source"""

    def is_my_business(self, action, **keywords):
        status = SourceInfo.is_my_business(self, action, **keywords)
        if status:
            file_name = keywords.get("file_name", None)
            if file_name:
                file_type = keywords.get("force_file_type")

                if file_type is None:
                    if is_string(type(file_name)):
                        file_type = find_file_type_from_file_name(
                            file_name, action
                        )
                    else:
                        raise IOError("Unsupported file type")
            else:
                file_type = keywords.get("file_type")

            status = self.can_i_handle(action, file_type)
        return status

    def can_i_handle(self, action, file_type):
        raise NotImplementedError("")


class InputSourceInfo(FileSourceInfo):
    """Plugin description for an input source"""

    def can_i_handle(self, action, file_type):
        __file_type = None
        if file_type:
            __file_type = file_type.lower()
        if action == constants.READ_ACTION:
            status = __file_type in PARSER.get_all_file_types()
        else:
            status = False
        return status


class OutputSourceInfo(FileSourceInfo):
    """Plugin description for a output file source"""

    def can_i_handle(self, action, file_type):
        if action == constants.WRITE_ACTION:
            status = file_type.lower() in tuple(RENDERER.get_all_file_types())
        else:
            status = False
        return status


def _has_field(field, keywords):
    return field in keywords and keywords[field] is not None


def find_file_type_from_file_name(file_name, action):
    """
    Extract file type from file name
    """
    if action == "read":
        list_of_file_types = PARSER.get_all_file_types()
    else:
        list_of_file_types = RENDERER.get_all_file_types()
    file_types = []
    lowercase_file_name = file_name.lower()
    for a_supported_type in list_of_file_types:
        if lowercase_file_name.endswith(a_supported_type):
            file_types.append(a_supported_type)
    if len(file_types) > 1:
        file_types = sorted(file_types, key=len)
        file_type = file_types[-1]
    elif len(file_types) == 1:
        file_type = file_types[0]
    else:
        file_type = lowercase_file_name.split(".")[-1]
        raise FileTypeNotSupported(
            constants.FILE_TYPE_NOT_SUPPORTED_FMT % (file_type, action)
        )

    return file_type


class IOPluginInfo(PluginInfo):
    """Plugin description for a parser or a renderer"""

    def tags(self):
        file_types = self.file_types
        if isinstance(file_types, types.FunctionType):
            file_types = file_types()
        for file_type in file_types:
            yield file_type


class PyexcelPluginChain(PluginInfoChain):
    """It is used by pyexcel plugins"""

    def add_a_source(self, relative_plugin_class_path=None, **keywords):
        """
        Add a data source plugin for signature functions
        """
        default = {"key": None, "attributes": []}
        default.update(keywords)
        self.add_a_plugin_instance(
            SourceInfo(
                self._get_abs_path(relative_plugin_class_path), **default
            )
        )
        return self

    def add_an_input_source(self, relative_plugin_class_path=None, **keywords):
        """
        append file input source
        """
        default = {"key": None, "attributes": []}
        default.update(keywords)
        self.add_a_plugin_instance(
            InputSourceInfo(
                self._get_abs_path(relative_plugin_class_path), **default
            )
        )
        return self

    def add_a_output_source(self, relative_plugin_class_path=None, **keywords):
        """
        append file output source
        """
        default = {"key": None, "attributes": []}
        default.update(keywords)
        self.add_a_plugin_instance(
            OutputSourceInfo(
                self._get_abs_path(relative_plugin_class_path), **default
            )
        )
        return self

    def add_a_parser(self, relative_plugin_class_path=None, file_types=None):
        """
        append an excel file reader
        """
        self.add_a_plugin_instance(
            IOPluginInfo(
                "parser",
                self._get_abs_path(relative_plugin_class_path),
                file_types=file_types,
            )
        )
        return self

    def add_a_renderer(
        self,
        relative_plugin_class_path=None,
        file_types=None,
        stream_type=None,
    ):
        """
        append an excel file writer
        """
        default = dict(file_types=file_types, stream_type=stream_type)
        self.add_a_plugin_instance(
            IOPluginInfo(
                "renderer",
                self._get_abs_path(relative_plugin_class_path),
                **default
            )
        )
        return self
