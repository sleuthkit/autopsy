"""This script determines the updated, added, and deleted properties from the '.properties-MERGED' files
and generates a csv file containing the items changed.  This script requires the python libraries:
gitpython and jproperties.  As a consequence, it also requires git >= 1.7.0 and python >= 3.4.
"""

import sys

from envutil import get_proj_dir
from gitutil import get_property_files_diff, get_commit_id, get_git_root
from itemchange import ItemChange
from csvutil import records_to_csv
import argparse
import pathlib
from typing import Union
import re

from langpropsutil import get_commit_for_language, LANG_FILENAME


def write_diff_to_csv(repo_path: str, output_path: str, commit_1_id: str, commit_2_id: str, show_commits: bool,
                      value_regex: Union[str, None]):
    """Determines the changes made in '.properties-MERGED' files from one commit to another commit.

    Args:
        repo_path (str): The local path to the git repo.
        output_path (str): The output path for the csv file.
        commit_1_id (str): The initial commit for the diff.
        commit_2_id (str): The latest commit for the diff.
        show_commits (bool): Show commits in the header row.
        value_regex (Union[str, None]): If non-none, only key value pairs where the value is a regex match with this
        value will be included.
    """

    row_header = ItemChange.get_headers()
    if show_commits:
        row_header += [get_commit_id(repo_path, commit_1_id), get_commit_id(repo_path, commit_2_id)]

    rows = [row_header]

    item_changes = get_property_files_diff(repo_path, commit_1_id, commit_2_id)
    if value_regex is not None:
        item_changes = filter(lambda item_change: re.match(value_regex, item_change.cur_val) is not None, item_changes)

    rows += map(lambda item_change: item_change.get_row(), item_changes)

    records_to_csv(output_path, rows)


def main():
    parser = argparse.ArgumentParser(description="determines the updated, added, and deleted properties from the "
                                                 "'.properties-MERGED' files and generates a csv file containing "
                                                 "the items changed.",
                                     formatter_class=argparse.ArgumentDefaultsHelpFormatter)
    parser.add_argument(dest='output_path', type=str, help='The path to the output csv file.')

    parser.add_argument('-r', '--repo', dest='repo_path', type=str, required=False,
                        help='The path to the repo.  If not specified, path of script is used.')
    parser.add_argument('-fc', '--first-commit', dest='commit_1_id', type=str, required=False,
                        help='The commit for previous release.  This flag or the language flag need to be specified'
                             ' in order to determine a start point for the difference.')
    parser.add_argument('-lc', '--latest-commit', dest='commit_2_id', type=str, default='HEAD', required=False,
                        help='The commit for current release.')
    parser.add_argument('-nc', '--no-commits', dest='no_commits', action='store_true', default=False,
                        required=False, help="Suppresses adding commits to the generated csv header.")
    parser.add_argument('-l', '--language', dest='language', type=str, default='HEAD', required=False,
                        help='Specify the language in order to determine the first commit to use (i.e. \'ja\' for '
                             'Japanese.  This flag overrides the first-commit flag.')
    parser.add_argument('-vr', '--value-regex', dest='value_regex', type=str, default=None, required=False,
                        help='Specify the regex for the property value where a regex match against the property value '
                             'will display the key value pair in csv output (i.e. \'[a-zA-Z]\' or \'\\S\' for removing '
                             'just whitespace items).  If this option is not specified, all key value pairs will be '
                             'accepted.')

    args = parser.parse_args()
    repo_path = args.repo_path if args.repo_path is not None else get_git_root(get_proj_dir())
    output_path = args.output_path
    commit_1_id = args.commit_1_id
    value_regex = args.value_regex
    if args.language is not None:
        commit_1_id = get_commit_for_language(args.language)

    if commit_1_id is None:
        print('Either the first commit or language flag need to be specified.  If specified, the language file, ' +
              LANG_FILENAME + ', may not have the latest commit for the language.', file=sys.stderr)
        parser.print_help(sys.stderr)
        sys.exit(1)

    commit_2_id = args.commit_2_id
    show_commits = not args.no_commits

    write_diff_to_csv(repo_path, output_path, commit_1_id, commit_2_id, show_commits, value_regex)

    sys.exit(0)


if __name__ == "__main__":
    main()
