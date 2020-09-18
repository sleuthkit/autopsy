from typing import List, Union


class OutputResult:
    """
    Describes a result that is ready to be written to file(s).
    """

    results: List[List[str]]
    omitted: Union[List[List[str]], None]
    deleted: Union[List[List[str]], None]

    def __init__(self, results: List[List[str]], omitted: Union[List[List[str]], None] = None,
                 deleted: Union[List[List[str]], None] = None):
        """
        Constructs a ProcessingResult.
        Args:
            results: Items to be written as results.  Data will be written such that the item at row,cell will be
            located within result at results[row][col].
            omitted: Items to be written as omitted.  Data will be written such that the item at row,cell will be
            located within result at results[row][col].
            deleted: Items to be written as omitted.  Data will be written such that the item at row,cell will be
            located within result at results[row][col].
        """

        self.results = results
        self.omitted = omitted
        self.deleted = deleted
