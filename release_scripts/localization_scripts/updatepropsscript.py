"""This script finds all '.properties-MERGED' files and writes relative path, key, and value to a CSV file.

This script requires the python libraries: gitpython, jproperties, pyexcel-xlsx, xlsxwriter and pyexcel along with
python >= 3.9.1 or the requirements.txt file found in this directory can be used
(https://packaging.python.org/guides/installing-using-pip-and-virtual-environments/#using-requirements-files).  As a
consequence of gitpython, this project also requires git >= 1.7.0.
"""

from typing import List, Dict, Tuple, Callable, Iterator, Union
import sys
import os

from envutil import get_proj_dir
from excelutil import excel_to_records, FOUND_SHEET_NAME, DELETED_SHEET_NAME, RESULTS_SHEET_NAME
from fileutil import get_new_path, get_path_pieces
from gitutil import get_git_root
from langpropsutil import set_commit_for_language
from propsutil import set_entry_dict, get_entry_dict_from_path, get_lang_bundle_name
from csvutil import csv_to_records
from propentry import PropEntry
import argparse


def write_prop_entries(entries: Iterator[PropEntry], repo_path: str):
    """Writes property entry items to their expected relative path within the repo path.
    Previously existing files will be overwritten and prop entries marked as should_be_deleted will
    not be included.

    Args:
        entries (List[PropEntry]): the prop entry items to write to disk.
        repo_path (str): The path to the git repo.
    """
    items_by_file = get_by_file(entries)
    for rel_path, (entries, ignored) in items_by_file.items():
        abs_path = os.path.join(repo_path, rel_path)
        set_entry_dict(entries, abs_path)


def update_prop_entries(entries: Iterator[PropEntry], repo_path: str):
    """Updates property entry items to their expected relative path within the repo path.  The union of
    entries provided and any previously existing entries will be created.  Keys marked for deletion will be
    removed from the generated property files.

    Args:
        entries (List[PropEntry]): the prop entry items to write to disk.
        repo_path (str): The path to the git repo.
    """
    items_by_file = get_by_file(entries)
    for rel_path, (entries, to_delete) in items_by_file.items():
        abs_path = os.path.join(repo_path, rel_path)

        changed = False
        prop_items = get_entry_dict_from_path(abs_path)
        if prop_items is None:
            prop_items = {}

        for key_to_delete in to_delete:
            if key_to_delete in prop_items:
                changed = True
                del prop_items[key_to_delete]

        for key, val in entries.items():
            changed = True
            prop_items[key] = val

        # only write to disk if a change was made
        if changed:
            set_entry_dict(prop_items, abs_path)


def get_by_file(entries: Iterator[PropEntry]) -> Dict[str, Tuple[Dict[str, str], List[str]]]:
    """Sorts a prop entry list by file.  The return type is a dictionary mapping 
    the file path to a tuple containing the key-value pairs to be updated and a 
    list of keys to be deleted.

    Args:
        entries (List[PropEntry]): The entries to be sorted.

    Returns:
        Dict[str, Tuple[Dict[str,str], List[str]]]: A dictionary mapping 
    the file path to a tuple containing the key-value pairs to be updated and a 
    list of keys to be deleted.
    """
    to_ret = {}
    for prop_entry in entries:
        rel_path = prop_entry.rel_path
        key = prop_entry.key
        value = prop_entry.value

        if rel_path not in to_ret:
            to_ret[rel_path] = ({}, [])

        if prop_entry.should_delete:
            to_ret[rel_path][1].append(prop_entry.key)
        else:
            to_ret[rel_path][0][key] = value

    return to_ret


def idx_bounded(num: int, max_exclusive: int) -> bool:
    return 0 <= num < max_exclusive


