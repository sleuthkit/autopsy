#! c:\perl\bin\perl.exe
#-----------------------------------------------------------
# Registry Ripper
# Parse a Registry hive file for data pertinent to an investigation
#
# Adv version...provides the basic functionality.  All plugins 
# can be used with both the basic version and the full-featured 
# version
#
# Change History:
#  20230822 - minor tweak in plugin processing
#  20220714 - added JSON::PP based on input from Mark McKinnon
#  20210302 - added Digest::MD5
#  20201026 - added SelectAll(), Clear() functions for Textfield; fixed issue with ID'ing UsrClass.dat hives
#  20200824 - Unicode parsing updates
#  20200803 - updated to version 4.0 Pro
#  20200511 - added code to provide date format in ISO 8601/RFC 3339 format
#  20200401 - Added code to check hive type, collect plugins, and automatically run those
#             plugins against the hive
#  20200322 - multiple updates
#  20190318 - modified code to allow the .exe to be run from anywhere within the file system
#  20190128 - added Time::Local, modifications to module Key.pm
#  20130429 - minor updates, including not adding .txt files to Profile list
#  20130425 - added alertMsg() functionality, updated to v2.8
#  20120505 - Updated to v2.5
#  20081111 - Updated code in setUpEnv() to parse the file paths for 
#             output files (log, etc) so that they paths were handled
#             properly; updated Perl2Exe include statements to support
#             Parse::Win32Registry 0.40
#  20080512 - Consolidated Basic and Advanced versions into a single
#             track
#  20080429 - Fixed issue with output report and log files having the
#             same (.log) file extension
#  20080422 - Added ComboBox to choose plugins file
#  20080414 - updated code to check for a selected hive file; set 
#             default plugin file to "ntuser" if none selected; check
#             for plugins file with no plugins or all plugins commented
#             out; keep track of plugins w/ hard errors generated via 
#             this GUI.
#  20080412 - added listbox; populate with list of plugin files
#             from plugin dir
#           - Log file now based on report file name and location
#  20080226 - added eval{} to wrap require pragma in go_Click() 
#  
#
# Functionality: 
#   - plugins file is selectable
# 
# copyright 2022 Quantum Research Analytics, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
#use strict;
use Win32::GUI();
#use Win32::GUI::Constants qw(CW_USEDEFAULT);
use Time::Local;
use Parse::Win32Registry qw(:REG_);
use File::Spec;
use Encode::Unicode;
use Digest::MD5;
use JSON::PP;
require 'time.pl';
require 'rr_helper.pl';

# Included to permit compiling via Perl2Exe
#perl2exe_include "Parse/Win32Registry.pm";
#perl2exe_include "Parse/Win32Registry/Key.pm";
#perl2exe_include "Parse/Win32Registry/Entry.pm";
#perl2exe_include "Parse/Win32Registry/Value.pm";
#perl2exe_include "Parse/Win32Registry/File.pm";
#perl2exe_include "Parse/Win32Registry/Win95/File.pm";
#perl2exe_include "Parse/Win32Registry/Win95/Key.pm";
#perl2exe_include "Encode.pm";
#perl2exe_include "Encode/Byte.pm";
#perl2exe_include "Encode/Unicode.pm";
#perl2exe_include "utf8.pm";
#perl2exe_include "unicore/Heavy.pl";
#perl2exe_include "unicore/To/Upper.pl";
#-----------------------------------------------------------
# Global variables
#-----------------------------------------------------------
my $VERSION = "4\.0";
my %env; 
my $plugindir;
($^O eq "MSWin32") ? ($plugindir = $str."plugins/")
                   : ($plugindir = File::Spec->catfile("plugins"));

#-----------------------------------------------------------
# GUI
#-----------------------------------------------------------
# create our menu
my $menu = Win32::GUI::MakeMenu(
		"&File"                => "File",
		" > O&pen..."          => { -name => "Open"},
		" > -"                 => 0,
    " > E&xit"             => { -name => "Exit", -onClick => sub {exit 1;}},
    "&Help"                => "Help",
    " > &About"            => { -name => "About", -onClick => \&RR_OnAbout},
);

