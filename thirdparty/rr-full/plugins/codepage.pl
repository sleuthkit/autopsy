#-----------------------------------------------------------
# codepage.pl
#
# 
#
# References:
#  <included inline>
#
# Change history:
#  20200904 - MITRE updates
#  20200519 - created
#
# copyright 2020 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package codepage;
use strict;

my %config = (hive          => "system",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "",
              category      => "config",
              version       => 20200904);

sub getConfig{return %config}
sub getShortDescr {
	return "Checks codepage value";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching codepage v.".$VERSION);
	::rptMsg("codepage v.".$VERSION); # banner
  ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key_path;
	my $key;

	my $ccs;
	my $current;
	if ($key = $root_key->get_subkey("Select")) {
		$current = $key->get_value("Current")->get_data();
		$ccs = "ControlSet00".$current;
	}

	$key_path = $ccs."\\Control\\Nls\\CodePage";
	eval {
		if ($key = $root_key->get_subkey($key_path)){
			my $acp = $key->get_value("ACP")->get_data();
			::rptMsg("CodePage key LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
			::rptMsg("  Code page value = ".$acp);
			::rptMsg("");
			::rptMsg("Code page description: https://en.wikipedia.org/wiki/Code_page");
		}
	};
	::rptMsg("Control\\Nls\\CodePage\\ACP value not found.") if ($@);
}
1;