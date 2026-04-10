#-----------------------------------------------------------
# sourcerouting.pl
# Check source routing setting; CVE-2021-24074
#
#
# Change history
#    20210212 - created
#
# References
#    https://meterpreter.org/cve-2021-24074-windows-tcp-ip-remote-code-execution-vulnerability-alert/
#    https://admx.help/?Category=SecurityBaseline&Policy=Microsoft.Policies.MSS::Pol_MSS_DisableIPSourceRouting
# 
# copyright 2021 Quantum Analytics Research, LLC
# author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package sourcerouting;
use strict;

my %config = (hive          => "System",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1203",
              category      => "execution",
			  output		=> "report",
              version       => 20210212);

sub getConfig{return %config}
sub getShortDescr {
	return "Get Source Routing setting";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	my %nics;
	my $ccs;
	::logMsg("Launching sourcerouting v.".$VERSION);
	::rptMsg("sourcerouting v.".$VERSION); 
	::rptMsg("(".getHive().") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
# First thing to do is get the ControlSet00x marked current...this is
# going to be used over and over again in plugins that access the system
# file
	my $current;
	eval {
		$current = ::getCCS($root_key);
	};
	
	my $key_path = $current."\\Services\\Tcpip\\Parameters";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		
		eval {
			my $d = $key->get_value("DisableIPSourceRouting")->get_data();
			::rptMsg("DisableIPSourceRouting value: ".$d);
		};
		::rptMsg("DisableIPSourceRouting value not found") if ($@);
		::rptMsg("");
		::rptMsg("Analysis Tip: Disabling Source Routing (set value to 2) can help protect against CVE-2021-24074");
		::rptMsg("0 - No additional protection, source routed packets are allowed");
		::rptMsg("1 - Medium, source routed packets ignored when IP forwarding is enabled");
		::rptMsg("2 - Highest protection, source routing is completely disabled");
	}	
	else {
		::rptMsg($key_path." not found.");
	}
}

1;