# Create Main Window
my $main = new Win32::GUI::Window (
    -name     => "Main",
    -title    => "RegRipper, v.".$VERSION,
    -left  => CW_USEDEFAULT,
    -pos      => [200, 200],
# Format: [width, height]
    -maxsize  => [500, 420],
    -size     => [500, 420],
    -menu     => $menu,
    -dialogui => 1,
) or die "Could not create a new Window: $!\n";

my $icon_file = "q\.ico";
my $icon = new Win32::GUI::Icon($icon_file);
$main->SetIcon($icon);

$main->AddLabel(
    -text   => "Hive File:",
    -left   => 20,
    -top    => 10);
    
my $ntuserfile = $main->AddTextfield(
    -name     => "ntuserdat",
    -tabstop  => 1,
    -left     => 100,
    -top      => 10,
    -width    => 250,
    -height   => 22,
    -tabstop  => 1,
    -foreground => "#000000",
    -background => "#FFFFFF");

my $browse1 = $main->AddButton(
		-name => 'browse1',
		-left => 375,
		-top  => 10,
		-width => 50,
		-height => 22,
		-tabstop  => 1,
		-text => "Browse");

$main->AddLabel(
    -text   => "Report File:",
    -left   => 20,
    -top    => 50);
    
my $rptfile = $main->AddTextfield(
    -name     => "rptfile",
    -tabstop  => 1,
    -left     => 100,
    -top      => 50,
    -width    => 250,
    -height   => 22,
    -tabstop  => 1,
    -foreground => "#000000",
    -background => "#FFFFFF");

my $browse2 = $main->AddButton(
		-name => 'browse2',
		-left => 375,
		-top  => 50,
		-width => 50,
		-height => 22,
		-tabstop  => 1,
		-text => "Browse");

my $testlabel = $main->AddLabel(
	-text => "",
	-name => "TestLabel",
	-pos => [10,90],
	-size => [445,210],
	-frame => etched,
	-sunken => 1
);

my $report = $main->AddTextfield(
    -name      => "Report",
    -pos       => [20,100],
    -size      => [425,190],
    -multiline => 1,
    -vscroll   => 1,
    -autohscroll => 1,
    -autovscroll => 1,
    -keepselection => 1 ,
    -tabstop => 1,
);

my $go = $main->AddButton(
		-name => 'go',
		-left => 320,
		-top  => 310,
		-width => 50,
		-height => 25,
		-tabstop => 1,
		-text => "Rip!");
		
$main->AddButton(
		-name => 'close',
		-left => 390,
		-top  => 310,
		-width => 50,
		-height => 25,
		-tabstop => 1,
		-text => "Close");

my $status = new Win32::GUI::StatusBar($main,
		-text  => "RegRipper v.".$VERSION." opened\.",
);

$status->Text("Ready.");

#-----------------------------------------------------------
# Added 20200322
$report->Append("NOTE: This tool does NOT automatically process and incorporate Registry hive\r\n");
$report->Append("transaction logs.  The tool will check to see if the hive is dirty.\r\n");
$report->Append("\r\n");
$report->Append("If you need to process/incorporate transaction logs, please consider using\r\n");
$report->Append("yarp + registryFlush.py (Maxim Suhanov) or rla.exe (Eric Zimmerman).\r\n");
$report->Append("\r\n");
#-----------------------------------------------------------

$main->Show();
Win32::GUI::Dialog();
#-----------------------------------------------------------
sub Open_Click {
	\&browse1_Click();	
}

sub browse1_Click {
  # Open a file
  my $file = Win32::GUI::GetOpenFileName(
                   -owner  => $main,
                   -title  => "Open a hive file",
                   -filter => ['All files' => '*.*',],
                   );
  
  $ntuserfile->Text($file);
  0;
}

sub browse2_Click {
  # Open a file
  my $file = Win32::GUI::GetSaveFileName(
                   -owner  => $main,
                   -title  => "Save a report file",
                   -filter => [
                       'Report file (*.txt)' => '*.txt',
                       'All files' => '*.*',
                    ],
                   );
  
 	$file = $file."\.txt" unless ($file =~ m/\.\w+$/i);
 	$rptfile->Text($file);
  0;
}

