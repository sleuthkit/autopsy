#-----------------------------------------------------------
# spp_clients
#
# History
#  20201005 - MITRE update
#  20130429 - added alertMsg() functionality
#  20120914 - created
#
# copyright 2020 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package spp_clients;
use strict;

my %config = (hive          => "Software",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "",
              category      => "config", 
			  output		=> "report",
              version       => 20201005);

sub getConfig{return %config}
sub getShortDescr {
	return "Determines volumes monitored by VSS";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching spp_clients v.".$VERSION);
	::rptMsg("spp_clients v.".$VERSION); 
	::rptMsg("(".getHive().") ".getShortDescr()."\n"); 
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = 'Microsoft\\Windows NT\\CurrentVersion\\SPP\\Clients';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("SPP_Clients");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");
		::rptMsg("Monitored volumes: ");
		my $mon;
		eval {
			$mon = $key->get_value("{09F7EDC5-294E-4180-AF6A-FB0E6A0E9513}")->get_data();
			::rptMsg($mon);
			::rptMsg("");
			::rptMsg("Analysis Tip: This value indicates volumes that are monitored for VSCs. A threat actor can read this value");
			::rptMsg("and use volumes not monitored, or modify the value.");
		};
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1;