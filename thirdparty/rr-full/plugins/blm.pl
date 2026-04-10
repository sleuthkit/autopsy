#-----------------------------------------------------------
# blm.pl
# 
#
# Change history:
#   20210705 - created
#
# References:
#   https://twitter.com/R3MRUM/status/1412064892870434818
#   https://twitter.com/Max_Mal_/status/1411261131033923586
#   
# copyright 2021 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package blm;
use strict;

my %config = (hive          => "software,ntuser\.dat",
			  category      => "config",
			  MITRE         => "N/A",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output 		=> "report",
              version       => 20210705);

sub getConfig{return %config}

sub getShortDescr {
	return "Look for BlackLivesMatter key assoc. w/ REvil ransomware";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

my %comp;

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching blm v.".$VERSION);
	::rptMsg("blm v.".$VERSION); 
  ::rptMsg("(".getHive().") ".getShortDescr());
  ::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	my %guess = ();
	my $hive_guess = "";
	my %guess = ::guessHive($hive);
	foreach my $g (keys %guess) {
		$hive_guess = $g if ($guess{$g} == 1);
	}  
# Set paths
 	my @paths = ();
 	if ($hive_guess eq "software") {
 		@paths = ("BlackLivesMatter","Wow6432Node\\BlackLivesMatter");
 	}
 	elsif ($hive_guess eq "ntuser") {
 		@paths = ("Software\\BlackLivesMatter","Software\\Wow6432Node\\BlackLivesMatter");
 	}
 	else {}
	
	my $key;
	foreach my $key_path (@paths) {
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg("");
			::rptMsg("Key path: ".$key_path);
			::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
			::rptMsg("");
			my @vals = get_list_of_values();
			if (scalar @vals > 0) {
				foreach my $v (@vals) {
					::rptMsg(sprintf "%-15s %-25s",$v->get_name(),$v->get_data());
				}
			}
		}	
		else {
			::rptMsg($key_path." key not found.");
		}
	}
	::rptMsg("");
	::rptMsg("Analysis Tip: Per \@REMRUM, REvil v2.04 & v2.07 (Kaseya) stored values beneath this key.");
	::rptMsg("Ref: https://twitter.com/R3MRUM/status/1412064892870434818");
}
1;