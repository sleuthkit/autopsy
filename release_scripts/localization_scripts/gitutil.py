"""Functions relating to using git and GitPython with an existing repo.
"""

from git import Repo, Diff, Blob, Tree
from typing import List, Union, Iterator, Tuple, Any
from itemchange import ItemChange, get_changed
from pathlib import Path
from propentry import PropEntry
from propsutil import DEFAULT_PROPS_EXTENSION, get_entry_dict


def get_text(blob: Blob) -> str:
    return blob.data_stream.read().decode('utf-8')


def get_git_root(child_path: str) -> str:
    """
    Taken from https://stackoverflow.com/questions/22081209/find-the-root-of-the-git-repository-where-the-file-lives,
    this obtains the root path of the git repo in which this file exists.

    Args:
        child_path:  The path of a child within the repo.

    Returns: The repo root path.

    """
    git_repo = Repo(child_path, search_parent_directories=True)
    git_root = git_repo.git.rev_parse("--show-toplevel")
    return git_root


def get_changed_from_diff(rel_path: str, diff: Diff) -> List[ItemChange]:
    """Determines changes from a git python diff.

    Args:
        rel_path (str): The relative path for the properties file.
        diff (Diff): The git python diff.

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


def get_rel_path(diff: Diff) -> Union[str, None]:
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
        return None


def get_diff(repo_path: str, commit_1_id: str, commit_2_id: str) -> Any:
    """Determines the diff between two commits.

    Args:
        repo_path (str): The local path to the git repo.
        commit_1_id (str): The initial commit for the diff.
        commit_2_id (str): The latest commit for the diff.

    Returns:
        The determined diff.
    """
    repo = Repo(repo_path, search_parent_directories=True)
    commit_1 = repo.commit(commit_1_id)
    commit_2 = repo.commit(commit_2_id)
    return commit_1.diff(commit_2)


def get_commit_id(repo_path: str, commit_id: str) -> str:
    """Determines the hash for head commit.  This does things like fetch the id of head if 'HEAD' is provided.

    Args:
        repo_path: The path to the repo.
        commit_id: The id for the commit.

    Returns:
        The hash for the commit in the repo.
    """
    repo = Repo(repo_path, search_parent_directories=True)
    commit = repo.commit(commit_id.strip())
    return str(commit.hexsha)


def get_property_files_diff(repo_path: str, commit_1_id: str, commit_2_id: str,
                            property_file_extension: str = DEFAULT_PROPS_EXTENSION) -> Iterator[ItemChange]:
    """Determines the item changes within property files as a diff between two commits.

    Args:
        repo_path (str): The repo path.
        commit_1_id (str): The first git commit.
        commit_2_id (str): The second git commit.
        property_file_extension (str): The extension for properties files to gather.

    Returns:
        All found item changes in values of keys between the property files.
    """

    diffs = get_diff(repo_path, commit_1_id.strip(), commit_2_id.strip())
    for diff in diffs:
        rel_path = get_rel_path(diff)
        if rel_path is None or not rel_path.endswith('.' + property_file_extension):
            continue

        yield from get_changed_from_diff(rel_path, diff)


def list_paths(root_tree: Tree, path: Path = Path('.')) -> Iterator[Tuple[str, Blob]]:
    """
    Given the root path to serve as a prefix, walks the tree of a git commit returning all files and blobs.
    Repurposed from: https://www.enricozini.org/blog/2019/debian/gitpython-list-all-files-in-a-git-commit/

    Args:
        root_tree: The tree of the commit to walk.
        path: The path to use as a prefix.

    Returns: A tuple iterator where each tuple consists of the path as a string and a blob of the file.

    """
    for blob in root_tree.blobs:
        ret_item = (str(path / blob.name), blob)
        yield ret_item
    for tree in root_tree.trees:
        yield from list_paths(tree, path / tree.name)


def get_tree(repo_path: str, commit_id: str) -> Tree:
    """
    Retrieves the tree that can be walked for files and file content at the specified commit.

    Args:
        repo_path: The path to the repo or a child directory of the repo.
        commit_id: The commit id.

    Returns: The tree.
    """
    repo = Repo(repo_path, search_parent_directories=True)
    commit = repo.commit(commit_id.strip())
    return commit.tree


def get_property_file_entries(repo_path: str, at_commit: str = 'HEAD',
                              property_file_extension: str = DEFAULT_PROPS_EXTENSION) -> Iterator[PropEntry]:
    """
    Retrieves all property files entries returning as an iterator of PropEntry objects.

    Args:
        repo_path: The path to the git repo.
        at_commit: The commit to use.
        property_file_extension: The extension to use for scanning for property files.

    Returns: An iterator of PropEntry objects.
    """
    for item in list_paths(get_tree(repo_path, at_commit)):
        path, blob = item
        if path.endswith(property_file_extension):
            for key, val in get_entry_dict(get_text(blob)).items():
                yield PropEntry(path, key, val)
