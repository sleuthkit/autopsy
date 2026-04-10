#-----------------------------------------------------------
# remoteaccess.pl
#
# History:
#  20200924 - MITRE update
#  20200517 - minor updates
#  20160906 - created
#
# References:
#  https://technet.microsoft.com/en-us/library/ff687746(v=ws.10).aspx
#
# 
# copyright 2020 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package remoteaccess;
use strict;

my %config = (hive          => "System",
			  category      => "config",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "",  
			  output		=> "report",
              version       => 20200924);

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
	my $key;	
	my $ccs = ::getCCS($root_key);
		
	my $key_path = $ccs."\\services\\RemoteAccess\\Parameters\\AccountLockout";
		
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
			
# Check for locked out accounts
		eval {
			my @subkeys = $key->get_list_of_subkeys();
			if (scalar @subkeys > 0) {
				::rptMsg("Locked out accounts:");
				foreach my $s (@subkeys) {
					::rptMsg($s->get_name()."  LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
				}
			}
		};
		
	}
	else {
		::rptMsg($key_path." not found.");
	}
		
}

1;