sub go_Click {	
# Set up the environment
	setUpEnv();
	if ($env{ntuser} eq "") {
		Win32::GUI::MessageBox($main,$ENV{USERNAME}.", you did not select a hive file.\r\n",
		                       "Doh!!",16);
		return;
	}
	
# added 20201026
	$report->SelectAll();	
	$report->Clear();	
	
# Guess the hive type, then run through all of the available plugins to get a list
# to run against that hive.
#----------------------------------------------------------------------------------------
# added 20200322
	my $dirty = checkHive($env{ntuser});
	if ($dirty == 1) {
		$status->Text("Hive is dirty.");
		$report->Append("Hive is dirty.  If you need to process hive transaction logs, please consider\r\n");
		$report->Append("doing so via yarp + registryFlush.py (Maxim Suhanov) or rla.exe (Eric Zimmerman).\r\n");
		logMsg("Hive (".$env{ntuser}.") is dirty.\n");
		rptMsg("Hive (".$env{ntuser}.") is dirty.");
		rptMsg("If you need to process hive transasction logs, please consider using yarp + registryFlush.py");
		rptMsg("(Maxim Suhanov) or rla.exe (Eric Zimmerman).\n");
	}
	elsif ($dirty == 0) {
		$status->Text("Hive is not dirty.");
		$report->Append("Hive is not dirty.\r\n");
		logMsg("Hive (".$env{ntuser}.") is not dirty.\n");
		rptMsg("Hive (".$env{ntuser}.") is not dirty.\n");
	}
	else {}
#----------------------------------------------------------------------------------------

	$report->Append("Logging to ".$env{logfile}."\r\n");

	logMsg("Log opened.");
	logMsg("File: ".$env{ntuser});
	logMsg("Environment set up.");

#----------------------------------------------------------------------------------------
# determine the type of hive file

	my %guess = guessHive($env{ntuser});
	my $type = "";
	foreach my $g (keys %guess) {
#		::rptMsg(sprintf "%-8s = %-2s",$g,$guess{$g});
		$type = $g if ($guess{$g} == 1);
	}
	$report->Append("Hive type: ".$type."\r\n");
#----------------------------------------------------------------------------------------
# get a list of plugins based on the hive type
	$report->Append("Getting list of plugins based on hive type...\r\n");
	my @plugins;
	opendir(DIR,$plugindir) || die "Could not open $plugindir: $!\n";
	@plugins = readdir(DIR);
	closedir(DIR);
# hash of lists to hold plugin names	
	my %files = ();

	foreach my $p (@plugins) {
		next unless ($p =~ m/\.pl$/);
# $pkg = name of plugin		
		my $pkg = (split(/\./,$p,2))[0];
# skip over plugins that end in _tln, _json, or _yara		
		next if ($pkg =~ m/tln$/ || $pkg =~ m/json$/ || $pkg =~ m/yara$/ || $pkg =~ /csv$/);
#		$p = $plugindir.$p;
		$p = File::Spec->catfile($plugindir,$p);
		eval {
			require $p;
			my $hive    = $pkg->getHive();
			my @hives = split(/,/,$hive);
			foreach my $lch (@hives) {
				$lch =~ tr/A-Z/a-z/;
				$lch =~ s/\.dat$//;
				$lch =~ s/^\s+//;
				$type =~ tr/A-Z/a-z/;
				$files{$pkg} = 1 if ($lch eq $type);
			}
		};
		print "Error: $@\n" if ($@);
	}
	$report->Append("...Done.\r\n");
	$report->Append("Start ripping...\r\n");
	my $err_cnt = 0;
	foreach my $f (sort keys %files) {
		eval {
#			require "plugins/".$plugins{$i}."\.pl";
			my $plugin_file = File::Spec->catfile($plugindir,$f.".pl");
			require $plugin_file;
			$f->pluginmain($env{ntuser});
		};
		if ($@) {
			$err_cnt++;
			logMsg("Error in ".$f.": ".$@);
		}
		$report->Append($f."...Done.\r\n");
		$status->Text($f." complete.");
		rptMsg("-" x 40);
		Win32::GUI::DoEvents();
	}

	$report->Append($err_cnt." plugins completed with errors.\r\n");
	$status->Text("Done.");
}

