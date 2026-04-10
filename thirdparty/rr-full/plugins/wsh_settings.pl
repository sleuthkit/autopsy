#-----------------------------------------------------------
# wsh_settings
#
# Change history:
#  20230201 - updated references, analysis tips
#  20200916 - MITRE updates
#  20200517 - updated date output format
#  20180819 - created
# 
# Ref:
#  http://www.hexacorn.com/blog/2018/08/18/lateral-movement-using-wshcontroller-wshremote-objects-iwshcontroller-and-iwshremote-interfaces/
#  https://www.trustedsec.com/blog/new-attacks-old-tricks-how-onenote-malware-is-evolving
#  https://www.thewindowsclub.com/windows-script-host-access-is-disabled-on-this-machine
#
# copyright 2023 QAR,LLC 
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package wsh_settings;
use strict;

my %config = (hive          => "Software",
			  category      => "config",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1210",
			  output		=> "report",
              version       => 20230201);

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
	::rptMsg("wsh_settings v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n");  
	my $key_path = ('Microsoft\\Windows Script Host\\Settings');
	
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("Key LastWrite: ".::format8601Date($key->get_timestamp())."Z");
		my @vals = $key->get_list_of_values();
		if (scalar @vals > 1) {
			foreach my $v (@vals) {
				$name = $v->get_name();
				$data = $v->get_data();
				::rptMsg(sprintf "%-20s  %d",$name,$data);
			}
			::rptMsg("");
			::rptMsg("Analysis Tip: If Remote value is set to 1, system may be WSH Remoting target.");
			::rptMsg("If Enable value is set to \"1\", WSH is enabled on the system; setting it to \"0\"");
			::rptMsg("to disable WSH can inhibit attacks that use WSH.");
			::rptMsg("");
			::rptMsg("Ref: https://www.trustedsec.com/blog/new-attacks-old-tricks-how-onenote-malware-is-evolving");
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