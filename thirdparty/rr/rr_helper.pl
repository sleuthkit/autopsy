#-------------------------------------------------------------
# rr_helper.pl
# This file contains helper functions for RegRipper
#
# Note: The main UI code (GUI or CLI) must 'use' the Time::Local 
#       module.
#
# Change history:
#   20200730 - created
#
# copyright 2020 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-------------------------------------------------------------

#-------------------------------------------------------------
# getCCS()
# 
# Given a key object for the System hive, return the ControlSet
# marked "Current"; pass $root_key to function
#
# my $root_key = $reg->get_root_key;
#-------------------------------------------------------------
sub getCCS {
	my $root_key = shift;
	my $current;
	my $ccs;
	my $key_path = 'Select';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		$current = $key->get_value("Current")->get_data();
		$ccs = "ControlSet".sprintf "%03d",$current;
		return $ccs;
	}
	else {
#		::rptMsg($key_path." not found.");
		return undef;
	}
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

#-------------------------------------------------------------
# getUnicodeStr()
# 
#-------------------------------------------------------------
sub getUnicodeStr {
	my $data = shift;
	Encode::from_to($data,'UTF-16LE','utf8');
	$data = Encode::decode_utf8($data);
	return $data;
}

#-----------------------------------------------------------
# parseGUID()
# Takes 16 bytes of binary data, returns a string formatted
# as an MS GUID.
#-----------------------------------------------------------
sub parseGUID {
	my $data     = shift;
  my $d1 = unpack("V",substr($data,0,4));
  my $d2 = unpack("v",substr($data,4,2));
  my $d3 = unpack("v",substr($data,6,2));
	my $d4 = unpack("H*",substr($data,8,2));
  my $d5 = unpack("H*",substr($data,10,6));
  my $guid = sprintf "{%08x-%04x-%04x-$d4-$d5}",$d1,$d2,$d3;
  
  return $guid;
  
}

#-------------------------------------------------------------
# function()
# 
#-------------------------------------------------------------

1;