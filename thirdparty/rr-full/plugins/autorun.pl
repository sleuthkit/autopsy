#-----------------------------------------------------------
# autorun
#   
#  
#  
# Change history
#   20221109 - created
#
# References
#   https://www.samlogic.net/articles/autorun-enable-disable-nodrivetypeautorun.htm
#   https://superuser.com/questions/1378243/nodrivetypeautorun-registry-key-missing-from-windows-10
#   https://learn.microsoft.com/en-us/windows/win32/shell/autoplay-reg
#
# Copyright 2022 QAR, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package autorun;
use strict;

my %config = (hive          => "NTUSER\.DAT, Software",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1204",
              category      => "execution",
			  output 		=> "report",
              version       => 20221109);

my $VERSION = getVersion();

sub getConfig {return %config}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}
sub getDescr {}
sub getShortDescr {
	return "Checks autorun settings";
}
sub getRefs {}

sub pluginmain {
	my $class = shift;
	my $hive = shift;

	::logMsg("Launching autorun v.".$VERSION);
	::rptMsg("autorun v.".$VERSION);
	::rptMsg("(".$config{hive}.") ".getShortDescr());   
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key;
	
	my %guess = ();
	my $hive_guess = "";
	my %guess = ::guessHive($hive);
	foreach my $g (keys %guess) {
		$hive_guess = $g if ($guess{$g} == 1);
	}  
# Set paths
 	my $key_path = ();
 	if ($hive_guess eq "software") {
 		$key_path = "Microsoft\\Windows\\CurrentVersion\\Policies\\Explorer";
 	}
 	elsif ($hive_guess eq "ntuser") {
 		$key_path = "Software\\Microsoft\\Windows\\CurrentVersion\\Policies\\Explorer";
 	}
 	else {}
	

	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");
		
		eval {
			my $a = $key->get_value("NoDriveTypeAutoRun")->get_data();
			::rptMsg(sprintf "%-20s 0x%04x","NoDriveTypeAutoRun",$a);
		};
		::rptMsg("NoDriveTypeAutoRun value not found.") if ($@);
		
		eval {
			my $a = $key->get_value("NoDriveAutoRun")->get_data();
			::rptMsg(sprintf "%-20s 0x%04x","NoDriveAutoRun",$a);
		};
		::rptMsg("NoDriveAutoRun value not found.") if ($@);
	}
	else {
		::rptMsg($key_path." key not found.");
	}
	
	if ($hive_guess eq "ntuser") {
		::rptMsg("");
		$key_path = "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\AutoplayHandlers";
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg($key_path);
			::rptMsg("LastWrite Time ".::format8601Date($key->get_timestamp())."Z");
			::rptMsg("");
			
			eval {
				my $a = $key->get_value("DisableAutoplay")->get_data();
				::rptMsg(sprintf "%-20s 0x%04x","DisableAutoplay",$a);
				::rptMsg("");
				::rptMsg("1 - Autoplay disabled");
				::rptMsg("0 - Autoplay enabled");
			};
			::rptMsg("DisableAutoplay value not found.") if ($@);
	
		}
		else {
			::rptMsg($key_path." key not found.");
		}
	}
}

1;
