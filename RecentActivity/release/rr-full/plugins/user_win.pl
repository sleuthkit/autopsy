#-----------------------------------------------------------
# user_win.pl
#
# copyright 2008 H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package user_win;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20080415);

sub getConfig{return %config}

sub getShortDescr {
	return " -- ";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching user_win v.".$VERSION);
	::rptMsg("user_win v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key_path = "Software\\Microsoft\\Windows NT\\CurrentVersion\\Windows";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		
		eval {
			my $load = $key->get_value("load")->get_data();
			::rptMsg("load value = ".$load);
			::rptMsg("*Should be blank; anything listed gets run when the user logs in.");
		};
		
		eval {
			my $run = $key->get_value("run")->get_data();
			::rptMsg("run value = ".$run);
			::rptMsg("*Should be blank; anything listed gets run when the user logs in.");
		};
		
	}
	else {
		::rptMsg($key_path." not found.");
		::logMsg($key_path." not found.");
	}
	
}
1;