#-----------------------------------------------------------------------------------------
# ddo.pl
# Registry entries created by devices that support device stage
#
# History
#  20200904 - MITRE updates
#  20200525 - updated date output format
#  20140414 - created
#
# 
# Reference: 
#  http://nicoleibrahim.com/part-4-usb-device-research-usb-first-insert-results/
#
# Original Author: Jasmine Chua, babymagic06@gmail.com
# copyright 2020 QAR, LLC
# Updating author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------------------------------------
package ddo;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              category      => "devices",
              MITRE         => "",
			  output        => "report",
              version       => 20200904);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets user's DeviceDisplayObjects key contents";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching DDO v.".$VERSION);
	::rptMsg("DDO v.".$VERSION); 
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); 
	
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;
	
	my $key_path = 'Software\\Microsoft\\Windows NT\\CurrentVersion\\DeviceDisplayObjects';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("DeviceDisplayObjects");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time: ".::format8601Date($key->get_timestamp())."Z\n");
		my @vals;
		eval {
			@vals = $key->get_list_of_values();
		};
		unless ($@) {
			foreach my $v (@vals) {
				::rptMsg("Value Name: ".$v->get_name(). "\n");
				::rptMsg("You can match the DDO values with the ContainerID in ENUM\\USB of SYSTEM hive.");
			}
		}		
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
