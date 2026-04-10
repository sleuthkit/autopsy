#-----------------------------------------------------------
# zerologon.pl
#
# History:
#  20200922 - created
#
# References:
#  https://twitter.com/h0tz3npl0tz/status/1308154057794744325
#  https://www.cynet.com/zerologon/
#  https://blog.zsec.uk/zerologon-attacking-defending/
# 
# copyright 2020 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package zerologon;
use strict;

my %config = (hive          => "System",
			  category      => "config",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "",  
			  output		=> "report",
              version       => 20200922);

sub getConfig{return %config}
sub getShortDescr {
	return "Check Registry setting to protect against ZeroLogon exploit";	
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
	::logMsg("Launching zerologon v.".$VERSION);
	::rptMsg("zerologon v.".$VERSION); # banner
  ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner 
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
# First thing to do is get the ControlSet00x marked current...this is
# going to be used over and over again in plugins that access the system
# file
	my $key;
	my $ccs = ::getCCS($root_key);
		
	my $key_path = $ccs."\\Services\\NetLogon\\Parameters";
		
	if ($key = $root_key->get_subkey($key_path)) {
		
		::rptMsg($key_path);
		::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");
		
		eval {
			my $f = $key->get_value("FullSecureChannelProtection")->get_data();
			::rptMsg("FullSecureChannelProtection = ".$f);
			::rptMsg("");
		};
		if ($@) {
			::rptMsg("FullSecureChannelProtection value not found.");
			::rptMsg("");
		}
		
		::rptMsg("Analysis Tip: The ".$key_path."\\FullSecureChannelProtection value needs to set to ");
		::rptMsg("\"1\" in order to fully protect a patched system (CVE-2020-1472).");
		::rptMsg("");
		::rptMsg("Ref: https://twitter.com/h0tz3npl0tz/status/1308154057794744325");
		::rptMsg("     https://www.cynet.com/zerologon/");
		::rptMsg("     https://blog.zsec.uk/zerologon-attacking-defending/");
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

1;