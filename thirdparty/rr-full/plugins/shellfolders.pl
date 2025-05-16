#-----------------------------------------------------------
# shellfolders.pl
# A threat actor can maintain persistence by modifying the StartUp folder location,
# and using that new location for persistence 
#
# Change history
#  20201005 - MITRE update
#  20200515 - updated date output format
#  20190902 - removed alert() function
#  20131028 - updated to include User Shell Folders entry
#  20131025 - created
#
# References
#   http://www.fireeye.com/blog/technical/malware-research/2013/10/evasive-tactics-terminator-rat.html
#   http://www.symantec.com/connect/articles/most-common-registry-key-check-while-dealing-virus-issue
#   https://attack.mitre.org/techniques/T1547/001/
#
# copyright 2020 QAR, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package shellfolders;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1547\.001",
              category      => "persistence",
			  output		=> "report",
              version       => 20201005);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets user's shell folders values";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching shellfolders v.".$VERSION);
	::rptMsg("shellfolders v.".$VERSION); 
    ::rptMsg(getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = 'Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Shell Folders';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".::format8601Date($key->get_timestamp())."Z");
		
		eval {
			my $start = $key->get_value("Startup")->get_data();
			::rptMsg("StartUp folder : ".$start);
			::rptMsg("");
			::rptMsg("Analysis Tip: A threat actor could modify the location of the user's StartUp folder.");
		};
	}
	else {
		::rptMsg($key_path." not found.");
	}
	
# added 20131028	
	::rptMsg("");
	$key_path = 'Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\User Shell Folders';
	if ($key = $root_key->get_subkey($key_path)) {
	::rptMsg($key_path);
	::rptMsg("LastWrite Time ".::format8601Date($key->get_timestamp())."Z");
		
		eval {
			my $start = $key->get_value("Startup")->get_data();
			::rptMsg("StartUp folder : ".$start);
			::rptMsg("");
			::rptMsg("Analysis Tip: A threat actor could modify the location of the user's StartUp folder.");
		};
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

1;