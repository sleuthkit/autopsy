"""This script finds all '.properties-MERGED' files and writes relative path, key, and value to a CSV file.
This script requires the python libraries: jproperties.  It also requires Python 3.x.
"""

from typing import List, Dict, Tuple, Callable, Iterator
import sys
import os

from envutil import get_proj_dir
from fileutil import get_new_path
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

        prop_items = get_entry_dict_from_path(abs_path)
        if prop_items is None:
            prop_items = {}

        for key_to_delete in to_delete:
            if key_to_delete in prop_items:
                del prop_items[key_to_delete]

        for key, val in entries.items():
            prop_items[key] = val

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
                   value_idx: int = 2,
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

    key = row[key_idx] if idx_bounded(key_idx, len(row)) else None
    value = row[value_idx] if idx_bounded(value_idx, len(row)) else None
    should_delete = False if should_delete_converter is None else should_delete_converter(row)
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
    propentry_iter = map(lambda row: get_prop_entry(row, path_idx, key_idx, value_idx, should_delete_converter,
                                                    path_converter), rows)

    # filter rows that have no value
    return filter(lambda entry: entry and entry.value.strip(), propentry_iter)


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


def main():
    # noinspection PyTypeChecker
    parser = argparse.ArgumentParser(description='Updates properties files in the autopsy git repo.',
                                     formatter_class=argparse.ArgumentDefaultsHelpFormatter)

    parser.add_argument(dest='csv_file', type=str, help='The path to the csv file.  The default format for the csv '
                                                        'file has columns of relative path, properties file key, '
                                                        'properties file value, whether or not the key should be '
                                                        'deleted, and commit id for how recent these updates are.  '
                                                        'If the key should be deleted, the deletion row should be '
                                                        '\'DELETION.\'  A header row is expected by default and the '
                                                        'commit id, if specified, should only be in the first row.  The'
                                                        ' input path should be specified as a relative path with the '
                                                        'dot slash notation (i.e. `./inputpath.csv`) or an absolute '
                                                        'path.')

    parser.add_argument('-r', '--repo', dest='repo_path', type=str, required=False,
                        help='The path to the repo.  If not specified, parent repo of path of script is used.')
    parser.add_argument('-p', '--path-idx', dest='path_idx', action='store', type=int, default=0, required=False,
                        help='The column index in the csv file providing the relative path to the properties file.')
    parser.add_argument('-k', '--key-idx', dest='key_idx', action='store', type=int, default=1, required=False,
                        help='The column index in the csv file providing the key within the properties file.')
    parser.add_argument('-v', '--value-idx', dest='value_idx', action='store', type=int, default=2, required=False,
                        help='The column index in the csv file providing the value within the properties file.')
    parser.add_argument('-d', '--should-delete-idx', dest='should_delete_idx', action='store', type=int, default=3,
                        required=False, help='The column index in the csv file providing whether or not the file '
                                             'should be deleted.  Any non-blank content will be treated as True.')
    parser.add_argument('-c', '--commit-idx', dest='latest_commit_idx', action='store', type=int, default=4,
                        required=False, help='The column index in the csv file providing the commit for which this '
                                             'update applies. The commit should be located in the header row.  ')

    parser.add_argument('-f', '--file-rename', dest='file_rename', action='store', type=str, default=None,
                        required=False, help='If specified, the properties file will be renamed to the argument'
                                             ' preserving the specified relative path.')
    parser.add_argument('-z', '--has-no-header', dest='has_no_header', action='store_true', default=False,
                        required=False, help='Specify whether or not there is a header within the csv file.')
    parser.add_argument('-o', '--should-overwrite', dest='should_overwrite', action='store_true', default=False,
                        required=False, help="Whether or not to overwrite the previously existing properties files"
                                             " ignoring previously existing values.")

    parser.add_argument('-l', '--language', dest='language', type=str, default='HEAD', required=False,
                        help='Specify the language in order to update the last updated properties file and rename '
                             'files within directories.  This flag overrides the file-rename flag.')

    args = parser.parse_args()

    repo_path = args.repo_path if args.repo_path is not None else get_git_root(get_proj_dir())
    input_path = args.csv_file
    path_idx = args.path_idx
    key_idx = args.key_idx
    value_idx = args.value_idx
    has_header = not args.has_no_header
    overwrite = args.should_overwrite

    # means of determining if a key should be deleted from a file
    if args.should_delete_idx is None:
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

    # retrieve records from csv
    all_items, header = list(csv_to_records(input_path, has_header))
    prop_entries = get_prop_entries(all_items, path_idx, key_idx, value_idx, should_delete_converter, path_converter)

    # write to files
    if overwrite:
        write_prop_entries(prop_entries, repo_path)
    else:
        update_prop_entries(prop_entries, repo_path)

    # update the language last update if applicable
    if args.language is not None and header is not None and len(header) > args.latest_commit_idx >= 0:
        set_commit_for_language(args.language, header[args.latest_commit_idx])

    sys.exit(0)


if __name__ == "__main__":
    main()
