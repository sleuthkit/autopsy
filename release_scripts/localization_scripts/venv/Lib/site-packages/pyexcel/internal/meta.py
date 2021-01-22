"""
    pyexcel.internal.meta
    ~~~~~~~~~~~~~~~~~~~~~~

    Annotate sheet and book class' attributes

    :copyright: (c) 2015-2020 by Onni Software Ltd.
    :license: New BSD License
"""
import sys
from functools import partial

from pyexcel import constants as constants
from pyexcel import docstrings as docs
from pyexcel._compact import PY2, append_doc
from pyexcel.internal import SOURCE
from pyexcel.internal.core import save_book, save_sheet, get_sheet_stream
from pyexcel.internal.utils import make_a_property


def make_presenter(source_getter, attribute=None):
    """make a custom presentation method for each file types"""

    def custom_presenter(self, **keywords):
        """docstring is assigned a few lines down the line"""
        keyword = SOURCE.get_keyword_for_parameter(attribute)
        keywords[keyword] = attribute
        memory_source = source_getter(**keywords)
        memory_source.write_data(self)
        try:
            content_stream = memory_source.get_content()
            content = content_stream.getvalue()
        except AttributeError:
            # python 3 _io.TextWrapper
            content = None

        return content

    custom_presenter.__doc__ = "Get data in %s format" % attribute
    return custom_presenter


def sheet_presenter(attribute=None):
    """make a custom presentation method for sheet"""
    source_getter = SOURCE.get_writable_source
    return make_presenter(source_getter, attribute)


def book_presenter(attribute=None):
    """make a custom presentation method for book"""
    source_getter = SOURCE.get_writable_book_source
    return make_presenter(source_getter, attribute)


def importer(attribute=None):
    """make a custom input method for sheet"""

    def custom_importer1(self, content, **keywords):
        """docstring is assigned a few lines down the line"""
        sheet_params = {}
        for field in constants.VALID_SHEET_PARAMETERS:
            if field in keywords:
                sheet_params[field] = keywords.pop(field)
        keyword = SOURCE.get_keyword_for_parameter(attribute)
        if keyword == "file_type":
            keywords[keyword] = attribute
            keywords["file_content"] = content
        else:
            keywords[keyword] = content
        named_content = get_sheet_stream(**keywords)
        self.init(named_content.payload, named_content.name, **sheet_params)

    custom_importer1.__doc__ = "Set data in %s format" % attribute
    return custom_importer1


def book_importer(attribute=None):
    """make a custom input method for book"""

    def custom_book_importer(self, content, **keywords):
        """docstring is assigned a few lines down the line"""
        keyword = SOURCE.get_keyword_for_parameter(attribute)
        if keyword == "file_type":
            keywords[keyword] = attribute
            keywords["file_content"] = content
        else:
            keywords[keyword] = content
        sheets, filename, path = _get_book(**keywords)
        self.init(sheets=sheets, filename=filename, path=path)

    custom_book_importer.__doc__ = "Set data in %s format" % attribute
    return custom_book_importer


def attribute(
    cls,
    file_type,
    instance_name="Sheet",
    description=constants.OUT_FILE_TYPE_DOC_STRING,
    **keywords
):
    """
    create custom attributes for each class
    """
    doc_string = description.format(file_type, instance_name)
    make_a_property(cls, file_type, doc_string, **keywords)


REGISTER_PRESENTATION = partial(
    attribute,
    getter_func=sheet_presenter,
    description=constants.OUT_FILE_TYPE_DOC_STRING,
)
REGISTER_BOOK_PRESENTATION = partial(
    attribute,
    getter_func=book_presenter,
    instance_name="Book",
    description=constants.OUT_FILE_TYPE_DOC_STRING,
)
REGISTER_INPUT = partial(
    attribute,
    setter_func=importer,
    description=constants.IN_FILE_TYPE_DOC_STRING,
)
REGISTER_BOOK_INPUT = partial(
    attribute,
    instance_name="Book",
    setter_func=book_importer,
    description=constants.IN_FILE_TYPE_DOC_STRING,
)
REGISTER_IO = partial(
    attribute,
    getter_func=sheet_presenter,
    setter_func=importer,
    description=constants.IO_FILE_TYPE_DOC_STRING,
)
REGISTER_BOOK_IO = partial(
    attribute,
    getter_func=book_presenter,
    setter_func=book_importer,
    instance_name="Book",
    description=constants.IO_FILE_TYPE_DOC_STRING,
)


