#-----------------------------------------------------------
# wab_tln.pl
#   
# Get WAB DLLPath value
#
# Change history
#   20200916 - MITRE updates
#   20191122 - created
#
# References
#   https://lolbas-project.github.io/lolbas/Binaries/Wab/
#
# Copyright 2019-2020 QAR, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package wab_tln;
use strict;

my %config = (hive          => "Software",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1546",
              category      => "persistence",
			  output		=> "tln",
              version       => 20200916);

my $VERSION = getVersion();

sub getConfig {return %config}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}
sub getDescr {}
sub getShortDescr {
	return "Get WAB DLLPath settings";
}
sub getRefs {}

sub pluginmain {
	my $class = shift;
	my $hive = shift;

#	::logMsg("Launching wab v.".$VERSION);
#  ::rptMsg("wab v.".$VERSION);
#  ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n");   
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key;
	my $key_path = "Microsoft\\WAB\\DLLPath";
	
	if ($key = $root_key->get_subkey($key_path)) {
#		::rptMsg($key_path);
#		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
#		::rptMsg("");
		
		my $lw = $key->get_timestamp();
		
		my $def = "";
		eval {
			$def = $key->get_value("")->get_data();
			::rptMsg($lw."|REG|||HKLM\\SOFTWARE\\".$key_path." (Default) value = ".$def);
		};
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

1;
