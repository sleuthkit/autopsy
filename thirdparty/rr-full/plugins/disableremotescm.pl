#-----------------------------------------------------------
# disableremotescm.pl
# Plugin for Registry Ripper; Access System hive file to get the
# computername
# 
# Change history
#   20200831 - MITRE updates
#   20200513 - created
#
# References
#   https://twitter.com/0gtweet/status/1260213942535757824
#   https://docs.microsoft.com/en-us/windows/win32/services/services-and-rpc-tcp
# 
# copyright 2020 Quantum Analytics Research, LLC
# author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package disableremotescm;
use strict;

my %config = (hive          => "system",
              hasShortDescr => 1,
              category      => "config",
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "",
			  output        => "report",
              version       => 20200831);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets DisableRemoteScmEndpoints value from System hive";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching disableremotescm v.".$VERSION);
	::rptMsg("disableremotescm v.".$VERSION); # banner
  ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
# First thing to do is get the ControlSet00x marked current...this is
# going to be used over and over again in plugins that access the system
# file
	my ($current,$ccs);
	my $key_path = 'Select';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		$current = $key->get_value("Current")->get_data();
		$ccs = "ControlSet00".$current;
		my $cn_path = $ccs."\\Control";
		my $cn;
		if ($cn = $root_key->get_subkey($cn_path)) {
			eval {
				my $dis = $cn->get_value("DisableRemoteScmEndpoints")->get_data();
				::rptMsg("DisableRemoteScmEndpoints    = ".$dis);
			};
			::rptMsg("DisableRemoteScmEndpoints value not found.") if ($@);
		}
		else {
			::rptMsg($cn_path." not found.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
	
}

1;