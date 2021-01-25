"""This script determines the updated, added, and deleted properties from the '.properties-MERGED' files
and generates a csv file containing the items changed.

This script requires the python libraries: gitpython, jproperties, pyexcel-xlsx, xlsxwriter and pyexcel along with
python >= 3.9.1 or the requirements.txt file found in this directory can be used
(https://packaging.python.org/guides/installing-using-pip-and-virtual-environments/#using-requirements-files).  As a
consequence of gitpython, this project also requires git >= 1.7.0.
"""
import sys
from envutil import get_proj_dir
from excelutil import write_results_to_xlsx
from gitutil import get_property_files_diff, get_git_root, get_commit_id, get_tree, list_paths
from itemchange import convert_to_output
from csvutil import write_results_to_csv
import argparse
from langpropsutil import get_commit_for_language, LANG_FILENAME
from outputtype import OutputType
from languagedictutil import extract_translations
from propsutil import get_lang_bundle_name, DEFAULT_PROPS_FILENAME


def main():
    # noinspection PyTypeChecker
    parser = argparse.ArgumentParser(description="Determines the updated, added, and deleted properties from the "
                                                 "'.properties-MERGED' files and generates a csv file containing "
                                                 "the items changed.",
                                     formatter_class=argparse.ArgumentDefaultsHelpFormatter)
    parser.add_argument(dest='output_path', type=str, help='The path to the output file.  The output path should '
                                                           'be specified as a relative path with the dot slash notation'
                                                           ' (i.e. \'./outputpath.xlsx\') or an absolute path.')

    parser.add_argument('-r', '--repo', dest='repo_path', type=str, required=False,
                        help='The path to the repo.  If not specified, path of script is used.')
    parser.add_argument('-fc', '--first-commit', dest='commit_1_id', type=str, required=False,
                        help='The commit for previous release.  This flag or the language flag need to be specified'
                             ' in order to determine a start point for the difference.')
    parser.add_argument('-lc', '--latest-commit', dest='commit_2_id', type=str, default='HEAD', required=False,
                        help='The commit for current release.')
    parser.add_argument('-nc', '--no-commits', dest='no_commits', action='store_true', default=False,
                        required=False, help="Suppresses adding commits to the generated csv header.")
    parser.add_argument('-o', '--output-type', dest='output_type', type=OutputType, choices=list(OutputType),
                        required=False, help="The output type.  Currently supports 'csv' or 'xlsx'.", default='xlsx')
    parser.add_argument('-l', '--language', dest='language', type=str, default=None, required=False,
                        help='Specify the language in order to determine the first commit to use (i.e. \'ja\' for '
                             'Japanese.  This flag overrides the first-commit flag.')
    parser.add_argument('-lf', '--language-updates-file', dest='language_file', type=str, default=None, required=False,
                        help='Specify the path to the properties file containing key value pairs of language mapped to '
                             'the commit of when bundles for that language were most recently updated.')

    parser.add_argument('-td', '--translation-dict', dest='translation_dict', type=str, default=None, required=False,
                        help='If this flag is specified, a dictionary mapping original prop key values to translated '
                             'values.  If this flag is specified, language is expected.  The value for the translation '
                             'dictionary flag should either be the commit id to use for original values and the commit'
                             ' id for translated values in the form of '
                             '"-td <original values commit id>,<translated values commit id>".  If only one commit id '
                             'is specified, it will be assumed to be the translated values commit id and the commit id '
                             'determined to be the first commit id for the diff will be the original values commit id.')

    parser.add_argument('-nt', '--no-translated-col', dest='no_translated_col', action='store_true', default=False,
                        required=False, help="Don't include a column for translation.")

    args = parser.parse_args()
    repo_path = args.repo_path if args.repo_path is not None else get_git_root(get_proj_dir())
    output_path = args.output_path
    commit_1_id = args.commit_1_id
    output_type = args.output_type
    show_translated_col = not args.no_translated_col
    language_updates_file = args.language_file
    translation_dict = args.translation_dict
    lang = args.language
    if lang is not None:
        commit_1_id = get_commit_for_language(lang, language_updates_file)

    if commit_1_id is None:
        print('Either the first commit or language flag need to be specified.  If specified, the language file, ' +
              LANG_FILENAME + ', may not have the latest commit for the language.', file=sys.stderr)
        parser.print_help(sys.stderr)
        sys.exit(1)

    if translation_dict and lang:
        trans_dict_commits = [x.strip() for x in translation_dict.split(',')]
        if len(trans_dict_commits) >= 2:
            dict_orig_commit = trans_dict_commits[0]
            dict_translated_commit = trans_dict_commits[1]
        else:
            dict_orig_commit = commit_1_id
            dict_translated_commit = trans_dict_commits[0]

        translation_dict = extract_translations(
            orig_file_iter=list_paths(get_tree(repo_path, dict_orig_commit)),
            translated_file_iter=list_paths(get_tree(repo_path, dict_translated_commit)),
            orig_filename=DEFAULT_PROPS_FILENAME,
            translated_filename=get_lang_bundle_name(lang))

    commit_2_id = args.commit_2_id
    show_commits = not args.no_commits

    changes = get_property_files_diff(repo_path, commit_1_id, commit_2_id)
    processing_result = convert_to_output(changes,
                                          commit1_id=get_commit_id(repo_path, commit_1_id) if show_commits else None,
                                          commit2_id=get_commit_id(repo_path, commit_2_id) if show_commits else None,
                                          translation_dict=translation_dict,
                                          show_translated_col=show_translated_col,
                                          separate_deleted=True)

    # based on https://stackoverflow.com/questions/60208/replacements-for-switch-statement-in-python
    {
        OutputType.csv: write_results_to_csv,
        OutputType.xlsx: write_results_to_xlsx
    }[output_type](processing_result, output_path)

    sys.exit(0)


if __name__ == "__main__":
    main()
