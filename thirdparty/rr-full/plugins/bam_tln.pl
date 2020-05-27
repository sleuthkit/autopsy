#-----------------------------------------------------------
# bam_tln.pl
#
# History:
#  20180225 - created
#
# References:
#  from Phill Moore via Twitter: https://padawan-4n6.hatenablog.com/entry/2018/02/22/131110
#  https://twitter.com/aionescu/status/891172221971910661?lang=en
#  http://batcmd.com/windows/10/services/bam/
# 
# 
# copyright 2018 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package bam_tln;
use strict;

my %config = (hive          => "System",
							hivemask      => 4,
							output        => "tln",
							category      => "Program Execution",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 31,  #XP - Win7
              version       => 20180225);

sub getConfig{return %config}
sub getShortDescr {
	return "Parse files from System hive BAM Services";	
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
		my $bam_path = $ccs."\\Services\\bam\\UserSettings";
		my $bam;
		if ($bam = $root_key->get_subkey($bam_path)) {
			my @sk = $bam->get_list_of_subkeys();
			if (scalar(@sk) > 0) {
				foreach my $s (@sk) {
					processKey($s);
				}
			}	
			
		}
		else {
#			::rptMsg($bam_path." not found.");
		}
	}
	else {
#		::rptMsg($key_path." not found.");
	}
}


sub processKey {
	my $key = shift;
	my ($t,$count);
	my @values = $key->get_list_of_values();
	
	foreach (@values) {
		$count = 1 if ($_->get_type() == 3);
	}
	
	if (scalar(@values) > 0 && $count == 1) {
		foreach my $v (@values) {
			my $name = $v->get_name();
			
			if ($v->get_type() == 3) {
				my ($t0,$t1) = unpack("VV",substr($v->get_data(),0,8));
				$t = ::getTime($t0,$t1);
				::rptMsg($t."|BAM|||".$name." (".$key->get_name().")");
			}
		}
	}		
}

1;