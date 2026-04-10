#-----------------------------------------------------------
# lastloggedon
# 
#
# History:
#  20201007 - MITRE update
#  20200517 - updated date output format
#  20180614 - Updated by Michael Godfrey
#  20160531 - created
#
# Ref:
#	 https://attack.mitre.org/techniques/T1078/
#
# copyright 2020 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package lastloggedon;
use strict;

my %config = (hive          => "software",
              MITRE         => "T1078",
              category      => "user activity",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output 		=> "report",
              version       => 20201007);

sub getConfig{return %config}

sub getShortDescr {
	return "Gets LastLoggedOn* values from LogonUI key";	
}
sub getDescr{}
sub getRefs {
	my %refs = ();	
	return %refs;
}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching lastloggedon v.".$VERSION);
	::rptMsg("lastloggedon v.".$VERSION);
  ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my ($key_path, $key);
	
	$key_path = "Microsoft\\Windows\\CurrentVersion\\Authentication\\LogonUI";
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("LastLoggedOn");
		::rptMsg($key_path);
		::rptMsg("LastWrite: ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");
		
		eval {
			my $lastuser = $key->get_value("LastLoggedOnUser")->get_data();
			::rptMsg("LastLoggedOnUser    = ".$lastuser);
		};
		
		eval {
			my $lastsamuser = $key->get_value("LastLoggedOnSAMUser")->get_data();
			::rptMsg("LastLoggedOnSAMUser = ".$lastsamuser);
		};
# Added by Michael Godfrey		
		eval {
			my $lastsamuserSID = $key->get_value("LastLoggedOnUserSID")->get_data();
			::rptMsg("LastLoggedOnUserSID = ".$lastsamuserSID);
		}
	}	
	else {
		::rptMsg($key_path." not found.");
	}
}

1;