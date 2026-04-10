#-----------------------------------------------------------
# regback.pl
#
# History:
#  20201130 - created
#
# References:
#   https://www.windowslatest.com/2019/07/01/enable-automatic-registry-backup-in-windows-10/
#   https://docs.microsoft.com/en-us/troubleshoot/windows-client/deployment/system-registry-no-backed-up-regback-folder
# 
# 
# copyright 2020 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package regback;
use strict;

my %config = (hive          => "system",
			  category      => "config",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "",
			  output		=> "report",
              version       => 20201130);

sub getConfig{return %config}
sub getShortDescr {
	return "Get EnablePeriodicBackup value";	
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
	::logMsg("Launching regback v.".$VERSION);
	::rptMsg("regback v.".$VERSION); # banner
  ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner 
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
# First thing to do is get the ControlSet00x marked current...this is
# going to be used over and over again in plugins that access the system
# file
	my $ccs = ::getCCS($root_key);
	my $key_path = $ccs."\\Control\\Session Manager\\Configuration Manager";
	my $key = ();
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
		
		eval {
			my $last = $key->get_value("EnablePeriodicBackup")->get_data();
			::rptMsg("EnablePeriodicBackup value : ".$last);
		};
		::rptMsg("EnablePeriodicBackup value not found.") if ($@);
		
	}
	else {
		::rptMsg($key_path." not found.");
	}
	::rptMsg("");
	::rptMsg("Analysis Tip: As of Win10 1803, copies of Reg hives were no longer maintained in the RegBack folder.");
	::rptMsg("  Adding and setting this value to \"1\" re-enables that.");
}

1;