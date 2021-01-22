from pyexcel_io.sheet import SheetReader
from pyexcel_io.plugins import NEW_READERS
from pyexcel_io._compact import OrderedDict


def clean_keywords(keywords):
    sheet_keywords = {}
    native_sheet_keywords = {}
    args_list = [
        "start_row",
        "row_limit",
        "start_column",
        "column_limit",
        "skip_column_func",
        "skip_row_func",
        "skip_empty_rows",
        "row_renderer",
        "keep_trailing_empty_cells",
    ]
    for arg in keywords:
        if arg in args_list:
            sheet_keywords[arg] = keywords[arg]
        else:
            native_sheet_keywords[arg] = keywords[arg]
    return sheet_keywords, native_sheet_keywords


class Reader(object):
    def __init__(self, file_type, library=None):
        self.file_type = file_type
        self.library = library
        self.keywords = None

        # if you know which reader class to use, this attribute allows
        # you to set reader class externally. Since there is no
        # so call private field in Python, I am not going to create
        # useless setter and getter functions like Java.
        # in pyexcel, this attribute is mainly used for testing
        self.reader_class = None

    def open(self, file_name, **keywords):
        if self.reader_class is None:
            self.reader_class = NEW_READERS.get_a_plugin(
                self.file_type, location="file", library=self.library
            )
        self.keywords, native_sheet_keywords = clean_keywords(keywords)
        self.reader = self.reader_class(
            file_name, self.file_type, **native_sheet_keywords
        )
        return self.reader

    def open_content(self, file_content, **keywords):
        self.keywords, native_sheet_keywords = clean_keywords(keywords)
        if self.reader_class is None:
            self.reader_class = NEW_READERS.get_a_plugin(
                self.file_type, location="content", library=self.library
            )
        self.reader = self.reader_class(
            file_content, self.file_type, **native_sheet_keywords
        )
        return self.reader

    def open_stream(self, file_stream, **keywords):
        self.keywords, native_sheet_keywords = clean_keywords(keywords)
        if self.reader_class is None:
            self.reader_class = NEW_READERS.get_a_plugin(
                self.file_type, location="memory", library=self.library
            )
        self.reader = self.reader_class(
            file_stream, self.file_type, **native_sheet_keywords
        )
        return self.reader

    def read_sheet_by_name(self, sheet_name):
        """
        read a named sheet from a excel data book
        """
        sheet_names = self.reader.sheet_names()
        index = sheet_names.index(sheet_name)

        return self.read_sheet_by_index(index)

    def read_sheet_by_index(self, sheet_index):
        sheet_reader = self.reader.read_sheet(sheet_index)
        sheet_names = self.reader.sheet_names()
        sheet = EncapsulatedSheetReader(sheet_reader, **self.keywords)
        return {sheet_names[sheet_index]: sheet.to_array()}

    def read_all(self):
        """
        read everything from a excel data book
        """
        result = OrderedDict()
        for sheet_index in range(len(self.reader)):
            content_dict = self.read_sheet_by_index(sheet_index)
            result.update(content_dict)
        return result

    def read_many(self, sheets):
        """
        read everything from a excel data book
        """
        result = OrderedDict()
        for sheet in sheets:
            if isinstance(sheet, int):
                result.update(self.read_sheet_by_index(sheet))
            else:
                result.update(self.read_sheet_by_name(sheet))
        return result

    def close(self):
        return self.reader.close()

    def __enter__(self):
        return self

    def __exit__(self, a_type, value, traceback):
        self.close()


class EncapsulatedSheetReader(SheetReader):
    def row_iterator(self):
        yield from self._native_sheet.row_iterator()

    def column_iterator(self, row):
        yield from self._native_sheet.column_iterator(row)
