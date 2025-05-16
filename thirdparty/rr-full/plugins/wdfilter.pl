#-----------------------------------------------------------
# wdfilter.pl - WdFilter is in group "FSFilter Anti-Virus"
#
# History:
#  20201229 - created
#
# References:
#  https://twitter.com/jonasLyk/status/1339437249528795136
#  https://twitter.com/jonasLyk/status/1343909320178741250
#  https://www.n4r1b.com/posts/2020/01/dissecting-the-windows-defender-driver-wdfilter-part-1/
#  https://docs.microsoft.com/en-us/windows-hardware/drivers/ifs/load-order-groups-and-altitudes-for-minifilter-drivers
#
#   https://attack.mitre.org/techniques/T1562/001/
# 
# copyright 2020 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package wdfilter;
use strict;

my %config = (hive          => "system",
			  output        => "report",
			  category      => "defense evasion",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1562\.001",  
              version       => 20201229);

sub getConfig{return %config}
sub getShortDescr {
	return "Get WDFilter Altitude value";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();
my %files;
my @temps;

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching wdfilter v.".$VERSION);
	::rptMsg("wdfilter v.".$VERSION);
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
# First thing to do is get the ControlSet00x marked current...this is
# going to be used over and over again in plugins that access the system
# file
	my $ccs = ::getCCS($root_key);
	my $key_path = $ccs."\\Services\\WdFilter\\Instances\\WdFilter Instance";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		my $alt = ();
		eval {
			::rptMsg("");
			::rptMsg($key_path);
			::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
			::rptMsg("");
			$alt = $key->get_value("Altitude")->get_data();
			::rptMsg("Altitude value = ".$alt);
			::rptMsg("");
			::rptMsg("Analysis Tip: \"Altitude\" values determine where a driver attaches to the stack.  The default value for WdFilter is");
			::rptMsg("\"328010\". A value of -1 indicates an attempt to prevent the filter from attaching to any volumes, disabling WinDefend.");
		};
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

1;