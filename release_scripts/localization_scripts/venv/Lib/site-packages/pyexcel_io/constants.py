"""
    pyexcel_io.constants
    ~~~~~~~~~~~~~~~~~~~

    Constants appeared in pyexcel

    :copyright: (c) 2014-2020 by Onni Software Ltd.
    :license: New BSD License
"""
# flake8: noqa
DEFAULT_NAME = "pyexcel"
DEFAULT_SHEET_NAME = "%s_sheet1" % DEFAULT_NAME
DEFAULT_PLUGIN_NAME = "__%s_io_plugins__" % DEFAULT_NAME

MESSAGE_INVALID_PARAMETERS = "Invalid parameters"
MESSAGE_ERROR_02 = "No content, file name. Nothing is given"
MESSAGE_ERROR_03 = "cannot handle unknown content"
MESSAGE_WRONG_IO_INSTANCE = "Wrong io instance is passed for your file format."
MESSAGE_FILE_NAME_SHOULD_BE_STRING = "file_name should be a string"
MESSAGE_CANNOT_WRITE_STREAM_FORMATTER = (
    "Cannot write content of file type %s to stream"
)
MESSAGE_CANNOT_READ_STREAM_FORMATTER = (
    "Cannot read content of file type %s from stream"
)
MESSAGE_CANNOT_WRITE_FILE_TYPE_FORMATTER = (
    "Cannot write content of file type %s to file %s"
)
MESSAGE_CANNOT_READ_FILE_TYPE_FORMATTER = (
    "Cannot read content of file type %s from file %s"
)
MESSAGE_LOADING_FORMATTER = (
    "The plugin for file type %s is not installed. Please install %s"
)
MESSAGE_NOT_FILE_FORMATTER = "%s is not a file"
MESSAGE_FILE_DOES_NOT_EXIST = "%s does not exist"
MESSAGE_EMPTY_ARRAY = "One empty row is found"
MESSAGE_IGNORE_ROW = "One row is ignored"
MESSAGE_DB_EXCEPTION = """
Warning: Bulk insertion got below exception. Trying to do it one by one slowly."""

FILE_FORMAT_CSV = "csv"
FILE_FORMAT_TSV = "tsv"
FILE_FORMAT_CSVZ = "csvz"
FILE_FORMAT_TSVZ = "tsvz"
FILE_FORMAT_ODS = "ods"
FILE_FORMAT_XLS = "xls"
FILE_FORMAT_XLSX = "xlsx"
FILE_FORMAT_XLSM = "xlsm"
FILE_FORMAT_XLSB = "xlsb"
FILE_FORMAT_HTML = "html"
FILE_FORMAT_PDF = "pdf"
DB_SQL = "sql"
DB_DJANGO = "django"
DB_QUERYSET = "queryset"
KEYWORD_TSV_DIALECT = "excel-tab"
KEYWORD_LINE_TERMINATOR = "lineterminator"

SKIP_DATA = -1
TAKE_DATA = 0
STOP_ITERATION = 1


DEFAULT_MULTI_CSV_SEPARATOR = "__"
SEPARATOR_FORMATTER = "---%s---" % DEFAULT_NAME + "%s"
SEPARATOR_MATCHER = "---%s:(.*)---" % DEFAULT_NAME
DEFAULT_CSV_STREAM_FILE_FORMATTER = "---%s:" % DEFAULT_NAME + "%s---%s"
DEFAULT_CSV_NEWLINE = "\r\n"

MAX_INTEGER = 999999999999999
