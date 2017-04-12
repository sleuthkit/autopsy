#-----------------------------------------------------------
# osversion_tln.pl
# Plugin to check for OSVersion value, which appears to be queried
# by some malware, and used by others; getting a response of "OSVersion
# not found" is a good thing.
#
# Change history
#  20120608 - created
#
# References
#  Search Google for "Software\Microsoft\OSVersion" - you'll get several
#    hits that refer to various malware; 
# 
# copyright 2012 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package osversion_tln;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20120608);

sub getConfig{return %config}
sub getShortDescr {
	return "Checks for OSVersion value (TLN)";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching osversion_tln v.".$VERSION);
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = 'Software\\Microsoft';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
#		::rptMsg("OSVersion");
#		::rptMsg($key_path);
#		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		my $t = $key->get_timestamp();
#		::rptMsg("");
		my $os;
		eval {
			$os = $key->get_value("OSVersion")->get_data();
			
		};
		if ($@) {
			::rptMsg("OSVersion value not found.");
		}
		else {
			::rptMsg($t."|REG|||[Malware] HKCU\\Software\\Microsoft, OSVersion = ".$os);
		}
	}
	else {
#		::rptMsg($key_path." not found.");
	}
}

1;