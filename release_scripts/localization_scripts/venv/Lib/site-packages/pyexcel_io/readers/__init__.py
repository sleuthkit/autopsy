"""
    pyexcel_io.readers
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    file readers

    :copyright: (c) 2014-2020 by Onni Software Ltd.
    :license: New BSD License, see LICENSE for more details
"""
from pyexcel_io.plugins import IOPluginInfoChainV2

IOPluginInfoChainV2(__name__).add_a_reader(
    relative_plugin_class_path="csv_in_file.FileReader",
    locations=["file"],
    file_types=["csv", "tsv"],
    stream_type="text",
).add_a_reader(
    relative_plugin_class_path="csv_content.ContentReader",
    locations=["content"],
    file_types=["csv", "tsv"],
    stream_type="text",
).add_a_reader(
    relative_plugin_class_path="csv_in_memory.MemoryReader",
    locations=["memory"],
    file_types=["csv", "tsv"],
    stream_type="text",
).add_a_reader(
    relative_plugin_class_path="csvz.FileReader",
    file_types=["csvz", "tsvz"],
    locations=["file", "memory"],
    stream_type="binary",
).add_a_reader(
    relative_plugin_class_path="csvz.ContentReader",
    file_types=["csvz", "tsvz"],
    locations=["content"],
    stream_type="binary",
)
