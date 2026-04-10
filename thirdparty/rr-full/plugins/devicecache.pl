#-----------------------------------------------------------
# devicecache.pl
# 
#
# Change history
#  20221018 - created
#
# References
#   https://www.istrosec.com/blog/windows-10-timeline/
#   https://www.forensicfocus.com/webinars/windows-10-activity-timeline-an-investigators-gold-mine/
#
# 
# copyright 2022 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package devicecache;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              category      => "",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "",
			  output		=> "report",
              version       => 20221018);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets DeviceCache entries";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();
my %types = (1 => "XBox One",
             6 => "Apple iPhone",
			 7 => "Apple iPad",
			 8 => "Android Device",
			 9 => "Windows 10 Desktop",
			 11 => "Windows 10 Phone",
			 12 => "Linux Device",
			 13 => "Windows IoT",
			 14 => "Surface Hub",
			 15 => "Windows Laptop");

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching devicecache v.".$VERSION);
	::rptMsg("devicecache v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); 
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = 'Software\\Microsoft\\Windows\\CurrentVersion\\TaskFlow\\DeviceCache';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("DeviceCache");
		::rptMsg($key_path);
#		::rptMsg("LastWrite Time ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");
		
		my @subkeys = $key->get_list_of_subkeys();
		
		my @vals = ("DeviceName","DeviceMake","DeviceModel");
		
		if (scalar @subkeys > 0) {
			foreach my $s (@subkeys) {
				::rptMsg("Key: ".$s->get_name());
				::rptMsg("LastWrite Time ".::format8601Date($s->get_timestamp())."Z");
				::rptMsg("");
				foreach my $v (@vals) {
					eval {
						my $x = $s->get_value($v)->get_data();
						::rptMsg(sprintf "%-15s %-25s",$v,$x);
					};
				}
				
				eval {
					my $x = $s->get_value("DeviceType")->get_data();
					::rptMsg(sprintf "%-15s %-25s","DeviceType",$types{$x});
				};
				
				::rptMsg("");
			}
			::rptMsg("Analysis Tip: Multiple subkeys beneath the DeviceCache key may indicate that the user loggged into multiple");
			::rptMsg("devices using the same Microsoft ID.");
			::rptMsg("");
			::rptMsg("Ref: https://cellebrite.com/en/exploring-the-windows-activity-timeline-part-2-syncing-across-devices/");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

1;