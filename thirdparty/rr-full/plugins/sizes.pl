#! c:\perl\bin\perl.exe
#-----------------------------------------------------------
# sizes.pl
# Plugin for RegRipper; traverses through a Registry hive,
# looking for values with binary data types, and checks their
# sizes; change $min_size value to suit your needs
#
# Change history
#    20180817 - updated to include brief output, based on suggestion from J. Wood
#    20180607 - modified based on Meterpreter input from Mari DeGrazia
#    20150527 - Created
# 
# copyright 2015 QAR, LLC
# Author: H. Carvey
#-----------------------------------------------------------
package sizes;
use strict;

my $min_size    = 5000;
my $output_size = 48;

my %config = (hive          => "All",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20180817);

sub getConfig{return %config}
sub getShortDescr {
	return "Scans a hive file looking for binary value data of a min size (".$min_size.")";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();
my $count = 0;

sub pluginmain {
	my $class = shift;
	my $file = shift;
	my $reg = Parse::Win32Registry->new($file);
	my $root_key = $reg->get_root_key;
	::logMsg("Launching sizes v.".$VERSION);
	::rptMsg("sizes v.".$VERSION); 
  ::rptMsg("(".getHive().") ".getShortDescr()."\n");  
  
  my $start = time;
    
	traverse($root_key);
	
	my $finish = time;
	
	::rptMsg("Scan completed: ".($finish - $start)." sec");
	::rptMsg("Total values  : ".$count);
}

sub traverse {
	my $key = shift;
#  my $ts = $key->get_timestamp();
  
  foreach my $val ($key->get_list_of_values()) {
  	$count++;
  	my $type = $val->get_type();
  	if ($type == 0 || $type == 3 || $type == 1 || $type == 2) {
  		my $data = $val->get_data();
			my $len  = length($data);
			if ($len > $min_size) {
				
				my @name = split(/\\/,$key->get_path());
				$name[0] = "";
				$name[0] = "\\" if (scalar(@name) == 1);
				my $path = join('\\',@name);
				::rptMsg("Key  : ".$path."  Value: ".$val->get_name()."  Size: ".$len." bytes");

# Data type "none", "Reg_SZ", "Reg_Expand_SZ"				
				if ($type == 0 || $type == 1 || $type == 2) {
					::rptMsg("Data Sample (first ".$output_size." bytes) : ".substr($data,0,$output_size)."...");
				}

# Binary data				
				if ($type == 3) {
					my $out = substr($data,0,$output_size);
					probe($out);				
				}
				
				::rptMsg("");
			}
  	}
  }
  
	foreach my $subkey ($key->get_list_of_subkeys()) {
		traverse($subkey);
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
	::rptMsg("");
	foreach (0..(scalar(@d) - 1)) {
		::rptMsg($d[$_]);
	}
	::rptMsg("");	
}

#-----------------------------------------------------------
# printData()
# subroutine used primarily for debugging; takes an arbitrary
# length of binary data, prints it out in hex editor-style
# format for easy debugging
#
# Usage: see probe()
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