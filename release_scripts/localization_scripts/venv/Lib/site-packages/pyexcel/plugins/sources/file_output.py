"""
    pyexcel.plugins.sources.file_output
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    Representation of output file sources

    :copyright: (c) 2015-2020 by Onni Software Ltd.
    :license: New BSD License
"""
from pyexcel.source import AbstractSource
from pyexcel.plugins import find_file_type_from_file_name
from pyexcel.internal import RENDERER


# pylint: disable=W0223
class WriteSheetToFile(AbstractSource):
    """Pick up 'file_name' field and do single sheet based read and write"""

    def __init__(self, file_name=None, renderer_library=None, **keywords):
        AbstractSource.__init__(self, **keywords)
        self._file_name = file_name

        if "force_file_type" in keywords:
            file_type = keywords.get("force_file_type")
        else:
            file_type = find_file_type_from_file_name(file_name, "write")

        self._renderer = RENDERER.get_a_plugin(file_type, renderer_library)

    def write_data(self, sheet):
        self._renderer.render_sheet_to_file(
            self._file_name, sheet, **self._keywords
        )


# pylint: disable=W0223
class WriteBookToFile(WriteSheetToFile):
    """Pick up 'file_name' field and do multiple sheet based read and write"""

    def write_data(self, book):
        self._renderer.render_book_to_file(
            self._file_name, book, **self._keywords
        )
