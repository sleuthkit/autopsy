#-----------------------------------------------------------
# menuorder.pl
# Plugin for Registry Ripper 
#
# Change history
#   20121005 - created  Tested on XP & Win7 only (not Vista)
#
# References:
#   http://kurtaubuchon.blogspot.com/2011/11/start-menu-and-ie-favorites-artifacts.html
#   http://journeyintoir.blogspot.com/2013/04/plugin-menuorder.html
#		
# copyright 2012 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package menuorder;
use strict;
use Time::Local;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20121005);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets contents of user's MenuOrder subkeys";
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching menuorder v.".$VERSION);
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;
	::rptMsg("menuorder v.".$VERSION);
	::rptMsg("");
# LastVistedMRU	
	my $key_path = "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\MenuOrder";
	my $key;
	my @vals;
	if ($key = $root_key->get_subkey($key_path)) {
		
		eval {
			my $start = $key->get_subkey("Start Menu2");
			recurseKeys($start,"");
			
		};
#		::rptMsg("Error: ".$@) if ($@);
		
		eval {
			my $fav = $key->get_subkey("Favorites");
			recurseKeys2($fav,"");
			
		};
#		::rptMsg("Error: ".$@) if ($@);
	
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

sub recurseKeys {
	my $key = shift;
	my $name = shift;
	
	::rptMsg($name."\\".$key->get_name());
	::rptMsg("LastWrite: ".gmtime($key->get_timestamp())." Z");
	
	my $order;
	eval {
		$order = $key->get_value("Order")->get_data();
		my @dat = split(/AugM/,$order);
# $dat[0] appears to be a header of some kind.
#		::rptMsg("Entries: ".unpack("V",substr($dat[0],0x10,4)));
# Within each section, starting with $dat[1], the 2nd DWORD appears to be the number of
# entries recorded in that section.		
		foreach my $n (1..(scalar(@dat) - 1)) {
			my %item = parseAugM($dat[$n]);
			::rptMsg("  ".$item{name});
		}
	};
	::rptMsg("");
	
	my @subkeys = $key->get_list_of_subkeys();
	if (scalar(@subkeys) > 0) {
		foreach my $s (@subkeys) {
			recurseKeys($s,$name."\\".$key->get_name());
		}	
	}
	else {
# No subkeys		
	}
	
}


sub recurseKeys2 {
	my $key = shift;
	my $name = shift;
	
	::rptMsg($name."\\".$key->get_name());
	::rptMsg("LastWrite: ".gmtime($key->get_timestamp())." Z");
	
	my $order;
	eval {
		$order = $key->get_value("Order")->get_data();
#		::rptMsg(" - Order value found.");
		parseOrder2($order);

	};
#	::rptMsg("Error: ".$@) if ($@);
	::rptMsg("");
	
	my @subkeys = $key->get_list_of_subkeys();
	if (scalar(@subkeys) > 0) {
		foreach my $s (@subkeys) {
			recurseKeys2($s,$name."\\".$key->get_name());
		}	
	}
	else {
# No subkeys		
	}
	
}

#-----------------------------------------------------------
# parseOrder2()
# 
#-----------------------------------------------------------
sub parseOrder2 {
	my $data = shift;
	my $ofs = 0x1c;
	
	my $num = unpack("V",substr($data,0x10,4));
	
	foreach my $n (1..$num) {
		my $sz = unpack("v",substr($data,$ofs,2));
		my $dat = substr($data,$ofs,$sz);
		my %item = parseItem($dat);
		::rptMsg("  ".$item{name});
		$ofs += ($sz + 0x0e);
	}
}

#-----------------------------------------------------------
# parseAugM()
# 
#-----------------------------------------------------------
sub parseAugM {
	my $data = shift;
	my %item = ();
	
	if (unpack("V",substr($data,0,4)) == 0x2) {
		
		my @mdate = unpack("VV",substr($data,0x10,4));	
		my $tag = 1;
		my $cnt = 0;
		my $str = "";
		while($tag) {
			my $s = substr($data,0x16 + $cnt,1);
			if ($s =~ m/\00/ && ((($cnt + 1) % 2) == 0)) {
				$tag = 0;
			}
			else {
				$str .= $s;
				$cnt++;
			}
		}
		my $ofs = 0x16 + $cnt + 1;
		my $shortname = $str;
	
		my $data2 = substr($data,$ofs,unpack("v",substr($data,$ofs,2)));
		my $sz = unpack("v",substr($data2,0,2));
		$item{version} = unpack("v",substr($data2,2,2));
		my $ext = unpack("v",substr($data2,4,2));
	
		my $ofs = 0x08;
# Get creation time values;
#		my @m = unpack("vv",substr($data,$ofs,4));
		$ofs += 4;
# Get last access time values		
#		my @m = unpack("vv",substr($data,$ofs,4));
		$ofs += 4;
		$ofs += 4;
		
		my $tag = 1;
		my $cnt = 0;
		my $str = "";
		while ($tag) {
			my $s = substr($data2,$ofs + $cnt,2);
			if (unpack("v",$s) == 0) {
				$tag = 0;
			}
			else {
				$str .= $s;
				$cnt += 2;
			}
		}
		$str =~ s/\00//g;
		$item{name} = $str;
		$ofs += $cnt;
#		::rptMsg(sprintf " - Ofs: 0x%x   Remaining Data: 0x%x",$ofs,$sz - $ofs);
		
		if (($sz - $ofs) > 0x10) {
			my $str = substr($data2,$ofs,$sz - $ofs);
			$str =~ s/^\00+//;
			my $s = (split(/\00/,$str,2))[0];
			$item{name} .= " (".$s.")";
		}
		
	}
	else {
		
	}
	return %item;
}

#-----------------------------------------------------------
# parseItem()
# 
#-----------------------------------------------------------
sub parseItem {
	my $data = shift;
	my %item = ();
	
	my $ofs = 0x08;
	my @mdate = unpack("VV",substr($data,$ofs,4));	
	$ofs += 6;
	
	my $tag = 1;
	my $cnt = 0;
	my $str = "";
	while($tag) {
		my $s = substr($data,$ofs + $cnt,1);
		if ($s =~ m/\00/ && ((($cnt + 1) % 2) == 0)) {
			$tag = 0;
		}
		else {
			$str .= $s;
			$cnt++;
		}
	}
	$ofs += ($cnt + 1);
	$item{shortname} = $str;
	
	my $data2 = substr($data,$ofs,unpack("v",substr($data,$ofs,2)));
	my $sz = unpack("v",substr($data2,0,2));
	$item{version} = unpack("v",substr($data2,2,2));

	my $ext = unpack("v",substr($data2,4,2));
	
	my $ofs = 0x08;
# Get creation time values;
#		my @m = unpack("vv",substr($data,$ofs,4));
	$ofs += 4;
# Get last access time values		
#		my @m = unpack("vv",substr($data,$ofs,4));
	$ofs += 4;
# Check the version	
	my $jmp;
	if ($item{version} == 0x03) {
		$jmp = 4;
	}
	elsif ($item{version} == 0x07) {
		$jmp = 22;
	}
	elsif ($item{version} == 0x08) {
		$jmp = 26;
	}
	else {}
	
	$ofs += $jmp;
	
	my $tag = 1;
	my $cnt = 0;
	my $str = "";
	while ($tag) {
		my $s = substr($data2,$ofs + $cnt,2);
		if (unpack("v",$s) == 0) {
			$tag = 0;
		}
		else {
			$str .= $s;
			$cnt += 2;
		}
	}
	$str =~ s/\00//g;
	$item{name} = $str;
	$ofs += $cnt;
	
	return %item;
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
	my $tag = 1;
	my $cnt = 0;
	
	my $loop = $len/16;
	$loop++ if ($len%16);
	
	foreach my $cnt (0..($loop - 1)) {
#	while ($tag) {
		my $left = $len - ($cnt * 16);
		
		my $n;
		($left < 16) ? ($n = $left) : ($n = 16);

		my $seg = substr($data,$cnt * 16,$n);
		my @str1 = split(//,unpack("H*",$seg));

		my @s3;
		my $str = "";

		foreach my $i (0..($n - 1)) {
			$s3[$i] = $str1[$i * 2].$str1[($i * 2) + 1];
			
			if (hex($s3[$i]) > 0x1f && hex($s3[$i]) < 0x7f) {
				$str .= chr(hex($s3[$i]));
			}
			else {
				$str .= "\.";
			}
		}
		my $h = join(' ',@s3);
		::rptMsg(sprintf "0x%08x: %-47s  ".$str,($cnt * 16),$h);
	}
}

#-----------------------------------------------------------
# convertDOSDate()
# subroutine to convert 4 bytes of binary data into a human-
# readable format.  Returns both a string and a Unix-epoch
# time.
#-----------------------------------------------------------
sub convertDOSDate {
	my $date = shift;
	my $time = shift;
	
	if ($date == 0x00 || $time == 0x00){
		return (0,0);
	}
	else {
		my $sec = ($time & 0x1f) * 2;
		$sec = "0".$sec if (length($sec) == 1);
		if ($sec == 60) {$sec = 59};
		my $min = ($time & 0x7e0) >> 5;
		$min = "0".$min if (length($min) == 1);
		my $hr  = ($time & 0xF800) >> 11;
		$hr = "0".$hr if (length($hr) == 1);
		my $day = ($date & 0x1f);
		$day = "0".$day if (length($day) == 1);
		my $mon = ($date & 0x1e0) >> 5;
		$mon = "0".$mon if (length($mon) == 1);
		my $yr  = (($date & 0xfe00) >> 9) + 1980;
		my $gmtime = timegm($sec,$min,$hr,$day,($mon - 1),$yr);
    return ("$yr-$mon-$day $hr:$min:$sec",$gmtime);
#		return gmtime(timegm($sec,$min,$hr,$day,($mon - 1),$yr));
	}
}



1;