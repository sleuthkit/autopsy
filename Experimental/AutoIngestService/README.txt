==============================
Service Files
==============================

Here's what is included...


AutoIngestService.exe
------------------------------
* Can be used as a way of manually starting, stopping, and configuring the
  service.
* Part of Procrun.
* Nice to have, but not required for service to operate.

install.cmd
------------------------------
* Registers 'autopsy64.exe' as a Windows service and copies the Autopsy
  configuration of the user running the installer to the NetworkServices
  profile.

prunsrv.exe
------------------------------
* Used to install, uninstall, start and stop the service.
* Part of Procrun.

uninstall.cmd
------------------------------
* Removes 'autopsy64.exe' from the system registry and cleans up the Autopsy
  configuration for the NetworkServices profile.


Missing is the 'AutoIngestServiceController.exe' file. The source code for
this is included in the 'Experimental\AutoIngestServiceController' directory.
After building the JAR, Launch4j can be used to wrap it as an EXE file.


==============================
Setup Instructions
==============================

1) Copy the service files (including the service controller) to the directory
   where 'autopsy64.exe' resides.

2) Run Autopsy under your administrator account.

3) Configure the Multi User and Auto Ingest settings in the Options panel.

4) Save the settings as a shared configuration.

5) Close Autopsy.

6) Open a command prompt window under your administrator account.

7) Navigate to the directory where 'autopsy64.exe' resides.

8) Type 'install <path>', where '<path>' is the the path to the shared
   configuration directory. Then press Enter.

9) Now the service is ready. You can start it by either restarting Windows
   (doing a shutdown will NOT work), starting it from 'AutoIngestService.exe',
   or starting it from the Task Manager.

==============================
Using the Service Controller
==============================

The service controller must be run from the command prompt. It can be used
either as a command interface, or it can be used for single-command execution.


Command Interface
------------------------------
To use as a command interface, run it as follows:

'AutoIngestServiceController <host> <port>'

The '<host>' will usually be 'localhost'. At the moment, the service will only
ever run on port '4150'.

Once you see 'Client>' appear on the screen, it's ready for input. The valid
commands are as follows:

* START
Start the auto ingest manager (starts by default when the service starts).

* SHUTDOWN
Shutdown the auto ingest manager and stop the service.

* PAUSE
Pause ingestion.

* RESUME
Resume ingestion.

* GETJOBS
Retrieve a list of jobs in the Pending, Running, and Completed queues.

* GETSTATE
Retrieve the current state of the auto ingest manager.


Single-Command Execution
------------------------------
To use for executing a single command, run it as follows:

'AutoIngestServiceController <host> <port> <command>'

The '<host>' will usually be 'localhost'. At the moment, the service will only
ever run on port '4150'. The '<command>' can be any of the commands listed
above.
