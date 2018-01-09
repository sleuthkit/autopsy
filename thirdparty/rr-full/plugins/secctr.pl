#-----------------------------------------------------------
# secctr
# Plugin to get data from Security Center keys
#
# Change History:
#   20100310 - created
#
# References:
#   
#
# copyright 2010 Quantum Analytics Research, LLC
#-----------------------------------------------------------
package secctr;
use strict;

my %config = (hive          => "Software",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20100310);

sub getConfig{return %config}
sub getShortDescr {
	return "Get data from Security Center key";	
}
sub getDescr{}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	my $infected = 0;
	::logMsg("Launching secctr v.".$VERSION);
	::rptMsg("secctr v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key_path = 'Microsoft\Security Center';
	my $key;
	::rptMsg("secctr");
	::rptMsg("");
	
	if ($key = $root_key->get_subkey($key_path)) {
		$infected++;
		::rptMsg("");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			foreach my $v (@vals) {
				my $str = sprintf "%-25s 0x%02x",$v->get_name(),$v->get_data();
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