def get_prop_entry(row: List[str], 
                   path_idx: int = 0,
                   key_idx: int = 1,
                   value_idx: int = 3,
                   should_delete_converter: Callable[[List[str]], bool] = None,
                   path_converter: Callable[[str], str] = None) -> PropEntry:
    """Parses a PropEntry object from a row of values in a csv.

    Args:
        row (List[str]): The csv file row to parse.
        path_idx (int, optional): The column index for the relative path of the properties file. Defaults to 0.
        key_idx (int, optional): The column index for the properties key. Defaults to 1.
        value_idx (int, optional): The column index for the properties value. Defaults to 2.
        should_delete_converter (Callable[[List[str]], bool], optional): If not None, this determines if the key should
        be deleted from the row values. Defaults to None.
        path_converter (Callable[[str], str], optional): If not None, this determines the relative path to use in the
        created PropEntry given the original relative path. Defaults to None.

    Returns:
        PropEntry: The generated prop entry object.
    """

    path = row[path_idx] if idx_bounded(path_idx, len(row)) else None
    if path_converter is not None:
        path = path_converter(path)

    key = str(row[key_idx]) if idx_bounded(key_idx, len(row)) else None
    value = str(row[value_idx]) if idx_bounded(value_idx, len(row)) else None
    should_delete = False if should_delete_converter is None else should_delete_converter(row)

    # delete this key if no value provided
    if not value or not value.strip():
        should_delete = True

    return PropEntry(path, key, value, should_delete)


def get_prop_entries(rows: List[List[str]], 
                     path_idx: int = 0,
                     key_idx: int = 1,
                     value_idx: int = 2,
                     should_delete_converter: Callable[[List[str]], bool] = None,
                     path_converter: Callable[[str], str] = None) -> Iterator[PropEntry]:

    """Parses PropEntry objects from rows of values in a csv.  Any items that have an empty string value will be
    ignored.

    Args:
        rows (List[List[str]]): The csv file rows to parse.
        path_idx (int, optional): The column index for the relative path of the properties file. Defaults to 0.
        key_idx (int, optional): The column index for the properties key. Defaults to 1.
        value_idx (int, optional): The column index for the properties value. Defaults to 2.
        should_delete_converter (Callable[[List[str]], bool], optional): If not None, this determines if the key should
        be deleted from the row values. Defaults to None.
        path_converter (Callable[[str], str], optional): If not None, this determines the relative path to use in the
        created PropEntry given the original relative path. Defaults to None.

    Returns:
        List[PropEntry]: The generated prop entry objects.
    """
    prop_entries = map(lambda row: get_prop_entry(row, path_idx, key_idx, value_idx, should_delete_converter,
                                                  path_converter), rows)

    # ensure a value is present
    return filter(lambda prop_entry: prop_entry and prop_entry.key.strip() and prop_entry.rel_path.strip(), prop_entries)


def get_should_deleted(row_items: List[str], requested_idx: int) -> bool:
    """If there is a value at row_items[requested_idx] and that value starts with 'DELET', then this will return true.

    Args:
        row_items (List[str]): The row items.
        requested_idx (int): The index specifying if the property should be deleted.

    Returns:
        bool: True if the row specifies it should be deleted.
    """
    if idx_bounded(requested_idx, len(row_items)) and row_items[requested_idx].strip().upper().startswith('DELET'):
        return True
    else:
        return False


class DataRows:
    """
    Defines pieces of an intermediate parsed result from a data source including the header row (if present), results
    as a 2d list, and deleted results as a 2d list.
    """
    header: Union[List[str], None]
    results: List[List[str]]
    deleted_results: Union[List[List[str]], None]

    def __init__(self, results: List[List[str]], header: Union[List[str], None] = None,
                 deleted_results: Union[List[List[str]], None] = None):
        """
        Creates a DataRows object.

        Args:
            results: The 2d list of strings representing cells.
            header: The header row if present.
            deleted_results: The 2d list of strings representing cells or None.
        """
        self.header = header
        self.results = results
        self.deleted_results = deleted_results


def get_csv_rows(input_path: str, has_header: bool) -> DataRows:
    """
    Gets rows of a csv file in a DataRows format.

    Args:
        input_path: The input path of the file.
        has_header: Whether or not it has a header.

    Returns: An intermediate result DataRows object for further parsing.

    """
    all_items, header = csv_to_records(input_path, has_header)
    return DataRows(header=header, results=all_items)


