#-----------------------------------------------------------
# update_test
# The WindowsUpdate\Test key reportedly provides persistence, as it is checked
# via Windows Update
#
#
# Change history:
#  20200907 - created
# 
# Ref:
#  https://www.hexacorn.com/blog/2020/09/06/beyond-good-ol-run-key-part-127-testhooks-bonus/
#
#  https://attack.mitre.org/techniques/T1546/010/
#
# copyright 2020 QAR,LLC 
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package update_test;
use strict;

my %config = (hive          => "Software",
			  category      => "persistence",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1546\.010",
			  output		=> "report",
              version       => 20200907);

sub getConfig{return %config}
sub getShortDescr {
	return "Get Windows Update\\Test values";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::rptMsg("Launching update_test v.".$VERSION);
	::rptMsg("update_test v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr());  
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $key_path = ('Microsoft\\Windows\\CurrentVersion\\WindowsUpdate\\Test');

	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".::format8601Date($key->get_timestamp())."Z");
		
		my @vals = $key->get_list_of_values();
		if (scalar @vals > 0) {
			foreach my $v (@vals) {
				::rptMsg($v->get_name()." - ".$v->get_data());
			}
		}
		::rptMsg("");
		::rptMsg("Analysis Tip: The WindowsUpdate\\Test key is reportedly checked by Windows Updates, and may serve");
		::rptMsg("as a persistence mechanism.");
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1;