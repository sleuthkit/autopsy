#-----------------------------------------------------------
# controlpanel.pl
# Vista ControlPanel key seems to contain some interesting info about the
# user's activities...
#
# copyright 2008 H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package controlpanel;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              osmask        => 64,  
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20080428);

sub getConfig{return %config}

sub getShortDescr {
	return "Look for RecentTask* values in ControlPanel key (Vista)";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching controlpanel v.".$VERSION);
	::rptMsg("controlpanel v.".$VERSION); # banner
    ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\ControlPanel";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		::rptMsg("Analysis Tip: The RecentTask* entries appear to only be populated through the");
		::rptMsg("choices in the Control Panel Home view (in Vista).  As each new choice is");
		::rptMsg("selected, the most recent choice is added as RecentTask1, and each ");
		::rptMsg("RecentTask* entry is incremented and pushed down in the stack.");
		::rptMsg("");
		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			foreach my $v (@vals) {
				my $str = sprintf "%-15s %-45s",$v->get_name(),$v->get_data();
				::rptMsg($str);
			}
			::rptMsg("");
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