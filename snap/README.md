## Installing Snap

An Autopsy [snap package](https://snapcraft.io/) file can be installed by running `sudo snap install --dangerous autopsy.snap`.  The `--dangerous` needs to be specified because the snap package isn't signed (see [install modes](https://snapcraft.io/docs/install-modes#heading--dangerous) for more information).  Super-priveleged may need to be connected.  This can be done manually by running `snap connections autopsy` to determine any missing connections, and then running `snap connect autopsy:home` replacing `home` with the name of the plug.  Another option is to run this script, which will connect all missing plugs: `snap connections autopsy | sed -nE 's/^[^ ]* *([^ ]*) *- *- *$/\1/p' | xargs -I{} sudo snap connect {}`.  One other possible option may be to install the application with `--devmode` instead of `--dangerous`.

## Running Autopsy

After installing Autopsy, you should be able to run with `autopsy`.  Snap also typically installs a `.desktop` file for your launcher.  If you want to perform an ingest on a local disk, you will need to run with permissions for disks in the `/dev` folder.  On Ubuntu, that command will be `sudo -g disk autopsy` as `disk` group permissions will give access to that folder.

## Generating The Snap Package

A [snap package](https://snapcraft.io/) of Autopsy can be generated using the [`snapcraft.yml`](./snapcraft.yaml) file.  You will need [snapcraft](https://snapcraft.io/) on your system and [lxd](https://snapcraft.io/lxd) works well for virtualization while building the snap package.  Since snapcraft needs virtualization to create the snap package, your computer's hardware will need to support virtualization and any relevant settings will need to be enabled.  From testing as of November 2022, VirtualBox and WSL are not good build environments.  Once the development environment has been set up, a snap package can be built with this command: `snapcraft --use-lxd --debug` run from this directory.  If you want to build async, but still get logs, you can run something like this: `nohup snapcraft --use-lxd --debug > ./output.log 2>&1 < /dev/null &`.

## Updating Versions for Snap

The version of Autopsy in the [`snapcraft.yml`](./snapcraft.yaml) can be updated by calling [`version_update.py`](./version_update/version_update.py) with a command like `python version_update.py -s sleuthkit_release_tag -a autopsy_release_tag -v snapcraft_version_name`.  You will likely need to install the python dependencies in the [requirements.txt](./version_update/requirements.txt) with a command like: `pip install -r requirements.txt`.

The version of Autopsy can be updated manually by modifying fields relating to git repositories and commits in [`snapcraft.yml`](./snapcraft.yaml) under `parts.autopsy` and `parts.sleuthkit`.  Specifically `source`, `source-branch`, and `source-tag`.  More information can be found [here](https://snapcraft.io/docs/snapcraft-yaml-reference).