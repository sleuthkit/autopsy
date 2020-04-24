#-----------------------------------------------------------
# recentapps.pl
#  
# Change history
#  20171013 - created
#
# References
#  https://twitter.com/EricRZimmerman/status/916422135987474433
# 
# copyright 2017 H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package recentapps;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20171013);

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
	::logMsg("Launching recentapps v.".$VERSION);
	::rptMsg("recentapps v.".$VERSION); # banner
  ::rptMsg("- ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = 'Software\\Microsoft\\Windows\\CurrentVersion\\Search\\RecentApps';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar(@subkeys) > 0) {
			foreach my $s (@subkeys) { 
				eval {
					::rptMsg("AppId           : ".$s->get_value("AppId")->get_data());
					my ($t1,$t2) = unpack("VV",$s->get_value("LastAccessedTime")->get_data());
					my $lat = ::getTime($t1,$t2);
					::rptMsg("LastAccessedTime: ".gmtime($lat)." UTC");
					::rptMsg("LaunchCount     : ".$s->get_value("LaunchCount")->get_data());
				};
				
				if (my $r = $s->get_subkey("RecentItems")) {
					::rptMsg("::RecentItems::");
					my @subkeys2 = $s->get_subkey("RecentItems")->get_list_of_subkeys();
					if (scalar(@subkeys2 > 0)) {
						foreach my $r (@subkeys2) {
							eval {
					      ::rptMsg("  Path           : ".$r->get_value("Path")->get_data());
					      my ($l1,$l2) = unpack("VV",$r->get_value("LastAccessedTime")->get_data());
					      my $l = ::getTime($l1,$l2);
					      ::rptMsg("  LastAccessedTime: ".gmtime($l)." UTC");
					      ::rptMsg("");
				      };
						}
					}
				}
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

1;