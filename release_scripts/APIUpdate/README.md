# APIDiff

## Overview

This code can be used to determine the public API changes between the previous version of Autopsy jars to current version of Autopsy jars.  Based on those changes, this code can update version numbers (release, implementation, specification, dependency version).

## Sample Usage & Procedure

1. Before starting download the nbm jar files from the [previous release](https://github.com/sleuthkit/autopsy/releases/).  The jar files will be located in the zip file or the installation directory (i.e. `C:\Program Files\Autopsy-x.xx.x`) at `autopsy/modules`.  
2. Make sure you build the current source directory in order for the program to find the new compiled jars.
3. This code can be called from this directory with a command like: `java -jar APIUpdate-1.0-jar-with-dependencies.jar -p C:\path\to\prev\vers\jars\` to get api updates.
    - You can specify the `-u` flag to make updates in source code making the command: `java -jar APIUpdate-1.0-jar-with-dependencies.jar -p C:\path\to\prev\vers\jars\ -u`.  
    - You can also add `  >C:\path\to\outputdiff.txt 2>&1` to output to a file. 

## Arguments

```
usage: APIUpdate
 -c,--curr-path <path>          The path to the current version jar files
 -cv,--curr-version <version>   The current version number
 -p,--prev-path <path>          The path to the previous version jar files
 -pv,--prev-version <version>   The previous version number
 -s,--src-path <path>           The path to the root of the autopsy report
 -u,--update                    Update source code versions
```
