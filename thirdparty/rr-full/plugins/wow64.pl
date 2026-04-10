#-----------------------------------------------------------
# wow64
#
# Change history:
#  20200916 - MITRE udpates
#  20200515 - updated date output format
#  20190712 - created
# 
# Ref:
#  http://www.hexacorn.com/blog/2019/07/11/beyond-good-ol-run-key-part-108-2/
#  https://wbenny.github.io/2018/11/04/wow64-internals.html
#
#	https://attack.mitre.org/techniques/T1546/
#
# copyright 2020 QAR,LLC 
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package wow64;
use strict;

my %config = (hive          => "Software",
			  category      => "persistence",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1546",
			  output		=> "report",
              version       => 20200916);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets contents of WOW64\\x86 key";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::rptMsg("Launching wow64 v.".$VERSION);
	::rptMsg("wow64 v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my @paths = ('Microsoft\\WOW64\\x86','Microsoft\\WOW64\\arm');
	
	::rptMsg("WOW64");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	foreach my $key_path (@paths) {
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg($key_path);
			::rptMsg("LastWrite Time ".::format8601Date($key->get_timestamp())."Z");
		
			my @vals;
			if (@vals = $key->get_list_of_values()) {
				if (scalar(@vals) > 0) {
					foreach my $v (@vals) {
						::rptMsg($v->get_name()."  ".$v->get_data());
					}
				}
			}
			else {
				::rptMsg($key_path." has no values.");
			}
		}
		else {
			::rptMsg($key_path." not found.");
		}
	}
}
1;