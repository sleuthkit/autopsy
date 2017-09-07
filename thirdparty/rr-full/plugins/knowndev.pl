#-----------------------------------------------------------------------------------------
# knowndev.pl
#
# History
#  20140414 - created
#
# Registry entries created by devices that support device stage 
# Reference: http://nicoleibrahim.com/part-4-usb-device-research-usb-first-insert-results/
#
# Author: Jasmine Chua, babymagic06@gmail.com
#-----------------------------------------------------------------------------------------
package knowndev;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20140414);

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
				if ($name =~ m/_COMP/) {
					my $m = (split(/#/,$name,3))[1];
						my $device = (split(/&/,$m,3))[0];
						my $model = (split(/&/,$m,3))[1];
						my $label;
						my $icon;
						eval {
							$label = $s->get_value('Label')->get_data();
							$icon = $s->get_value('Icon')->get_data();
						};
						my $time = gmtime($s->get_timestamp());
						::rptMsg("Device: ".$device);
						::rptMsg("Model: ".$model);
						::rptMsg("Label: ".$label) unless ($@);
						::rptMsg("Icon: ".$icon) unless ($@);
						::rptMsg("LastWrite Time: ".$time." (UTC)\n");
				}
				elsif ($name =~ m/_USB/) {
					my $vidpid = (split(/#/,$name,3))[1];
					my $serial = (split(/#/,$name,3))[2];
					my $label;
					my $icon;
					eval {
							$label = $s->get_value('Label')->get_data();
							$icon = $s->get_value('Icon')->get_data();
					};
					my $time = gmtime($s->get_timestamp());
					::rptMsg("VID&PID: ".$vidpid);
					::rptMsg("Serial: ".$serial);
					::rptMsg("Label: ".$label) unless ($@);
					::rptMsg("Icon: ".$icon) unless ($@);
					::rptMsg("LastWrite Time: ".$time." (UTC)\n");
				}
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

