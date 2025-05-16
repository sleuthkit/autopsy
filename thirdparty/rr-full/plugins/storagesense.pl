#-----------------------------------------------------------
# storagesense.pl
# Get StorageSense values
#
# Change history:
#   20201230 - created
#
# References:
#   http://port139.hatenablog.com/entry/2018/12/24/122856
#   
#        
# copyright 2020 Quantum Analytics Research, LLC
# Author: H. Carvey, 2013
#-----------------------------------------------------------
package storagesense;
use strict;

my %config = (hive          => "software, ntuser\.dat",
			  category      => "persistence",
			  MITRE         => "T1547",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              version       => 20201230);

sub getConfig{return %config}

sub getShortDescr {
	return "Get StorageSense values";	
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
	::logMsg("Launching storagesense v.".$VERSION);
	::rptMsg("storagesense v.".$VERSION); 
	::rptMsg("(".getHive().") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	my %guess = ();
	my $hive_guess = "";
	my %guess = ::guessHive($hive);
	foreach my $g (keys %guess) {
		$hive_guess = $g if ($guess{$g} == 1);
	}
	
	if ($hive_guess eq "software") {
		
		
	}
	elsif ($hive_guess eq "ntuser") {
		
		
	}
	else {}
	
	
	my @paths = ("Software\\Microsoft\\Windows\\CurrentVersion\\StorageSense\\Parameters\\StoragePolicy",  # HKCU
	             "Microsoft\\Windows\\CurrentVersion\\StorageSense\\Parameters",            # HKLM
	             "Policies\\Microsoft\\Windows\\StorageSense");                                            # HKLM GPO
	
	foreach my $key_path (@paths) {
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg("");
			::rptMsg("Key path: ".$key_path);
			::rptMsg("");
			my @vals = $key->get_list_of_values();
			if (scalar @vals > 0) {
				foreach my $v (@vals) {
					::rptMsg($v->get_name()." - ".$v->get_data());
					
				}
				
			}
			else {
				::rptMsg($key_path." has no values.");
			}
		}
		else {
#			::rptMsg($key_path." not found.");
		}
	}
	::rptMsg("Analysis Tip: ");
}
1;