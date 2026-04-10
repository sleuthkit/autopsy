#-----------------------------------------------------------
# shadow.pl
# The "Shadow" value allows for eavesdropping on RDP connections by admins;
# this could be used for insider threat issues.
#
# Change history:
#   20210425 - added "bitsamin.in" reference
#   20210217 - created
#
# References:
#   https://admx.help/?Category=Windows_10_2016&Policy=Microsoft.Policies.TerminalServer::TS_RemoteControl_2
#   http://woshub.com/rdp-session-shadow-to-windows-10-user/
#   https://bitsadm.in/blog/spying-on-users-using-rdp-shadowing   
#   https://twitter.com/SagieSec/status/1469001618863624194 #added 20220114
#        
# copyright 2021 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package shadow;
use strict;

my %config = (hive          => "software",
			  category      => "defense evasion",
			  MITRE         => "T1112",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              version       => 20210425);

sub getConfig{return %config}

sub getShortDescr {
	return "Shadow value allows for eavesdropping on RDP connections";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching shadow v.".$VERSION);
	::rptMsg("shadow v.".$VERSION); 
	::rptMsg("(".getHive().") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = "Policies\\Microsoft\\Windows NT\\Terminal Services";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("");
		::rptMsg("Key path: ".$key_path);
		::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");
		
		eval {
			my $shadow = $key->get_value("Shadow")->get_data();
			::rptMsg("Shadow value = ".$shadow);
		};
		::rptMsg("Shadow value not found.") if ($@);
		
	}
	else {
			::rptMsg($key_path." not found.");
	}
	::rptMsg("");
	::rptMsg("Analysis Tip: The \"Shadow\" value allows admins to interact with a user's RDP session based on the option selected");
	::rptMsg("0 - No remote control allowed");
	::rptMsg("1 - Full control with user's permission");
	::rptMsg("2 - Full control without user's permission");
	::rptMsg("3 - View session with user's permission");
	::rptMsg("4 - View session without user's permission");
}
1;