"""This script determines the updated, added, and deleted properties from the '.properties-MERGED' files
and generates a csv file containing the items changed.  This script requires the python libraries:
gitpython and jproperties.  As a consequence, it also requires git >= 1.7.0 and python >= 3.4.
"""

import sys
from gitutil import get_property_files_diff, get_commit_id
from itemchange import ItemChange
from csvutil import records_to_csv
import argparse


def write_diff_to_csv(repo_path: str, output_path: str, commit_1_id: str, commit_2_id: str, show_commits: bool):
    """Determines the changes made in '.properties-MERGED' files from one commit to another commit.

    Args:
        repo_path (str): The local path to the git repo.
        output_path (str): The output path for the csv file.
        commit_1_id (str): The initial commit for the diff.
        commit_2_id (str): The latest commit for the diff.
        show_commits (bool): show commits in the header row.
    """

    row_header = ItemChange.get_headers()
    if show_commits:
        row_header += [get_commit_id(repo_path, commit_1_id), get_commit_id(repo_path, commit_2_id)]

    rows = [row_header]

    rows += map(lambda item_change: item_change.get_row(),
                get_property_files_diff(repo_path, commit_1_id, commit_2_id))

    records_to_csv(output_path, rows)


def main():
    parser = argparse.ArgumentParser(description="determines the updated, added, and deleted properties from the " +
                                                 "'.properties-MERGED' files and generates a csv file containing " +
                                                 "the items changed.")
    parser.add_argument(dest='repo_path', type=str, help='The path to the repo.')
    parser.add_argument(dest='output_path', type=str, help='The path to the output csv file.')
    parser.add_argument(dest='commit_1_id', type=str, help='The commit for previous release.')
    parser.add_argument('-lc', '--latest_commit', dest='commit_2_id', type=str, default='HEAD', required=False,
                        help='The commit for current release.')
    parser.add_argument('-nc', '--no_commits', dest='no_commits', action='store_true', default=False,
                        required=False, help="Suppresses adding commits to the generated csv header.")

    args = parser.parse_args()
    repo_path = args.repo_path
    output_path = args.output_path
    commit_1_id = args.commit_1_id
    commit_2_id = args.commit_2_id
    show_commits = not args.no_commits

    write_diff_to_csv(repo_path, output_path, commit_1_id, commit_2_id, show_commits)

    sys.exit(0)


if __name__ == "__main__":
    main()
