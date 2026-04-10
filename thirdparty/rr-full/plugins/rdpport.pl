#-----------------------------------------------------------
# rdpport.pl
# Determine the RDP Port used
#
# History
#  20220809 - updated MITRE ATT&CK
#  20200922 - MITRE update
#  20200526 - minor updates
#  20100713 - created
#
# References
#   http://support.microsoft.com/kb/306759
#
# copyright 2022 Quantum Analytics Research, LLC
# author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package rdpport;
use strict;
my %config = (hive          => "System",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1133",
              category      => "initial access",
			  output		=> "report",
              version       => 20220809);

sub getConfig{return %config}
sub getShortDescr {
	return "Queries System hive for RDP Port";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	my $key;
	
	::logMsg("Launching rdpport v.".$VERSION);
	::rptMsg("rdpport v.".$VERSION); 
    ::rptMsg("(".getHive().") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	my $ccs = ::getCCS($root_key);
	my $key_path = $ccs."\\Control\\Terminal Server\\WinStations\\RDP-Tcp";
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("rdpport v.".$VERSION);
		::rptMsg("");
		my $port;
		eval {
			$port = $key->get_value("PortNumber")->get_data();
			::rptMsg("Remote Desktop Listening Port Number = ".$port);
			::rptMsg("");
			::rptMsg("Analysis Tip: Modifying the RDP port number can be considered a defense evasion/masquerading technique.");
		};
		::rptMsg("Error getting PortNumber: ".$@) if ($@);
		
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1