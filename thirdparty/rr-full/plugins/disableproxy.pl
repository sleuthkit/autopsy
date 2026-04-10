#-----------------------------------------------------------
# disableproxy.pl
# Get disableproxy settings
#
# Change history:
#   20211025 - created
#
# References:
#   https://twitter.com/PythonResponder/status/1451657791970623490
#   
#        
# copyright 2021 Quantum Analytics Research, LLC
# Author: H. Carvey
#-----------------------------------------------------------
package disableproxy;
use strict;

my %config = (hive          => "software",
			  category      => "config",
			  MITRE         => "",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output        => "report",
              version       => 20211025);

sub getConfig{return %config}

sub getShortDescr {
	return "Get disableproxy settings";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching disableproxy v.".$VERSION);
	::rptMsg("disableproxy v.".$VERSION); 
    ::rptMsg("(".getHive().") ".getShortDescr()); 
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my @paths = ("Microsoft\\Windows\\CurrentVersion\\Internet Settings",
	             "Wow6432Node\\Microsoft\\Windows\\CurrentVersion\\Internet Settings");
	
	foreach my $key_path (@paths) {
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg("");
			::rptMsg("Key path: ".$key_path);
			::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
			eval {
				my $a = $key->get_value("DisableProxyAuthenticationSchemes")->get_data();
				::rptMsg("DisableProxyAuthenticationSchemes value     : ".$a);
				::rptMsg("4 - Disable NTLM");
			};
		}
		else {
#			::rptMsg($key_path." not found.");
		}
	}
	::rptMsg("");
	::rptMsg("Analysis Tip: A value of 4 indicates that NTLM is disabled");
#	::rptMsg("");
#	::rptMsg("");
}
1;