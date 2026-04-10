#-----------------------------------------------------------
# driverinstall
#
# Change history:
#  20221024 - created
# 
# Ref:
#  https://twitter.com/wdormann/status/1413889342372724740
#
# copyright 2022 QAR,LLC 
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package driverinstall;
use strict;

my %config = (hive          => "software",
			  category      => "",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "",
			  output        => "report",
              version       => 20221024);

sub getConfig{return %config}
sub getShortDescr {
	return "Check driverinstall settings";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::rptMsg("Launching driverinstall v.".$VERSION);
	::rptMsg("driverinstall v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n");  

	my $key_path = ('Policies\\Microsoft\\Windows\\DriverInstall\\Restrictions\\AllowUserDeviceClasses');
	
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");
		
		my @vals = $key->get_list_of_values();
		if (scalar @vals > 0) {
			foreach my $v (@vals) {
				eval {
					my $x = $key->get_value($v)->get_data();
					::rptMsg(sprintf "%-4s %-45s",$v->get_name(),$x);
				};
			}
		}
		else {
			::rptMsg($key_path." has no values.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
	::rptMsg("");
	::rptMsg("Analysis Tip: Values beneath the AllowUserDeviceClasses key allow for users/non-admins to install the devices.");
	::rptMsg("This can present significant risk to the system, and potentially the infrastructure.");
}
1;