"""This script finds all '.properties-MERGED' files and writes relative path, key, and value to a CSV file.
This script requires the python libraries: gitpython and jproperties.  As a consequence, it also requires
git >= 1.7.0 and python >= 3.4.  This script relies on fetching 'HEAD' from current branch.  So make sure
repo is on correct branch (i.e. develop).
"""

import sys

from envutil import get_proj_dir
from fileutil import get_filename_addition, OMITTED_ADDITION
from gitutil import get_property_file_entries, get_commit_id, get_git_root
from csvutil import records_to_csv
from typing import Union
import re
import argparse


def write_items_to_csv(repo_path: str, output_path: str, show_commit: bool, value_regex: Union[str, None] = None):
    """Determines the contents of '.properties-MERGED' files and writes to a csv file.

    Args:
        repo_path (str): The local path to the git repo.
        output_path (str): The output path for the csv file.
        show_commit (bool): Whether or not to include the commit id in the header
        value_regex (Union[str, None]): If non-none, only key value pairs where the value is a regex match with this
        value will be included.
    """

    row_header = ['Relative path', 'Key', 'Value']
    if show_commit:
        row_header.append(get_commit_id(repo_path, 'HEAD'))

    rows = []
    omitted = []

    for entry in get_property_file_entries(repo_path):
        new_entry = [entry.rel_path, entry.key, entry.value]
        if value_regex is None or re.match(value_regex, entry.value):
            rows.append(new_entry)
        else:
            omitted.append(new_entry)

    records_to_csv(output_path, [row_header] + rows)

    if len(omitted) > 0:
        records_to_csv(get_filename_addition(output_path, OMITTED_ADDITION), [row_header] + omitted)


def main():
    # noinspection PyTypeChecker
    parser = argparse.ArgumentParser(description='Gathers all key-value pairs within .properties-MERGED files into '
                                                 'one csv file.',
                                     formatter_class=argparse.ArgumentDefaultsHelpFormatter)
    parser.add_argument(dest='output_path', type=str, help='The path to the output csv file.  The output path should be'
                                                           ' specified as a relative path with the dot slash notation '
                                                           '(i.e. \'./outputpath.csv\') or an absolute path.')
    parser.add_argument('-r', '--repo', dest='repo_path', type=str, required=False,
                        help='The path to the repo.  If not specified, path of script is used.')
    parser.add_argument('-nc', '--no_commit', dest='no_commit', action='store_true', default=False,
                        required=False, help="Suppresses adding commits to the generated csv header.")

    args = parser.parse_args()
    repo_path = args.repo_path if args.repo_path is not None else get_git_root(get_proj_dir())
    output_path = args.output_path
    show_commit = not args.no_commit

    write_items_to_csv(repo_path, output_path, show_commit)

    sys.exit(0)


if __name__ == "__main__":
    main()
