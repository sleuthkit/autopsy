#-----------------------------------------------------------------------------------------
# knowndev.pl
#
# History
#  20190714 - updated
#  20140414 - created
#
# Registry entries created by devices that support device stage 
# Reference: http://nicoleibrahim.com/part-4-usb-device-research-usb-first-insert-results/
#
# Author: Jasmine Chua, babymagic06@gmail.com
# updates: QAR, LLC (H. Carvey, keydet89@yahoo.com)
#-----------------------------------------------------------------------------------------
package knowndev;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20190714);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets user's KnownDevices key contents";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching knowndev v.".$VERSION);
	::rptMsg("knowndev v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;
	
	my $key_path = 'Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\AutoplayHandlers\\KnownDevices';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("KnownDevices");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)\n");
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar @subkeys > 0) {
			foreach my $s (@subkeys) {
				my $name = $s->get_name();
				my $lw   = gmtime($s->get_timestamp());
				::rptMsg($name."  ".$lw." Z");
				
				eval {
					my $label = $s->get_value("Label")->get_data();
					::rptMsg("Label: ".$label);
				};
				::rptMsg("");
			}
		}
		else {
			::rptMsg($key_path." has no subkeys.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

