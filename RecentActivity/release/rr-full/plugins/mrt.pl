#-----------------------------------------------------------
# mrt.pl
#
# Per http://support.microsoft.com/kb/891716/, whenever MRT is run, a new
# GUID is written to the Version value.  Check the KB article to compare
# GUIDs against the last time the tool was run.  Also be sure to check the 
# MRT logs in %WinDir%\Debug (mrt.log)
#
#
# copyright 2008 H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package mrt;
use strict;

my %config = (hive          => "Software",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 1,
              version       => 20080804);

sub getConfig{return %config}

sub getShortDescr {
	return "Check to see if Malicious Software Removal Tool has been run";	
}
sub getDescr{}
sub getRefs {"Deployment of the Microsoft Windows Malicious Software Removal Tool" => 
	           		"http://support.microsoft.com/kb/891716/",
	           "The Microsoft Windows Malicious Software Removal Tool" => "http://support.microsoft.com/?kbid=890830"}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching mrt v.".$VERSION);
	::rptMsg("mrt v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;


	my $key_path = "Microsoft\\RemovalTools\\MRT";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("Key Path: ".$key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		
		my $version;
		eval {
			$version = $key->get_value("Version")->get_data();
		};
		if ($@) {
			::rptMsg("Error getting Version information: ".$@);
			
		}
		else {
			::rptMsg("Version: ".$version);
			::rptMsg("");
			::rptMsg("Analysis Tip:  Go to http://support.microsoft.com/kb/891716/ to see when MRT");
			::rptMsg("was last run.  According to the KB article, each time MRT is run, a new GUID");
			::rptMsg("is written to the Version value.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
		::logMsg($key_path." not found.");
	}
}
1;