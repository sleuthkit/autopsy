# Setting up the PostgreSQL DB for use in Enterprise Artifact Manager

## Using Command Line (cmd.exe)

The easiest way to do this is with the scripts that come with the PostgreSQL server.
Add the PostgreSQL Server bin directory to your path in your user's environment
variables.

    PATH=$PATH;c:\Program Files\PostgreSQL\9.6\bin

Note: I've had issues getting these Windows PostgreSQL binaries to run in
a cygwin terminal. But they work perfectly in cmd.exe.

### Create Role

The role we use has user name "testuser" and password "testpass".

    $ createuser -U postgres -P testuser

When prompted for a password enter "testpass".

### Create Database

The database we use is named "enterpriseartifactmanagerdb".

    $ createdb -T template0 -U postgres -O testuser enterpriseartifactmanagerdb

### Drop Database

If we want to reset the database, it's easiest to just drop it.

    $ dropdb -U postgres enterpriseartifactmanagerdb

### Load the database content from a .sql file

Before loading a schema.sql file, you must have an empty database named
enterpriseartifactmanagerdb and an existing user named testuser that is the database owner.
Use the schema.sql files to create the tables, indices, and other required settings.

    $ psql -U postgres enterpriseartifactmanagerdb < c:\path\to\schema.sql

## Using pgAdmin tool

### Create Role

Use the right-click menus to create a role named "testuser" with a password of
"testpass".

### Create Database

Use the right-click menus to create a database named "enterpriseartifactmanagerdb" 
and set testuser as the owner.

### Load the database content from a .sql file

Right-click on the enterpriseartifactmanagerdb database and select "CREATE script".
In the new window, delete all of the content and paste in the content of the
schema.sql file.
Click on the lightning icon to execute the contents you just pasted.

## Notes

- The schema.sql file does not contain commands to check for the existence of 
existing table objects, nor does it drop them before trying to add new ones.
So, it is best to drop and create the database freshly before loading the schema.
- pg_restore cannot load a .sql file.
- The schema.sql file cannot have commands to drop/create the database.

### Purpose of each schema file

schema1 - 2 non-normalized tables, no FKs, no enforced uniqueness

schema2 - 2 non-normalized tables, no FKs, enforced uniqueness on artifacts
- requires PostgreSQL Server ver 9.2+
- postgresql automatically creates a unique index when a unique constraint is defined,
so there is no need to manually create a unique index for the same column(s).
Doing so would duplicate the automatically-created index.