class StreamAttribute(object):
    """Provide access to get_*_stream methods"""

    def __init__(self, cls):
        self.cls = cls

    def __getattr__(self, name):
        getter = getattr(self.cls, "save_to_memory")
        return getter(file_type=name)


class PyexcelObject(object):
    """parent class for pyexcel.Sheet and pyexcel.Book"""

    @property
    def stream(self):
        """Return a stream in which the content is properly encoded

        Example::

            >>> import pyexcel as p
            >>> b = p.get_book(bookdict={"A": [[1]]})
            >>> csv_stream = b.stream.texttable
            >>> print(csv_stream.getvalue())
            A:
            +---+
            | 1 |
            +---+

        Where b.stream.xls.getvalue() is equivalent to b.xls. In some situation
        b.stream.xls is prefered than b.xls.

        Sheet examples::

            >>> import pyexcel as p
            >>> s = p.Sheet([[1]], 'A')
            >>> csv_stream = s.stream.texttable
            >>> print(csv_stream.getvalue())
            A:
            +---+
            | 1 |
            +---+

        Where s.stream.xls.getvalue() is equivalent to s.xls. In some situation
        s.stream.xls is prefered than s.xls.

        It is similar to :meth:`~pyexcel.Book.save_to_memory`.
        """
        return StreamAttribute(self)

    def save_to_memory(self, file_type, **keywords):
        """Save the content to memory

        :param file_type: any value of 'csv', 'tsv', 'csvz',
                          'tsvz', 'xls', 'xlsm', 'xlsm', 'ods'
        :param stream: the memory stream to be written to. Note in
                       Python 3, for csv  and tsv format, please
                       pass an instance of StringIO. For xls, xlsx,
                       and ods, an instance of BytesIO.
        """
        raise NotImplementedError("save to memory is not implemented")

    def plot(self, file_type="svg", **keywords):
        """
        Visualize the data

        Parameters:
        -----------------

        file_type:string
           'svg' by default. 'png', 'jpeg' possible depending on plugins

        chart_type:string
           'bar' by default. other chart types are subjected to plugins.
        """
        memory_content = self.save_to_memory(file_type, **keywords)
        if file_type in ["png", "svg", "jpeg"]:
            # make the signature for jypter notebook
            def get_content(self):
                return self.getvalue().decode("utf-8")

            setattr(
                memory_content,
                "_repr_%s_" % file_type,
                partial(get_content, memory_content),
            )
        return memory_content

    def _repr_html_(self):
        return self.html

    def __repr__(self):
        if PY2:
            default_encoding = sys.getdefaultencoding()
            if default_encoding == "ascii":
                result = self.texttable
                return result.encode("utf-8")

        return self.texttable

    def __str__(self):
        return self.__repr__()


