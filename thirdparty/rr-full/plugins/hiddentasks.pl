#-----------------------------------------------------------
# hiddentasks.pl
#
# Change history
#   20220413 - updated code for clarity
#   20220412 - created
#
# Refs:
#   https://www.microsoft.com/security/blog/2022/04/12/tarrask-malware-uses-scheduled-tasks-for-defense-evasion/
#
#
# Copyright (c) 2022 QAR,LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package hiddentasks;
use strict;

my %config = (hive          => "software",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              MITRE         => "T1070", #indicator removal from host
              category      => "defense evasion",
              version       => 20220413);

my $VERSION = getVersion();

sub getConfig {return %config}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}
sub getDescr {}
sub getShortDescr {return "Checks TaskCache\\Tree subkeys for evidence of hiding tasks";}
sub getRefs {}

sub pluginmain {
	my $class = shift;
	my $hive = shift;

	::logMsg("Launching hiddentasks v.".$VERSION);
	::rptMsg("hiddentasks v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr());   
    ::rptMsg("MITRE ATT&CK technique ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");  
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key;
	my $key_path = 'Microsoft\\Windows NT\\CurrentVersion\\Schedule\\TaskCache\\Tree';
	if ($key = $root_key->get_subkey($key_path)) {
		traverse($key);
	}
	else {
		::rptMsg($key_path." not found.");
	}
	::rptMsg("");
	::rptMsg("Analysis Tip: MS DART identified Tarrask malware, part of Hafnium, deleting the \"SD\" value to remain hidden");
	::rptMsg("from view while persisting on systems.");
	::rptMsg("");
	::rptMsg("Ref: https://www.microsoft.com/security/blog/2022/04/12/tarrask-malware-uses-scheduled-tasks-for-defense-evasion/");
}

sub traverse {
	my $key = shift;

	my @subkeys = $key->get_list_of_subkeys();
	if (scalar @subkeys > 0) {
		foreach my $s (@subkeys) {
			my $name = $s->get_name();
#			::rptMsg("Key: ".$name);
			eval {
				my $sd = $s->get_value("SD")->get_data();
			};
			if ($@) {
				::rptMsg("Task ".$name." has no SD value!");
				::rptMsg("LastWrite time: ".::format8601Date($s->get_timestamp())."Z");
				::rptMsg("");
			}
		}
	}
	 
	foreach my $subkey (@subkeys) {
		traverse($subkey);
    }
}



1;