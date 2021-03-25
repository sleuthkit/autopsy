"""This script finds all '.properties-MERGED' files with no relevant translation for a given language.

This script requires the python libraries: gitpython, jproperties, pyexcel-xlsx, xlsxwriter and pyexcel along with
python >= 3.9.1 or the requirements.txt file found in this directory can be used
(https://packaging.python.org/guides/installing-using-pip-and-virtual-environments/#using-requirements-files).  As a
consequence of gitpython, this project also requires git >= 1.7.0.
"""
import sys
from typing import List

from envutil import get_proj_dir
from excelutil import write_results_to_xlsx
from gitutil import get_property_file_entries, get_commit_id, get_git_root, list_paths, get_tree
from csvutil import write_results_to_csv
import argparse

from languagedictutil import find_unmatched_translations
from outputtype import OutputType
from propentry import convert_to_output, PropEntry
from propsutil import DEFAULT_PROPS_FILENAME, get_lang_bundle_name


def get_unmatched(repo_path: str, language: str, original_commit: str, translated_commit: str) -> List[PropEntry]:
    """
    Get all original key values that have not been translated.
    :param repo_path: Path to repo.
    :param language: The language identifier (i.e. 'ja')
    :param original_commit: The commit to use for original key values.
    :param translated_commit: The commit to use for translated key values.
    :return: The list of unmatched items
    """
    original_files = filter(lambda x: x[0].endswith(DEFAULT_PROPS_FILENAME),
                            list_paths(get_tree(repo_path, original_commit)))

    translated_name = get_lang_bundle_name(language)
    translated_files = filter(lambda x: x[0].endswith(translated_name),
                              list_paths(get_tree(repo_path, translated_commit)))

    return find_unmatched_translations(orig_file_iter=original_files, translated_file_iter=translated_files,
                                       orig_filename=DEFAULT_PROPS_FILENAME, translated_filename=translated_name)


def main():
    # noinspection PyTypeChecker
    parser = argparse.ArgumentParser(description='Gathers all key-value pairs within .properties-MERGED files that '
                                                 'have not been translated.',
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
    parser.add_argument('-l', '--language', dest='language', type=str, required=False, default=None,
                        help="The language identifier (i.e. ja).  If specified, this only returns items where the key"
                             " is not translated (i.e. no matching Japanese key or value is empty)")

    parser.add_argument('-oc', '--original-commit', dest='original_commit', type=str, required=False, default=None,
                        help="The commit to gather original keys.")
    parser.add_argument('-tc', '--translated-commit', dest='translated_commit', type=str, required=False, default=None,
                        help="The commit to gather translations.")

    args = parser.parse_args()
    repo_path = args.repo_path if args.repo_path is not None else get_git_root(get_proj_dir())
    output_path = args.output_path
    output_type = args.output_type
    translated_col = not args.no_translated_col
    original_commit = args.original_commit
    translated_commit = args.translated_commit

    prop_entries = get_unmatched(repo_path, args.language, original_commit, translated_commit) \
        if args.language else get_property_file_entries(repo_path)

    processing_result = convert_to_output(prop_entries, original_commit, translated_col)

    # based on https://stackoverflow.com/questions/60208/replacements-for-switch-statement-in-python
    {
        OutputType.csv: write_results_to_csv,
        OutputType.xlsx: write_results_to_xlsx
    }[output_type](processing_result, output_path)

    sys.exit(0)


if __name__ == "__main__":
    main()
