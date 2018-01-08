#-----------------------------------------------------------
# remoteaccess.pl
#
# History:
#  20160906 - created
#
# References:
#  https://technet.microsoft.com/en-us/library/ff687746(v=ws.10).aspx
#
# 
# copyright 2016 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package remoteaccess;
use strict;

my %config = (hive          => "System",
							hivemask      => 4,
							output        => "report",
							category      => "Config settings",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 31,  #XP - Win7
              version       => 20160906);

sub getConfig{return %config}
sub getShortDescr {
	return "Get RemoteAccess AccountLockout settings";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();
my %files;
my $str = "";

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching remoteaccess v.".$VERSION);
	::rptMsg("remoteaccess v.".$VERSION); # banner
  ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner 
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
# First thing to do is get the ControlSet00x marked current...this is
# going to be used over and over again in plugins that access the system
# file
	my ($current,$ccs);
	my $key_path = 'Select';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		$current = $key->get_value("Current")->get_data();
		$ccs = "ControlSet00".$current;
		
		$key_path = $ccs."\\services\\RemoteAccess\\Parameters\\AccountLockout";
		
		if ($key = $root_key->get_subkey($key_path)) {
			
			eval {
				my $deny = $key->get_value("MaxDenials")->get_data();
				::rptMsg("MaxDenials : ".$deny);
				::rptMsg("Remote Access Account Lockout Disabled.") if ($deny == 0);
				::rptMsg("");
			};
			
			eval {
				my $res = $key->get_value("ResetTime (mins)")->get_data();
				::rptMsg("ResetTime (mins) : ".$res);
				::rptMsg("Default reset time is 2880 min, or 48 hrs");
				::rptMsg("");
			};
			
			
		}
		else {
			::rptMsg($key_path." not found.");
		}
		
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

1;