#-----------------------------------------------------------
# railrunonce.pl
# The Run keys are only processed when the Explorer shell is started. 
# With RemoteApp, Explorer is not the shell but rather the Remote Desktop 
# service provides a shell for the application.
# 
# References:
#  https://blog.truesec.com/2020/07/10/onedrive-with-remote-desktop-services/
#
# Change history:
#  20201020 - created
# 
# copyright 2020 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package railrunonce;
use strict;

my %config = (hive          => "system",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1547\.001",
              category      => "persistence",
			  output		=> "report",
              version       => 20201020);

sub getConfig{return %config}
sub getShortDescr {
	return "Checks RemoteApp shell persistence";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching railrunonce v.".$VERSION);
	::rptMsg("railrunonce v.".$VERSION); 
	::rptMsg("(".getHive().") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key_path;
	my $key;

# System Hive
	my $ccs = ::getCCS($root_key);
	
	$key_path = $ccs."\\Control\\Terminal Server\\RailRunonce";
	if ($key = $root_key->get_subkey($key_path)){
		my @vals = $key->get_list_of_values();
		if (scalar @vals > 0) {
			foreach my $v (@vals) {
				::rptMsg(sprintf "%-25s %-50s",$v->get_name(),$v->get_data());
			}			
			::rptMsg("");
			::rptMsg("Analysis Tip: The RailRunonce key serves the same purpose as the local Run keys, albeit for RemoteApp.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1;