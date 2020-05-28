#-----------------------------------------------------------
# recentapps_tln.pl
#  
# Change history
#  20190513 - updated timestamp issue
#  20171013 - created
#
# References
#  https://twitter.com/EricRZimmerman/status/916422135987474433
# 
# copyright 2017 H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package recentapps_tln;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20190513);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets contents of user's RecentApps key";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching recentapps_tln v.".$VERSION);
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = 'Software\\Microsoft\\Windows\\CurrentVersion\\Search\\RecentApps';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar(@subkeys) > 0) {
			foreach my $s (@subkeys) { 
				my $appid;
				eval {
					my ($t1,$t2) = unpack("VV",$s->get_value("LastAccessedTime")->get_data());
					my $lat = ::getTime($t1,$t2);
					$appid = $s->get_value("AppId")->get_data();
					my $launchcount = $s->get_value("LaunchCount")->get_data();
					::rptMsg($lat."|REG|||".$appid." (".$launchcount.")");
				};
				
				if (my $r = $s->get_subkey("RecentItems")) {
					my @subkeys2 = $s->get_subkey("RecentItems")->get_list_of_subkeys();
					if (scalar(@subkeys2 > 0)) {
						foreach my $r (@subkeys2) {
							eval {
					      my $path = $r->get_value("Path")->get_data();
					      my ($l1,$l2) = unpack("VV",$r->get_value("LastAccessedTime")->get_data());
					      my $l = ::getTime($l1,$l2);
# Update to plugin
# If the LastAccessedTime for a RecentItem entry is 0, get the key LastWrite time instead					      
					      if ($l == 0) {
					      	$l = $r->get_timestamp();
					      }
					      
					      ::rptMsg($l."|REG|||".$appid." RecentItem: ".$path);
				      };
						}
					}
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

1;