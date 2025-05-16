#-----------------------------------------------------------
# runvirtual_tln.pl
#   
#
# Change history
#   20220427 - updated code
#   20201005 - MITRE update
#   20191211 - created
#
# References
#   https://docs.microsoft.com/en-us/microsoft-desktop-optimization-pack/appv-v5/running-a-locally-installed-application-inside-a-virtual-environment-with-virtualized-applications
#
# Copyright 2020 QAR, LLC 
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package runvirtual_tln;
use strict;

my %config = (hive          => "NTUSER\.DAT, Software",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1610", 
              category      => "execution",
			  output		=> "tln",
              version       => 20220427);

my $VERSION = getVersion();

sub getConfig {return %config}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}
sub getDescr {}
sub getShortDescr {
	return "Gets RunVirtual entries";
}
sub getRefs {}

sub pluginmain {
	my $class = shift;
	my $hive = shift;
#  ::logMsg("Launching runvirtual v.".$VERSION);
#  ::rptMsg("runvirtual v.".$VERSION); 
#  ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); 
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key;
	
	my %guess = ();
	my $hive_guess = "";
	my %guess = ::guessHive($hive);
	foreach my $g (keys %guess) {
		$hive_guess = $g if ($guess{$g} == 1);
	}  
# Set paths
 	my $key_path = ();
 	if ($hive_guess eq "software") {
 		$key_path = ("Microsoft\\AppV\\Client\\RunVirtual");
 	}
 	elsif ($hive_guess eq "ntuser") {
 		$key_path = ("Software\\Microsoft\\AppV\\Client\\RunVirtual");
 	}
 	else {}
	             
	if ($key = $root_key->get_subkey($key_path)) {
#			::rptMsg($key_path);
#			::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
#			::rptMsg("");
			
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar @subkeys > 0) {
			foreach my $s (@subkeys) {
				my $name = $s->get_name();
				my $lw   = $s->get_timestamp();
				my $str = "RunVirtual: ".$name."  ";
				eval {
					my $def = $s->get_value("")->get_data();
					$str .= "Default value = ".$def;
				};
				::rptMsg($lw."|REG|||".$str);
			}
		}
		else {
#				::rptMsg($key_path." has no subkeys\.");
		}
	}
	else {
#			::rptMsg($key_path." not found\.");
	}
}

1;
