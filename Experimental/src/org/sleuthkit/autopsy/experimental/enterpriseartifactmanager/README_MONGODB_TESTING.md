# Mongo Database for testing

These instructions assume that you already have MongoDB Server 3.4 installed and
that the server binary is at:

    C:\Program Files\MongoDB\Server\3.4\bin\mongod.exe

## Using multiple configurations

It is possible to use a configuration file and even to run MongoDB as a service,
but for our testing, we want one instance that is the default non-secure instance.
And we want a second instance that has auth enabled and has a defined role for
our test user.

The the easiest way to have multiple instances of MongoDB that have
unique configurations is to have each of them use distinct directories.

The default directories are:

    C:\data\db
    C:\data\log

So, we need to create a second set of directories at:

    C:\dataauth\db
    C:\dataauth\log

### Create config files, so it is easier to start MongoDB

The config file is YAML formatted, so do not use TABs. And every
sub-level should be indented 2 spaces from the previous level.

We will not define the host/port values in the config files, because we assume
that you are using the default port and host values AND 
that you will only run ONE of these two instances at a time.
If you want them to run at the same time, include the net.port and net.bindIp
parameters in the config file and make sure they are not both using the same
port/IP pair.

The first config file is for the default instance, create a new file:

    C:\data\mongod.cfg

Enter the following content into that file:

    systemLog:
      destination: file
      path: c:\data\log\mongod.log
    storage:
      dbPath: c:\data\db

The second config file is for the auth instance, create a new file:

    C:\dataauth\mongod.cfg

Enter the following content into that file:

    systemLog:
      destination: file
      path: c:\dataauth\log\mongod.log
    storage:
      dbPath: c:\dataauth\db
    security:
      authorization: enabled

Note: Ensure Windows did not name your file ending with cfg.txt. You'll
have to go to Folder Options -> View and uncheck the option to hide file extensions
for common files.

Also, if you will be running mongod using a windows terminal (cmd.exe or powershell),
make sure to use correct Windows path separators (i.e. c:\data\db). 
If you are instead using cygwin, make sure to use valid cygwin/unix path
separators (i.e. c:/data/db).

### To start MongoDB using a config file

for Windows cmd or ps:

    C:\path\to\bin\mongod.exe --config C:\data\mongod.cfg

or

    C:\path\to\bin\mongod.exe --config C:\dataauth\mongod.cfg

for cygwin/unix:

    cd /cygdrive/c/path/to/bin/
    ./mongod.exe --config C:/data/mongod.cfg

or    

    ./mongod.exe --config C:/dataauth/mongod.cfg


If it starts correctly, you'll see nothing in the terminal and all logs will
go to the specified log file.
If there is a problem it will display an error in the terminal and fail to start.

### Setting up the auth'd instance

The first time you start this mongod instance, you MUST start with auth disabled,
so it will let you create the admin user. Do this in the config file:

    security:
      authorization: disabled

Now start the auth'd mongod in one terminal.

#### Create admin user

In a second terminal, connect to that instance with mongo client.

    C:\path\to\mongo.exe

Enter the following in the mongo client:

    use admin
    db.createUser(
      {
        user: "adminuser",
        pwd: "adminpass",
        roles: [ { role: "userAdminAnyDatabase", db: "admin" } ]
      }
    )

Logout of the mongo client.
Stop mongod.
Set security.authorization to enabled in the auth'd mongod.cfg.
Start the auth'd mongod.
From now on, you can always start this instance of mongod with authorization
enabled.

#### Create test user

In the second terminal, connect to the auth'd instance with mongo client.

    C:\path\to\mongo.exe

Authenticate as the admin user:

    use admin
    db.auth("adminuser", "adminpass")

Create the test user in the enterpriseartifactmanagerdb database with the readWrite role:

    use enterpriseartifactmanagerdb
    db.createUser(
      {
        user: "testuser",
        pwd: "testpass",
        roles: [ { role: "readWrite", db: "enterpriseartifactmanagerdb" } ]
      }
    )

Now the EnterpriseArtifactManager code can use the user "testuser" to use the
enterpriseartifactmanagerdb database. This includes creating/dropping indices/collections
along with the usual insert/update/delete commands.

NOTE: The database where a user is created is that user's "authentication database".
So, when that user needs to authenticate, they need to provide their username,
password, and authentication database.

### References

Installation and setup: https://docs.mongodb.com/manual/tutorial/install-mongodb-on-windows/
Config file: https://docs.mongodb.com/manual/reference/configuration-options/
Enabling Auth: https://docs.mongodb.com/manual/tutorial/enable-authentication/
readWrite Role: https://docs.mongodb.com/manual/reference/built-in-roles/#readWrite