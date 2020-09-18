"""Provides tools for parsing and writing to a csv file.
"""
import collections
from typing import List, OrderedDict
import pyexcel

from outputresult import OutputResult

Workbook = OrderedDict[str, List[List[str]]]

# The name for the results sheet
RESULTS_SHEET_NAME = 'results'

# The name for the sheet of deleted items
DELETED_SHEET_NAME = 'deleted'

# The name for the sheet of omitted items
OMITTED_SHEET_NAME = 'omitted'


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


def excel_to_records(input_path: str) -> Workbook:
    """Reads rows to a excel file at the specified path.

    Args:
        input_path (str): The path where the excel file will be read.
    """

    return pyexcel.get_book_dict(
        file_name=input_path
    )


def write_results_to_xlsx(results: OutputResult, output_path: str):
    """
    Writes the result of processing to the output path as a xlsx file.  Results will be written to a 'results' sheet.
    Omitted results will be written to an 'omitted' sheet.  Deleted results will be written to a 'deleted' sheet.

    Args:
        results: The results to be written.
        output_path: The output path.
    """

    workbook = collections.OrderedDict([(RESULTS_SHEET_NAME, results.results)])
    if results.omitted:
        workbook[OMITTED_SHEET_NAME] = results.omitted
    if results.deleted:
        workbook[DELETED_SHEET_NAME] = results.deleted

    records_to_excel(output_path, workbook)
