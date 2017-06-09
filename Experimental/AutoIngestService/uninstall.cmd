@echo off
  
set SERVICE_NAME=AutoIngestService
  
REM Uninstall service
prunsrv.exe //DS//%SERVICE_NAME%

REM Remove Autopsy configuration from NetworkService profile
rd /S/Q %SystemRoot%\ServiceProfiles\NetworkService\AppData\Roaming\.autopsy