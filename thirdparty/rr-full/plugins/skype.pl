#-----------------------------------------------------------
# skype.pl
#  
#
# History
#   20100713 - created
#
# References
#
# 
# copyright 2010 Quantum Analytics Research, LLC
#-----------------------------------------------------------
package skype;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20100713);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets data user's Skype key";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching acmru v.".$VERSION);
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = 'Software\\Skype';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		
		my $install;
		eval {
			$install = $key->get_subkey("Installer")->get_value("DonwloadLastModified")->get_data();
			::rptMsg("DonwloadLastModified = ".$install);
		};
		::rptMsg("DonwloadLastModified value not found: ".$@) if ($@);
		
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1;