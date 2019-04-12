


ESEDatabaseView v1.62
Copyright (c) 2013 - 2018 Nir Sofer
Web site: http://www.nirsoft.net



Description
===========

ESEDatabaseView is a simple utility that reads and displays the data
stored inside Extensible Storage Engine (ESE) database (Also known as
'Jet Blue' or .edb file). It displays a list of all tables available in
the opened database file, allows you to choose the desired table to view,
and then when you choose a table, it displays all records found in the
selected table. ESEDatabaseView also allows you to easily choose one or
more records, and then export them into
comma-delimited/tab-delimited/html/xml file, or copy the records to the
clipboard (Ctrl+C) and then paste them into Excel or other spreadsheet
application.



System Requirements
===================

This utility works on any version of Windows, starting from Windows 2000
and up to Windows 10. Both 32-bit and 64-bit systems are supported.
esent.dll (The dll file of Extensible Storage Engine) is not required to
read the database.



Versions History
================


* Version 1.62:
  o Fixed to sort date/time columns properly.

* Version 1.61:
  o Added 'Run As Administrator' option (Ctrl+F11).

* Version 1.60:
  o Fixed bug: On some tables ESEDatabaseView failed to read properly
    some of the fields.
  o Added 'Detect Ascii Strings In Binary Data' option. When it's
    turned on, ESEDatabaseView displays binary data as string if it
    detects that the binary data is Ascii string. This option is useful
    for cookies names and values (CookieEntryEx_XX tables) in the
    database of MS-Edge browser (WebCacheV01.dat).
  o Added 'Put Icon On Tray' option.

* Version 1.54:
  o Added new quick filter options: 'Find records with all words
    (space-delimited list)' and 'Find records with all strings
    (comma-delimited list)'
  o Added new quick filter combo-box: 'Show only items match the
    filter' and 'Hide items that match the filter'.

* Version 1.53:
  o Added 'Open spartan.edb Database' which automatically opens the
    spartan.edb database of IE11. This file stores the Favorites of IE11
    and the full path of this file is
    %LOCALAPPDATA%\Packages\Microsoft.MicrosoftEdge_8wekyb3d8bbwe\AC\Micros
    oftEdge\User\Default\DataStore\Data\nouser1\120712-0049\DBStore\spartan
    .edb)
  o Made the display of binary data a little faster.

* Version 1.52:
  o Added 'Detect UTF-16 Strings In Binary Data'. When it's turned
    on, ESEDatabaseView displays binary data as string if it detects that
    the binary data is UTF-16 string. (e.g: 'Key' field in MSysLocales
    table)
  o Fixed bug: 'Copy Selected Items' worked improperly when setting
    the 'Unicode/Ascii Save Mode' to 'Always UTF-8'.

* Version 1.51:
  o Fixed the 'Show Binary URL As String' feature to work properly
    when the URL string starts in different position.

* Version 1.50:
  o Added 'Quick Filter' feature (View -> Use Quick Filter or
    Ctrl+Q). When it's turned on, you can type a string in the text-box
    added under the tables combo-box and ESEDatabaseView will instantly
    filter the ESE database records, showing only lines that contain the
    string you typed.

* Version 1.43:
  o Added 'Save All Items' (Shift+Ctrl+S).

* Version 1.42:
  o Fixed bug: ESEDatabaseView crashed when using the find option
    while the last item was selected.

* Version 1.41:
  o Added 'Align Numeric Columns To Right' option.

* Version 1.40:
  o Fixed bug: On some databases/tables (like Recipient table in
    store.vol or tbUpdateLocalizedProps table in DataStore.edb)
    ESEDatabaseView omitted the first 4 characters of a string.
  o Added 'Select All' and 'Deselect All' buttons to the 'Choose
    Column' window.

* Version 1.37:
  o You can now choose the desired encoding (ANSI, UTF-8, UTF-16) to
    save the csv/xml/text/html files. (Under the Options menu)

* Version 1.36:
  o Added 'New ESEDatabaseView Instance' under the File menu, for
    opening a new window of ESEDatabaseView.

* Version 1.35:
  o When 'Auto Detect 64-bit Date/Time Value' option is turned on,
    ESEDatabaseView now detects the Modified field of tbFiles table
    inside DataStore.edb
  o The properties window is now resizable.

* Version 1.33:
  o Fixed issue: ESEDatabaseView failed to display dates earlier than
    01/01/1986.

