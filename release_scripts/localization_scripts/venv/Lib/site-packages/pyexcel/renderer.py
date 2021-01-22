"""
    pyexcel.renderer
    ~~~~~~~~~~~~~~~~~~~

    Renders pyexcel.Book and pyexcel.Sheet to any format

    :copyright: (c) 2015-2020 by Onni Software Ltd.
    :license: New BSD License
"""
from pyexcel import _compact as compact


class AbstractRenderer(object):
    """
    Close some functions that will not be used
    """

    WRITE_FLAG = "w"

    def __init__(self, file_type):
        self._file_type = file_type
        self._stream = None
        self._write_title = True

    def get_io(self):
        """
        If your renderer's output is binary, please override it and
        return BytesIO instead
        """
        raise NotImplementedError("No io for this renderer")

    def render_sheet_to_file(self, file_name, sheet, **keywords):
        """Render a sheet to a physical file

        :param file_name: the output file name
        :param sheet: pyexcel sheet instance to be rendered
        :param write_title: to write sheet name
        :param keywords: any other keywords to the renderer
        """
        raise NotImplementedError("We are not writing to file")

    def render_sheet_to_stream(self, file_stream, sheet, **keywords):
        """Render a sheet to a file stream

        :param file_stream: the output file stream
        :param sheet: pyexcel sheet instance to be rendered
        :param write_title: to write sheet name
        :param keywords: any other keywords to the renderer
        """
        raise NotImplementedError("We are not writing to file")

    def render_book_to_file(self, file_name, book, **keywords):
        """Render a book to a physical file

        :param file_name: the output file name
        :param book: pyexcel book instance to be rendered
        :param write_title: to write sheet names
        :param keywords: any other keywords to the renderer
        """
        raise NotImplementedError("We are not writing to file")

    def render_book_to_stream(self, file_stream, book, **keywords):
        """Render a book to a file stream

        :param file_stream: the output file stream
        :param book: pyexcel book instance to be rendered
        :param write_title: to write sheet names
        :param keywords: any other keywords to the renderer
        """
        raise NotImplementedError("We are not writing to file")


class Renderer(AbstractRenderer):
    """
    Render pyexcel sheet or book into excel format as any formats
    """

    def get_io(self):
        return compact.StringIO()

    def render_sheet_to_file(
        self, file_name, sheet, write_title=True, **keywords
    ):
        self.set_write_title(write_title)
        with open(file_name, self.WRITE_FLAG) as outfile:
            self.set_output_stream(outfile)
            self.render_sheet(sheet, **keywords)

    def render_sheet_to_stream(
        self, file_stream, sheet, write_title=True, **keywords
    ):
        self.set_write_title(write_title)
        self.set_output_stream(file_stream)
        self.render_sheet(sheet, **keywords)

    def render_book_to_file(
        self, file_name, book, write_title=True, **keywords
    ):
        self.set_write_title(write_title)
        with open(file_name, self.WRITE_FLAG) as outfile:
            self.set_output_stream(outfile)
            self.render_book(book, **keywords)

    def render_book_to_stream(
        self, file_stream, book, write_title=True, **keywords
    ):
        self.set_write_title(write_title)
        self.set_output_stream(file_stream)
        self.render_book(book, **keywords)

    def render_sheet(self, sheet, **keywords):
        """
        If your renderer is kind of text format, you just
        need to implement this function.

        :param sheet: pyexcel sheet instance to be rendered
        :param keywords: any other keywords to the renderer
        """
        raise NotImplementedError("Please render sheet")

    def render_book(self, book, **keywords):
        """
        Implementation of book rendering

        :param book: pyexcel book instance to be rendered
        :param keywords: any other keywords to the renderer
        """
        number_of_sheets = book.number_of_sheets() - 1
        for index, sheet in enumerate(book):
            self.render_sheet(sheet)
            if index < number_of_sheets:
                self._stream.write("\n")

    def set_output_stream(self, stream):
        """update internal stream"""
        self._stream = stream

    def set_write_title(self, flag):
        """update write title flag"""
        self._write_title = flag


class BinaryRenderer(Renderer):
    """
    Renderer pyexcel data into a binary object
    """

    def __init__(self, file_type):
        Renderer.__init__(self, file_type)
        if compact.PY3_AND_ABOVE:
            self.WRITE_FLAG = "wb"

    def get_io(self):
        io = compact.BytesIO()
        return io


# pylint: disable=W0223
class DbRenderer(AbstractRenderer):
    """
    Close some functions that will not be used
    """

    def get_io(self):
        raise Exception("No io for this renderer")

    def render_sheet_to_file(self, file_name, sheet, **keywords):
        raise Exception("We are not writing to file")

    def render_book_to_file(self, file_name, book, **keywords):
        raise Exception("We are not writing to file")
