#-----------------------------------------------------------
# wsh_settings
#
# Change history:
#  20180819 - created
# 
# Ref:
#  http://www.hexacorn.com/blog/2018/08/18/lateral-movement-using-wshcontroller-wshremote-objects-iwshcontroller-and-iwshremote-interfaces/
#
# copyright 2018 QAR,LLC 
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package wsh_settings;
use strict;

my %config = (hive          => "Software",
							category      => "config",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20180819);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets WSH Settings";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	my ($name,$data);
	::rptMsg("Launching wsh_settings v.".$VERSION);
	::rptMsg("wsh_settings v.".$VERSION); # banner
	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner 
	my $key_path = ('Microsoft\\Windows Script Host\\Settings');
	
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("Key LastWrite: ".gmtime($key->get_timestamp())." Z");
		my @vals = $key->get_list_of_values();
		if (scalar @vals > 1) {
			foreach my $v (@vals) {
				$name = $v->get_name();
				$data = $v->get_data();
				::rptMsg(sprintf "%-20s  %d",$name,$data);
			}
			::rptMsg("");
			::rptMsg("Analysis Tip: If Remote value is set to 1, system may be WSH Remoting target");
		}
		else {
			::rptMsg($key_path." has no values.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1;