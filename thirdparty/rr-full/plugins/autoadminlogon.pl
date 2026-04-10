#-----------------------------------------------------------
# autoadminlogon.pl
# Get autoadminlogon settings
#
# History
# 20220829 - created
#
# References
#   https://docs.microsoft.com/en-us/troubleshoot/windows-server/user-profiles-and-logon/turn-on-automatic-logon
#
# copyright 2022, QAR LLC
# H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package autoadminlogon;
use strict;

my %config = (hive          => "software",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output 		=> "report",
			  category      => "persistence",
			  MITRE	        => "T1078\.003",
              version       => 20220829);

sub getConfig{return %config}

sub getShortDescr {
	return "Get autoadminlogon settings";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching autoadminlogon v.".$VERSION);
	::rptMsg("Launching autoadminlogon v.".$VERSION);
	::rptMsg("(".$config{hive}.") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key = ();
	my $key_path = "Microsoft\\Windows NT\\CurrentVersion\\WinLogon";
	
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg(" ");
		
		eval {
			my $a = $key->get_value("AutoAdminLogon")->get_data();
			::rptMsg("AutoAdminLogon enabled.") if ($a == 1);
			::rptMsg("AutoAdminLogon disabled.") if ($a == 0);
		};
		::rptMsg("AutoAdminLogon value not found.") if ($@);
		
		eval {
			my $p = $key->get_value("DefaultPassword")->get_data();
			::rptMsg("DefaultPassword: ".$p);
		};
		
	}
	::rptMsg("");
	::rptMsg("Analysis Tip: If the \"AutoAdminLogon\" value exists and is set to \"1\", the system will automatically log into");
	::rptMsg("the admin account, and the password can be found in plain text in the \"DefaultPassword\" value.");
	::rptMsg("");
	::rptMsg("Ref: https://docs.microsoft.com/en-us/troubleshoot/windows-server/user-profiles-and-logon/turn-on-automatic-logon");
}
1;