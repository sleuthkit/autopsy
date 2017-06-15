# Configuration

## Database Setup

There are 2 choices for database platforms: SQLite and PostgreSQL.
1. SQLite is a database in a file stored locally on the same host that is running Autopsy.
There is nothing to do to setup this database. It will be created by Autopsy on your
behalf, if it doesn't already exist.
2. PostgreSQL is a database server that can be run either on the same host that is
running Autopsy or on a remote server. To use PostgreSQL with the EnterpriseArtifactManager module,
you will need the server to be running, have an existing database named "enterpriseartifactmanagerdb"
and have an existing user/pass with ownership of the enterpriseartifactmanagerdb database.
The tables and indices will be automatically created by Autopsy.
See the [Autopsy multi-user settings documentation[(http://sleuthkit.org/autopsy/docs/user-docs/4.3/install_postgresql.html) for help setting up your PostgreSQL server.

## Enable Module and Configure Database Settings

In the menu go to: Tools -> Options -> EnterpriseArtifactManager

1. Check the box to Enable Enterprise Artifact Manager. This will enable the Database Platform dropdown and Configure button.
2. In the dropdown, select the database platform that you want to use.
3. Click the Configure button to configure the settings for the chosen database platform.
4. Click the Apply button to save your database configuration settings.

### Configure SQLite Database Settings

There is only one step here, to specify the path and filename for the database.
You can accept the default value or use the Open button to choose another path.
The database file name can be called anything you want, but it is convenient to
give it a ".db" suffix.

Once you have selected the path, click the Test Connection button.
If you see a green check next to the button, everything is ready to go.
If you see a red check next to the button, there is a problem with the path
you selected and you'll have to resolve that problem.

Once the test passes, click the Save button to save your selection and close the window.

### Configure PostgreSQL Database Settings

For PostgreSQL all values are required, but some defaults are provided for convenience.

1. Host Name/IP is the hostname or IP of your PostgreSQL server.
2. Port is the port that the PostgreSQL server is listening on; default is 5432.
3. Database name is the name of the database you are using for this module; default is enterpriseartifactmanagerdb.
4. User Name is the PostgreSQL user that owns and has full permissions to the database specified in step 3.
5. User Password is the password for the user.

Once all values have been entered, click the Test Connection button.
If you see a green check next to the button, everything is ready to go.
If you see a red check next to the button, there is a problem with the values
you entered and you'll have to resolve that problem.

Once the test passes, click the Save button to save your selection and close the window.

## Import Globally Known Artifacts

The purpose of this feature is to store any Known or Known Bad Artifacts in
the database. Think of this feature like a dynamic Hash List.
These artifacts are used during Ingest to flag files as Interesting.
They are also displayed in the Content Viewer when a file or artifact is selected that is
associated with one of the globally known artifacts.

When importing a hash database, all fields are required.

1. Select the Database Path using the Open button. This is the file containing
the hash values that you want to import. You can import multiple files, but only
one at a time. The format of these files must be the same format as used by
the hash database module.
2. Select the database type. The type of content in the database being imported.
3. Define the attribution for this database.
    a. Select the Source Organization in the dropdown list. 
This is the organization that provided the hash database to you.
    b. If you do not see the Organization in the list, use the [Add New Organization](FEATURES.md#adding-a-new-organization) button to add it. 
Once you add it, you can then select it in the dropdown list.
    c. Enter a name for the dataset. This can be anything you want, but is often something like "child porn", "drugs", "malware", "corp hashlist", etc.
    d. Enter a version number for that dataset. This can be anything you want, but is often something like "1.0", "1.1a", 20170505", etc.
4. Click the OK button to start the import.

## Manage Correlatable Tags

In Autopsy, you are allowed to define your own Tag names, tag files and artifacts,
 and add comments when you tag a file or artifact. 

The purpose of this feature is to associate one or more of those tags with this module
to be used for Correlation.
By default there is a tag called "Evidence" as the only tag associated with this module.

To associate one or more tag(s) with this module, check the Correlate box next to the tag
name(s) and click OK.

### What does it mean for a tag to be associated with this module?

Any file or artifact that a user tags with one of the associated tags will be
added to the database as a file or artifact of interest.
Any future data source ingest, where this module is enabled, will use those 
files or artifacts as if they were part of the Known Bad list, causing matching files 
from that ingest to be added to the Interesting Artifacts list in that currently open case.

The term Correlate means that files processed during a future ingest will be correlated
with files existing in the database.

As an example, I have a case open and I tag an image called "evilphoto.png" with the
default "Evidence" tag. That image will be stored in the database as a file of interest.
In the next data source that I ingest for the same case or a future case, 
if an image with the same MD5 hash as "evilphoto.png"
is found, it will be automatically added to the Interesting Files tree and assumed
to be evidence.
This makes it easy to find and flag things in future cases that you know are
Interesting.

## Manage Correlation Types

This feature allows the user to control how much data is being stored in the database
to use for correlation and analysis.
By default, only FILES is enabled.
Select the types that you want to enable and click OK.

The meaning of each type is as follows:

* FILES - file path and MD5 hash
* DOMAIN - domain name
* EMAIL - email address
* PHONE - phone number
* USBID - device ID of connected USB devices.

### What does Correlation mean?

Artifacts stored in the database are available for this module to use for analysis.
That analysis comes in many forms.
When a file or artifact is extracted during ingest, this module will use the database
to find other files or artifacts that match it, to determine if that new file should be
flagged as an Interesting File.

If that file or artifact does not exist in the database, and that Correlation Type
is enabled, then it will be added to the database.

Having more data in the database will obviously allow this module to be more thorough,
but for some, database size is a concern, so we allow them to select a subset of data
to collect and use for analysis.
