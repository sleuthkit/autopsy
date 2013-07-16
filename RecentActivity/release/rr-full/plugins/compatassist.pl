#-----------------------------------------------------------
# compatassist.pl
# Provides indication of applications run; see the Reference listed
# below; note that there are no time stamps associated with this
# information.  Note: Value names that start with "SIGN.MEDIA" indicate
# that the app was run from removable media
#
# Category: Programs launched by user
#
# Change history
#  20120515 - created
#
# References
#  http://msdn.microsoft.com/en-us/library/bb756937.aspx
# 
# copyright 2012 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package compatassist;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20120515);

sub getConfig{return %config}
sub getShortDescr {
	return "Checks user's Compatibility Assistant\\Persisted values";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	my @temps;
	
	::logMsg("Launching compatassist v.".$VERSION);
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = 'Software\\Microsoft\\Windows NT\\CurrentVersion\\AppCompatFlags\\Compatibility Assistant\\Persisted';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("compatassist");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			foreach my $v (@vals) { 
				my $name = $v->get_name();
				::rptMsg("  ".$name);
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