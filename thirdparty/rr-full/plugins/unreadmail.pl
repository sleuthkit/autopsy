#-----------------------------------------------------------
# unreadmail.pl
# 
#
# Change history
#   20100218 - created
#
# References
#    http://support.microsoft.com/kb/304148
#    http://support.microsoft.com/kb/831403
#
# copyright 2010 Quantum Analytics Research, LLC
#-----------------------------------------------------------
package unreadmail;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20100218);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets contents of Unreadmail key";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	my %hist;
	::logMsg("Launching unreadmail v.".$VERSION);
	::rptMsg("unreadmail v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = 'Software\\Microsoft\\Windows\\CurrentVersion\\UnreadMail';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		
		eval {
			my $e = $key->get_value("MessageExpiryDays")->get_data();
			::rptMsg("MessageExpiryDays  : ".$e);
			::rptMsg("");
		};
		
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar @subkeys > 0) {
			::rptMsg("");
			foreach my $s (@subkeys) {
				::rptMsg($s->get_name());
				::rptMsg("LastWrite Time ".gmtime($s->get_timestamp())." (UTC)");
				eval {
					my $m = $s->get_value("MessageCount")->get_data();
					::rptMsg("  MessageCount: ".$m);
				};
				
				eval {
					my $a = $s->get_value("Application")->get_data();
					::rptMsg("  Application : ".$a);
				};
				
				eval {
					my @t = unpack("VV",$s->get_value("TimeStamp")->get_data());
					my $ts = ::getTime($t[0],$t[1]);
					::rptMsg("  TimeStamp   : ".gmtime($ts)." (UTC)");
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