def get_xlsx_rows(input_path: str, has_header: bool, results_sheet: str,
                  found_sheet: Union[str, None], deleted_sheet: Union[str, None]) -> DataRows:
    """
    Gets worksheets of an excel workbook in a DataRows format.

    Args:
        input_path: The input path of the file.
        has_header: Whether or not is has a header.
        results_sheet: The name of the results sheet.
        found_sheet: The name of the found sheet.
        deleted_sheet: The name of the sheet containing deleted items.

    Returns: An intermediate result DataRows object for further parsing.

    """
    workbook = excel_to_records(input_path)
    results_items = workbook[results_sheet]
    header = None
    if has_header and len(results_items) > 0:
        header = results_items[0]
        results_items = results_items[1:len(results_items)]

    found_items = workbook[found_sheet] if found_sheet and found_sheet in workbook else None
    if has_header and found_items and len(found_items) > 0:
        found_items = found_items[1:len(found_items)]

    # add found items to result items to be inserted into properties
    if found_items:
        results_items = results_items + found_items

    deleted_items = workbook[deleted_sheet] if deleted_sheet and deleted_sheet in workbook else None
    if has_header and deleted_items and len(deleted_items) > 0:
        deleted_items = deleted_items[1:len(deleted_items)]

    return DataRows(header=header, results=results_items, deleted_results=deleted_items)


def get_prop_entries_from_data(datarows: DataRows, path_idx: int, key_idx: int, value_idx: int,
                               should_delete_converter: Union[Callable[[List[str]], bool], None],
                               path_converter: Callable) -> List[PropEntry]:
    """
    Converts a DataRows object into PropEntry objects.

    Args:
        datarows: The DataRows object.
        path_idx: The index of the column containing the path.
        key_idx: The index of the column containing the key.
        value_idx: The index of the column containing the value.
        should_delete_converter: Given a list of strings representing a row, returns true if the entry should be
        deleted.
        path_converter: Converts the path to the proper format.

    Returns: A list of PropEntry items.

    """

    prop_entries = list(get_prop_entries(datarows.results, path_idx, key_idx, value_idx, should_delete_converter,
                                         path_converter))

    if datarows.deleted_results and len(datarows.deleted_results) > 0:
        prop_entries += list(get_prop_entries(datarows.deleted_results, path_idx, key_idx, value_idx, lambda row: True,
                                              path_converter))

    return prop_entries


