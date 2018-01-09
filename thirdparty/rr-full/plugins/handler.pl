#-----------------------------------------------------------
# handler.pl
#
# Several pieces of malware will modify the HKCR\Network\SharingHandler key
#  default value, pointing it to something other than ntshrui.dll
#
#
# References:
#  http://www.trendmicro.com/vinfo/us/threat-encyclopedia/malware/worm_cosmu.elg
#
# Change history:
#  20150826 - created
#
# copyright 2015 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package handler;
use strict;

my %config = (hive          => "Software",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              category      => "malware",
              version       => 20150826);

sub getConfig{return %config}
sub getShortDescr {
	return "Checks HKCR/Network/SharingHandler (default) value";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching handler v.".$VERSION);
	::rptMsg("handler v.".$VERSION); # banner
  ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key_path = "Classes\\Network\\SharingHandler";
	
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		::rptMsg("(Default) value = ".$key->get_value("")->get_data());
			
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1;