* Version 1.32:
  o Added 'Show Binary URL As String'. When it's turned on, the Urls
    field of tbFiles table inside DataStore.edb is displayed as string.

* Version 1.31:
  o Fixed the 'Open Locked IE10/IE11 Database' option to work with
    the latest build of Windows 10/IE11.

* Version 1.30:
  o Added option to export to JSON file.
  o Fixed bug: ESEDatabaseView failed to load records on some
    tables/databases.
  o Fixed bug: ESEDatabaseView crashed when trying to load a very
    large binary value.

* Version 1.25:
  o Fixed bug: ESEDatabaseView displayed incorrect values in
    date/time fields.

* Version 1.24:
  o Fixed bug: ESEDatabaseView failed to remember the last
    size/position of the main window if it was not located in the primary
    monitor.

* Version 1.23:
  o You can now specify an empty string ("") in order to send the
    data to stdout, for example:
    ESEDatabaseView.exe /table "c:\temp\contacts.edb"
    "SimpleContact-v081111-0122-1303" /scomma ""

* Version 1.22:
  o Added 'Copy Sorted Column Data' option, which copies to the
    clipboard the text of all selected items, but only the column that is
    currently sorted.

* Version 1.21:
  o Fixed to find the correct item when typing the string you want to
    search into the main List View.

* Version 1.20:
  o Added option to export all tables from command-line (Each table
    in a separated file), for example:
    ESEDatabaseView.exe /table "C:\temp\WebCacheV01.dat" * /scomma
    "C:\Temp\export\webcache_*.csv"

* Version 1.18:
  o Fixed to display local date/time values according to daylight
    saving time settings.

* Version 1.17:
  o Added 'Open SoftwareDistribution Database' option, which opens
    the database file containing information about installed Winodws
    updates (C:\WINDOWS\SoftwareDistribution\DataStore\DataStore.edb)

* Version 1.16:
  o Added 'Clear Recent Files List' option.

* Version 1.15:
  o Added 'Open Recent File' menu, which allows you to easily open
    the last 10 database files that you previously opened.

* Version 1.10:
  o Added 'Open Locked IE10 Database' option, which copies the locked
    database file of Internet Explorer 10 (WebCacheV01.dat or
    WebCacheV24.dat) into a temporary filename, and then opens the
    temporary filename in ESEDatabaseView. You can use this option to
    easily view the cache/history/cookies information stored by IE10.

* Version 1.07:
  o Fixed the flickering appeared while scrolling the database
    records.

* Version 1.06:
  o Added 'Convert Date/Time From GMT To Local Time' option.

* Version 1.05:
  o Added command-line support

* Version 1.00 - First release.



Known Limitations
=================


* Currently, ESEDatabaseView is somewhat a Beta version. It generally
  reads the ESE databases properly, but in tables with complex data
  structure, you may experience the following problems:
  o Some fields in some of the records may display incorrect value or
    display empty string while it actually contains some data.
  o ESEDatabaseView may hang/stop responding when loading a table
    with large amount of data.




Example for ESE Databases
=========================

ESE Databases are used by many Microsoft products. Usually, the file
extension of ESE database is .edb, but in some products the file
extension is different.
Here's some examples for .edb files used by Microsoft products:
* contacts.edb - Stores contacts information in Microsoft live products.
* WLCalendarStore.edb - Stores calendar information in Microsoft
  Windows Live Mail.
* Mail.MSMessageStore - Stores messages information in Microsoft
  Windows Live Mail.
* WebCacheV24.dat and WebCacheV01.dat - Stores cache, history, and
  cookies information in Internet Explorer 10.
* Mailbox Database.edb and Public Folder Database.edb - Stores mail
  data in Microsoft Exchange Server.
* Windows.edb - Stores index information (for Windows search) by
  Windows operating system.
* DataStore.edb - Stores Windows updates information (Located under
  C:\windows\SoftwareDistribution\DataStore )
* spartan.edb - Stores the Favorites of Internet Explorer 10/11.
  (Stored under
  %LOCALAPPDATA%\Packages\Microsoft.MicrosoftEdge_8wekyb3d8bbwe\AC\Microsof
  tEdge\User\Default\DataStore\Data\nouser1\120712-0049)



Start Using ESEDatabaseView
===========================

