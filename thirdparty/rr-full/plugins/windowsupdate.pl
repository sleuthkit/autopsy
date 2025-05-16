#-----------------------------------------------------------
# windowsupdate
#
# Change history:
#  20221024 - created
# 
# Ref:
#  https://admx.help/?Category=Windows_10_2016&Policy=Microsoft.Policies.WindowsUpdate::DoNotConnectToWindowsUpdateInternetLocations
#  https://gist.github.com/powershellshocked/2aa2cceb102e84d4d328e0412202c228
#
# copyright 2022 QAR,LLC 
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package windowsupdate;
use strict;

my %config = (hive          => "software",
			  category      => "defense evasion",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1562\.001",
			  output		=> "report",
              version       => 20221024);

sub getConfig{return %config}
sub getShortDescr {
	return "Check settings that may disable Windows Updates";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::rptMsg("Launching windowsupdate v.".$VERSION);
	::rptMsg("windowsupdate v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr());  
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");

	my $key_path = ('Policies\\Microsoft\\Windows\\WindowsUpdate');
	
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");

# https://gist.github.com/powershellshocked/2aa2cceb102e84d4d328e0412202c228		
		eval {
			my $x = $key->get_value("ElevateNonAdmins")->get_data();
			::rptMsg("ElevateNonAdmins value: ".$x);
			::rptMsg("1 - Users in the Users security group are allowed to approve/disapprove updates");
			::rptMsg("0 - Only users in the Administrators group can approve/disapprove updates");
			::rptMsg("");
			::rptMsg("Analysis Tip: A setting of \"0\" may inhibit Windows Updates.");
#			::rptMsg("");
		};
		::rptMsg("ElevateNonAdmins value not found.") if ($@);
		
# https://admx.help/?Category=Windows_10_2016&Policy=Microsoft.Policies.WindowsUpdate::DoNotConnectToWindowsUpdateInternetLocations		
		eval {
			my $x = $key->get_value("DoNotConnectToWindowsUpdateInternetLocations")->get_data();
			::rptMsg("DoNotConnectToWindowsUpdateInternetLocations value: ".$x);
			::rptMsg("1 - Enabled");
			::rptMsg("0 - Disabled");
			::rptMsg("");
			::rptMsg("Analysis Tip: Even if Windows systems are configured to retrieve updates from an internal server, it may ");
			::rptMsg("periodically contact the public services to enable future connections. Enabling the policy (setting to 1)");
			::rptMsg("will disable the functionality, and may cause connections to other public services (i.e., Windows Store) to");
			::rptMsg("stop working, as well.");
		
		};
		::rptMsg("DoNotConnectToWindowsUpdateInternetLocations value not found.") if ($@);
		
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1;