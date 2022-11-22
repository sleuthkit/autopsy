import sys
import argparse
from typing import Union
from os.path import join, dirname, abspath, realpath
import hashlib
from urllib.request import urlopen
import re

HOMEBREW_RUBY_PATH = join(dirname(dirname(abspath(realpath(__file__)))), 'autopsy4.rb')
TSK_URL_KEY = "TSK_RESOURCE_URL"
TSK_SHA256_KEY = "TSK_RESOURCE_SHA256"
AUTOPSY_URL_KEY = "AUTOPSY_RESOURCE_URL"
AUTOPSY_SHA256_KEY = "AUTOPSY_RESOURCE_SHA256"

MAX_FILE_SIZE = 100 * 1024 * 1024 * 1024


def hash_url(url: str) -> str:
    remote = urlopen(url)
    total_read = 0
    hasher = hashlib.sha256()

    while total_read < MAX_FILE_SIZE:
        data = remote.read(4096)
        total_read += 4096
        hasher.update(data)

    return hasher.hexdigest()


def replace_variable(file_contents: str, var_key: str, var_value: str) -> str:
    search_regex = rf'^(\s*{re.escape(var_key)}\s*=\s*").+?("[^"]*)$'
    replacement = rf'\g<1>{var_value}\g<2>'
    return re.sub(search_regex, replacement, file_contents, flags=re.M)


def update_versions(tsk_resource_url: str, autopsy_resource_url: str, file_path: Union[str, None]):
    tsk_sha256 = hash_url(tsk_resource_url)
    autopsy_sha256 = hash_url(autopsy_resource_url)

    file_path = file_path if file_path is not None and len(file_path.strip()) > 0 else HOMEBREW_RUBY_PATH

    with open(file_path, 'r') as f:
        content = f.read()

    for k, v in [
        (TSK_URL_KEY, tsk_resource_url),
        (TSK_SHA256_KEY, tsk_sha256),
        (AUTOPSY_URL_KEY, autopsy_resource_url),
        (AUTOPSY_SHA256_KEY, autopsy_sha256)
    ]:
        content = replace_variable(content, k, v)

    with open(file_path, 'w') as f:
        f.write(content)


def main():
    parser = argparse.ArgumentParser(
        description="Updates homebrew file with current versions of autopsy and sleuthkit",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter)

    parser.add_argument('-s', '--sleuthkit_resource_url', required=True, dest='sleuthkit_resource_url', type=str,
                        help='The compressed build file system of the sleuthkit release ' +
                         '(i.e. https://github.com/sleuthkit/sleuthkit/releases/download/sleuthkit-4.11.1/sleuthkit-4.11.1.tar.gz)')
    parser.add_argument('-a', '--autopsy_resource_url', required=True, dest='autopsy_resource_url', type=str,
                        help='The compressed build file system of the autopsy release ' +
                             '(i.e. https://github.com/sleuthkit/autopsy/releases/download/autopsy-4.19.2/autopsy-4.19.2.zip)')

    parser.add_argument('-p', '--homebrew_path', dest='homebrew_path', type=str, default=HOMEBREW_RUBY_PATH,
                        help='Path to homebrew file.')

    args = parser.parse_args()
    update_versions(
        tsk_resource_url=args.sleuthkit_resource_url,
        autopsy_resource_url=args.autopsy_resource_url,
        file_path=args.homebrew_path
    )


if __name__ == '__main__':
    main()
