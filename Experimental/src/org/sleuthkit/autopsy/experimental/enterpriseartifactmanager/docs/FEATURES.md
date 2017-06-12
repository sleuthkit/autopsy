# Features

Once you have configured everything, created a case, and have run the ingest of at least one data source,
you can make use of some other exciting features that are described below.

## Content Viewer

This module adds a new tab to the [Content Viewer](http://sleuthkit.org/autopsy/docs/user-docs/4.3/content_viewer_page.html).
The tab for this module is called "Other Cases".
It can display data that is found in other cases, other data sources for the same case, or imported global artifacts.

If at least one other case or data source has been ingested with this module enabled,
there is a potential that data will be displayed in the Other Cases content viewer.
If the selected file or artifact is associated by one of the supported Correlation Types,
to one or more file(s) or artifact(s) in the database, the associated files/artifacts will be displayed.
Note: the Content Viewer will display ALL associated files and artifacts available in the database.
It ignores the user's [enabled/disabled Correlation Types](CONFIG.md#manage-correlation-types).

If the user right-clicks on a row, a menu will be displayed.
This menu has several options.
1. [Show Commonality Details](FEATURES.md#show-commonality-details)
2. [Save to CSV](FEATURES.md#save-to-csv)
3. [Show Case Details](FEATURES.md#show-case-details)
4. [Select All](FEATURES.md#select-all)

Click option for more details.

### Rows in the table

By default, the rows in the content viewer will have background colors to indicate if they
are known to be of interest.
Files/artifacts that are Known Bad will have a Red background, Unknown will have Yellow background,
and Known will have a White background.

The user can click on any column heading to sort by the values in that column.

### Show Commonality Details

The concept of Commonality simply means, how common is the selected file.
The value is the percentage of case/data source tuples that have the selected file or artifact.

### Save to CSV

This option will save ALL SELECTED rows in the Content Viewer table to a CSV file.
By default, the CSV file is saved into the Export directory inside the currently open Autopsy case,
but the user is free to select a different location.

Note: if you want to copy/paste rows, it is usually possible to use CTRL+C to copy the
selected rows and then CTRL+V to paste them into a file, but it will not be CSV formatted.

### Show Case Details

This option will open a dialog that displays all of the relevant details for the selected case.
The details will include:
1. Case UUID
2. Case Name
3. Case Creation Date
4. Case Examiner contact information
5. Case Examiner's notes

These details would have been entered by the examiner of the selected case, by visiting
the Case -> Enterprise Artifact Manager Case Details menu, when that case was open.

### Select All

This option will select all rows in the Content Viewer table.

## Interesting Items tree

In the Results tree of an open case is an entry called Interesting Items.
When this module is enabled, all of the enabled Correlatable Types will cause
matching files to be added to this Interesting Items tree during ingest.

As an example, if the FILES Correlatable Type is enabled, and the ingest is
currently processing a file, for example "badfile.exe", and the MD5 hash for that
file already exists in the database as a KNOWN BAD file, then an entry in the Interesting Items tree
will be added for the current instance of "badfile.exe" in the data source currently being ingested.

The same type of thing will happen for each [enabled Correlatable Type](CONFIG.md#manage-correlation-types).

In the case of the PHONE correlatable type, the Interesting Items tree will start 
a sub-tree for each phone number. The sub-tree will then contain each instance of that
Known Bad phone number.

## Edit Enterprise Artifact Manager Case Details

By default, Autopsy lets you edit Case Details in the Case menu. 
When this module is enabled, there is an additional option in the Case menu, 
called "Enterprise Artifact Manager Case Details".

This is where the examiner can store a number of details about the case.
1. The organization of the case examiner.
2. The contact information of the case examiner.
3. The case examiner's case notes.

To define the organization of the case examiner, simply select the organization name
from the dropdown box.
If the organization is not listed, you can click [Add New Organization](FEATURES.md#adding-a-new-organization) button.
Once the new organization is added, it should be available in the dropdown box.

## Adding a New Organization

An Organization can have two purposes in this module.

1. It defines the Organization that the forensic examiner belongs to. 
This organization is selected or added when Editing Correlation Case Details.
2. It defines the Organization that is the source of a Globally Known Artifact List.
This organization is selected or added during Import of a Globally Known Artifact hash list.

When adding a new organization, only the Organization Name is required.
It is recommended to also include a Point of Contact for that organization.
This will be someone that is a manager or team lead at that Organization that
could be contacted for any questions about a case or a shared Globally Known Artifact
hash list.

Click OK to save the new Organization.
