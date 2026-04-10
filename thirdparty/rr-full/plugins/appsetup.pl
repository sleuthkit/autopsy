#-----------------------------------------------------------
# appsetup
# The WindowsUpdate\Test key reportedly provides persistence, as it is checked
# via Windows Update
#
#
# Change history:
#  20200909 - created
# 
# Ref:
#  https://support.microsoft.com/en-us/help/195461/how-to-set-up-a-logon-script-only-for-terminal-server-users
#
#  https://attack.mitre.org/techniques/T1546
#
# copyright 2020 QAR,LLC 
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package appsetup;
use strict;

my %config = (hive          => "Software",
			  category      => "persistence",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output        => "report",
              MITRE         => "T1546",
              version       => 20200909);

sub getConfig{return %config}
sub getShortDescr {
	return "Get autolaunch entries for when user connects to Terminal Server";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::rptMsg("Launching appsetup v.".$VERSION);
	::rptMsg("appsetup v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr());  
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
    ::rptMsg("");
	my $key_path = ('Microsoft\\Windows NT\\CurrentVersion\\WinLogon');

	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time: ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");
		eval {
			my $app = $key->get_value("AppSetup")->get_data();
			::rptMsg("AppSetup value = ".$app);
			::rptMsg("");
			::rptMsg("Analysis Tip: The commands listed will be launched when the user connects to a Terminal Server.");
			::rptMsg("The entries will be found in the system32 folder.");
		};
		::rptMsg("AppSetup value not found.") if ($@);
		
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1;