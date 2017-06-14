@echo off

if [%1]==[] (
  echo Usage: install [shared config path]
  goto end
)


set SHARED_CONFIG_PATH=%1
set INSTALL_PATH=%~dp0

REM Service configuration
set SERVICE_NAME=AutoIngestService
set PR_DISPLAYNAME=Auto Ingest Service
set PR_DESCRIPTION=Auto Ingest Service
set PR_SERVICEUSER=%USERDOMAIN%\%USERNAME%
set PR_SERVICEPASSWORD=
set PR_INSTALL=%INSTALL_PATH%prunsrv.exe

REM Service log configuration
set PR_LOGPREFIX=%SERVICE_NAME%
set PR_LOGPATH=%INSTALL_PATH%logs
set PR_STDOUTPUT=%PR_LOGPATH%\stdout.txt
set PR_STDERROR=%PR_LOGPATH%\stderr.txt
set PR_LOGLEVEL=Info

REM Startup configuration
set PR_STARTUP=auto
set PR_STARTMODE=exe
set PR_STARTPATH=%INSTALL_PATH%
set PR_STARTIMAGE=%INSTALL_PATH%autopsy64.exe
set PR_STARTPARAMS=--autoingestservice;--sharedconfig=%SHARED_CONFIG_PATH%

REM Shutdown configuration
set PR_STOPMODE=exe
set PR_STOPPATH=%INSTALL_PATH%
set PR_STOPIMAGE=%INSTALL_PATH%AutoIngestServiceController.exe
set PR_STOPPARAMS=localhost;4150;shutdown


:: BatchGotAdmin
::-------------------------------------
REM  --> Check for permissions
>nul 2>&1 "%SYSTEMROOT%\system32\cacls.exe" "%SYSTEMROOT%\system32\config\system"

REM --> If error flag set, we do not have admin.
if '%errorlevel%' NEQ '0' (
    echo Requesting administrative privileges...
    goto UACPrompt
) else ( goto gotAdmin )

:UACPrompt
    echo Set UAC = CreateObject^("Shell.Application"^) > "%temp%\getadmin.vbs"
    set params = %*:"="
    echo UAC.ShellExecute "cmd.exe", "/c %~s0 %params%", "", "runas", 1 >> "%temp%\getadmin.vbs"

    "%temp%\getadmin.vbs"
    del "%temp%\getadmin.vbs"
    exit /B

:gotAdmin
    pushd "%CD%"
    CD /D "%~dp0"


REM Install service
%PR_INSTALL% //IS//%SERVICE_NAME% ^
--DisplayName="%SERVICE_NAME%" ^
--Description="%PR_DISPLAYNAME%" ^
--ServiceUser="%PR_SERVICEUSER%" ^
--ServicePassword="%PR_SERVICEPASSWORD%" ^
--Install="%PR_INSTALL%" ^
--LogPrefix="%PR_LOGPREFIX%" ^
--LogPath="%PR_LOGPATH%" ^
--StdOutput="%PR_STDOUTPUT%" ^
--StdError="%PR_STDERROR%" ^
--LogLevel="%PR_LOGLEVEL%" ^
--Startup="%PR_STARTUP%" ^
--StartMode="%PR_STARTMODE%" ^
--StartPath="%PR_STARTPATH%" ^
--StartImage="%PR_STARTIMAGE%" ^
--StartParams="%PR_STARTPARAMS%" ^
--StopMode="%PR_STOPMODE%" ^
--StopPath="%PR_STOPPATH%" ^
--StopImage="%PR_STOPIMAGE%" ^
--StopParams="%PR_STOPPARAMS%"


echo Use the Log On tab in the Properties dialog to enter the correct credentials.
echo.

REM Open properties dialog
start AutoIngestService


:end