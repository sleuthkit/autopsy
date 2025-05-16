#-----------------------------------------------------------
# denydeviceids
#
# Change history:
#  20221023 - created
# 
# Ref:
#  https://superuser.com/questions/1189380/is-there-any-way-to-control-device-installation-restrictions-via-the-registry
#  https://twitter.com/CyberRaiju/status/1584119443860647940
#  https://twitter.com/InfosecRDM/status/803041636506894336 (2016)
#  
#
# copyright 2022 QAR,LLC 
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package denydeviceids;
use strict;

my %config = (hive          => "Software",
			  category      => "initial access",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              MITRE         => "T1200",
              version       => 20221023);

sub getConfig{return %config}
sub getShortDescr {
	return "Check DenyDeviceIDs settings";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::rptMsg("Launching denydeviceids v.".$VERSION);
	::rptMsg("denydeviceids v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr());  
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");

	my $key_path = ('Policies\\Microsoft\\Windows\\DeviceInstall\\Restrictions');
	
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");
		
		my @vals = ("DenyDeviceIDs","DenyDeviceIDsRetroactive");
		foreach my $v (@vals) {
			eval {
				my $x = $key->get_value($v)->get_data();
				::rptMsg(sprintf "%-25s %-4s",$v,$x);
			};
		}
		::rptMsg("");
		
		if (my $k = $key->get_subkey("DenyDeviceIDs")) {
			my @vals = $k->get_list_of_values();
			if (scalar @vals > 0) {
				foreach my $v (@vals) {
					::rptMsg(sprintf "%-5s %-45s",$v->get_name(),$v->get_data());
				}
			}
		}
	
	}
	else {
		::rptMsg($key_path." not found.");
	}
	::rptMsg("");
	::rptMsg("Analysis Tip: ");
	::rptMsg("");
	::rptMsg("");
	::rptMsg("DenyDeviceIDsRetroactive corresponds to \"Also apply to matching devices that are already installed\" policy.");
	::rptMsg("");
	::rptMsg("");
}
1;