import mmap

from pyexcel_io.book import _convert_content_to_stream
from pyexcel_io.readers.csv_sheet import CSVMemoryMapIterator
from pyexcel_io.readers.csv_in_memory import MemoryReader


class ContentReader(MemoryReader):
    def __init__(self, file_content, file_type, **keywords):
        file_stream = ContentReader.convert_content_to_stream(
            file_content, file_type, **keywords
        )
        super().__init__(file_stream, file_type, **keywords)

    @staticmethod
    def convert_content_to_stream(file_content, file_type, **keywords):
        encoding = keywords.get("encoding", "utf-8")
        if isinstance(file_content, mmap.mmap):
            # load from mmap
            file_stream = CSVMemoryMapIterator(file_content, encoding)
        else:
            if isinstance(file_content, bytes):
                file_content = file_content.decode(encoding)

            file_stream = _convert_content_to_stream(file_content, file_type)

        return file_stream
