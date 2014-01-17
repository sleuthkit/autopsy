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
# copyright 2013 Quantum Research Analytics, LLC
# Author: H. Carvey, keydet89@yahoo.com
# 
# This software is released via the GPL v3.0 license:
# http://www.gnu.org/licenses/gpl.html
#-----------------------------------------------------------
#use strict;
use Win32::GUI();
use Parse::Win32Registry qw(:REG_);

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
my $VERSION = "2\.8";
my %env; 
my @alerts = ();

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

$main->AddLabel(
    -text   => "Profile:",
    -left   => 20,
    -top    => 90);

# http://perl-win32-gui.sourceforge.net/cgi-bin/docs.cgi?doc=combobox
my $combo = $main->AddCombobox(
 -name   => "Combobox",
# -dropdown => 1,
 -dropdownlist => 1,
 -top    => 90,
 -left   => 100,
 -width  => 120,
 -height => 110,
 -tabstop=> 1,
 );

my $testlabel = $main->AddLabel(
	-text => "",
	-name => "TestLabel",
	-pos => [10,140],
	-size => [445,160],
	-frame => etched,
	-sunken => 1
);

my $report = $main->AddTextfield(
    -name      => "Report",
    -pos       => [20,150],
    -size      => [425,140],
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
		-text => "Rip It");
		
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

populatePluginsList();
$combo->Text("<Select>");
$status->Text("Profile List Populated.");

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
# Get the selected item from the Plugins file listbox
# only allows for single selections at this time; defaults to ntuser
# if none selected
	my $pluginfile = $combo->GetLBText($combo->GetCurSel());
	$pluginfile = "ntuser" if ($pluginfile eq "");
	$report->Append("Logging to ".$env{logfile}."\r\n");
	$report->Append("Using plugins file ".$pluginfile."\r\n");
	logMsg("Log opened.");
	logMsg("File: ".$env{ntuser});
	logMsg("Environment set up.");
	my %plugins = parsePluginsFile($pluginfile);
	logMsg("Parsed Plugins file ".$pluginfile);
	if (scalar(keys %plugins) == 0) {
		Win32::GUI::MessageBox($main,$ENV{USERNAME}.", the plugins file has no plugins!!.\r\n",
		                       "Doh!!",16);
		return;
	}
	my $err_cnt = 0;
	foreach my $i (sort {$a <=> $b} keys %plugins) {
		eval {
			require "plugins\\".$plugins{$i}."\.pl";
			$plugins{$i}->pluginmain($env{ntuser});
		};
		if ($@) {
			$err_cnt++;
			logMsg("Error in ".$plugins{$i}.": ".$@);
		}
		
		$report->Append($plugins{$i}."...Done.\r\n");
		$status->Text($plugins{$i}." completed.");
		
		Win32::GUI::DoEvents();
		logMsg($err_cnt." plugins completed with errors.");
		logMsg($plugins{$i}." complete.");
		rptMsg("-" x 40);
	}
# add output of alerts to the report file here	
	if (scalar(@alerts) > 0) {
#		rptMsg("");
#		rptMsg("Alerts");
#		rptMsg("-" x 40);
		foreach my $a (@alerts) {
			rptMsg($a);
		}
	}
	
	$report->Append($err_cnt." plugins completed with errors.\r\n");
	$status->Text("Done.");
}

sub close_Click {
	$main->Hide();
	exit -1;
}

sub Combobox_CloseUp {
	$status->Text("Profile = ".$combo->GetLBText($combo->GetCurSel()));	
}

# About box
sub RR_OnAbout {
  my $self = shift;
  $self->MessageBox(
     "Registry Ripper, v.".$VERSION."\r\n".
     "Parses Registry hive (NTUSER\.DAT, System, etc.) files, placing pertinent info in a report ".
     "file in a readable manner.\r\n".
     "\r\n".
     "Copyright 2013 Quantum Analytics Research, LLC.\r\n".
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
	print join('\\',@path)."\n";
	$env{logfile} = join('\\',@path);

# Use the above code to set up the path to the Timeline
# (.tln) file	
# Assemble path to log file	
#	$f[scalar(@f) - 1] = "tln";
#	$path[$last] = join('.',@f);
#	print join('\\',@path)."\n";
#	$env{tlnfile} = join('\\',@path);

}

#-----------------------------------------------------------
# get a list of plugins files from the plugins dir
#-----------------------------------------------------------
sub getProfiles {
	my @pluginfiles;
	opendir(DIR,"plugins");
	my @files = readdir(DIR);
	close(DIR);
	
	foreach my $f (@files) {
		next if ($f =~ m/^\.$/ || $f =~ m/^\.\.$/);
		next if ($f =~ m/\.pl$/ || $f =~ m/\.txt$/);
		push(@pluginfiles,$f);
	}
	return @pluginfiles;
}

#-----------------------------------------------------------
# populate the list of plugins files
#-----------------------------------------------------------
sub populatePluginsList {
	my @files = getProfiles();
	foreach my $f (@files) {
		$combo->InsertItem($f);
	}
}

#-----------------------------------------------------------
# 
#-----------------------------------------------------------
sub parsePluginsFile {
	my $file = $_[0];
	my %plugins;
# Parse a file containing a list of plugins
# Future versions of this tool may allow for the analyst to 
# choose different plugins files	
	my $pluginfile = "plugins\\".$file;
	if (-e $pluginfile) {
		open(FH,"<",$pluginfile);
		my $count = 1;
		while(<FH>) {
			chomp;
			next if ($_ =~ m/^#/ || $_ =~ m/^\s+$/);
#			next unless ($_ =~ m/\.pl$/);
			next if ($_ eq "");
			$_ =~ s/^\s+//;
			$_ =~ s/\s+$//;
			$plugins{$count++} = $_; 
		}
		close(FH);
		$status->Text("Plugin file parsed and loaded.");
		return %plugins;
	}
	else {
		$report->Append($pluginfile." not found.\r\n");
		return undef;
	}
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

sub alertMsg {
	push(@alerts,$_[0]);
}

#-------------------------------------------------------------
# getTime()
# Translate FILETIME object (2 DWORDS) to Unix time, to be passed
# to gmtime() or localtime()
#-------------------------------------------------------------
sub getTime($$) {
	my $lo = shift;
	my $hi = shift;
	my $t;

	if ($lo == 0 && $hi == 0) {
		$t = 0;
	} else {
		$lo -= 0xd53e8000;
		$hi -= 0x019db1de;
		$t = int($hi*429.4967296 + $lo/1e7);
	};
	$t = 0 if ($t < 0);
	return $t;
}