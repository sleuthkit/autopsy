#-----------------------------------------------------------
# sysinternals.pl
#  
#
# Change history
#   20220824 - updated to check for global flag
#   20201005 - MITRE update
#   20200511 - updated date output format
#   20120608 - created
#
# References
#   https://twitter.com/leonzandman/status/1561736801953382400
# 
# copyright 2022 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package sysinternals;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1204",
              category      => "program execution",
			  output		=> "report",
              version       => 20220824);

sub getConfig{return %config}
sub getShortDescr {
	return "Checks for SysInternals apps keys";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching sysinternals v.".$VERSION);
	::rptMsg("sysinternals v.".$VERSION);
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = 'Software\\SysInternals';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("SysInternals");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".::format8601Date($key->get_timestamp())."Z");

# added 20220824		
		::rptMsg("");
		eval {
			my $e = $key->get_value("EulaAccepted")->get_data();
			::rptMsg("Global EulaAccepted value: ".$e);
			::rptMsg("");
		};
		
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar(@subkeys) > 0) {
			foreach my $s (@subkeys) { 
				::rptMsg($s->get_name()." [".::format8601Date($s->get_timestamp())."Z]");
				
				my $eula;
				eval {
					$eula = $s->get_value("EulaAccepted")->get_data();
				};
				if ($@) {
					::rptMsg("  EulaAccepted value not found.");
				}
				else {
					::rptMsg("  EulaAccepted: ".$eula);
				}
				::rptMsg("");
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