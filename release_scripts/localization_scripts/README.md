## Description

This folder provides tools to handle updates of bundle files for language localization.  There are three main scripts:
- `allbundlesscript.py` - generates a file containing the relative path of the bundle file, the key, and the value for each property.
- `diffscript.py` - determines the property values that have changed between two commits and generates a file containing the relative path, the key, the previous value, the new value, and the change type (addition, deletion, change).
- `updatepropsscript.py` - Given a csv or xlsx file containing the relative path of the bundle, the key, and the new value, will update the property values for a given language within the project.

All of these scripts provide more details on usage by calling the script with `-h`. 

## Basic Localization Update Workflow

1. Call `python3 diffscript.py <output path> -l <language>` to generate a file containing differences in properties file values from the language's previous commit to the `HEAD` commit.  The language identifier should be the abbreviated identifier used for the bundle (i.e. 'ja' for Japanese).  The output path should be specified as a relative path with the dot slash notation (i.e. `./outputpath.xlsx`) or an absolute path.
2. Update csv file with translations
3. Call `python3 updatepropsscript.py <input path> -l <language>` to update properties files based on the newly generated file.  The file should be formatted such that the columns are bundle relative path, property files key, original value (or empty column), translated value and commit id for the latest commit id for which these changes represent.  The commit id only needs to be in the header row.  The output path should be specified as a relative path with the dot slash notation (i.e. `./outputpath.xlsx`) or an absolute path.

## Localization Generation for the First Time
First-time updates should follow a similar procedure except that instead of calling `diffscript.py`, call `python3 allbundlesscript <output path>` to generate a file with relative paths of bundle files, property file keys, property file values.  The output path should be specified as a relative path with the dot slash notation (i.e. `./inputpath.xlsx`) or an absolute path.

##Unit Tests
Unit tests can be run from this directory using `python3 -m unittest`.