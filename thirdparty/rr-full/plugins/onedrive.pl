#-----------------------------------------------------------
# onedrive.pl
# 
#
# Change history
#   20200922 - MITRE update
#   20200515 - updated date output format
#	  20190823 - created
#
# References
#   https://attack.mitre.org/techniques/T1567/002/
# 
# copyright 2020 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package onedrive;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1567\.002",
              category      => "user activity",
			  output		=> "report",
              version       => 20200922);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets contents of user's OneDrive key";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching onedrive v.".$VERSION);
	::rptMsg("onedrive v.".$VERSION); 
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); 
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = "Software\\Microsoft\\OneDrive";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		
		eval {
			::rptMsg("UserCID                   : ".$key->get_subkey("Accounts\\Personal")->get_value("UserCID")->get_data());
		};
		
		eval {
			::rptMsg("UserFolder                : ".$key->get_subkey("Accounts\\Personal")->get_value("UserFolder")->get_data());
		};
		
		eval {
			my $t = $key->get_subkey("Accounts\\Personal")->get_value("ClientFirstSignInTimestamp")->get_data();
			my $s = unpack("Vx4",$t);
			::rptMsg("ClientFirstSignInTimestamp: ".::format8601Date($s)."Z");
		};
		
		eval {
			my $t = $key->get_subkey("Accounts\\Personal")->get_value("NextOneRmUpdateTime")->get_data();
			my $s = unpack("Vx4",$t);
			::rptMsg("NextOneRmUpdateTime       : ".::format8601Date($s)."Z");
		};
		
		eval {
			my $t = $key->get_subkey("Accounts\\Personal")->get_value("NextMigrationScan")->get_data();
			my $s = unpack("Vx4",$t);
			::rptMsg("NextMigrationScan         : ".::format8601Date($s)."Z");
		};
		
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

1;