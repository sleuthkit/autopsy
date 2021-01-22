"""
    pyexcel_io.plugins
    ~~~~~~~~~~~~~~~~~~~

    factory for getting readers and writers

    :copyright: (c) 2014-2020 by Onni Software Ltd.
    :license: New BSD License, see LICENSE for more details
"""
import pyexcel_io.utils as ioutils
import pyexcel_io.manager as manager
import pyexcel_io.constants as constants
import pyexcel_io.exceptions as exceptions
from lml.loader import scan_plugins_regex
from lml.plugin import PluginInfo, PluginManager, PluginInfoChain

ERROR_MESSAGE_FORMATTER = "one of these plugins for %s data in '%s': %s"
UPGRADE_MESSAGE = "Please upgrade the plugin '%s' according to \
plugin compactibility table."
READER_PLUGIN = "pyexcel-io reader"
READER_PLUGIN_V2 = "pyexcel-io v2 reader"
WRITER_PLUGIN = "pyexcel-io writer"
WRITER_PLUGIN_V2 = "pyexcel-io v2 writer"


class IOPluginInfo(PluginInfo):
    """Pyexcel-io plugin info description"""

    def tags(self):
        for file_type in self.file_types:
            yield file_type


class IOPluginInfoChain(PluginInfoChain):
    """provide custom functions to add a reader and a writer """

    def add_a_reader(
        self,
        relative_plugin_class_path=None,
        file_types=None,
        stream_type=None,
    ):
        """ add pyexcle-io reader plugin info """
        a_plugin_info = IOPluginInfo(
            READER_PLUGIN,
            self._get_abs_path(relative_plugin_class_path),
            file_types=file_types,
            stream_type=stream_type,
        )
        return self.add_a_plugin_instance(a_plugin_info)

    def add_a_writer(
        self,
        relative_plugin_class_path=None,
        file_types=None,
        stream_type=None,
    ):
        """ add pyexcle-io writer plugin info """
        a_plugin_info = IOPluginInfo(
            WRITER_PLUGIN,
            self._get_abs_path(relative_plugin_class_path),
            file_types=file_types,
            stream_type=stream_type,
        )
        return self.add_a_plugin_instance(a_plugin_info)


class IOPluginInfoChainV2(PluginInfoChain):
    """provide custom functions to add a reader and a writer """

    def add_a_reader(
        self,
        relative_plugin_class_path=None,
        locations=(),
        file_types=None,
        stream_type=None,
    ):
        """ add pyexcle-io reader plugin info """
        a_plugin_info = IOPluginInfo(
            READER_PLUGIN_V2,
            self._get_abs_path(relative_plugin_class_path),
            file_types=[
                f"{location}-{file_type}"
                for file_type in file_types
                for location in locations
            ],
            stream_type=stream_type,
        )
        return self.add_a_plugin_instance(a_plugin_info)

    def add_a_writer(
        self,
        relative_plugin_class_path=None,
        locations=(),
        file_types=(),
        stream_type=None,
    ):
        """ add pyexcle-io writer plugin info """
        a_plugin_info = IOPluginInfo(
            WRITER_PLUGIN_V2,
            self._get_abs_path(relative_plugin_class_path),
            file_types=[
                f"{location}-{file_type}"
                for file_type in file_types
                for location in locations
            ],
            stream_type=stream_type,
        )
        return self.add_a_plugin_instance(a_plugin_info)


