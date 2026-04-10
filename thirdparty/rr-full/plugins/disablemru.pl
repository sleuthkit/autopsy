#-----------------------------------------------------------
# disablemru.pl 
# Check config settings that could be used to minimize/obviate artifacts
# associated with user activity; while most of the artifacts are likely on 
# a per-user basis, included check of Software hive, just in case
#
# Change history
#  20230710 - updated output
#  20230106 - added check to disable UserAssist
#  20200911 - MITRE updates
#  20190924 - updated to include Software hive
#  20180807 - created
#
# References
#  *Provided in the code
#  https://blog.didierstevens.com/2007/09/25/update-disabling-userassist-logging-for-windows-vista/
#	https://attack.mitre.org/techniques/T1562/001/
# 
# copyright 2023 QAR,LLC
# author: H. Carvey keydet89@yahoo.com
#-----------------------------------------------------------
package disablemru;
use strict;

my %config = (hive          => "NTUSER\.DAT, Software",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              category      => "defense evasion",
              MITRE         => "T1562\.001", 
			  output		=> "report",
              version       => 20230710);

sub getConfig{return %config}
sub getShortDescr {
	return "Checks settings disabling user's MRUs, UserAssist, JumpLists";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching disablemru v.".$VERSION);
	::rptMsg("disablemru v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key;
	my $key_path;
	
	my %guess = ();
	my $hive_guess = "";
	my %guess = ::guessHive($hive);
	foreach my $g (keys %guess) {
		$hive_guess = $g if ($guess{$g} == 1);
	}  
# Set paths
 	my $key_path = ();
 	if ($hive_guess eq "software") {
 		$key_path = "Microsoft\\Windows\\CurrentVersion\\Explorer\\Advanced";
 	}
 	elsif ($hive_guess eq "ntuser") {
 		$key_path = "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Advanced";
 	}
 	else {}
	
# Windows 10 JumpLists
# https://winaero.com/blog/disable-jump-lists-windows-10/
# https://ss64.com/nt/syntax-reghacks.html
	::rptMsg("Query the ".$key_path." key for the Start_TrackDocs value to");
	::rptMsg("determine if JumpLists are disabled, and the Start_TrackProgs and Start_TrackEnabled values to determine ");
	::rptMsg("if UserAssist entries are disabled.");
	::rptMsg("");
	::rptMsg("Ref: https://book.hacktricks.xyz/generic-methodologies-and-resources/basic-forensic-methodology/anti-forensic-techniques");
	if ($key = $root_key->get_subkey($key_path)) {
		eval {
			my $start = $key->get_value("Start_TrackDocs")->get_data();
			::rptMsg($key_path." Start_TrackDocs value = ".$start);
		};
		::rptMsg($key_path." Start_TrackDocs value not found.") if ($@);
		
# https://book.hacktricks.xyz/generic-methodologies-and-resources/basic-forensic-methodology/anti-forensic-techniques	
# https://github.com/githubfoam/forensics-experience/blob/master/README.md	
# https://blog.didierstevens.com/2007/09/08/disabling-userassist-logging-for-windows-vista/
# The following two values together will disable populating UserAssist entries
		eval {
			my $s = $key->get_value("Start_TrackProgs")->get_data();
			::rptMsg($key_path." Start_TrackProgs value = ".$s);
			::rptMsg("0 - disabled");
			::rptMsg("1 - enabled");
		};
		::rptMsg($key_path." Start_TrackProgs value not found.") if ($@);
		
		eval {
			my $s = $key->get_value("Start_TrackEnabled")->get_data();
			::rptMsg($key_path." Start_TrackEnabled value = ".$s);
			::rptMsg("0 - disabled");
			::rptMsg("1 - enabled");
		};
		::rptMsg($key_path." Start_TrackEnabled value not found.") if ($@);
	}
	else {
		::rptMsg($key_path." key not found.");
	}

# Note: For below code, left Software hive check in place on purpose, even though it's probably not necessary
	my $key_path = ();
	if ($hive_guess eq "software") {
 		$key_path = "Microsoft\\Windows\\CurrentVersion\\Policies\\Comdlg32";
 	}
 	elsif ($hive_guess eq "ntuser") {
 		$key_path = "Software\\Microsoft\\Windows\\CurrentVersion\\Policies\\Comdlg32";
 	}
 	else {}
	::rptMsg("");
# https://answers.microsoft.com/en-us/windows/forum/windows_xp-security/how-do-i-disable-most-recent-used-list-in-run/dab29225-4222-4412-8bc3-0516cee65a78	
	::rptMsg("Query NoFileMRU value in".$key_path." key to determine if");
	::rptMsg("maintaining file MRU is disabled.");
	::rptMsg("");
	::rptMsg("Ref: https://admx.help/?Category=Windows_11_2022&Policy=Microsoft.Policies.WindowsExplorer::NoFileMRU");
	if ($key = $root_key->get_subkey($key_path)) {
		eval {
			my $file = $key->get_value("NoFileMRU")->get_data();
			::rptMsg($key_path." NoFileMRU value = ".$file);
			if ($file == 1) {
				::rptMsg("NoFileMRU = 1; Recording for Comdlg32 disabled");
			}
		};
		::rptMsg($key_path." NoFileMRU value not found.") if ($@);
	}
	else {
		::rptMsg($key_path." key not found.");
	}
	::rptMsg("");
	my $key_path = ();
 	if ($hive_guess eq "software") {
 		$key_path = "Microsoft\\Windows\\CurrentVersion\\Policies\\Explorer";
 	}
 	elsif ($hive_guess eq "ntuser") {
 		$key_path = "Software\\Microsoft\\Windows\\CurrentVersion\\Policies\\Explorer";
 	}
 	else {}

# http://systemmanager.ru/win2k_regestry.en/92853.htm	
# https://admx.help/?Category=Windows_10_2016&Policy=Microsoft.Policies.StartMenu::NoRecentDocsMenu	
	::rptMsg("Query NoRecentDocsMenu value in ".$key_path." key to determine if");
	::rptMsg("maintaining recent docs MRU is disabled.");
	::rptMsg("");
	::rptMsg("Ref: https://admx.help/?Category=Windows_10_2016&Policy=Microsoft.Policies.StartMenu::NoRecentDocsMenu");
	::rptMsg("");
	if ($key = $root_key->get_subkey($key_path)) {

		eval {
			my $mru = $key->get_value("NoRecentDocsMenu")->get_data();
			::rptMsg($key_path." NoRecentDocsMenu value = ".$mru);
			if ($mru == 1) {
				::rptMsg("NoRecentDocsMenu = 1; No Documents menu in Start menu");
			}
		};
		::rptMsg($key_path." NoRecentDocsMenu value not found.") if ($@);
	
		eval {
			my $mru = $key->get_value("ClearRecentDocsOnExit")->get_data();
			::rptMsg($key_path." ClearRecentDocsOnExit value = ".$mru);
			if ($mru == 1) {
				::rptMsg("ClearRecentDocsOnExit = 1; RecentDocs cleared on exit");
			}
		};
		::rptMsg($key_path." ClearRecentDocsOnExit value not found.") if ($@);
		
		eval {
			my $mru = $key->get_value("NoRecentDocsHistory")->get_data();
			::rptMsg($key_path." NoRecentDocsHistory value = ".$mru);
			if ($mru == 1) {
				::rptMsg("NoRecentDocsHistory = 1; No RecentDocs history");
			}
		};
		::rptMsg($key_path." NoRecentDocsHistory value not found.") if ($@);
	}
	else {
		::rptMsg($key_path." key not found.");
	}
}

1;