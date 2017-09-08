#-----------------------------------------------------------
# startup.pl
# Plugin for Registry Ripper, NTUSER.DAT edition - gets the 
# ACMru values 
#
# Change history
#  20131028 - updated to include User Shell Folders entry
#  20131025 - created
#
# References
#   http://www.fireeye.com/blog/technical/malware-research/2013/10/evasive-tactics-terminator-rat.html
#   http://www.symantec.com/connect/articles/most-common-registry-key-check-while-dealing-virus-issue
# 
# copyright 2013 QAR, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package startup;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20131028);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets user's Startup Folder location";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching startup v.".$VERSION);
	::rptMsg("startup v.".$VERSION); # banner
    ::rptMsg(getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = 'Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Shell Folders';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		
		eval {
			my $start = $key->get_value("Startup")->get_data();
			::rptMsg("StartUp folder : ".$start);
			processPath($start);
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
	::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		
		eval {
			my $start = $key->get_value("Startup")->get_data();
			::rptMsg("StartUp folder : ".$start);
			processPath($start);
		};
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

sub processPath {
	my $path = shift;
	my $lcpath = $path;
	$lcpath =~ tr/[A-Z]/[a-z]/;
	::rptMsg("Alert: Possible incorrect path found") unless ($lcpath =~ m/start menu\\programs\\startup$/);
}

1;