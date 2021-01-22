"""
    pyexcel_io.writers
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    file writers

    :copyright: (c) 2014-2020 by Onni Software Ltd.
    :license: New BSD License, see LICENSE for more details
"""
from pyexcel_io.plugins import IOPluginInfoChainV2

IOPluginInfoChainV2(__name__).add_a_writer(
    relative_plugin_class_path="csv_in_file.CsvFileWriter",
    locations=["file", "content"],
    file_types=["csv", "tsv"],
    stream_type="text",
).add_a_writer(
    relative_plugin_class_path="csv_in_memory.CsvMemoryWriter",
    locations=["memory"],
    file_types=["csv", "tsv"],
    stream_type="text",
).add_a_writer(
    relative_plugin_class_path="csvz_writer.CsvZipWriter",
    locations=["memory", "file", "content"],
    file_types=["csvz", "tsvz"],
    stream_type="binary",
)