class SheetMeta(PyexcelObject):
    """Annotate sheet attributes"""

    register_io = classmethod(REGISTER_IO)
    register_presentation = classmethod(REGISTER_PRESENTATION)
    register_input = classmethod(REGISTER_INPUT)

    @append_doc(docs.SAVE_AS_OPTIONS)
    def save_as(self, filename, **keywords):
        """Save the content to a named file"""
        return save_sheet(self, file_name=filename, **keywords)

    def save_to_memory(self, file_type, stream=None, **keywords):
        stream = save_sheet(
            self, file_type=file_type, file_stream=stream, **keywords
        )
        return stream

    def save_to_django_model(
        self, model, initializer=None, mapdict=None, batch_size=None
    ):
        """Save to database table through django model

        :param model: a database model
        :param initializer: a initialization functions for your model
        :param mapdict: custom map dictionary for your data columns
        :param batch_size: a parameter to Django concerning the size
                           for bulk insertion
        """
        save_sheet(
            self,
            model=model,
            initializer=initializer,
            mapdict=mapdict,
            batch_size=batch_size,
        )

    def save_to_database(
        self, session, table, initializer=None, mapdict=None, auto_commit=True
    ):
        """Save data in sheet to database table

        :param session: database session
        :param table: a database table
        :param initializer: a initialization functions for your table
        :param mapdict: custom map dictionary for your data columns
        :param auto_commit: by default, data is auto committed.

        """
        save_sheet(
            self,
            session=session,
            table=table,
            initializer=initializer,
            mapdict=mapdict,
            auto_commit=auto_commit,
        )


class BookMeta(PyexcelObject):
    """Annotate book attributes"""

    register_io = classmethod(REGISTER_BOOK_IO)
    register_presentation = classmethod(REGISTER_BOOK_PRESENTATION)
    register_input = classmethod(REGISTER_BOOK_INPUT)

    @append_doc(docs.SAVE_AS_OPTIONS)
    def save_as(self, filename, **keywords):
        """
        Save the content to a new file
        """
        return save_book(self, file_name=filename, **keywords)

    def save_to_memory(self, file_type, stream=None, **keywords):
        """
        Save the content to a memory stream

        :param file_type: what format the stream is in
        :param stream: a memory stream.  Note in Python 3, for csv and tsv
                       format, please pass an instance of StringIO. For xls,
                       xlsx, and ods, an instance of BytesIO.
        """
        stream = save_book(
            self, file_type=file_type, file_stream=stream, **keywords
        )
        return stream

    def save_to_django_models(
        self, models, initializers=None, mapdicts=None, **keywords
    ):
        """
        Save to database table through django model

        :param models: a list of database models, that is accepted by
                       :meth:`Sheet.save_to_django_model`. The sequence
                       of tables matters when there is dependencies in
                       between the tables. For example, **Car** is made
                       by **Car Maker**. **Car Maker** table should be
                       specified before **Car** table.
        :param initializers: a list of intialization functions for your
                             tables and the sequence should match tables,
        :param mapdicts: custom map dictionary for your data columns
                         and the sequence should match tables

        optional parameters:
        :param batch_size: django bulk_create batch size
        :param bulk_save: whether to use bulk_create or to use single save
                          per record
        """
        save_book(
            self,
            models=models,
            initializers=initializers,
            mapdicts=mapdicts,
            **keywords
        )

    def save_to_database(
        self,
        session,
        tables,
        initializers=None,
        mapdicts=None,
        auto_commit=True,
    ):
        """
        Save data in sheets to database tables

        :param session: database session
        :param tables: a list of database tables, that is accepted by
                       :meth:`Sheet.save_to_database`. The sequence of tables
                       matters when there is dependencies in between the
                       tables. For example, **Car** is made by **Car Maker**.
                       **Car Maker** table should
                       be specified before **Car** table.
        :param initializers: a list of intialization functions for your
                             tables and the sequence should match tables,
        :param mapdicts: custom map dictionary for your data columns
                         and the sequence should match tables
        :param auto_commit: by default, data is committed.

        """
        save_book(
            self,
            session=session,
            tables=tables,
            initializers=initializers,
            mapdicts=mapdicts,
            auto_commit=auto_commit,
        )


def _get_book(**keywords):
    """Get an instance of :class:`Book` from an excel source

    Where the dictionary should have text as keys and two dimensional
    array as values.
    """
    a_source = SOURCE.get_book_source(**keywords)
    sheets = a_source.get_data()
    filename, path = a_source.get_source_info()
    return sheets, filename, path
