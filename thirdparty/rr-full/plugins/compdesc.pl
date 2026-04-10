#-----------------------------------------------------------
# compdesc.pl
# Plugin for Registry Ripper,
# ComputerDescriptions key parser
#
# Change history
#  20200904 - MITRE updates
#  20200511 - updated date output format
#  20080324 - created
#
# References
#
# 
# copyright 2020 H. Carvey
#-----------------------------------------------------------
package compdesc;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              category      => "config",
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "",
			  output		=> "report",
              version       => 20200904);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets contents of user's ComputerDescriptions key";
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching compdesc v.".$VERSION);
	::rptMsg("compdesc v.".$VERSION); # banner
    ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner 
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = 'Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\ComputerDescriptions';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("ComputerDescriptions");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".::format8601Date($key->get_timestamp())."Z");
		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			foreach my $v (@vals) {
				::rptMsg("  ".$v->get_name()."   ".$v->get_data());
			}
		}
		else {
			::rptMsg($key_path." has no values.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

1;