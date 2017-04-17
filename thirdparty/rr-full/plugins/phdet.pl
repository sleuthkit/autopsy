#-----------------------------------------------------------
# phdet.pl
#
# History:
#  20121213 - created
#
# References:
#  http://www.microsoft.com/security/portal/Threat/Encyclopedia/Entry.aspx?Name=Win32/Phdet
#
# 
# copyright 2012 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package phdet;
use strict;

my %config = (hive          => "System",
							hivemask      => 4,
							output        => "report",
							category      => "Malware",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 31,  #XP - Win7
              version       => 20120817);

sub getConfig{return %config}
sub getShortDescr {
	return "Check for a Phdet infection";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();
my %files;
my @temps;

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching phdet v.".$VERSION);
	::rptMsg("phdet v.".$VERSION); # banner
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
		my $phdet_path = $ccs."\\Services\\msupdate";
		my $phdet;
		if ($phdet = $root_key->get_subkey($phdet_path)) {
			my @vals = $phdet->get_values();
			if (scalar(@vals) > 0) {
				my %p_vals;
				foreach my $v (@vals) {
					$p_vals{$v->get_name()} = $v->get_data();
				}
				::rptMsg("DisplayName: ".$p_vals{"DisplayName"});
				::rptMsg("Image Path : ".$p_vals{"ImagePath"});
			}
			else {
				::rptMsg($phdet_path." key has no values.");
			}
		}
		else {
			::rptMsg($phdet_path." not found.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

1;