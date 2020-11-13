#-----------------------------------------------------------
# shellactivities.pl
#  
#
# Change history
#   20180709 - updated
#   20180611 - created (per request submitted by John McCash)
#
# References
#  https://twitter.com/gazambelli/status/1005170301355864065
# 
# copyright 2018 QAR, LLC
# author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package shellactivities;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20180709);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets contents of user's ShellActivities key";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching shellactivities v.".$VERSION);
	::rptMsg("shellactivities v.".$VERSION); 
  ::rptMsg("- ".getShortDescr()."\n"); 
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = 'Software\\Microsoft\\Windows\\CurrentVersion\\CloudStore\\Store\\Cache\\DefaultAccount\\$$windows.data.taskflow.shellactivities\\Current';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("Key LastWrite: ".gmtime($key->get_timestamp()));
		eval {
			my $data = $key->get_value("Data")->get_data();
			processShellActivities($data);
		};
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

#-----------------------------------------------------------
# 
#-----------------------------------------------------------
sub processShellActivities {
	my $data = shift;
	my $sz = length($data);
	my $count = 0;
	my $offset = 4;
	my ($l,$tag,$str);
	my ($t0,$t1) = unpack("VV",substr($data,$offset,8));
	::rptMsg("Time stamp: ".gmtime(::getTime($t0,$t1))." Z");
	::rptMsg("");
	
	while ($offset < ($sz - 10)) {
        
# Code to locate the appropriate identifier		
		$tag = 1;
		while ($tag) {
			if (unpack("v",substr($data,$offset,2)) == 0x14d2) {
				$tag = 0;
			}
			else {
				$offset++;
                # Check if at end of file and exit loop if it is
                last if ($offset >= $sz ); 
			}
		}

		# Check if at end of file and exit loop if it is
        last if ($offset >= $sz ); 

        
		$offset += 2;
		$l = unpack("C",substr($data,$offset,1));
#		::rptMsg("String Length: ".sprintf "0x%x",$l);
		$offset += 1;
		$str = substr($data,$offset,$l * 2);
		$str =~ s/\00//g;
		::rptMsg("Path: ".$str);
		$offset += $l * 2;
		
		$tag = 1;
		while ($tag) {
			if (unpack("v",substr($data,$offset,2)) == 0x23d2) {
				$tag = 0;
			}
			else {
				$offset++;
			}
		}

		$offset += 2;
		$l = unpack("C",substr($data,$offset,1));
		$offset += 1;
		$str = substr($data,$offset,$l * 2);
		$str =~ s/\00//g;
#		::rptMsg($str);
		$offset += $l * 2;
		
		$tag = 1;
		while ($tag) {
			if (unpack("v",substr($data,$offset,2)) == 0x28d2) {
				$tag = 0;
			}
			else {
				$offset++;
			}
		}
		
		$offset += 2;
		$l = unpack("C",substr($data,$offset,1));
		$offset += 1;
		$str = substr($data,$offset,$l * 2);
		$str =~ s/\00//g;
		::rptMsg("Window Title: ".$str);
		$offset += $l * 2;

		$tag = 1;
		while ($tag) {
			if (unpack("v",substr($data,$offset,2)) == 0x32c6) {
				$tag = 0;
			}
			else {
				$offset++;
			}
		}
		
		$offset += 3;
#		probe(substr($data,$offset,8));
		($t0,$t1) = unpack("VV",substr($data,$offset,8));
#		::rptMsg("Time 1: ".gmtime(::getTime($t0,$t1))." Z");
		
		$tag = 1;
		while ($tag) {
			if (unpack("v",substr($data,$offset,2)) == 0x3cc6) {
				$tag = 0;
			}
			else {
				$offset++;
			}
		}
		
		$offset += 3;
#		probe(substr($data,$offset,8));
		($t0,$t1) = unpack("VV",substr($data,$offset,8));
#		::rptMsg("Time 2: ".gmtime(::getTime($t0,$t1))." Z");
		$offset += 8;

		$count++;
		::rptMsg("");
	}
	::rptMsg("Total Count: ".$count);
}

#-----------------------------------------------------------
# 
#-----------------------------------------------------------


#-----------------------------------------------------------
# 
#-----------------------------------------------------------


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