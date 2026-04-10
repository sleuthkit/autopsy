#-----------------------------------------------------------
# smartscreen.pl
# Windows Defender SmartScreen warns users before allowing them to run unrecognized programs
# downloaded from the Internet
#
# Change history:
#   20221108 - updated with Explorer\SmartScreenEnabled value check
#   20210806 - created
#
# References:
#   https://www.stigviewer.com/stig/windows_10/2018-04-06/finding/V-63685
#   https://admx.help/?Category=Windows_8.1_2012R2&Policy=Microsoft.Policies.WindowsExplorer::EnableSmartScreen
#   
# copyright 2022 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package smartscreen;
use strict;

my %config = (hive          => "software",
			  category      => "defense evasion",
			  MITRE         => "T1562\.001",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              version       => 20221108);

sub getConfig{return %config}

sub getShortDescr {
	return "Check Windows Defender SmartScreen settings";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

my %comp;

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching smartscreen v.".$VERSION);
	::rptMsg("smartscreen v.".$VERSION); 
	::rptMsg("(".getHive().") ".getShortDescr());
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	 
	my $key; 
	my $key_path = "Policies\\Microsoft\\Windows\\System";
 	if ($key = $root_key->get_subkey($key_path)) {
 			eval {
 				my $c = $key->get_value("EnableSmartScreen")->get_data();
 				::rptMsg("");
				::rptMsg("Analysis Tip: Windows Defender SmartScreen will warn users before running unrecognized programs downloaded from");
				::rptMsg("the Internet.");
				::rptMsg("0 - Disabled");
				::rptMsg("1 - Enabled");
 			};
 			::rptMsg($key_path."\\EnableSmartScreen value not found.") if ($@);
 			
 			eval {
 				my $c = $key->get_value("ShellSmartScreenLevel")->get_data();
 				::rptMsg("ShellSmartScreenLevel value: ".$c);
 				::rptMsg("");
				::rptMsg("Analysis Tip: The ShellSmartScreenLevel value determines the actions taken when SmartScreen is enabled.");
				::rptMsg("Block - Will not present user with option to disregard warning and run the app.");
				::rptMsg("Warn  - Warn user, but allow them to disregard the warning and run the app.");
 			};
 	}
 	else {
 		::rptMsg($key_path." not found.");
 	}
	::rptMsg("");
# Added 20221108
# https://twitter.com/wdormann/status/1588879659906711552
	my $key_path = "Microsoft\\Windows\\CurrentVersion\\Explorer";
	if ($key = $root_key->get_subkey($key_path)) {
	
		eval {
			my $s = $key->get_value("SmartScreenEnabled")->get_data();
			::rptMsg("SmartScreenEnabled value: ".$s);
		};
		::rptMsg($key_path."\\SmartScreenEnabled value not found.") if ($@);
	
	}
	else {
		::rptMsg($key_path." not found.");
	}
	
}
1;