sub close_Click {
	$main->Hide();
	exit -1;
}

# About box
sub RR_OnAbout {
  my $self = shift;
  $self->MessageBox(
     "Registry Ripper, v.".$VERSION."\r\n".
     "Parses Registry hive (NTUSER\.DAT, System, etc.) files, placing pertinent info in a report ".
     "file in a readable manner.\r\n".
     "\r\n".
     "Copyright 2023 Quantum Analytics Research, LLC.\r\n".
     "H\. Carvey, keydet89\@yahoo\.com",
     "About...",
     MB_ICONINFORMATION | MB_OK,
  );
  0;
}
#-----------------------------------------------------------

#-----------------------------------------------------------
sub setUpEnv {
	$env{ntuser} = $ntuserfile->Text();
	$env{rptfile} = $rptfile->Text();
# Ensure that the report file has a .txt extension if none was given
	$env{rptfile} = $env{rptfile}."\.txt" unless ($env{rptfile} =~ m/\.\w+$/i);
	$rptfile->Text($env{rptfile});
	
	my @path = split(/\\/,$env{rptfile});
	my $last = scalar(@path) - 1;
	my @f = split(/\./,$path[$last]);
	my $ext = $f[scalar(@f) - 1];
	
# Assemble path to log file	
	$f[scalar(@f) - 1] = "log";
	$path[$last] = join('.',@f);
#	print join('\\',@path)."\n";
	$env{logfile} = join('\\',@path);
}

sub logMsg {
	open(FH,">>",$env{logfile});
	print FH localtime(time).": ".$_[0]."\n";
	close(FH);
}

sub rptMsg {
	open(FH,">>",$env{rptfile});
	binmode FH,":utf8";
	print FH $_[0]."\n";
	close(FH);
}

#-------------------------------------------------------------
# guessHive()
# updated 20200322
#-------------------------------------------------------------
sub guessHive {
	my $hive = shift;
	my $reg;
	my $root_key;
	my %guess;
	eval {
		$reg = Parse::Win32Registry->new($hive);
	  $root_key = $reg->get_root_key;
	};
	$guess{unknown} = 1 if ($@);
#-------------------------------------------------------------
# updated 20200322
# see if we can get the name from the hive file	
	my $embed = $reg->get_embedded_filename();
	my @n = split(/\\/,$embed);
	my $r = $n[scalar(@n) - 1];
	$r =~ tr/A-Z/a-z/;
	my $name = (split(/\./,$r,2))[0];
	$guess{$name} = 1;
#-------------------------------------------------------------
	
# Check for SAM
	eval {
		$guess{sam} = 1 if (my $key = $root_key->get_subkey("SAM\\Domains\\Account\\Users"));
	};
# Check for Software	
	eval {
		$guess{software} = 1 if ($root_key->get_subkey("Microsoft\\Windows\\CurrentVersion") &&
				$root_key->get_subkey("Microsoft\\Windows NT\\CurrentVersion"));
	};

# Check for System	
	eval {
		$guess{system} = 1 if ($root_key->get_subkey("MountedDevices") &&
				$root_key->get_subkey("Select"));
	};
	
# Check for Security	
	eval {
		$guess{security} = 1 if ($root_key->get_subkey("Policy\\Accounts") &&
				$root_key->get_subkey("Policy\\PolAdtEv"));
	};
# Check for NTUSER.DAT	
	eval {
		$guess{ntuser} = 1 if ($root_key->get_subkey("Software\\Microsoft\\Windows\\CurrentVersion")&&
				$root_key->get_subkey("Software\\Microsoft\\Windows NT\\CurrentVersion"));
	};	
	
	eval {
		$guess{usrclass} = 1 if ($root_key->get_subkey("Local Settings\\Software") &&
				$root_key->get_subkey("lnkfile"));
	};
	
	return %guess;
}

#-------------------------------------------------------------
# checkHive()
# check to see if hive is "dirty"
# Added 20200322
#-------------------------------------------------------------
sub checkHive {
	my $hive = shift;
	my $reg = Parse::Win32Registry->new($hive);
	return $reg->is_dirty();
}
