#-----------------------------------------------------------
# hello.pl
# Get Active Setup StubPath values
#
# Change history:
#   20210315 - created
#
# References:
#   https://www.thewindowsclub.com/users-must-enter-a-username-and-password-to-use-this-computer-missing
#   https://winaero.com/enable-passwordless-sign-in-for-microsoft-accounts/
#        
# copyright 2021 Quantum Analytics Research, LLC
# Author: H. Carvey, 2013
#-----------------------------------------------------------
package hello;
use strict;

my %config = (hive          => "software",
			  category      => "config",
			  MITRE         => "",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output 		=> "report",
              version       => 20210315);

sub getConfig{return %config}

sub getShortDescr {
	return "Check to see if \"Require Windows Hello Sign-in\" is enabled.";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

my %comp;

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching hello v.".$VERSION);
	::rptMsg("hello v.".$VERSION); 
  ::rptMsg("(".getHive().") ".getShortDescr()); 
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = "Microsoft\\Windows NT\\CurrentVersion\\PasswordLess\\Device";
	
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("");
		::rptMsg("Key path: ".$key_path);
		::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
			
		eval {
			my $a = $key->get_value("DevicePasswordLessBuildVersion")->get_data();
			::rptMsg("DevicePasswordLessBuildVersion value: ".$a);
		};
						
	}
	else {
		::rptMsg("");
		::rptMsg($key_path." not found.");
	}
	::rptMsg("");
	::rptMsg("Analysis Tip: Starting with Win10 Build 18936, you can enable a new Passwordless Sign-in feature, allowing you to");
	::rptMsg("switch MS accounts on Win10 devices to using modern authentication with Windows Hello Face, Fingerprint, or PIN.");
	::rptMsg("This can help investigators understand the authentication mechanisms available on the system.");
	::rptMsg("");
	::rptMsg("0 - Windows Hello sign-in feature disabled");
	::rptMsg("    The \"User must enter username and password\" option should be visible in netplwiz.");
	::rptMsg("2 - Passwordless sign-in feature enabled");
	::rptMsg("Ref: https://www.thewindowsclub.com/users-must-enter-a-username-and-password-to-use-this-computer-missing");
}
1;