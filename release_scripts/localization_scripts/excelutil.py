"""Provides tools for parsing and writing to a csv file.
"""
from typing import List, Dict, OrderedDict
import pyexcel

Workbook = OrderedDict[str, List[List[str]]]


def records_to_excel(output_path: str, workbook: Workbook):
    """Writes workbook data model to an excel workbook.

    Args:
        output_path (str): The path where the csv file will be written.
        workbook (Workbook): The worksheet to be written.  The dictionary corresponds to sheet name and sheet contents.
                             Each row of the contents of strings will be written according to their index (i.e. column 3
                              will be index 2).
    """

    pyexcel.save_book_as(
        bookdict=workbook,
        dest_file_name=output_path
    )


def csv_to_records(input_path: str) -> Workbook:
    """Reads rows to a csv file at the specified path.

    Args:
        input_path (str): The path where the csv file will be written.
    """

    return pyexcel.get_book_dict(
        file_name=input_path
    )