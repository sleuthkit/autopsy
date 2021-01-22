"""
    pyexcel.plugins.sources
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    A list of built-in sources

    :copyright: (c) 2015-2020 by Onni Software Ltd.
    :license: New BSD License
"""
from pyexcel.plugins import PyexcelPluginChain
from pyexcel.internal import PARSER, RENDERER

PyexcelPluginChain(__name__).add_a_source(
    relative_plugin_class_path="http.HttpSource",
    fields=["url"],
    targets=["sheet", "book"],
    actions=["read"],
    attributes=["url"],
    key="url",
).add_a_source(
    relative_plugin_class_path="sheet_stream_source.SheetStreamSource",
    fields=["sheet_stream"],
    targets=["sheet"],
    attributes=[],
    actions=["read"],
).add_an_input_source(
    relative_plugin_class_path="file_input.ReadExcelFromFile",
    fields=["file_name"],
    targets=["sheet", "book"],
    attributes=[],
    actions=["read"],
).add_an_input_source(
    relative_plugin_class_path="memory_input.ReadExcelFileMemory",
    fields=["file_type"],
    targets=["sheet", "book"],
    actions=["read"],
    key="file_type",
    attributes=PARSER.get_all_file_types,
).add_a_output_source(
    relative_plugin_class_path="file_output.WriteSheetToFile",
    fields=["file_name"],
    targets=["sheet"],
    attributes=[],
    actions=["write"],
).add_a_output_source(
    relative_plugin_class_path="file_output.WriteBookToFile",
    fields=["file_name"],
    targets=["book"],
    attributes=[],
    actions=["write"],
).add_a_output_source(
    relative_plugin_class_path="output_to_memory.WriteSheetToMemory",
    fields=["file_type"],
    targets=["sheet"],
    actions=["write"],
    key="file_type",
    attributes=RENDERER.get_all_file_types,
).add_a_output_source(
    relative_plugin_class_path="output_to_memory.WriteBookToMemory",
    fields=["file_type"],
    targets=["book"],
    actions=["write"],
    key="file_type",
    attributes=RENDERER.get_all_file_types,
).add_a_source(
    relative_plugin_class_path="pydata.bookdict.BookDictSource",
    fields=["bookdict"],
    targets=["sheet", "book"],
    actions=["write", "read"],
    key="bookdict",
    attributes=["bookdict"],
).add_a_source(
    relative_plugin_class_path="pydata.dictsource.DictSource",
    fields=["adict"],
    targets=["sheet", "book"],
    actions=["write", "read"],
    key="adict",
    attributes=["dict"],
).add_a_source(
    relative_plugin_class_path="pydata.arraysource.ArraySource",
    fields=["array"],
    targets=["sheet", "book"],
    actions=["write", "read"],
    key="array",
    attributes=["array"],
).add_a_source(
    relative_plugin_class_path="pydata.records.RecordsSource",
    fields=["records"],
    targets=["sheet", "book"],
    actions=["write", "read"],
    key="records",
    attributes=["records"],
).add_a_source(
    relative_plugin_class_path="django.SheetDjangoSource",
    fields=["model"],
    targets=["sheet"],
    actions=["write", "read"],
).add_a_source(
    relative_plugin_class_path="django.BookDjangoSource",
    fields=["models"],
    targets=["book"],
    actions=["write", "read"],
).add_a_source(
    relative_plugin_class_path="sqlalchemy.SheetSQLAlchemySource",
    fields=["session", "table"],
    targets=["sheet"],
    actions=["write", "read"],
).add_a_source(
    relative_plugin_class_path="sqlalchemy.BookSQLSource",
    fields=["session", "tables"],
    targets=["book"],
    actions=["write", "read"],
).add_a_source(
    relative_plugin_class_path="querysets.SheetQuerySetSource",
    fields=["column_names", "query_sets"],
    targets=["sheet"],
    actions=["read"],
)
