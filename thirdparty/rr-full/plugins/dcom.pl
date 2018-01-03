#-----------------------------------------------------------
# dcom.pl
# DCOM ports can be set, and in Matt Graeber's BlackHat 2015 paper,
# he recommends modifying the ports
#
# https://www.blackhat.com/docs/us-15/materials/us-15-Graeber-Abusing-Windows
#   -Management-Instrumentation-WMI-To-Build-A-Persistent%20Asynchronous-And-Fileless-Backdoor-wp.pdf  
#
#
# Change history
#   20151203 - created
#
# References
#   http://blog.backslasher.net/setting-dynamic-rpc-port-ranges.html
#
# Copyright (c) 2015 QAR, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package dcom;
use strict;

# Declarations #
my %config = (hive          => "Software",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              category      => "system config",
              version       => 20151203);
my $VERSION = getVersion();

# Functions #
sub getConfig {return %config}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}
sub getDescr {}
sub getShortDescr {
	return "Check DCOM Ports";
}
sub getRefs {}

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching dcom v.".$VERSION);
  ::rptMsg("dcom v.".$VERSION); 
  ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n");  
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key;
	my $ports;
	
	my $key_path = "Microsoft\\Rpc\\Internet";
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");

		eval {
			$ports = $key->get_value("Ports")->get_data();
			::rptMsg("Ports value found: ".$ports);
		};
		
		eval {
			$ports = $key->get_value("PortsInternetAvailable")->get_data();
			::rptMsg("PortsInternetAvailable value found: ".$ports);
		};
		
		eval {
			$ports = $key->get_value("UseInternetPorts")->get_data();
			::rptMsg("UseInternetPorts value found: ".$ports);
		};

	}
	else {

	} 
}

1;
