#-----------------------------------------------------------
# databasepath.pl
# Get DatabasePath value from System hive
# 
# Change history
#   20201021 - created
#
# References
#   https://support.microsoft.com/en-us/help/172218/microsoft-tcp-ip-host-name-resolution-order
# 
# copyright 2020 QAR, LLC
# H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package databasepath;
use strict;

my %config = (hive          => "System",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              category      => "defense evasion",
              MITRE         => "T1564",
              version       => 20201021);

sub getConfig{return %config}
sub getShortDescr {
	return "Get DataBasePath value from System hive";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching databasepath v.".$VERSION);
	::rptMsg("databasepath v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
# First thing to do is get the ControlSet00x marked current...this is
# going to be used over and over again in plugins that access the system
# file
	my $ccs = ::getCCS($root_key);
	
	my $key_path = $ccs."\\Services\\Tcpip\\Parameters";
	my $key = ();
	if ($key = $root_key->get_subkey($key_path)) {
		my $db = ();
		eval {
			$db = $key->get_value("DataBasePath")->get_data();
			::rptMsg(sprintf "%-20s %-50s","DataBasePath",$db);
			::rptMsg("");
			::rptMsg("Analysis Tip: A threat actor can change the location of the hosts file, and plant a malicious hosts file on");
			::rptMsg("the system, preventing DNS queries from appearing on the network.  This value should point to:");
			::rptMsg("\"%SystemRoot%\\System32\\drivers\\etc\".");
			::rptMsg("");
			::rptMsg("Ref: https://support.microsoft.com/en-us/help/172218/microsoft-tcp-ip-host-name-resolution-order");
		};
		::rptMsg("DataBasePath value not found.") if ($@);
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

1;