ESEDatabaseView doesn't require any installation process or additional
dll files. In order to start using it, simple run the executable file
(ESEDatabaseView.exe) and then use the 'Open ESE Database File' option
(Ctrl+O) to open the desired .edb file. You can also drag the database
file from Explorer window into the window of ESEDatabaseView.

After opening the desired database file, the combo-box located below the
toolbar is filled with the list of all tables found in the database. By
default, MSysObjects table is selected and displayed in the main window
of ESEDatabaseView. MSysObjects is a system table available in all ESE
databases which provides the list of all tables and fields stored in the
database.
In order to view the content of another table, simply choose the desired
table in the combo-box located below the toolbar.

By default, the table is sorted according to the first column, but you
can sort by another field, simply by clicking the desired column header.
The sorting is made according to the type of the field, so... for
example, if the field is an integer value, then ESEDatabaseView will use
a numeric comparison in order to sort the column properly.

You can select one or more records (or select all records with Ctrl+A)
and then export them into text/csv/tab-delimited/html/xml file, by using
the 'Save Selected Items' option. You can also copy the selected records
into the clipboard (Ctrl+C) and then paste them (Ctrl+V) into Excel or
other spreadsheet application.



Command-Line Options
====================



/table <Database Filename> <Table Name>
Specifies the database and table to open. If the <Table Name> is "*" ,
all tables will be exported, each table in a separated file.

/stext <Filename>
Save the database table into a regular text file.

/stab <Filename>
Save the database table into a tab-delimited text file.

/scomma <Filename>
Save the database table into a comma-delimited text file (csv).

/stabular <Filename>
Save the database table into a tabular text file.

/shtml <Filename>
Save the database table into HTML file (Horizontal).

/sverhtml <Filename>
Save the database table into HTML file (Vertical).

/sxml <Filename>
Save the database table into XML file.

/sjson <Filename>
Save the database table into JSON file.

/sort <column>
This command-line option can be used with other save options for sorting
by the desired column. The <column> parameter can specify the column
index (0 for the first column, 1 for the second column, and so on) or the
name of the column, like "StatusState" and "CalculatedBuddyIdentifier".
You can specify the '~' prefix character (e.g:
"~CalculatedBuddyIdentifier") if you want to sort in descending order.
You can put multiple /sort in the command-line if you want to sort by
multiple columns.

Examples:
ESEDatabaseView.exe /table "c:\temp\contacts.edb"
"SimpleContact-v081111-0122-1303" /scomma c:\temp\1.csv
ESEDatabaseView.exe /table "c:\files\contacts.edb"
"SimpleContact-v081111-0777-1111" /shtml c:\files\1.html /Sort
"CalculatedBuddyIdentifier"

Example for exporting all tables: (Each table is exported into a
separated file)
ESEDatabaseView.exe /table "C:\temp\WebCacheV01.dat" * /scomma
"C:\Temp\export\webcache_*.csv"

The table name will replace the '*' character specified in the export
filename. For example, if the table name is Container1, then the exported
filename will be webcache_Container1.csv



Translating ESEDatabaseView to other languages
==============================================

In order to translate ESEDatabaseView to other language, follow the
instructions below:
1. Run ESEDatabaseView with /savelangfile parameter:
   ESEDatabaseView.exe /savelangfile
   A file named ESEDatabaseView_lng.ini will be created in the folder of
   ESEDatabaseView utility.
2. Open the created language file in Notepad or in any other text
   editor.
3. Translate all string entries to the desired language. Optionally,
   you can also add your name and/or a link to your Web site.
   (TranslatorName and TranslatorURL values) If you add this information,
   it'll be used in the 'About' window.
4. After you finish the translation, Run ESEDatabaseView, and all
   translated strings will be loaded from the language file.
   If you want to run ESEDatabaseView without the translation, simply
   rename the language file, or move it to another folder.



License
=======

This utility is released as freeware. You are allowed to freely
distribute this utility via floppy disk, CD-ROM, Internet, or in any
other way, as long as you don't charge anything for this and you don't
sell it or distribute it as a part of commercial product. If you
distribute this utility, you must include all files in the distribution
package, without any modification !



Disclaimer
==========

The software is provided "AS IS" without any warranty, either expressed
or implied, including, but not limited to, the implied warranties of
merchantability and fitness for a particular purpose. The author will not
be liable for any special, incidental, consequential or indirect damages
due to loss of data or any other reason.



Feedback
========

If you have any problem, suggestion, comment, or you found a bug in my
utility, you can send a message to nirsofer@yahoo.com
