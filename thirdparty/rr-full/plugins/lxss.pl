#-----------------------------------------------------------
# lxss.pl
#
# Change history
#  20200927 - MITRE update
#  20200511 - updated date output format
#  20190813 - created
#
# References
#  https://attack.mitre.org/techniques/T1564/006/
# 
# copyright 2019-2020 QAR, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package lxss;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              category      => "defense evasion",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              MITRE         => "T1564\.006",
              version       => 20200927);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets WSL config.";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching lxss v.".$VERSION);
	::rptMsg("lxss v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = 'Software\\Microsoft\\Windows\\CurrentVersion\\Lxss';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("Lxss");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".::format8601Date($key->get_timestamp())."Z");
		
		eval {
			my $def = $key->get_value("DefaultDistribution")->get_data();
			::rptMsg("DefaultDistribution: ".$def);
		};
		
		::rptMsg("");
		
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar @subkeys > 0) {
			foreach my $sk (@subkeys) {
				::rptMsg($sk->get_name());
				::rptMsg("LastWrite: ".::format8601Date($sk->get_timestamp())."Z");
				
				eval {
					my $dist = $sk->get_value("DistributionName")->get_data();
					::rptMsg("DistributionName: ".$dist);
				};
				
				eval {
					my $kern = $sk->get_value("KernelCommandLine")->get_data();
					::rptMsg("KernelCommandLine: ".$kern);
				};
				::rptMsg("");
			}
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

1;