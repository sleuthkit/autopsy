# Use Cases

This doc outines some of the ways that the database can be used in an investigation.

## Definitions:

NOTE: These are in flux and subject to change.

- Feature: A single name/value pair that is associated with a file or Autopsy artifact.  Examples include:
 - MD5 hash of a file
 - Path of a file
 - Domain name in a web history artifact
 - Email address in a phone contact book or email message


## Finding Other Instances of a Feature

If you find a file or Autopsy artifact (such as a Web History item), there is a content viewer in the bottom right that will show you other cases that had this same file or that had items with the same feature (such as Domain name).   You will also be able to see what other data sources in the same case had this feature. 

To use this feature, you must have the EAM ingest module configured so that it sends data to the database for future correlations. You can still make correlations about past instances of a feature even if the EAM ingest module was not enabled for the current case.


## Alerting When Previously Notable Features Occur

You can configure the EAM to record which features were associated with files and artifacts that were evidence (or notable).  To do this:
- Use the EAM options panel to associate one or more tag names as being "BAD"
- Have the EAM ingest module enabled so that it inserts features into the EAM for the current case. 
- Tag files or artifacts using this tag name and EAM will record that in the EAM.
- When that feature is seen again in future cases (and the EAM ingest module is enabled on those cases), an Interesting Item result will be created in the directory tree to alert you to this. 


## Global Hash Database

You can import hash databases into the EAM so that all Autopsy clients can use it instead of having local copies of the databases.  You can do this for both "KNOWN" databases (i.e. NIST NSRL) and "KNOWN BAD"/notable databases.  

Currently, it will take only .idx files as inputs (which is the index file that The Sleuth Kit generates).  

To use this feature, you need to:
- import that databases
- Enable the EAM ingest module when a data source is added
- You will see the results in the Hash Sets part of the tree

