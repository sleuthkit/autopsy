#-----------------------------------------------------------
# svchost
# Plugin to get data from Security Center keys
#
# Change History:
#   20100322 - created
#
# References:
#   http://support.microsoft.com/kb/314056
#
# copyright 2010 Quantum Analytics Research, LLC
#-----------------------------------------------------------
package svchost;
use strict;

my %config = (hive          => "Software",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20100322);

sub getConfig{return %config}
sub getShortDescr {
	return "Get entries from SvcHost key";	
}
sub getDescr{}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	my $infected = 0;
	::logMsg("Launching svchost v.".$VERSION);
	 ::rptMsg("svchost v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key_path = 'Microsoft\Windows NT\CurrentVersion\SvcHost';
	my $key;
	::rptMsg("svchost");
	::rptMsg("");
	
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			foreach my $v (@vals) {
				my @data = $v->get_data();
				my $d;
				if (scalar(@data) > 1) {
					$d = join(',',@data);
				}
				else {
					$d = $data[0];
				}
				my $str = sprintf "%-15s %-55s",$v->get_name(),$d;
				::rptMsg($str);
			}
		}
		else {
			::rptMsg($key_path." has no values.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
		::rptMsg("");
	}
}
1;