def main():
    # noinspection PyTypeChecker
    parser = argparse.ArgumentParser(description='Updates properties files in the autopsy git repo.',
                                     formatter_class=argparse.ArgumentDefaultsHelpFormatter)

    parser.add_argument(dest='file', type=str, help='The path to the file (ending in either .xlsx or .csv). '
                                                    'The default format for the file has columns of relative path, '
                                                    'properties file key, properties file value, translated value, '
                                                    'first commit, and commit id for how recent these updates '
                                                    'are. If the key should be deleted, the deletion row should be '
                                                    '\'DELETION.\' A header row is expected by default and the '
                                                    'commit id, if specified, should only be in the first row. The'
                                                    ' input path should be specified as a relative path with the '
                                                    'dot slash notation (i.e. `./inputpath.csv`) or an absolute '
                                                    'path.')

    parser.add_argument('-r', '--repo', dest='repo_path', type=str, required=False,
                        help='The path to the repo.  If not specified, parent repo of path of script is used.')

    parser.add_argument('-p', '--path-idx', dest='path_idx', action='store', type=int, default=0, required=False,
                        help='The column index in the csv file providing the relative path to the properties file.')
    parser.add_argument('-k', '--key-idx', dest='key_idx', action='store', type=int, default=1, required=False,
                        help='The column index in the csv file providing the key within the properties file.')
    parser.add_argument('-v', '--value-idx', dest='value_idx', action='store', type=int, default=3, required=False,
                        help='The column index in the csv file providing the value within the properties file.')
    parser.add_argument('-c', '--commit-idx', dest='latest_commit_idx', action='store', type=int, default=5,
                        required=False, help='The column index in the csv file providing the commit for which this '
                                             'update applies. The commit should be located in the header row.  ')
    parser.add_argument('-rs', '--results-sheet', dest='results_sheet', action='store', type=str,
                        default=RESULTS_SHEET_NAME, required=False,
                        help='In an excel workbook, the sheet that indicates results items.  This is only used for '
                             'xlsx files.')
    parser.add_argument('-ds', '--deleted-sheet', dest='deleted_sheet', action='store', type=str,
                        default=DELETED_SHEET_NAME, required=False,
                        help='In an excel workbook, the sheet that indicates deleted items.  This is only used for '
                             'xlsx files.')
    parser.add_argument('-fs', '--found-sheet', dest='found_sheet', action='store', type=str,
                        default=FOUND_SHEET_NAME, required=False,
                        help='In an excel workbook, the sheet that indicates items where the translation was found.  '
                             'This is only used for xlsx files.')
    parser.add_argument('-di', '--should-delete-idx', dest='should_delete_idx', action='store', type=int, default=-1,
                        required=False, help='The column index in the csv file providing whether or not the file '
                                             'should be deleted.  Any non-blank content will be treated as True.')

    parser.add_argument('-z', '--has-no-header', dest='has_no_header', action='store_true', default=False,
                        required=False, help='Specify whether or not there is a header within the csv file.')

    parser.add_argument('-f', '--file-rename', dest='file_rename', action='store', type=str, default=None,
                        required=False, help='If specified, the properties file will be renamed to the argument'
                                             ' preserving the specified relative path.')

    parser.add_argument('-o', '--should-overwrite', dest='should_overwrite', action='store_true', default=False,
                        required=False, help="Whether or not to overwrite the previously existing properties files"
                                             " ignoring previously existing values.")

    parser.add_argument('-l', '--language', dest='language', type=str, default=None, required=False,
                        help='Specify the language in order to update the last updated properties file and rename '
                             'files within directories.  This flag overrides the file-rename flag.')
    parser.add_argument('-lf', '--language-updates-file', dest='language_file', type=str, default=None, required=False,
                        help='Specify the path to the properties file containing key value pairs of language mapped to '
                             'the commit of when bundles for that language were most recently updated.')

    args = parser.parse_args()

    repo_path = args.repo_path if args.repo_path is not None else get_git_root(get_proj_dir())

    input_path = args.file
    path_idx = args.path_idx
    key_idx = args.key_idx
    value_idx = args.value_idx
    has_header = not args.has_no_header
    overwrite = args.should_overwrite
    deleted_sheet = args.deleted_sheet
    results_sheet = args.results_sheet
    found_sheet = args.found_sheet

    # means of determining if a key should be deleted from a file
    if args.should_delete_idx < 0:
        should_delete_converter = None
    else:
        def should_delete_converter(row_items: List[str]):
            return get_should_deleted(row_items, args.should_delete_idx)

    # provides the means of renaming the bundle file
    if args.language is not None:
        def path_converter(orig_path: str):
            return get_new_path(orig_path, get_lang_bundle_name(args.language))
    elif args.file_rename is not None:
        def path_converter(orig_path: str):
            return get_new_path(orig_path, args.file_rename)
    else:
        path_converter = None

    # retrieve records from file
    ext = get_path_pieces(input_path)[2]
    if ext == 'xlsx':
        data_rows = get_xlsx_rows(input_path, has_header, results_sheet, found_sheet, deleted_sheet)
    elif ext == 'csv':
        data_rows = get_csv_rows(input_path, has_header)
    else:
        raise ValueError('Expected either a csv file or xlsx file for input.')

    # convert to PropEntry objects
    prop_entries = get_prop_entries_from_data(data_rows, path_idx, key_idx, value_idx,
                                              should_delete_converter, path_converter)
    header = data_rows.header

    # write to files
    if overwrite:
        write_prop_entries(prop_entries, repo_path)
    else:
        update_prop_entries(prop_entries, repo_path)

    # update the language last update if applicable
    if args.language and header is not None and len(header) > args.latest_commit_idx >= 0:
        set_commit_for_language(args.language, header[args.latest_commit_idx], args.language_file)

    sys.exit(0)


if __name__ == "__main__":
    main()
