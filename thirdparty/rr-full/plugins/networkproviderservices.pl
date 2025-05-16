#-----------------------------------------------------------
# networkproviderservices.pl 
# Plugin to check Windows services keys for a NetworkProvider subkey
# Based on the networkproviders.pl plugin, but checks Windows services keys for a NetworkProvider subkey
#
# History:
#  20230118 - created
#
# References:
#  https://twitter.com/0gtweet/status/1283532806816137216
#  https://www.scip.ch/en/?labs.20220217 <-- added 20220217
#  https://attack.mitre.org/techniques/T1556/003/
#
# copyright 2023 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package networkproviderservices;
use strict;

my %config = (hive          => "system",
			  output        => "report",
			  category      => "credential access",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              MITRE         => "T1556\.003",  
              version       => 20220803);

sub getConfig{return %config}
sub getShortDescr {
	return "Check Windows services keys for NetworkProvider subkey";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching networkproviderservices v.".$VERSION);
	::rptMsg("networkproviderservices v.".$VERSION);
	::rptMsg("Category: ".$config{category}."  MITRE: ".$config{MITRE});
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
# First thing to do is get the ControlSet00x marked current...this is
# going to be used over and over again in plugins that access the system
# file
	my $ccs = ::getCCS($root_key);
	my $key_path = $ccs."\\services";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {

		my @subkeys = $key->get_list_of_subkeys();
		if (scalar @subkeys > 0) {
			foreach my $s (@subkeys) {
				
				if (my $n = $s->get_subkey("NetworkProvider")) {
					::rptMsg($key_path."\\".$s->get_name()."\\NetworkProvider subkey found");
					::rptMsg("LastWrite time    : ".::format8601Date($n->get_timestamp())."Z");
					eval {
						my $dev = $n->get_value("DeviceName")->get_data();
						::rptMsg("DeviceName        : ".$dev);
					};
					
					eval {
						my $path = $n->get_value("ProviderPath")->get_data();
						::rptMsg("ProviderPath      : ".$path);
						
					};
					::rptMsg("");
				}
		
			}
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

1;