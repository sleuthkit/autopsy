@echo off &MODE CON: COLS=75 LINES=20 &color 1e
:: All my thanks to hilander999 and Siegfried for their help
:: Original code from hilander999
: edit and modified to fit this plugin by Xtreme

SETLOCAL ENABLEEXTENSIONS
cd /d %~dp0

set INFname=Testdisk.inf
::SET NOW=2
::SET TOPBOX=? 
::SET MIDBOX= ????????????????????????????????????????????????????????????????????????ธ
::SET NUMROW= 0%%                25%%               50%%               75%%              100%%

echo ===================================== > "%cd%\File_Grabber.log"
echo Testdisk and PhotoRec 7.1 Plugin >> File_Grabber.log
echo Plugin by: Xtreme (Ahmed Hossam) >> File_Grabber.log
echo Collector by: Xtreme >> File_Grabber.log
echo Plugin for: Windows Xpire Rd CD >> File_Grabber.log
echo Website: http://xtreme.boot-land.net >> File_Grabber.log
echo  -mirror: http://xtremee.orgfree.com >> File_Grabber.log
echo Copyright ฉ Windows Xpire Tech Center. All rights reserved. >> File_Grabber.log
echo ===================================== >> File_Grabber.log

IF NOT EXIST cd ..\..\63\cygwin GOTO :error1

cls&CALL :BRANDH
echo  Please wait till Grabber finish Testdisk and PhotoRec grabbing process...

IF NOT EXIST "%cd%\TestDisk_PE\files" md "TestDisk_PE\files"
IF NOT EXIST "%cd%\TestDisk_PE\files\63" md "TestDisk_PE\files\63"

echo.&echo   -Grabbing file: Copy Testdisk files... &FOR %%A IN (
..\..\cygwin1.dll
..\..\cyggcc_s-1.dll
..\..\cygiconv-2.dll
..\..\cygjpeg-8.dll
..\..\cygncursesw-10.dll
..\..\cygssp-0.dll
..\..\cygwin1.dll
..\..\cygz.dll
..\..\iconv.dll
..\..\libgcc_s_sjlj-1.dll
..\..\libjpeg-62.dll
..\..\libpng16-16.dll
..\..\libssp-0.dll
..\..\libstdc++-6.dll
..\..\libwinpthread-1.dll
..\..\QtCore4.dll
..\..\QtGui4.dll
..\..\zlib1.dll
..\..\photorec_win.exe
..\..\qphotorec_win.exe
..\..\testdisk_win.exe
..\..\fidentify_win.exe
	)DO (ECHO. Grabbing file:%%~A >>"%cd%\File_Grabber.log"
		Copy /Y "%%~A" TestDisk_PE\files >NUL
		if errorlevel 1 (SET ERRORLEVEL=1&echo. *ERROR: File_Grabber can't find %%~A >>"%cd%\File_Grabber.log")
		)

FOR %%B IN (
..\..\63\cygwin
	)DO (ECHO. Grabbing file:%%~B >>"%cd%\File_Grabber.log"
		Copy /Y "%%~B" TestDisk_PE\files\63 >NUL
		if errorlevel 1 (SET ERRORLEVEL=1&echo. *ERROR: File_Grabber can't find %%~B >>"%cd%\File_Grabber.log")
		)

CALL :PROGRESS

::pause
MODE CON: COLS=76 LINES=23
IF "%ERRORLEVEL%"=="1" (GOTO :error2)else goto :done
GOTO :END

:PROGRESS
::SET /A NOW+=1
::IF %NOW% LEQ 2 GOTO :EOF
cls&CALL :BRANDH
::echo.&echo.   File: %~1&echo.&echo.%TOPBOX%&echo.%MIDBOX%&echo.%NUMROW%
::SET TOPBOX=%TOPBOX%U
::SET NOW=1
copy SCRIPTS\StaticINF.dat TestDisk_PE\"%INFname%"
copy testdisk_nu2menu.xml TestDisk_PE\
if exist start.inf del start.inf
GOTO :EOF

:error1
MODE CON: COLS=78 LINES=28 &COLOR 4F &cls

echo.&echo. >> File_Grabber.log
echo   TestDisk and PhotoRec can't be localized on your system >> File_Grabber.log
echo.  >> File_Grabber.log
echo You can download TestDisk and PhotoRec from here  >> File_Grabber.log
echo.&echo -TestDisk Official website:  >> File_Grabber.log
echo   	   https://www.cgsecurity.org/wiki/TestDisk_Download/ >> File_Grabber.log
echo. >> File_Grabber.log
echo For help you can check Help.html >> File_Grabber.log

CALL :BRANDH
echo.&echo.	   TestDisk and PhotoRec can't be localized on your system
echo.&echo.       You need to download TestDisk and DON'T change the download folders         structure then try again
echo.&echo.     You can download TestDisk and PhotoRec from here
echo.&echo.       -TestDisk Official website:
echo            https://www.cgsecurity.org/wiki/TestDisk_Download/&echo.
CALL :BRAND2
GOTO :END


:error2
cls&COLOR 4F
CALL :BRANDH
echo.&echo 		One or more required files was not found.
echo.&echo.	Check the log for details...&echo.	"%cd%\File_Grabber.log"&echo.
CALL :BRAND2
pause
IF EXIST %systemroot%\system32\notepad.exe (start %systemroot%\system32\notepad.exe "%cd%\File_Grabber.log")
endlocal
exit

:done
cls&CALL :BRANDH
echo.&echo.	TestDisk and PhotoRec Files have been succesfully collected
echo.&echo.		You can now start build your PE version &echo.
CALL :BRAND2
GOTO :END

:BRANDH
MODE CON: COLS=75 LINES=35 &color 1e
echo.
ECHO          ษออออออออออออออออออออออออออออออออออออออออออออออออออออออป
ECHO          บ                                                      บ
ECHO          บ      TestDisk and PhotoRec plugin Files Collector    บ
ECHO          บ                                                      บ
ECHO          บ      Plugin by Xtreme ( Xtremesony_xp@yahoo.com )    บ
ECHO          บ                                                      บ 
ECHO          ศออออออออออออออออออออออออออออออออออออออออออออออออออออออผ
echo.
GOTO :EOF

:BRAND2
echo.       - For more help and update you Can join us in our group
echo.           http://groups.yahoo.com/group/Windows-Xpire/
echo.
echo.       - For more BartPE Plugin go to our website:
echo.                    http://xtreme.boot-land.net
echo.           (mirror) http://xtremee.orgfree.com 
echo.
echo.       - Send all comments about plugin to Xtreme Xtremesony_xp@yahoo.com
echo.
echo.
ECHO          ษออออออออออออออออออออออออออออออออออออออออออออออออออออออป
ECHO          บ       Copyright (C) Windows Xpire Tech Center        บ
ECHO          ศออออออออออออออออออออออออออออออออออออออออออออออออออออออผ 

GOTO :EOF


:END
ENDLOCAL
echo.
PAUSE&EXIT
