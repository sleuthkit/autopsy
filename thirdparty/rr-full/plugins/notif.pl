#-----------------------------------------------------------
# notif.pl
# Get user's Notification settings
#
# Change history
#   20200926 - created
#
# References
#   https://twitter.com/el_jasoon/status/854302900994101252
#
# copyright 2020 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package notif;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              category      => "config",
              MITRE         => "",
			  output		=> "report",
              version       => 20200926);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets user's Notification settings";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching notif v.".$VERSION);
	::rptMsg("notif v.".$VERSION); 
  ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); 
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;
	
	my $path = "Software\\Microsoft\\Windows\\CurrentVersion\\Notifications\\Settings";
	if (my $key = $root_key->get_subkey($path)) {
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar @subkeys > 0) {
			foreach my $s (@subkeys) {
				::rptMsg($s->get_name());
				
				eval {
					my $e = $s->get_value("Enabled")->get_data();
					::rptMsg("Enabled : ".$e);
				};
				
				eval {
					my $e = $s->get_value("LastNotificationAddedTime")->get_data();
					my ($t0,$t1) = unpack("VV",$e);
					::rptMsg("LastNotificationAddedTime : ".::format8601Date(::getTime($t0,$t1)."Z"));
				};
				
				::rptMsg("");
			}
		}
	}
}

1;