#-----------------------------------------------------------
# ips.pl
# Check System hive for IPAddresses and domains, including those for
# DHCP 
#
#
# Change history
#    20200911 - MITRE updates
#    20200518 - created
#
# References
# 
# copyright 2020 Quantum Analytics Research, LLC
# author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package ips;
use strict;

my %config = (hive          => "system",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "",
              category      => "config",
			  output		=> "report",
              version       => 20200911);

sub getConfig{return %config}
sub getShortDescr {
	return "Get IP Addresses and domains (DHCP,static)";	
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
	::logMsg("Launching ips v.".$VERSION);
	::rptMsg("ips v.".$VERSION); # banner
  ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
# First thing to do is get the ControlSet00x marked current...this is
# going to be used over and over again in plugins that access the system
# file
	my $current;
	eval {
		$current = ::getCCS($root_key);
	};
	
	my $key_path = $current."\\Services\\Tcpip\\Parameters\\Interfaces";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		my @subkey1 = $key->get_list_of_subkeys();
		if (scalar @subkey1 > 0) {
			
			::rptMsg(sprintf "%-20s %-30s","IPAddress","Domain");
			
			foreach my $s1 (@subkey1) {
				
				getIPs($s1);
				
				my @subkey2 = $s1->get_list_of_subkeys();
				if (scalar @subkey2 > 0) {
					foreach my $s2 (@subkey2) {
						getIPs($s2);
						
					}
				}
			}
		}
	}	
	else {
		::rptMsg($key_path." not found.");
	}
}

sub getIPs {
	my $key = shift;
	
	my $dh = ();
	my $dhdom = ();
	my $hint = ();
	my $ip  = ();
	my $dom = ();
	
	eval {
		$dh = $key->get_value("DhcpIPAddress")->get_data();
	};
	
	eval {
		$dhdom = $key->get_value("DhcpDomain")->get_data();
	};
	
	eval {
		$hint = $key->get_value("DhcpNetworkHint")->get_data();
		$hint = pack("h*",reverse $hint);
	};
	
	::rptMsg(sprintf "%-20s %-30s %-30s",$dh,$dhdom,"Hint: ".$hint) if ($dh);
	

	eval {
		$ip = $key->get_value("IPAddress")->get_data();
	};
	
	eval {
		$dom = $key->get_value("Domain")->get_data();
	};
	::rptMsg(sprintf "%-20s %-30s",$ip,$dom) if ($ip);
}

1;