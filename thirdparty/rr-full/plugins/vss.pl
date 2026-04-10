#-----------------------------------------------------------
# vss.pl
#
# History:
#  20210128 - created
#
# References:
#  https://twitter.com/0gtweet/status/1354766164166115331
#  https://support.hpe.com/hpesc/public/docDisplay?docLocale=en_US&docId=a00091959en_us
# 
#  https://attack.mitre.org/techniques/T1562/001/
# 
# copyright 2021 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package vss;
use strict;

my %config = (hive          => "System",
			  category      => "defense evasion",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1562\.001",
			  output		=> "report",
              version       => 20210128);

sub getConfig{return %config}
sub getShortDescr {
	return "Check VSS\\Diag settings";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();
my %files;
my $str = "";

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching vss v.".$VERSION);
	::rptMsg("vss v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr());  
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
# First thing to do is get the ControlSet00x marked current...this is
# going to be used over and over again in plugins that access the system
# file
	my $ccs = ::getCCS($root_key);
	my $key_path = $ccs."\\Services\\VSS\\Diag";
	my $key = ();
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
		eval {
			my $dis = $key->get_value("")->get_data();
			::rptMsg("(Default) value = ".$dis);
		};
		::rptMsg("(Default) value not found.") if ($@);
	}
	else {
		::rptMsg($key_path." not found.");
	}	
	::rptMsg("");
	::rptMsg("Analysis Tip: A \"(Default)\" setting of \"Disabled\" disables VSS Legacy Tracing, and prevents");
	::rptMsg("Windows Backup from running. If the value is set, no reboot is required.");
}

1;