#-----------------------------------------------------------
# susclient.pl
#   Values within this key appear to include the hard drive serial number
#
# Change history
#   20140326 - created
#
# References
#   Issues with WMI: http://www.techques.com/question/1-10989338/WMI-HDD-Serial-Number-Transposed
#   *command "wmic diskdrive get serialnumber" will return transposed info
#
# Copyright 2014 QAR, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package susclient;
use strict;

my %config = (hive          => "Software",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              category      => "System Config",
              version       => 20140326);
my $VERSION = getVersion();

# Functions #
sub getConfig {return %config}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}
sub getDescr {}
sub getShortDescr {
	return "Extracts SusClient* info, including HDD SN (if avail)";
}
sub getRefs {}

sub pluginmain {
	my $class = shift;
	my $hive = shift;

	# Initialize #
	::logMsg("Launching susclient v.".$VERSION);
  ::rptMsg("susclient v.".$VERSION); 
  ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n");      
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key;
	my $key_path = ("Microsoft\\Windows\\CurrentVersion\\WindowsUpdate");
	
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			foreach my $v (@vals) {
				if ($v->get_name() eq "LastRestorePointSetTime") {
					::rptMsg(sprintf "%-25s  %-30s",$v->get_name(),$v->get_data());
				}
				elsif ($v->get_name() eq "SusClientId") {
					::rptMsg(sprintf "%-25s  %-30s",$v->get_name(),$v->get_data());
				}
				elsif ($v->get_name() eq "SusClientIdValidation") {
					::rptMsg("SusClientIdValidation");
#					probe($v->get_data());
#					::rptMsg("");
					my $sn = parseSN($v->get_data());
					::rptMsg("  Serial Number: ".$sn);
					
				}
				else {}
	
			}
		}
		else {
			::rptMsg($key_path." has no values\.");
		}
	
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

sub parseSN {
	my $data = shift;
	my $sn;
	
	my $offset = unpack("C",substr($data,0,1));
	my $sz     = unpack("C",substr($data,2,1));
	
	$sn = substr($data,$offset,$sz);
	$sn =~ s/\x00//g;
	$sn =~ s/\x20//g;
	return $sn;
}

#-----------------------------------------------------------
# probe()
#
# Code the uses printData() to insert a 'probe' into a specific
# location and display the data
#
# Input: binary data of arbitrary length
# Output: Nothing, no return value.  Displays data to the console
#-----------------------------------------------------------
sub probe {
	my $data = shift;
	my @d = printData($data);
	
	foreach (0..(scalar(@d) - 1)) {
		print $d[$_]."\n";
	}
}

#-----------------------------------------------------------
# printData()
# subroutine used primarily for debugging; takes an arbitrary
# length of binary data, prints it out in hex editor-style
# format for easy debugging
#-----------------------------------------------------------
sub printData {
	my $data = shift;
	my $len = length($data);
	
	my @display = ();
	
	my $loop = $len/16;
	$loop++ if ($len%16);
	
	foreach my $cnt (0..($loop - 1)) {
# How much is left?
		my $left = $len - ($cnt * 16);
		
		my $n;
		($left < 16) ? ($n = $left) : ($n = 16);

		my $seg = substr($data,$cnt * 16,$n);
		my $lhs = "";
		my $rhs = "";
		foreach my $i ($seg =~ m/./gs) {
# This loop is to process each character at a time.
			$lhs .= sprintf(" %02X",ord($i));
			if ($i =~ m/[ -~]/) {
				$rhs .= $i;
    	}
    	else {
				$rhs .= ".";
     	}
		}
		$display[$cnt] = sprintf("0x%08X  %-50s %s",$cnt,$lhs,$rhs);
	}
	return @display;
}

1;
