#-----------------------------------------------------------
# iejava.pl
# 
# Category: Malware
# 
# History
#   20130429 - added alertMsg() functionality
#   20130214 - created
#
# References
#   http://www.greyhathacker.net/?p=610
#
# See also: http://support.microsoft.com/kb/2751647
# 
# Notes: this was seen on a system that was infected with ZeroAccess; during
#  the infection process, the key in question was set and the Flags value was
#  set to 1.
#
# copyright 2013, Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package iejava;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              osmask        => 22,
              category      => "malware",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20130429);

sub getConfig{return %config}

sub getShortDescr {
	return "Checks NTUSER for status of kill bit for IE Java ActiveX control";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	
	::logMsg("Launching iejava v.".$VERSION);
	::rptMsg("iejava v.".$VERSION); # banner
	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = "Software\\Microsoft\\Windows\\CurrentVersion\\Ext\\Settings\\{8AD9C840-044E-11D1-B3E9-00805F499D93}";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		
		my $flags;
		eval {
			$flags = $key->get_value("Flags")->get_data();
			::rptMsg("Flags: ".$flags);
			if ($flags == 1) {
				::rptMsg(" If the Flags value is set to 1, the IE Java ActiveX control is disabled,");
				::rptMsg(" as if thru IE's \"Manage Add-ons\"\.  Note: this NOT setting the kill bit.");
				::alertMsg("ALERT: ".$key_path." Flag value set to 1; IE Java ActiveX control disabled\.");
			}
			
		};
		if ($@) {
			::rptMsg("Flags value not found\.");
		}
		
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1;