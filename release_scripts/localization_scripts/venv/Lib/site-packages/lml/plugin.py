"""
    lml.plugin
    ~~~~~~~~~~~~~~~~~~~

    lml divides the plugins into two category: load-me-later plugins and
    load-me-now ones. load-me-later plugins refer to the plugins were
    loaded when needed due its bulky and/or memory hungry dependencies.
    Those plugins has to use lml and respect lml's design principle.

    load-me-now plugins refer to the plugins are immediately imported. All
    conventional Python classes are by default immediately imported.

    :class:`~lml.plugin.PluginManager` should be inherited to form new
    plugin manager class. If you have more than one plugins in your
    architecture, it is advisable to have one class per plugin type.

    :class:`~lml.plugin.PluginInfoChain` helps the plugin module to
    declare the available plugins in the module.

    :class:`~lml.plugin.PluginInfo` can be subclassed to describe
    your plugin. Its method :meth:`~lml.plugin.PluginInfo.tags`
    can be overridden to help its matching :class:`~lml.plugin.PluginManager`
    to look itself up.

    :copyright: (c) 2017-2020 by Onni Software Ltd.
    :license: New BSD License, see LICENSE for more details
"""
import logging
from collections import defaultdict

from lml.utils import json_dumps, do_import_class

PLUG_IN_MANAGERS = {}
CACHED_PLUGIN_INFO = defaultdict(list)

log = logging.getLogger(__name__)


class PluginInfo(object):
    """
    Information about the plugin.

    It is used together with PluginInfoChain to describe the plugins.
    Meanwhile, it is a class decorator and can be used to register a plugin
    immediately for use, in other words, the PluginInfo decorated plugin
    class is not loaded later.

    Parameters
    -------------
    name:
       plugin name

    absolute_import_path:
       absolute import path from your plugin name space for your plugin class

    tags:
       a list of keywords help the plugin manager to retrieve your plugin

    keywords:
       Another custom properties.

    Examples
    -------------

    For load-me-later plugins:

        >>> info = PluginInfo("sample",
        ...      abs_class_path='lml.plugin.PluginInfo', # demonstration only.
        ...      tags=['load-me-later'],
        ...      custom_property = 'I am a custom property')
        >>> print(info.module_name)
        lml
        >>> print(info.custom_property)
        I am a custom property

    For load-me-now plugins:

        >>> @PluginInfo("sample", tags=['load-me-now'])
        ... class TestPlugin:
        ...     def echo(self, words):
        ...         print("echoing %s" % words)

    Now let's retrive the second plugin back:

        >>> class SamplePluginManager(PluginManager):
        ...     def __init__(self):
        ...         PluginManager.__init__(self, "sample")
        >>> sample_manager = SamplePluginManager()
        >>> test_plugin=sample_manager.get_a_plugin("load-me-now")
        >>> test_plugin.echo("hey..")
        echoing hey..

    """

    def __init__(
        self, plugin_type, abs_class_path=None, tags=None, **keywords
    ):
        self.plugin_type = plugin_type
        self.absolute_import_path = abs_class_path
        self.cls = None
        self.properties = keywords
        self.__tags = tags

    def __getattr__(self, name):
        if name == "module_name":
            if self.absolute_import_path:
                module_name = self.absolute_import_path.split(".")[0]
            else:
                module_name = self.cls.__module__
            return module_name
        return self.properties.get(name)

    def tags(self):
        """
        A list of tags for identifying the plugin class

        The plugin class is described at the absolute_import_path
        """
        if self.__tags is None:
            yield self.plugin_type
        else:
            for tag in self.__tags:
                yield tag

    def __repr__(self):
        rep = {
            "plugin_type": self.plugin_type,
            "path": self.absolute_import_path,
        }
        rep.update(self.properties)
        return json_dumps(rep)

    def __call__(self, cls):
        self.cls = cls
        _register_a_plugin(self, cls)
        return cls


class PluginInfoChain(object):
    """
    Pandas style, chained list declaration

    It is used in the plugin packages to list all plugin classes
    """

    def __init__(self, path):
        self._logger = logging.getLogger(
            self.__class__.__module__ + "." + self.__class__.__name__
        )
        self.module_name = path

    def add_a_plugin(self, plugin_type, submodule=None, **keywords):
        """
        Add a plain plugin

        Parameters
        -------------

        plugin_type:
          plugin manager name

        submodule:
          the relative import path to your plugin class
        """
        a_plugin_info = PluginInfo(
            plugin_type, self._get_abs_path(submodule), **keywords
        )

        self.add_a_plugin_instance(a_plugin_info)
        return self

    def add_a_plugin_instance(self, plugin_info_instance):
        """
        Add a plain plugin

        Parameters
        -------------

        plugin_info_instance:
          an instance of PluginInfo

        The developer has to specify the absolute import path
        """
        self._logger.debug(
            "add %s as '%s' plugin",
            plugin_info_instance.absolute_import_path,
            plugin_info_instance.plugin_type,
        )
        _load_me_later(plugin_info_instance)
        return self

    def _get_abs_path(self, submodule):
        return "%s.%s" % (self.module_name, submodule)


