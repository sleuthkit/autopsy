#-----------------------------------------------------------
# load.pl
# The load and run values in the Windows NT\CurrentVersion\Windows 
# key are throw-backs to the old win.ini file, and can be/are used 
# by malware.
#
# Change history
#   20100811 - created
#
# References
#   http://support.microsoft.com/kb/103865
#   http://security.fnal.gov/cookbook/WinStartup.html
# 
# copyright 2010 Quantum Analytics Research, LLC
#-----------------------------------------------------------
package load;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20100811);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets load and run values from user hive";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching load v.".$VERSION);
	::rptMsg("load v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner 
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = 'Software\\Microsoft\\Windows NT\\CurrentVersion\\Windows';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("load");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			::rptMsg("");
			my %win;
			foreach my $v (@vals) { 
				$win{$v->get_name()} = $v->get_data();
			}
			
			if (exists $win{"load"}) {
				::rptMsg("load = ".$win{"load"});
			}
			else {
				::rptMsg("load value not found.");
			}
				
			if (exists $win{"run"}) {
				::rptMsg("run = ".$win{"run"});
			}
			else {
				::rptMsg("run value not found.");
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