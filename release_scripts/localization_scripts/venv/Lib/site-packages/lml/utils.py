"""
    lml.utils
    ~~~~~~~~~~~~~~~~~~~

    json utils for dump plugin info class

    :copyright: (c) 2017-2020 by Onni Software Ltd.
    :license: New BSD License, see LICENSE for more details
"""
import sys
import logging
from json import JSONEncoder, dumps

PY2 = sys.version_info[0] == 2
log = logging.getLogger(__name__)


class PythonObjectEncoder(JSONEncoder):
    """
    Custom object encoder for json dump
    """

    def default(self, obj):
        a_list_of_types = (list, dict, str, int, float, bool, type(None))
        if PY2:
            a_list_of_types += (unicode,)
        if isinstance(obj, a_list_of_types):
            return JSONEncoder.default(self, obj)
        return {"_python_object": str(obj)}


def json_dumps(keywords):
    """
    Dump function keywords in json
    """
    return dumps(keywords, cls=PythonObjectEncoder)


def do_import(plugin_module_name):
    """dynamically import a module"""
    try:
        return _do_import(plugin_module_name)
    except ImportError:
        log.exception("%s is absent or cannot be imported", plugin_module_name)
        raise


def _do_import(plugin_module_name):
    plugin_module = __import__(plugin_module_name)
    if "." in plugin_module_name:
        modules = plugin_module_name.split(".")
        for module in modules[1:]:
            plugin_module = getattr(plugin_module, module)
    log.debug("found " + plugin_module_name)
    return plugin_module


def do_import_class(plugin_class):
    """dynamically import a class"""
    try:
        plugin_module_name = plugin_class.rsplit(".", 1)[0]
        plugin_module = __import__(plugin_module_name)
        modules = plugin_class.split(".")
        for module in modules[1:]:
            plugin_module = getattr(plugin_module, module)
        return plugin_module
    except ImportError:
        log.exception("Failed to import %s", plugin_module_name)
        raise
