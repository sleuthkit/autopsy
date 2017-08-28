#-----------------------------------------------------------
# dnschanger.pl
# DNSChanger malware modifies the NameServer and/or DhcpNameServer values
# within the Registry for the interfaces.
# 
# Change history
#    20120203 - created
#
# Need to add grep() for ranges:
#  start range	end range
# 85.255.112.0	85.255.127.255
# 67.210.0.0	67.210.15.255
# 93.188.160.0	93.188.167.255
# 77.67.83.0	77.67.83.255
# 213.109.64.0	213.109.79.255
# 64.28.176.0	64.28.191.255
#
# Note: these may not be the only ranges used.  The best use of the
# plugin is to know what your ranges are, and eyeball the output of 
# the plugin.
#
# References
#   https://twitter.com/#!/saved-search/%23DFIR
# 
# copyright 2012 Quantum Analytics Research, LLC
#-----------------------------------------------------------
package dnschanger;
use strict;

my %config = (hive          => "System",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20120203);

sub getConfig{return %config}
sub getShortDescr {
	return "Check for indication of DNSChanger infection.";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	my %nics;
	my $ccs;
	::logMsg("Launching dnschanger v.".$VERSION);
	::rptMsg("dnschanger v.".$VERSION);
	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner 
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
# First thing to do is get the ControlSet00x marked current...this is
# going to be used over and over again in plugins that access the system
# file
	my $current;
	eval {
		$current = $root_key->get_subkey("Select")->get_value("Current")->get_data();
	};
	my @nics;
	my $key_path = "ControlSet00".$current."\\Services\\Tcpip\\Parameters\\Interfaces";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		my @guids = $key->get_list_of_subkeys();
		if (scalar @guids > 0) {
			foreach my $g (@guids) {
				::rptMsg("Adapter: ".$g->get_name());
				::rptMsg("LastWrite Time: ".gmtime($g->get_timestamp())." Z");
				eval {
					my @vals = $g->get_list_of_values();
					foreach my $v (@vals) {
						my $name = $v->get_name();
						next unless ($name =~ m/NameServer$/);
						my $data = $v->get_data();
						::rptMsg(sprintf "  %-28s %-20s",$name,$data);
					}
					::rptMsg("");
				};
			}
		}
		else {
			::rptMsg($key_path." has no subkeys.");
		}	
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1;