#-----------------------------------------------------------
# vnchooksapplicationprefs.pl
#   read application preference keys for apps launched in VNC session.
#   Beta version.
#
# Change history
#   20110208 [sme] % created
#   20110830 [fpi] + banner, no change to the version number
#
# References
#
# Copyright 2011 SecurityMetrics, Inc.
#-----------------------------------------------------------
package vnchooksapplicationprefs;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20110208);
              
sub getConfig{return %config}
sub getShortDescr {
	return "Get VNCHooks Application Prefs list";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching vnchookapplicationprefs v.".$VERSION);
    ::rptMsg("vnchookapplicationprefs v.".$VERSION); # 20110830 [fpi] + banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # 20110830 [fpi] + banner

	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key_path = "Software\\ORL\\VNCHooks\\Application_Prefs";
	my $app_pref;
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("VNCHooks\\Application_Prefs");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");

		my @apps = $key->get_list_of_subkeys();
		if (scalar(@apps) > 0) {
			foreach my $a (@apps) {
				::rptMsg($a->get_name());
				::rptMsg("    ".gmtime($a->get_timestamp())." Z");
			}
		}
		else {
			::rptMsg($key_path." has no values.");
			::logMsg($key_path." has no values.");	
		}
	}
	else {
		::rptMsg($key_path." not found.");
		::logMsg($key_path." not found.");
	}
}
1;