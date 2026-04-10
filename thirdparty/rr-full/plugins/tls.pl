#-----------------------------------------------------------
# tls.pl
#
# History:
#  20210122 - created
#
# References:
#  https://www.aon.com/cyber-solutions/aon_cyber_labs/cyber-labs-blog-see-ya-in-s3/
#  https://docs.microsoft.com/en-us/dotnet/framework/network-programming/tls#configuring-schannel-protocols-in-the-windows-registry
# 
#  https://attack.mitre.org/techniques/T1562/001/
# 
# copyright 2021 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package tls;
use strict;

my %config = (hive          => "System",
			  category      => "defense evasion",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1562\.001",
			  output		=> "report",
              version       => 20210122);

sub getConfig{return %config}
sub getShortDescr {
	return "Check TLS settings";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();
my %files;
my $str = "";

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching tls v.".$VERSION);
	::rptMsg("tls v.".$VERSION); 
  ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n");  
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
# First thing to do is get the ControlSet00x marked current...this is
# going to be used over and over again in plugins that access the system
# file
	my $ccs = ::getCCS($root_key);
	my @versions = ("1.1", "1.2"); 
	foreach my $v (@versions) {
		my $key_path = $ccs."\\Control\\SecurityProviders\\SCHANNEL\\Protocols\\TLS ".$v."\\Client";
		my $key = ();
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg($key_path);
			::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
			eval {
				my $dis = $key->get_value("DisabledByDefault")->get_data();
				::rptMsg("DisabledByDefault value = ".$dis);
			};
		}
		else {
			::rptMsg($key_path." not found.");
		}
	}	
	::rptMsg("");
	::rptMsg("Analysis Tip: Disabling the TLS client settings serves to remove security settings on the client side.");
#	::rptMsg("");
}

1;