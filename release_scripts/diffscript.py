"""This script determines the updated, added, and deleted properties from the '.properties-MERGED' files
and generates a csv file containing the items changed.  This script requires gitpython and jproperties.
"""

from git import Repo
from typing import List, Dict, Tuple
import re
import csv
from jproperties import Properties
import sys


class ItemChange:
    def __init__(self, rel_path: str, key: str, prev_val: str, cur_val: str):
        """Describes the change that occurred for a particular key of a properties file.

        Args:
            rel_path (str): The relative path of the properties file.
            key (str): The key in the properties file.
            prev_val (str): The previous value for the key.
            cur_val (str): The current value for the key.
        """
        self.rel_path = rel_path
        self.key = key
        self.prev_val = prev_val
        self.cur_val = cur_val
        if ItemChange.has_str_content(cur_val) and not ItemChange.has_str_content(prev_val):
            self.type = 'ADDITION'
        elif not ItemChange.has_str_content(cur_val) and ItemChange.has_str_content(prev_val):
            self.type = 'DELETION'
        else:
            self.type = 'CHANGE'

    @staticmethod
    def has_str_content(content: str):
        """Determines whether or not the content is empty or None.

        Args:
            content (str): The text.

        Returns:
            bool: Whether or not it has content.
        """
        return content is not None and len(content.strip()) > 0

    @staticmethod
    def get_headers() -> List[str]:
        """Returns the csv headers to insert when serializing a list of ItemChange objects to csv.

        Returns:
            List[str]: The column headers
        """
        return ['Relative Path', 'Key', 'Change Type', 'Previous Value', 'Current Value']

    def get_row(self) -> List[str]:
        """Returns the list of values to be entered as a row in csv serialization.

        Returns:
            List[str]:  The list of values to be entered as a row in csv serialization.
        """
        return [
            self.rel_path,
            self.key,
            self.type,
            self.prev_val,
            self.cur_val]


def get_entry_dict(diff_str: str) -> Dict[str, str]:
    """Retrieves a dictionary mapping the properties represented in the string.

    Args:
        diff_str (str): The string of the properties file.

    Returns:
        Dict[str,str]: The mapping of keys to values in that properties file.
    """
    props = Properties()
    props.load(diff_str, "utf-8")
    return props.properties


def get_item_change(rel_path: str, key: str, prev_val: str, cur_val: str) -> ItemChange:
    """Returns an ItemChange object if the previous value is not equal to the current value.

    Args:
        rel_path (str): The relative path for the properties file.
        key (str): The key within the properties file for this potential change.
        prev_val (str): The previous value.
        cur_val (str): The current value.

    Returns:
        ItemChange: The ItemChange object or None if values are the same.
    """
    if (prev_val == cur_val):
        return None
    else:
        return ItemChange(rel_path, key, prev_val, cur_val)


def get_changed(rel_path: str, a_str: str, b_str: str) -> List[ItemChange]:
    """Given the relative path of the properties file that

    Args:
        rel_path (str): The relative path for the properties file.
        a_str (str): The string representing the original state of the file.
        b_str (str): The string representing the current state of the file.

    Returns:
        List[ItemChange]: The changes determined.
    """
    print('Retrieving changes for {}...'.format(rel_path))
    a_dict = get_entry_dict(a_str)
    b_dict = get_entry_dict(b_str)
    all_keys = set().union(a_dict.keys(), b_dict.keys())
    mapped = map(lambda key: get_item_change(
        rel_path, key, a_dict.get(key), b_dict.get(key)), all_keys)
    return filter(lambda entry: entry is not None, mapped)


def get_text(blob) -> str:
    return blob.data_stream.read().decode('utf-8')


def get_changed_from_diff(rel_path: str, diff) -> List[ItemChange]:
    """Determines changes from a git python diff.

    Args:
        rel_path (str): The relative path for the properties file.
        diff: The git python diff.

    Returns:
        List[ItemChange]: The changes in properties.
    """
    # an item was added
    if diff.change_type == 'A':
        changes = get_changed(rel_path, '', get_text(diff.b_blob))
    # an item was deleted
    elif diff.change_type == 'D':
        changes = get_changed(rel_path, get_text(diff.a_blob), '')
    # an item was modified
    elif diff.change_type == 'M':
        changes = get_changed(rel_path, get_text(
            diff.a_blob), get_text(diff.b_blob))
    else:
        changes = []

    return changes


def get_rel_path(diff) -> str:
    """Determines the relative path based on the git python.

    Args:
        diff: The git python diff.

    Returns:
        str: The determined relative path.
    """
    if diff.b_path is not None:
        return diff.b_path
    elif diff.a_path is not None:
        return diff.a_path
    else:
        return '<Uknown Path>'


def write_diff_to_csv(repo_path: str, output_path: str, commit_1_id: str, commit_2_id: str):
    """Determines the changes made in '.properties-MERGED' files from one commit to another commit.

    Args:
        repo_path (str): The local path to the git repo.
        output_path (str): The output path for the csv file.
        commit_1_id (str): The initial commit for the diff.
        commit_2_id (str): The latest commit for the diff.
    """
    repo = Repo(repo_path)
    commit_1 = repo.commit(commit_1_id)
    commit_2 = repo.commit(commit_2_id)

    diffs = commit_1.diff(commit_2)
    with open(output_path, 'w', newline='') as csvfile:
        writer = csv.writer(csvfile)
        writer.writerow(ItemChange.get_headers())

        for diff in diffs:
            rel_path = get_rel_path(diff)
            if not rel_path.endswith('.properties-MERGED'):
                continue

            changes = get_changed_from_diff(rel_path, diff)

            for item_change in changes:
                writer.writerow(item_change.get_row())


def print_help():
    """Prints a quick help message.
    """
    print("diffscript.py [path to repo] [csv output path] [commit for previous release] [commit for current release (optional; defaults to 'HEAD')]")


def main():
    if len(sys.argv) <= 3:
        print_help()
        sys.exit(1)

    repo_path = sys.argv[1]
    output_path = sys.argv[2]
    commit_1_id = sys.argv[3]
    commit_2_id = sys.argv[4] if len(sys.argv) > 4 else 'HEAD'

    write_diff_to_csv(repo_path, output_path, commit_1_id, commit_2_id)

    sys.exit(0)


if __name__ == "__main__":
    main()
