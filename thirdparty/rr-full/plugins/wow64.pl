#-----------------------------------------------------------
# wow64
#
# Change history:
#  20190712 - created
# 
# Ref:
#  http://www.hexacorn.com/blog/2019/07/11/beyond-good-ol-run-key-part-108-2/
#  https://wbenny.github.io/2018/11/04/wow64-internals.html
#
# copyright 2019 QAR,LLC 
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package wow64;
use strict;

my %config = (hive          => "Software",
							category      => "persistence",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20190712);

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
	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); 
	my @paths = ('Microsoft\\WOW64\\x86','Microsoft\\WOW64\\arm');
	
	::rptMsg("WOW64");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	foreach my $key_path (@paths) {
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg($key_path);
			::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		
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