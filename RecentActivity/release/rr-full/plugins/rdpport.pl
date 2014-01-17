#-----------------------------------------------------------
# rdpport.pl
# Determine the RDP Port used
#
# History
#  20100713 - created
#
# References
#   http://support.microsoft.com/kb/306759
#
# copyright 2010 Quantum Analytics Research, LLC
#-----------------------------------------------------------
package rdpport;
use strict;
my %config = (hive          => "System",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20100713);

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
	::rptMsg("rdpport v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	my $ccs = $root_key->get_subkey("Select")->get_value("Current")->get_data();
	my $key_path = "ControlSet00".$ccs."\\Control\\Terminal Server\\WinStations\\RDP-Tcp";
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("rdpport v.".$VERSION);
		::rptMsg("");
		my $port;
		eval {
			$port = $key->get_value("PortNumber")->get_data();
			::rptMsg("Remote Desktop Listening Port Number = ".$port);
		};
		::rptMsg("Error getting PortNumber: ".$@) if ($@);
		
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1