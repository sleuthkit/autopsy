#-----------------------------------------------------------
# disablemru.pl 
#
# Change history
#  20180807 - created
#
# References
#  *Provided in the code
# 
# copyright 2018 H. Carvey
#-----------------------------------------------------------
package disablemru;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20180807);

sub getConfig{return %config}
sub getShortDescr {
	return "Checks settings disabling user's MRUs";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching disablemru v.".$VERSION);
	::rptMsg("disablemru v.".$VERSION); # banner
  ::rptMsg("- ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

# Windows 10 JumpLists
# https://winaero.com/blog/disable-jump-lists-windows-10/
	my $key_path = 'Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Advanced';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		eval {
			my $start = $key->get_value("Start_TrackDocs")->get_data();
			
		};
		
	}
	else {
		::rptMsg($key_path." not found.");
	}
	
# https://answers.microsoft.com/en-us/windows/forum/windows_xp-security/how-do-i-disable-most-recent-used-list-in-run/dab29225-4222-4412-8bc3-0516cee65a78	
	$key_path = 'Software\\Microsoft\\Windows\\CurrentVersion\\Policies\\Comdlg32';
	if ($key = $root_key->get_subkey($key_path)) {
		eval {
			my $file = $key->get_value("NoFileMRU")->get_data();
			if ($file == 1) {
				::rptMsg("NoFileMRU = 1; Recording for Comdlg32 disabled");
			}
		};
	}
	else {
		::rptMsg($key_path." not found.");
	}

# http://systemmanager.ru/win2k_regestry.en/92853.htm	
	$key_path = 'Software\\Microsoft\\Windows\\CurrentVersion\\Policies\\Explorer';
	if ($key = $root_key->get_subkey($key_path)) {
		my $mru;
		eval {
			$mru = $key->get_value("NoRecentDocsMenu")->get_data();
			if ($mru == 1) {
				::rptMsg("NoRecentDocsMenu = 1; No Documents menu in Start menu");
			}
		};
		
		eval {
			$mru = $key->get_value("ClearRecentDocsOnExit")->get_data();
			if ($mru == 1) {
				::rptMsg("ClearRecentDocsOnExit = 1; RecentDocs cleared on exit");
			}
		};
		
		eval {
			$mru = $key->get_value("NoRecentDocsHistory")->get_data();
			if ($mru == 1) {
				::rptMsg("NoRecentDocsHistory = 1; No RecentDocs history");
			}
		};
		
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

1;