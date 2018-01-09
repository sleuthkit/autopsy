#-----------------------------------------------------------
# cached_tln.pl
# Plugin to get cached shell extensions list from the 
#   NTUSER.DAT hive
#
# History:
#   20150608 - created
#
# References:
#   http://herrcore.blogspot.com.tr/2015/06/malware-persistence-with.html
#   http://www.nobunkum.ru/analytics/en-com-hijacking
#
#
# copyright 2015 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package cached_tln;
use strict;

my %config = (hive          => "NTUSER.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20150608);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets cached Shell Extensions from NTUSER.DAT hive (TLN)";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching cached_tln v.".$VERSION);
	::rptMsg("cached_tln v.".$VERSION); # banner
  ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key_path = "Software\\Microsoft\\Windows\\CurrentVersion\\Shell Extensions\\Cached";;
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
#		::rptMsg($key_path);
#		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
#		::rptMsg("");

		my @vals = $key->get_list_of_values();
		if (scalar (@vals) > 0) {
			foreach my $v (@vals) {
				my ($clsid1, $clsid2, $mask) = split(/\s/,$v->get_name(),3);
				my @t = unpack("VV",substr($v->get_data(),8,8));
				my $tm = ::getTime($t[0],$t[1]);
				::rptMsg($tm."|REG|||Cached Shell Ext First Load: ".$clsid1);
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
