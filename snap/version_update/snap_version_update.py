import sys
import argparse
import ruamel.yaml
from typing import Union
from os.path import join, dirname, abspath, realpath

SNAPCRAFT_YAML_PATH = join(dirname(dirname(abspath(realpath(__file__)))), 'snapcraft.yaml')
SLEUTHKIT_REPO = 'https://github.com/sleuthkit/sleuthkit.git'
AUTOPSY_REPO = 'https://github.com/sleuthkit/autopsy.git'


def update_versions(sleuthkit_version_tag: str,
                    autopsy_version_tag: str,
                    snapcraft_version: str,
                    snapcraft_yaml_path: Union[str, None],
                    sleuthkit_repo: Union[str, None],
                    autopsy_repo: Union[str, None]):

    snapcraft_yaml_path = snapcraft_yaml_path if snapcraft_yaml_path is not None and len(
        snapcraft_yaml_path.strip()) > 0 else SNAPCRAFT_YAML_PATH
    sleuthkit_repo = sleuthkit_repo if sleuthkit_repo is not None and len(
        sleuthkit_repo.strip()) > 0 else SLEUTHKIT_REPO
    autopsy_repo = autopsy_repo if autopsy_repo is not None and len(
        autopsy_repo.strip()) > 0 else AUTOPSY_REPO

    yaml = ruamel.yaml.YAML()
    with open(snapcraft_yaml_path) as snapcraft_file:
        yaml_dict = yaml.load(snapcraft_file)

    yaml_dict['version'] = snapcraft_version

    yaml_dict['parts']['sleuthkit']['source'] = sleuthkit_repo
    yaml_dict['parts']['sleuthkit']['source-tag'] = sleuthkit_version_tag
    yaml_dict['parts']['sleuthkit'].pop('source-branch', None)

    yaml_dict['parts']['autopsy']['source'] = autopsy_repo
    yaml_dict['parts']['autopsy']['source-tag'] = autopsy_version_tag
    yaml_dict['parts']['autopsy'].pop('source-branch', None)

    with open(snapcraft_yaml_path, "w") as snapcraft_file:
        yaml.dump(yaml_dict, snapcraft_file)


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Updates snapcraft.yml file with current versions of autopsy and sleuthkit",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter)

    parser.add_argument('-s', '--sleuthkit_tag', required=True, dest='sleuthkit_version_tag', type=str,
                        help='The git tag to use for sleuthkit.')
    parser.add_argument('-a', '--autopsy_tag', required=True, dest='autopsy_version_tag', type=str,
                        help='The git tag to use for autopsy.')
    parser.add_argument('-v', '--version', required=True, dest='snapcraft_version', type=str,
                        help='Version for snapcraft metadata.')

    parser.add_argument('-p', '--snapcraft_path', dest='snapcraft_yaml_path', type=str, default=SNAPCRAFT_YAML_PATH,
                        help='Path to snapcraft.yaml.')
    parser.add_argument('--sleuthkit_repo', dest='sleuthkit_repo', type=str, default=SLEUTHKIT_REPO,
                        help='Location of sleuthkit repo.')
    parser.add_argument('--autopsy_repo', dest='autopsy_repo', type=str, default=AUTOPSY_REPO,
                        help='Location of sleuthkit repo.')

    args = parser.parse_args()
    update_versions(
        sleuthkit_version_tag=args.sleuthkit_version_tag,
        autopsy_version_tag=args.autopsy_version_tag,
        snapcraft_version=args.snapcraft_version,
        snapcraft_yaml_path=args.snapcraft_yaml_path,
        sleuthkit_repo=args.sleuthkit_repo,
        autopsy_repo=args.autopsy_repo
    )


if __name__ == '__main__':
    sys.exit(main())
