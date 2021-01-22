"""
    pyexcel.plugins.parsers
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    A list of built-in parsers

    :copyright: (c) 2015-2020 by Onni Software Ltd.
    :license: New BSD License
"""
from pyexcel.plugins import PyexcelPluginChain

from pyexcel_io.plugins import READERS
from pyexcel_io.constants import DB_SQL, DB_DJANGO

PyexcelPluginChain(__name__).add_a_parser(
    relative_plugin_class_path="excel.ExcelParser",
    file_types=READERS.get_all_formats(),
).add_a_parser(
    relative_plugin_class_path="sqlalchemy.SQLAlchemyExporter",
    file_types=[DB_SQL],
).add_a_parser(
    relative_plugin_class_path="django.DjangoExporter", file_types=[DB_DJANGO]
)
