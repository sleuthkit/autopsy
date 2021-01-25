## Description

This folder provides tools to handle updates of bundle files for language localization.  There are three main scripts:
- `allbundlesscript.py` - generates a file containing the relative path of the bundle file, the key, and the value for each property.
- `diffscript.py` - determines the property values that have changed between two commits and generates a file containing the relative path, the key, the previous value, the new value, and the change type (addition, deletion, change).
- `updatepropsscript.py` - Given a csv or xlsx file containing the relative path of the bundle, the key, and the new value, will update the property values for a given language within the project.

All of these scripts provide more details on usage by calling the script with `-h`. 

## Dependencies

This script requires the python libraries: gitpython, jproperties, pyexcel-xlsx, xlsxwriter and pyexcel along with python >= 3.9.1 or the requirements.txt file found in this directory can be used (more information on that can be found [here](https://packaging.python.org/guides/installing-using-pip-and-virtual-environments/#using-requirements-files)).  As a consequence of gitpython, this project also requires git >= 1.7.0.

## Basic Localization Update Workflow

1. Call `python3 diffscript.py <output path> -l <language> -td <last language update commit id>` to generate a file containing differences in properties file values from the language's previous commit to the `HEAD` commit.  The language identifier should be the abbreviated identifier used for the bundle (i.e. 'ja' for Japanese).  The last language update commit id is the SHA-1 commit id for the last time this language (i.e. ja/Japanese) was last updated.  The program will try to cross-reference English values with the corresponding language values from the last known point of consistency to automatically determine some translations.  For this reason, it is important that the language file values (i.e. Bundle_ja.properties) are consistent with the English values at the commit id recorded in lastupdated.properties. The output path should be specified as a relative path with the dot slash notation (i.e. `./outputpath.xlsx`) or an absolute path.
2. Update the generated file(s) with translations where needed.
3. Call `python3 updatepropsscript.py <input path> -l <language>` to update properties files based on the newly generated file.  The file should be formatted such that the columns are the bundle's relative path to the repo, the property files key, the original value (or empty column), the translated value and commit id for the earliest and latest commit id for which these changes represent.  The commit id only needs to be in the header row.  The output path should be specified as a relative path with the dot slash notation (i.e. `./outputpath.xlsx`) or an absolute path.

## Localization Generation for the First Time
First-time updates should follow a similar procedure except that instead of calling `diffscript.py`, call `python3 allbundlesscript <output path>` to generate a file with relative paths of bundle files, property file keys, property file values.  The output path should be specified as a relative path with the dot slash notation (i.e. `./inputpath.xlsx`) or an absolute path.

## Unit Tests
Unit tests can be run from this directory using `python3 -m unittest`.