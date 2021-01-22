"""
    pyexcel.constants
    ~~~~~~~~~~~~~~~~~~~

    Constants appeared in pyexcel

    :copyright: (c) 2015-2020 by Onni Software Ltd.
    :license: New BSD License
"""
# flake8: noqa
DEFAULT_NA = ""
DEFAULT_NAME = "pyexcel sheet"
DEFAULT_SHEET_NAME = "pyexcel_sheet1"
DEFAULT_NO_DATA = "<No data>"

MESSAGE_WARNING = "We do not overwrite files"
MESSAGE_WRITE_ERROR = "Cannot write sheet"
MESSAGE_ERROR_02 = "No valid parameters found!"
MESSAGE_DATA_ERROR_NO_SERIES = "No column names or row names found"
MESSAGE_DATA_ERROR_EMPTY_COLUMN_LIST = (
    "Column list is empty. Do not waste resource"
)
MESSAGE_DATA_ERROR_COLUMN_LIST_INTEGER_TYPE = (
    "Column list should be a list of integers"
)
MESSAGE_DATA_ERROR_COLUMN_LIST_STRING_TYPE = (
    "Column list should be a list of integers"
)
MESSAGE_INDEX_OUT_OF_RANGE = "Index out of range"
MESSAGE_DATA_ERROR_EMPTY_CONTENT = "Nothing to be pasted!"
MESSAGE_DATA_ERROR_DATA_TYPE_MISMATCH = "Data type mismatch"
MESSAGE_DATA_ERROR_ORDEREDDICT_IS_EXPECTED = "Please give a ordered list"

MESSAGE_DEPRECATED_ROW_COLUMN = "Deprecated usage. Please use [row, column]"
MESSAGE_DEPRECATED_OUT_FILE = (
    "Depreciated usage of 'out_file'. please use dest_file_name"
)
MESSAGE_DEPRECATED_CONTENT = (
    "Depreciated usage of 'content'. please use file_content"
)

MESSAGE_NOT_IMPLEMENTED_01 = (
    "Please use attribute row or column to extend sheet"
)
MESSAGE_NOT_IMPLEMENTED_02 = (
    "Confused! What do you want to put as column names"
)
MESSAGE_READONLY = "This attribute is readonly"
MESSAGE_ERROR_NO_HANDLER = "No suitable plugins imported or installed"
MESSAGE_UNKNOWN_IO_OPERATION = "Internal error: an illegal source action"
MESSAGE_UPGRADE = "Please upgrade the plugin '%s' according to \
plugin compactibility table."


_IMPLEMENTATION_REMOVED = "Deprecated since 0.3.0! Implementation removed"
IO_FILE_TYPE_DOC_STRING = """
Get/Set data in/from {0} format

You could obtain content in {0} format by dot notation::

    {1}.{0}

And you could as well set content by dot notation::

    {1}.{0} = the_io_stream_in_{0}_format

if you need to pass on more parameters, you could use::

    {1}.get_{0}(**keywords)
    {1}.set_{0}(the_io_stream_in_{0}_format, **keywords)
"""
OUT_FILE_TYPE_DOC_STRING = """
Get data in {0} format

You could obtain content in {0} format by dot notation::

    {1}.{0}

if you need to pass on more parameters, you could use::

    {1}.get_{0}(**keywords)
"""
IN_FILE_TYPE_DOC_STRING = """
Set data in {0} format

You could set content in {0} format by dot notation::

    {1}.{0}

if you need to pass on more parameters, you could use::

    {1}.set_{0}(the_io_stream_in_{0}_format, **keywords)
"""
VALID_SHEET_PARAMETERS = [
    "name_columns_by_row",
    "name_rows_by_column",
    "colnames",
    "rownames",
    "transpose_before",
    "transpose_after",
]


# for sources
# targets
SOURCE = "source"
SHEET = "sheet"
BOOK = "book"

# actions
READ_ACTION = "read"
WRITE_ACTION = "write"
RW_ACTION = "read-write"
FILE_TYPE_NOT_SUPPORTED_FMT = "File type '%s' is not supported for %s."
