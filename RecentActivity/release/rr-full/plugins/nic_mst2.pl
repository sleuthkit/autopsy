#-----------------------------------------------------------
# nic_mst2.pl
# Plugin for Registry Ripper; Get information on network 
# interfaces from the System hive file - start with the
# Control\Network GUID subkeys...within the Connection key,
# look for MediaSubType == 2, and maintain a list of GUIDs.
# Then go over to the Services\Tcpip\Parameters\Interfaces 
# key and get the IP configurations for each of the interface
# GUIDs
# 
# Change history
#
#
# References
#   http://support.microsoft.com/kb/555382
#   http://support.microsoft.com/kb/894564
#   http://support.microsoft.com/kb/899868
#
# copyright 2008 H. Carvey
#-----------------------------------------------------------
package nic_mst2;
use strict;

my %config = (hive          => "System",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20080324);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets NICs from System hive; looks for MediaType = 2";	
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
	::logMsg("Launching nic_mst2 v.".$VERSION);
	::rptMsg("nic_mst2 v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
# First thing to do is get the ControlSet00x marked current...this is
# going to be used over and over again in plugins that access the system
# file
	my $current;
	my $key_path = 'Select';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		$current = $key->get_value("Current")->get_data();
		$ccs = "ControlSet00".$current;
		my $nw_path = $ccs."\\Control\\Network\\{4D36E972-E325-11CE-BFC1-08002BE10318}";
		my $nw;
		if ($nw = $root_key->get_subkey($nw_path)) {
			::rptMsg("Network key");
			::rptMsg($nw_path);
# Get all of the subkey names
			my @sk = $nw->get_list_of_subkeys();
			if (scalar(@sk) > 0) {
				foreach my $s (@sk) {
					my $name = $s->get_name();
					next if ($name eq "Descriptions");
					if (my $conn = $nw->get_subkey($name."\\Connection")) {
						my %conn_vals;
						my @vals = $conn->get_list_of_values();
						map{$conn_vals{$_->get_name()} = $_->get_data()}@vals;
# See what the active NICs were on the system; "active" based on PnpInstanceID having
# a string value
# Get the GUID of the interface, the name, and the LastWrite time of the Connection
# key						
						if (exists $conn_vals{PnpInstanceID} && $conn_vals{PnpInstanceID} ne "") {
							$nics{$name}{Name} = $conn_vals{Name};
							$nics{$name}{LastWrite} = $conn->get_timestamp();
						}
					}
				}
				
			}
			else {
				::rptMsg($nw_path." has no subkeys.");
			}			
		}
		else {
			::rptMsg($nw_path." could not be found.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
	::rptMsg("");
# access the Tcpip Services key to get the IP address information	
	if (scalar(keys %nics) > 0) {
		my $key_path = $ccs."\\Services\\Tcpip\\Parameters\\Interfaces";
		if ($key = $root_key->get_subkey($key_path)) {
			my %guids;
			::rptMsg($key_path);
			::rptMsg("LastWrite time ".gmtime($key->get_timestamp())." (UTC)");
			::rptMsg("");
# Dump the names of the subkeys under Parameters\Interfaces into a hash			
			my @sk = $key->get_list_of_subkeys();
			map{$guids{$_->get_name()} = 1}(@sk);
			
			foreach my $n (keys %nics) {
				if (exists $guids{$n}) {
					my $if = $key->get_subkey($n);
					::rptMsg("Interface ".$n);
					::rptMsg("Name: ".$nics{$n}{Name});
					::rptMsg("Control\\Network key LastWrite time ".gmtime($nics{$n}{LastWrite})." (UTC)");
					::rptMsg("Services\\Tcpip key LastWrite time ".gmtime($if->get_timestamp())." (UTC)");
					
					my @vals = $if->get_list_of_values;
					my %ip;
					map{$ip{$_->get_name()} = $_->get_data()}@vals;
					
					if (exists $ip{EnableDHCP} && $ip{EnableDHCP} == 1) {
						::rptMsg("\tDhcpDomain     = ".$ip{DhcpDomain});
						::rptMsg("\tDhcpIPAddress  = ".$ip{DhcpIPAddress});
						::rptMsg("\tDhcpSubnetMask = ".$ip{DhcpSubnetMask});
						::rptMsg("\tDhcpNameServer = ".$ip{DhcpNameServer});
						::rptMsg("\tDhcpServer     = ".$ip{DhcpServer});
					}
					else {
						::rptMsg("\tIPAddress      = ".$ip{IPAddress});
						::rptMsg("\tSubnetMask     = ".$ip{SubnetMask});
						::rptMsg("\tDefaultGateway = ".$ip{DefaultGateway});
					}
					
				}
				else {
					::rptMsg("Interface ".$n." not found in the ".$key_path." key.");
				}
				::rptMsg("");
			}
		}
	}
	else {
		::rptMsg("No active network interface cards were found.");
		::logMsg("No active network interface cards were found.");
	}
}
1;