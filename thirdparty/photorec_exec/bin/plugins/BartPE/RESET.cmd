@echo off
rd files /s /q
del File_Grabber.log
del *.inf
copy SCRIPTS\Start_INF.dat start.inf
pause