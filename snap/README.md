## Installing Snap

An Autopsy [snap package](https://snapcraft.io/) file can be installed by running `sudo snap install autopsy.snap --classic --dangerous`.  The `--classic` flag gives the snap package access to necessary system resources (see [confinement](https://snapcraft.io/docs/snap-confinement) for more information) and `--dangerous` needs to be specified because the snap package isn't signed (see [install modes](https://snapcraft.io/docs/install-modes#heading--dangerous) for more information).

## Generating The Snap Package

A [snap package](https://snapcraft.io/) of Autopsy can be generated using the [`snapcraft.yml`](./snapcraft.yaml) file.  You will need [snapcraft](https://snapcraft.io/) on your system and [lxd](https://snapcraft.io/lxd) works well for virtualization while building the snap package.  Since snapcraft needs virtualization to create the snap package, your computer's hardware will need to support virtualization and any relevant settings will need to be enabled.  From testing as of November 2022, VirtualBox and WSL are not good build environments.  Once the development environment has been set up, a snap package can be built with this command: `snapcraft --use-lxd --debug` run from this directory.

## Updating Versions for Snap

The version of Autopsy in the [`snapcraft.yml`](./snapcraft.yaml) can be updated by calling [`version_update.py`](./version_update/version_update.py) with a command like `python version_update.py -s sleuthkit_release_tag -a autopsy_release_tag -v snapcraft_version_name`.  You will likely need to install the python dependencies in the [requirements.txt](./version_update/requirements.txt) with a command like: `pip install -r requirements.txt`.

The version of Autopsy can be updated manually by modifying fields relating to git repositories and commits in [`snapcraft.yml`](./snapcraft.yaml) under `parts.autopsy` and `parts.sleuthkit`.  Specifically `source`, `source-branch`, and `source-tag`.  More information can be found [here](https://snapcraft.io/docs/snapcraft-yaml-reference).

*There is more information in Jira 8425.*