class PluginManager(object):
    """
    Load plugin info into in-memory dictionary for later import

    Parameters
    --------------

    plugin_type:
        the plugin type. All plugins of this plugin type will be
        registered to it.
    """

    def __init__(self, plugin_type):
        self.plugin_name = plugin_type
        self.registry = defaultdict(list)
        self.tag_groups = dict()
        self._logger = logging.getLogger(
            self.__class__.__module__ + "." + self.__class__.__name__
        )
        _register_class(self)

    def get_a_plugin(self, key, **keywords):
        """ Get a plugin

        Parameters
        ---------------

        key:
             the key to find the plugins

        keywords:
             additional parameters for help the retrieval of the plugins
        """
        self._logger.debug("get a plugin called")
        plugin = self.load_me_now(key)
        return plugin()

    def raise_exception(self, key):
        """Raise plugin not found exception

        Override this method to raise custom exception

        Parameters
        -----------------

        key:
            the key to find the plugin
        """
        self._logger.debug(self.registry.keys())
        raise Exception("No %s is found for %s" % (self.plugin_name, key))

    def load_me_later(self, plugin_info):
        """
        Register a plugin info for later loading

        Parameters
        --------------

        plugin_info:
            a instance of plugin info
        """
        self._logger.debug("load %s later", plugin_info.absolute_import_path)
        self._update_registry_and_expand_tag_groups(plugin_info)

    def load_me_now(self, key, library=None, **keywords):
        """
        Import a plugin from plugin registry

        Parameters
        -----------------

        key:
            the key to find the plugin

        library:
            to use a specific plugin module
        """
        if keywords:
            self._logger.debug(keywords)
        __key = key.lower()

        if __key in self.registry:
            for plugin_info in self.registry[__key]:
                cls = self.dynamic_load_library(plugin_info)
                module_name = _get_me_pypi_package_name(cls)
                if library and module_name != library:
                    continue
                else:
                    break
            else:
                # only library condition could raise an exception
                self._logger.debug("%s is not installed" % library)
                self.raise_exception(key)
            self._logger.debug("load %s now for '%s'", cls, key)
            return cls
        else:
            self.raise_exception(key)

    def dynamic_load_library(self, a_plugin_info):
        """Dynamically load the plugin info if not loaded


        Parameters
        --------------

        a_plugin_info:
            a instance of plugin info
        """
        if a_plugin_info.cls is None:
            self._logger.debug("import " + a_plugin_info.absolute_import_path)
            cls = do_import_class(a_plugin_info.absolute_import_path)
            a_plugin_info.cls = cls
        return a_plugin_info.cls

    def register_a_plugin(self, plugin_cls, plugin_info):
        """ for dynamically loaded plugin during runtime

        Parameters
        --------------

        plugin_cls:
            the actual plugin class refered to by the second parameter

        plugin_info:
            a instance of plugin info
        """
        self._logger.debug("register %s", _show_me_your_name(plugin_cls))
        plugin_info.cls = plugin_cls
        self._update_registry_and_expand_tag_groups(plugin_info)

    def get_primary_key(self, key):
        __key = key.lower()
        return self.tag_groups.get(__key, None)

    def _update_registry_and_expand_tag_groups(self, plugin_info):
        primary_tag = None
        for index, key in enumerate(plugin_info.tags()):
            self.registry[key.lower()].append(plugin_info)
            if index == 0:
                primary_tag = key.lower()
            self.tag_groups[key.lower()] = primary_tag


def _register_class(cls):
    """Reigister a newly created plugin manager"""
    log.debug("declare '%s' plugin manager", cls.plugin_name)
    PLUG_IN_MANAGERS[cls.plugin_name] = cls
    if cls.plugin_name in CACHED_PLUGIN_INFO:
        # check if there is early registrations or not
        for plugin_info in CACHED_PLUGIN_INFO[cls.plugin_name]:
            if plugin_info.absolute_import_path:
                log.debug(
                    "load cached plugin info: %s",
                    plugin_info.absolute_import_path,
                )
            else:
                log.debug(
                    "load cached plugin info: %s",
                    _show_me_your_name(plugin_info.cls),
                )
            cls.load_me_later(plugin_info)

        del CACHED_PLUGIN_INFO[cls.plugin_name]


def _register_a_plugin(plugin_info, plugin_cls):
    """module level function to register a plugin"""
    manager = PLUG_IN_MANAGERS.get(plugin_info.plugin_type)
    if manager:
        manager.register_a_plugin(plugin_cls, plugin_info)
    else:
        # let's cache it and wait the manager to be registered
        try:
            log.debug("caching %s", _show_me_your_name(plugin_cls.__name__))
        except AttributeError:
            log.debug("caching %s", _show_me_your_name(plugin_cls))
        CACHED_PLUGIN_INFO[plugin_info.plugin_type].append(plugin_info)


def _load_me_later(plugin_info):
    """ module level function to load a plugin later"""
    manager = PLUG_IN_MANAGERS.get(plugin_info.plugin_type)
    if manager:
        manager.load_me_later(plugin_info)
    else:
        # let's cache it and wait the manager to be registered
        log.debug(
            "caching %s for %s",
            plugin_info.absolute_import_path,
            plugin_info.plugin_type,
        )
        CACHED_PLUGIN_INFO[plugin_info.plugin_type].append(plugin_info)


def _get_me_pypi_package_name(module):
    try:
        module_name = module.__module__
        root_module_name = module_name.split(".")[0]
        return root_module_name.replace("_", "-")
    except AttributeError:
        return None


def _show_me_your_name(cls_func_or_data_type):
    try:
        return cls_func_or_data_type.__name__
    except AttributeError:
        return str(type(cls_func_or_data_type))
