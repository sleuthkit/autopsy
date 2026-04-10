#-----------------------------------------------------------
# trailersupport.pl
#
# History:
#  20220111 - created
#
# References:
#  https://msrc.microsoft.com/update-guide/vulnerability/CVE-2022-21907
# 
# 
# copyright 2022 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package trailersupport;
use strict;

my %config = (hive          => "System",
			  category      => "config",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "",  
			  output		=> "report",
              version       => 20220111);

sub getConfig{return %config}
sub getShortDescr {
	return "Check EnableTrailerSupport value (CVE-2022-21907)";	
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
	::logMsg("Launching trailersupport v.".$VERSION);
	::rptMsg("trailersupport v.".$VERSION); 
  ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); 
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $ccs = ::getCCS($root_key);
	my $key_path = $ccs.'\\Services\\HTTP\\Parameters';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		
		eval {
			my $cmd = $key->get_value("EnableTrailerSupport")->get_data();	
			::rptMsg($key_path."\\EnableTrailerSupport value = ".$cmd);
			::rptMsg("");
			::rptMsg("1 - Enabled (system vulnerable)");
		};
		::rptMsg("EnableTrailerSupport value not found\.") if ($@);
	}
	else {
		::rptMsg($key_path." not found.");
	}
	::rptMsg("");
	::rptMsg("Analysis Tip: MS's patch for CVE-2022-21907 indicates that the vulnerable condition is not enabled by");
	::rptMsg("default\. Setting the EnableTrailerSupport value to \"1\" enables the vulnerable condition.");
}


1;