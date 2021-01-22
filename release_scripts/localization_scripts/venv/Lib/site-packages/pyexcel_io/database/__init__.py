"""
    pyexcel_io.database
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    database data importer and exporter

    :copyright: (c) 2014-2020 by Onni Software Ltd.
    :license: New BSD License, see LICENSE for more details
"""
from pyexcel_io.plugins import IOPluginInfoChainV2
from pyexcel_io.constants import DB_SQL, DB_DJANGO, DB_QUERYSET

IOPluginInfoChainV2(__name__).add_a_reader(
    relative_plugin_class_path="exporters.queryset.QueryReader",
    locations=["file", "memory", "content"],
    file_types=[DB_QUERYSET],
).add_a_reader(
    relative_plugin_class_path="exporters.django.DjangoBookReader",
    locations=["file", "memory", "content"],
    file_types=[DB_DJANGO],
).add_a_writer(
    relative_plugin_class_path="importers.django.DjangoBookWriter",
    locations=["file", "content", "memory"],
    file_types=[DB_DJANGO],
).add_a_reader(
    relative_plugin_class_path="exporters.sqlalchemy.SQLBookReader",
    locations=["file", "memory", "content"],
    file_types=[DB_SQL],
).add_a_writer(
    relative_plugin_class_path="importers.sqlalchemy.SQLBookWriter",
    locations=["file", "content", "memory"],
    file_types=[DB_SQL],
)
