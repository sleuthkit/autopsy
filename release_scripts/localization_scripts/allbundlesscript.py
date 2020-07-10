"""This script finds all '.properties-MERGED' files and writes relative path, key, and value to a CSV file.
This script requires the python libraries: gitpython and jproperties.  As a consequence, it also requires
git >= 1.7.0 and python >= 3.4.  This script relies on fetching 'HEAD' from current branch.  So make sure
repo is on correct branch (i.e. develop).
"""

import sys
from gitutil import get_property_file_entries, get_commit_id
from csvutil import records_to_csv
import argparse


def write_items_to_csv(repo_path: str, output_path: str, show_commit: bool):
    """Determines the contents of '.properties-MERGED' files and writes to a csv file.

    Args:
        repo_path (str): The local path to the git repo.
        output_path (str): The output path for the csv file.
        show_commit (bool): Whether or not to include the commit id in the header
    """

    row_header = ['Relative path', 'Key', 'Value']
    if show_commit:
        row_header.append(get_commit_id(repo_path, 'HEAD'))

    rows = [row_header]
    for entry in get_property_file_entries(repo_path):
        rows.append([entry.rel_path, entry.key, entry.value])

    records_to_csv(output_path, rows)


def main():
    parser = argparse.ArgumentParser(description='Gathers all key-value pairs within .properties-MERGED files into ' +
                                                 'one csv file.')
    parser.add_argument(dest='repo_path', type=str, help='The path to the repo.')
    parser.add_argument(dest='output_path', type=str, help='The path to the output csv file.')
    parser.add_argument('-nc', '--no_commit', dest='no_commit', action='store_true', default=False,
                        required=False, help="Suppresses adding commits to the generated csv header.")

    args = parser.parse_args()
    repo_path = args.repo_path
    output_path = args.output_path
    show_commit = not args.no_commit

    write_items_to_csv(repo_path, output_path, show_commit)

    sys.exit(0)


if __name__ == "__main__":
    main()
