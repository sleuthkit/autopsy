#-----------------------------------------------------------
# mzthunderbird.pl
# 	Gets Thunderbird profile data
#
# Change history
#   20180712 - created
#
# References
# 	https://www.thunderbird.net/en-US/ 
# 
# Author: M. Jones, mictjon@gmail.com
#-----------------------------------------------------------
package mzthunderbird;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20180712);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets Thunderbird profile data";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching mzthunderbird v.".$VERSION);
	::rptMsg("mzthunderbird v.".$VERSION); # banner
    ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = "Software\\Microsoft\\Windows\\CurrentVersion\\UnreadMail";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("Thunderbird Email Addresses");
		::rptMsg($key_path);
		
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar(@subkeys) > 0) {
			foreach my $s (@subkeys) { 
				::rptMsg($s->get_name()." [".gmtime($s->get_timestamp())." (UTC)]");
				my ($app,$msgct,$ts);
						
				eval {
					$app = $s->get_value("Application")->get_data();
					::rptMsg("  Application: ".$app);
				};
					
				eval {
					$msgct = $s->get_value("MessageCount")->get_data();
					::rptMsg("  MessageCount: ".$msgct);
				};
						
				eval {
					my ($t0,$t1) = unpack("VV",$s->get_value("TimeStamp")->get_data());
					my $t = ::getTime($t0,$t1);
					::rptMsg("  TimeStamp: ".gmtime($t));
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

1;