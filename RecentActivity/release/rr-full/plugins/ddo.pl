#-----------------------------------------------------------------------------------------
# ddo.pl
#
# History
#  20140414 - created
#
# Registry entries created by devices that support device stage
# Reference: http://nicoleibrahim.com/part-4-usb-device-research-usb-first-insert-results/
#
# # Author: Jasmine Chua, babymagic06@gmail.com
#-----------------------------------------------------------------------------------------
package ddo;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20140414);

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
	::rptMsg("DDO v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;
	
	my $key_path = 'Software\\Microsoft\\Windows NT\\CurrentVersion\\DeviceDisplayObjects';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("DeviceDisplayObjects");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time: ".gmtime($key->get_timestamp())." (UTC)\n");
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