class IOManager(PluginManager):
    """Manage pyexcel-io plugins"""

    def __init__(self, plugin_type, known_list):
        PluginManager.__init__(self, plugin_type)
        self.known_plugins = known_list
        self.action = "read"
        if self.plugin_name == WRITER_PLUGIN:
            self.action = "write"

    def load_me_later(self, plugin_info):
        PluginManager.load_me_later(self, plugin_info)
        _do_additional_registration(plugin_info)

    def register_a_plugin(self, cls, plugin_info):
        """ for dynamically loaded plugin """
        PluginManager.register_a_plugin(self, cls, plugin_info)
        _do_additional_registration(plugin_info)

    def get_a_plugin(self, file_type=None, library=None, **keywords):
        __file_type = file_type.lower()
        try:
            plugin = self.load_me_now(__file_type, library=library)
        except Exception:
            self.raise_exception(__file_type)
        handler = plugin()
        handler.set_type(__file_type)
        return handler

    def raise_exception(self, file_type):
        plugins = self.known_plugins.get(file_type, None)
        if plugins:
            message = "Please install "
            if len(plugins) > 1:
                message += ERROR_MESSAGE_FORMATTER % (
                    self.action,
                    file_type,
                    ",".join(plugins),
                )
            else:
                message += plugins[0]
            raise exceptions.SupportingPluginAvailableButNotInstalled(message)

        else:
            raise exceptions.NoSupportingPluginFound(
                "No suitable library found for %s" % file_type
            )

    def get_all_formats(self):
        """ return all supported formats """
        all_formats = set(
            list(self.registry.keys()) + list(self.known_plugins.keys())
        )
        all_formats = all_formats.difference(
            set([constants.DB_SQL, constants.DB_DJANGO])
        )
        return all_formats


class NewIOManager(IOManager):
    def load_me_later(self, plugin_info):
        PluginManager.load_me_later(self, plugin_info)
        _do_additional_registration_for_new_plugins(plugin_info)

    def register_a_plugin(self, cls, plugin_info):
        """ for dynamically loaded plugin """
        PluginManager.register_a_plugin(self, cls, plugin_info)
        _do_additional_registration_for_new_plugins(plugin_info)

    def get_a_plugin(
        self, file_type=None, location=None, library=None, **keywords
    ):
        __file_type = file_type.lower()
        plugin = self.load_me_now(f"{location}-{__file_type}", library=library)
        return plugin

    def raise_exception(self, file_type):
        file_type = file_type.split("-")[1]
        plugins = self.known_plugins.get(file_type, None)
        if plugins:
            message = "Please install "
            if len(plugins) > 1:
                message += ERROR_MESSAGE_FORMATTER % (
                    self.action,
                    file_type,
                    ",".join(plugins),
                )
            else:
                message += plugins[0]
            raise exceptions.SupportingPluginAvailableButNotInstalled(message)

        else:
            raise exceptions.NoSupportingPluginFound(
                "No suitable library found for %s" % file_type
            )

    def get_all_formats(self):
        """ return all supported formats """
        all_formats = set(
            [x.split("-")[1] for x in self.registry.keys()]
            + list(self.known_plugins.keys())
        )
        return all_formats


def _do_additional_registration(plugin_info):
    for file_type in plugin_info.tags():
        manager.register_stream_type(file_type, plugin_info.stream_type)
        manager.register_a_file_type(file_type, plugin_info.stream_type, None)


def _do_additional_registration_for_new_plugins(plugin_info):
    for file_type in plugin_info.tags():
        manager.register_stream_type(
            file_type.split("-")[1], plugin_info.stream_type
        )
        manager.register_a_file_type(
            file_type.split("-")[1], plugin_info.stream_type, None
        )


class AllReaders:
    def get_all_formats(self):
        return OLD_READERS.get_all_formats().union(
            NEW_READERS.get_all_formats()
        ) - set([constants.DB_SQL, constants.DB_DJANGO])


class AllWriters:
    def get_all_formats(self):
        return OLD_WRITERS.get_all_formats().union(
            NEW_WRITERS.get_all_formats()
        ) - set([constants.DB_SQL, constants.DB_DJANGO])


OLD_READERS = IOManager(READER_PLUGIN, ioutils.AVAILABLE_READERS)
OLD_WRITERS = IOManager(WRITER_PLUGIN, ioutils.AVAILABLE_WRITERS)
NEW_WRITERS = NewIOManager(WRITER_PLUGIN_V2, ioutils.AVAILABLE_WRITERS)
NEW_READERS = NewIOManager(READER_PLUGIN_V2, ioutils.AVAILABLE_READERS)
READERS = AllReaders()
WRITERS = AllWriters()


def load_plugins(plugin_name_patterns, path, black_list, white_list):
    """Try to discover all pyexcel-io plugins"""
    scan_plugins_regex(
        plugin_name_patterns=plugin_name_patterns,
        pyinstaller_path=path,
        black_list=black_list,
        white_list=white_list,
    )
