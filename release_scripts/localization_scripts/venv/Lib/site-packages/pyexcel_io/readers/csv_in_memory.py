import re

import pyexcel_io._compact as compact
from pyexcel_io import constants
from pyexcel_io.sheet import NamedContent
from pyexcel_io.plugin_api import IReader
from pyexcel_io.readers.csv_sheet import CSVinMemoryReader

DEFAULT_SHEET_SEPARATOR_FORMATTER = f"---{constants.DEFAULT_NAME}---%s"


class MemoryReader(IReader):
    def __init__(
        self, file_stream, file_type, multiple_sheets=False, **keywords
    ):
        """Load content from memory
        :params stream file_content: the actual file content in memory
        :returns: a book
        """
        self.handles = []
        self.keywords = keywords
        if file_type == constants.FILE_FORMAT_TSV:
            self.keywords["dialect"] = constants.KEYWORD_TSV_DIALECT
        self.file_type = file_type

        self.__load_from_memory_flag = True
        self.__line_terminator = keywords.get(
            constants.KEYWORD_LINE_TERMINATOR, constants.DEFAULT_CSV_NEWLINE
        )
        separator = DEFAULT_SHEET_SEPARATOR_FORMATTER % self.__line_terminator
        if multiple_sheets:
            # will be slow for large files
            file_stream.seek(0)
            content = file_stream.read()
            sheets = content.split(separator)
            named_contents = []
            for sheet in sheets:
                if sheet == "":  # skip empty named sheet
                    continue

                lines = sheet.split(self.__line_terminator)
                result = re.match(constants.SEPARATOR_MATCHER, lines[0])
                new_content = "\n".join(lines[1:])
                new_sheet = NamedContent(
                    result.group(1), compact.StringIO(new_content)
                )
                named_contents.append(new_sheet)
            self.content_array = named_contents

        else:
            if hasattr(file_stream, "seek"):
                file_stream.seek(0)
            self.content_array = [NamedContent(self.file_type, file_stream)]

    def read_sheet(self, index):
        reader = CSVinMemoryReader(self.content_array[index], **self.keywords)
        self.handles.append(reader)
        return reader

    def close(self):
        for reader in self.handles:
            reader.close()
