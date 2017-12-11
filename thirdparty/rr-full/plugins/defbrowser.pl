#-----------------------------------------------------------
# defbrowser.pl
# Get default browser information - check #1 can apply to HKLM
# as well as to HKCU
#
# Change History: 
#   20091116 - Added Check #1
#   20081105 - created
# 
# copyright 2009 H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package defbrowser;
use strict;

my %config = (hive          => "Software",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20091116);

sub getConfig{return %config}

sub getShortDescr {
	return "Gets default browser setting from HKLM";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching defbrowser v.".$VERSION);
	::rptMsg("defbrowser v.".$VERSION); # banner
    ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = "Clients\\StartMenuInternet";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("Default Browser Check #1");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		my $browser = $key->get_value("")->get_data();
		::rptMsg("Default Browser : ".$browser);
	}
	else {
		::rptMsg($key_path." not found.");
	}

	::rptMsg("");

	$key_path = "Classes\\HTTP\\shell\\open\\command";
	if (my $key = $root_key->get_subkey($key_path)) {
		::rptMsg("Default Browser Check #2");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		my $browser;
		eval {
			$browser = $key->get_value("")->get_data();
		};
		if ($@) {
			::rptMsg("Error locating default browser setting.");
		}
		else {
			::rptMsg("Default Browser = ".$browser);
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1;
