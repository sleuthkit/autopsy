"""This script finds all '.properties-MERGED' files and writes relative path, key, and value to a CSV file.

This script requires the python libraries: gitpython, jproperties, pyexcel-xlsx, xlsxwriter and pyexcel along with
python >= 3.9.1 or the requirements.txt file found in this directory can be used
(https://packaging.python.org/guides/installing-using-pip-and-virtual-environments/#using-requirements-files).  As a
consequence of gitpython, this project also requires git >= 1.7.0.

This script relies on fetching 'HEAD' from current branch.  So make sure repo is on correct branch (i.e. develop).
"""
import sys

from envutil import get_proj_dir
from excelutil import write_results_to_xlsx
from gitutil import get_property_file_entries, get_commit_id, get_git_root
from csvutil import write_results_to_csv
import argparse

from outputtype import OutputType
from propentry import convert_to_output


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
    parser.add_argument('-nc', '--no-commit', dest='no_commit', action='store_true', default=False,
                        required=False, help="Suppresses adding commits to the generated header.")
    parser.add_argument('-nt', '--no-translated-col', dest='no_translated_col', action='store_true', default=False,
                        required=False, help="Don't include a column for translation.")

    args = parser.parse_args()
    repo_path = args.repo_path if args.repo_path is not None else get_git_root(get_proj_dir())
    output_path = args.output_path
    show_commit = not args.no_commit
    output_type = args.output_type
    translated_col = not args.no_translated_col
    commit_id = get_commit_id(repo_path, 'HEAD') if show_commit else None

    processing_result = convert_to_output(get_property_file_entries(repo_path), commit_id, translated_col)

    # based on https://stackoverflow.com/questions/60208/replacements-for-switch-statement-in-python
    {
        OutputType.csv: write_results_to_csv,
        OutputType.xlsx: write_results_to_xlsx
    }[output_type](processing_result, output_path)

    sys.exit(0)


if __name__ == "__main__":
    main()
