"""Provides tools for parsing and writing to a csv file.
"""
import collections
from typing import List, OrderedDict, Tuple, Union
import xlsxwriter
import pyexcel
from xlsxwriter.format import Format

from outputresult import OutputResult, ColumnStyle

Workbook = OrderedDict[str, List[List[str]]]

# The name for the results sheet
RESULTS_SHEET_NAME = 'results'

# The name for the sheet of deleted items
DELETED_SHEET_NAME = 'deleted'

# The name for the sheet of omitted items
OMITTED_SHEET_NAME = 'omitted'

# The name for the sheet of found items
FOUND_SHEET_NAME = 'found'


def excel_to_records(input_path: str) -> Workbook:
    """Reads rows to a excel file at the specified path.

    Args:
        input_path (str): The path where the excel file will be read.
    """

    return pyexcel.get_book_dict(
        file_name=input_path
    )


def get_writer_format(workbook: xlsxwriter.Workbook, style: ColumnStyle) -> Union[Tuple[Format, int], None]:
    if style:
        wb_format = workbook.add_format({'text_wrap': 1, 'valign': 'top'}) if style['wrap_text'] else None
        return wb_format, style['width']
    else:
        return None


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
    if results.found:
        workbook[FOUND_SHEET_NAME] = results.found

    wb_file = xlsxwriter.Workbook(output_path)
    styles = []
    if results.column_styles:
        styles = list(map(lambda style: get_writer_format(wb_file, style), results.column_styles))

    for sheet_name, values in workbook.items():
        sheet = wb_file.add_worksheet(name=sheet_name)
        if results.freeze_first_row:
            sheet.freeze_panes(1, 0)

        for col_idx in range(0, len(styles)):
            if styles[col_idx]:
                col_format, width = styles[col_idx]
                sheet.set_column(col_idx, col_idx, width)

        for row_idx in range(0, len(values)):
            row = values[row_idx]
            for col_idx in range(0, len(row)):
                cell_format = None
                if len(styles) > col_idx and styles[col_idx] and styles[col_idx][0]:
                    cell_format = styles[col_idx][0]

                cell_value = row[col_idx]
                sheet.write(row_idx, col_idx, cell_value, cell_format)

    wb_file.close()
