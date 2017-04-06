#-----------------------------------------------------------
# bitbucket_user
# Get HKLM\..\BitBucket keys\values (if any)
# 
# Change history
#
# References
#
# NOTE: In limited testing, the volume letter subkeys beneath the
#       BitBucket key appear to be volatile.
#
# copyright 2009 H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package bitbucket_user;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20091020);

sub getConfig{return %config}

sub getShortDescr {
	return "TEST - Get user BitBucket values";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching bitbucket_user v.".$VERSION);
	::rptMsg("bitbucket_user v.".$VERSION); # banner
    ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\BitBucket";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar(@subkeys) > 0) {
			foreach my $s (@subkeys) {
				::rptMsg($key_path."\\".$s->get_name());
				::rptMsg("LastWrite Time = ".gmtime($s->get_timestamp())." (UTC)");
				eval {
					my $purge = $s->get_value("NeedToPurge")->get_data();
					::rptMsg("  NeedToPurge = ".$purge);
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