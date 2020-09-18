"""This script finds all '.properties-MERGED' files and writes relative path, key, and value to a CSV file.
This script requires the python libraries: gitpython and jproperties.  As a consequence, it also requires
git >= 1.7.0 and python >= 3.4.  This script relies on fetching 'HEAD' from current branch.  So make sure
repo is on correct branch (i.e. develop).
"""
import sys
from envutil import get_proj_dir
from excelutil import write_results_to_xlsx
from gitutil import get_property_file_entries, get_commit_id, get_git_root
from csvutil import write_results_to_csv
from typing import Union
import re
import argparse

from outputresult import OutputResult
from outputtype import OutputType


def get_items_to_be_written(repo_path: str, show_commit: bool,
                            value_regex: Union[str, None] = None) -> OutputResult:
    """Determines the contents of '.properties-MERGED' files and writes to a csv file.

    Args:
        repo_path (str): The local path to the git repo.
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

    omitted_to_write = [row_header] + omitted if len(omitted) > 0 else None
    return OutputResult([row_header] + rows, omitted_to_write)


def main():
    # noinspection PyTypeChecker
    parser = argparse.ArgumentParser(description='Gathers all key-value pairs within .properties-MERGED files into '
                                                 'one file.',
                                     formatter_class=argparse.ArgumentDefaultsHelpFormatter)
    parser.add_argument(dest='output_path', type=str, help='The path to the output file.  The output path should be'
                                                           ' specified as a relative path with the dot slash notation '
                                                           '(i.e. \'./outputpath.xlsx\') or an absolute path.')
    parser.add_argument('-r', '--repo', dest='repo_path', type=str, required=False,
                        help='The path to the repo.  If not specified, path of script is used.')
    parser.add_argument('-o', '--output-type', dest='output_type', type=OutputType, choices=list(OutputType),
                        required=False, help="The output type.  Currently supports 'csv' or 'xlsx'.", default='xlsx')
    parser.add_argument('-nc', '--no_commit', dest='no_commit', action='store_true', default=False,
                        required=False, help="Suppresses adding commits to the generated header.")

    args = parser.parse_args()
    repo_path = args.repo_path if args.repo_path is not None else get_git_root(get_proj_dir())
    output_path = args.output_path
    show_commit = not args.no_commit
    output_type = args.output_type

    processing_result = get_items_to_be_written(repo_path, show_commit)

    # based on https://stackoverflow.com/questions/60208/replacements-for-switch-statement-in-python
    {
        OutputType.csv: write_results_to_csv,
        OutputType.xlsx: write_results_to_xlsx
    }[output_type](processing_result, output_path)

    sys.exit(0)


if __name__ == "__main__":
    main()
