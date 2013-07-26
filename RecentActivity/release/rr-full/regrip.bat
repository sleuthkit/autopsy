@echo off
echo ***Scanning Software hive***
echo -------------------------------------------------------
REM rip -r %1\software -f software 
echo ***Scanning System hive***
echo -------------------------------------------------------
REM rip -r %1\system -f system 
echo ***Scanning SAM hive***
echo -------------------------------------------------------
REM rip -r %1\sam -f sam
echo ***Scanning Security hive*** 
echo -------------------------------------------------------
rip -r %1\SECURITY –f security