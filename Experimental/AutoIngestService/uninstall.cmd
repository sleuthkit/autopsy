@echo off
  
set SERVICE_NAME=AutoIngestService
  
REM Uninstall service
prunsrv.exe //DS//%SERVICE_NAME%