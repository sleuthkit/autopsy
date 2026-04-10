#-----------------------------------------------------------
# restartmanager.pl
# 
#
# Change history
#  20210111 - created
#
# References
#  https://docs.microsoft.com/en-us/windows/win32/rstmgr/about-restart-manager
#
#
# 
# copyright 2021 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package restartmanager;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              category      => "defense evasion",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1562\.001",
			  output		=> "report",
              version       => 20210111);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets RestartManager\\Session0000 values";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching restartmanager v.".$VERSION);
	::rptMsg("restartmanager v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = 'Software\\Microsoft\\RestartManager';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");
		
		my $sess = ();
		if ($sess = $key->get_subkey("Session0000")) {
			::rptMsg($key_path."\\Session0000");
			::rptMsg("LastWrite Time ".::format8601Date($sess->get_timestamp())."Z");
			::rptMsg("");
			my @vals = $sess->get_list_of_values();
			if (scalar @vals > 0) {
				foreach my $v (@vals) {
					::rptMsg(sprintf "%-20s %-50s",$v->get_name(),$v->get_data());
				}
			}
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
	::rptMsg("");
	::rptMsg("Analysis Tip: The Restart Manager determines apps/processes that need to be shutdown & restarted during an ");
	::rptMsg("install process\. Malware has been observed using this technique to keep files open during encryption, or to");
	::rptMsg("to encrypt files that otherwise could not be accessed.");
	::rptMsg("");
	::rptMsg("During an installation, the Session0000 key may be deleted after the FileInUse dialog is closed.");
}

1;