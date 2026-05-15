## Installing from Homebrew

Autopsy can be installed from homebrew by running brew with [`autopsy4.rb`](./autopsy4.rb) in this directory.  Before you run the command, you may want to remove any previous versions of Autopsy and The Sleuth Kit, especially if they are installed `/usr/local/` as homebrew installs binaries and libraries in the same directory, which may cause conflicts.  So the full path would be something like: `brew install --debug --build-from-source --verbose ./autopsy4.rb` when executing from this directory.  After that point, you should be able to run autopsy from the command line by calling `autopsy`.  

## Updating Versions for Homebrew

The version of Autopsy in the homebrew script can be updated by calling [`version_update.py`](./version_update/version_update.py) with a command like `python version_update.py -s https://path/to/sleuthkit-version.tar.gz -a https://path/to/autopsy-version.zip`.  You will likely need to install the python dependencies in the [requirements.txt](./version_update/requirements.txt) with a command like: `pip install -r requirements.txt`.

The version of Autopsy can be updated manually by modifying the following variables in [`autopsy4.rb`](./autopsy4.rb): 
- `AUTOPSY_RESOURCE_URL`: the location of the autopsy platform zip like `https://github.com/sleuthkit/autopsy/releases/download/autopsy-4.19.2/autopsy-4.19.2.zip`
- `AUTOPSY_RESOURCE_SHA256`: the SHA-256 of the autopsy platform zip.  This can be calculated by running `curl <url> | sha256sum`
- `TSK_RESOURCE_URL`: the location of the sleuthkit compressed files like `https://github.com/sleuthkit/sleuthkit/releases/download/sleuthkit-4.11.1/sleuthkit-4.11.1.tar.gz`
- `TSK_RESOURCE_SHA256`: the SHA-256 of the sleuthkit compressed files.


*There is more information in Jira 8425.*
