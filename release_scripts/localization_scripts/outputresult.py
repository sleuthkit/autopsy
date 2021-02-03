from typing import List, Union, TypedDict


class ColumnStyle(TypedDict):
    """
    Describes style for each cell in a column.
    """
    width: int
    wrap_text: bool


class OutputResult:
    """
    Describes a result that is ready to be written to tabular file(s).
    """
    column_styles: List[ColumnStyle]
    freeze_first_row: bool
    results: List[List[str]]
    omitted: Union[List[List[str]], None]
    deleted: Union[List[List[str]], None]
    found: Union[List[List[str]], None]

    def __init__(self, results: List[List[str]], omitted: Union[List[List[str]], None] = None,
                 deleted: Union[List[List[str]], None] = None, found: Union[List[List[str]], None] = None,
                 style: Union[List[ColumnStyle], None] = None, freeze_first_row: bool = True):
        """
        Constructs a ProcessingResult.

        Args:
            results: Items to be written as results.  Data will be written such that the item at row,cell will be
            located within result at results[row][col].
            omitted: Items to be written as omitted.  Data will be written such that the item at row,cell will be
            located within result at results[row][col].
            deleted: Items to be written as omitted.  Data will be written such that the item at row,cell will be
            located within result at results[row][col].
            found: Items where a translation was found elsewhere.  Data will be written such that the item at row,cell
            will be located within result at results[row][col].
            style: Style for each column.  No column formatting will happen for null.
            freeze_first_row: Whether or not first row should be frozen.
        """

        self.results = results
        self.omitted = omitted
        self.deleted = deleted
        self.found = found
        self.column_styles = style
        self.freeze_first_row = freeze_first_row
