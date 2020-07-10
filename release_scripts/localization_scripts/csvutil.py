"""Provides tools for parsing and writing to a csv file.
"""

from typing import List, Iterable, Iterator
import csv
import os


def records_to_csv(output_path: str, rows: Iterable[List[str]]):
    """Writes rows to a csv file at the specified path.

    Args:
        output_path (str): The path where the csv file will be written.
        rows (List[List[str]]): The rows to be written.  Each row of a 
                                list of strings will be written according 
                                to their index (i.e. column 3 will be index 2).
    """

    parent_dir, file = os.path.split(output_path)
    if not os.path.exists(parent_dir):
        os.makedirs(parent_dir)

    with open(output_path, 'w', encoding="utf-8", newline='') as csvfile:
        writer = csv.writer(csvfile)

        for row in rows:
            writer.writerow(row)


def csv_to_records(input_path: str, header_row: bool) -> Iterator[List[str]]:
    """Writes rows to a csv file at the specified path.

    Args:
        input_path (str): The path where the csv file will be written.
        header_row (bool): The rows to be written.  Each row of a
                                list of strings will be written according 
                                to their index (i.e. column 3 will be index 2).
    """

    with open(input_path, encoding='utf-8') as csv_file:
        csv_reader = csv.reader(csv_file, delimiter=',')

        for row in csv_reader:
            if header_row:
                header_row = False
            else:
                yield row
