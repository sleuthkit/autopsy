#-----------------------------------------------------------
# itempos.pl
# 
# History:
#   20191111 - Added default value to $jmp if $item{extver} cannot be determined.
#
# References
#    http://c0nn3ct0r.blogspot.com/2011/11/windows-shellbag-forensics.html
#  Andrew's Python code for Registry Decoder
#    http://code.google.com/p/registrydecoder/source/browse/trunk/templates/template_files/ShellBag.py
#  Joachim Metz's shell item format specification
#    http://download.polytechnic.edu.na/pub4/download.sourceforge.net/pub/
#      sourceforge/l/project/li/liblnk/Documentation/Windows%20Shell%20Item%20format/
#      Windows%20Shell%20Item%20format.pdf
#  Converting DOS Date format
#    http://msdn.microsoft.com/en-us/library/windows/desktop/ms724274(v=VS.85).aspx
#
# Thanks to Willi Ballenthin and Joachim Metz for the documentation they 
# provided, Andrew Case for posting the Registry Decoder code, and Kevin 
# Moore for writing the shell bag parser for Registry Decoder, as well as 
# assistance with some parsing.
# 
# copyright 2013 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package itempos;
use strict;
use Time::Local;

my %config = (hive          => "NTUSER\.DAT",
							hivemask      => 16,
							output        => "report",
							category      => "User Activity",
              osmask        => 16, #Win7/Win2008R2
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20130514);

sub getConfig{return %config}

sub getShortDescr {
	return "Shell/Bags/1/Desktop ItemPos* value parsing; Win7 NTUSER.DAT hives";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching itempos v.".$VERSION);
	::rptMsg("itempos v.".$VERSION); # banner
	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner
	my %itempos = ();

	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = "Software\\Microsoft\\Windows\\Shell\\Bags\\1\\Desktop";
	my $key;
	
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		my $lw = $key->get_timestamp();
		::rptMsg("LastWrite: ".gmtime($lw));
		::rptMsg("");
		
		my @vals = $key->get_list_of_values();
		foreach my $v (@vals) {
			my $name = $v->get_name();
			if ($name =~ m/^ItemPos/) {
				$itempos{$name} = $v->get_data();
			}
		} 
		
		if (scalar keys %itempos > 0) {
			foreach my $i (keys %itempos) {
				::rptMsg("Value: ".$i);
				::rptMsg(sprintf "%-10s|%-20s|%-20s|%-20s|Name","Size","Modified","Accessed","Created");
				::rptMsg(sprintf "%-10s|%-20s|%-20s|%-20s|"."-" x 10,"-" x 10,"-" x 20,"-" x 20,"-" x 20);
				parseBagEntry($itempos{$i});
				::rptMsg("");
			}
		}
		else {
			::rptMsg("No ItemPos* values found.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
#	::rptMsg("");
# The following was added on 20130514 to address Windows XP systems	
	$key_path = "Software\\Microsoft\\Windows\\ShellNoRoam\\Bags";
	if ($key = $root_key->get_subkey($key_path)) {
		my @sk = $key->get_list_of_subkeys();
		if (scalar(@sk) > 0) {
			foreach my $s (@sk) {
				my %itempos = ();
				my @vals = $s->get_subkey("Shell")->get_list_of_values();
				
				if (scalar(@vals) > 0) {
					foreach my $v (@vals) {
						my $name = $v->get_name();
					  if ($name =~ m/^ItemPos/) {
					  	$itempos{$name} = $v->get_data();
					  }
					}
					
					if (scalar keys %itempos > 0) {
						::rptMsg($key_path."\\".$s->get_name()."\\Shell");
						foreach my $i (keys %itempos) {
							::rptMsg("Value: ".$i);
							::rptMsg(sprintf "%-10s|%-20s|%-20s|%-20s|Name","Size","Modified","Accessed","Created");
							::rptMsg(sprintf "%-10s|%-20s|%-20s|%-20s|"."-" x 10,"-" x 10,"-" x 20,"-" x 20,"-" x 20);
							parseBagEntry($itempos{$i});
							::rptMsg("");
						}
					}
							
				}
			}
		}
		else {
# No subkeys			
		}
	}
	else {
		::rptMsg($key_path." not found\.");
	}
}

#-----------------------------------------------------------
# 
#-----------------------------------------------------------


#-----------------------------------------------------------
# parseBagEntry()
#-----------------------------------------------------------
sub parseBagEntry {
	my $data = shift;
	my $ofs = 24;
	my $len = length($data);
	while ($ofs < $len) {
		my %item = ();
		my $sz = unpack("v",substr($data,$ofs,2));
		
		my $data = substr($data,$ofs,$sz);
		
		my $type = unpack("C",substr($data,2,1));
		
		if ($type == 0x1f) {
			%item = parseSystemBagItem($data);
			::rptMsg(sprintf "%-10s|%-20s|%-20s|%-20s|".$item{name},"","","","");
		}
		elsif ($type == 0x31 || $type == 0x32 || $type == 0x3a) {
			%item = parseFolderItem($data);
			
			my ($m,$a,$c);
 			(exists $item{mtime_str} && $item{mtime_str} ne "0") ? ($m = $item{mtime_str}) : ($m = "");
 			(exists $item{atime_str} && $item{atime_str} ne "0") ? ($a = $item{atime_str}) : ($a = "");
 			(exists $item{ctime_str} && $item{ctime_str} ne "0") ? ($c = $item{ctime_str}) : ($c = "");
			my $str = sprintf "%-10s|%-20s|%-20s|%-20s|",$item{size},$m,$a,$c;
			::rptMsg($str.$item{name});
			
		}
		else {
			
		}
		$ofs += $sz + 8;
	}
}
#-----------------------------------------------------------
# parseSystemBagItem()
#-----------------------------------------------------------
sub parseSystemBagItem {
	my $data = shift;
	my %item = ();
	my %vals = (0x00 => "Explorer",
	            0x42 => "Libraries",
	            0x44 => "Users",
	            0x4c => "Public",
	            0x48 => "My Documents",
	            0x50 => "My Computer",
	            0x58 => "My Network Places",
	            0x60 => "Recycle Bin",
	            0x68 => "Explorer",
	            0x70 => "Control Panel",
	            0x78 => "Recycle Bin",
	            0x80 => "My Games");
	
	$item{type} = unpack("C",substr($data,2,1));
	$item{id}   = unpack("C",substr($data,3,1));
	if (exists $vals{$item{id}}) {
		$item{name} = $vals{$item{id}};
	}
	else {
		$item{name} = parseGUID(substr($data,4,16));
	}
	return %item;
}

#-----------------------------------------------------------
# parseFolderItem()
#-----------------------------------------------------------
sub parseFolderItem {
	my $data = shift;
	my %item = ();
	my $ofs_mdate = 0x08;
	$item{type} = unpack("C",substr($data,2,1));
	
	$item{size} = unpack("V",substr($data,4,4));
	
	my @m = unpack("vv",substr($data,$ofs_mdate,4));
	($item{mtime_str},$item{mtime}) = convertDOSDate($m[0],$m[1]);
		
	my $ofs_shortname = $ofs_mdate + 6;	
	my $tag = 1;
	my $cnt = 0;
	my $str = "";
	while($tag) {
		my $s = substr($data,$ofs_shortname + $cnt,1);
        return %item unless (defined $s);
		if ($s =~ m/\x00/ && ((($cnt + 1) % 2) == 0)) {
			$tag = 0;
		}
		else {
			$str .= $s;
			$cnt++;
		}
	}
#	$str =~ s/\x00//g;
	my $shortname = $str;
	my $ofs = $ofs_shortname + $cnt + 1;
# Read progressively, 1 byte at a time, looking for 0xbeef	
	$tag = 1;
	$cnt = 0;
	while ($tag) {
		my $s = substr($data,$ofs + $cnt,2);
        return %item unless (defined $s); 
		if (unpack("v",$s) == 0xbeef) {
			$tag = 0;
		}
		else {
			$cnt++;
		}
	}
	$item{extver} = unpack("v",substr($data,$ofs + $cnt - 4,2));
	$ofs = $ofs + $cnt + 2;
	
	@m = unpack("vv",substr($data,$ofs,4));
	($item{ctime_str},$item{ctime}) = convertDOSDate($m[0],$m[1]);
	$ofs += 4;
	@m = unpack("vv",substr($data,$ofs,4));
	($item{atime_str},$item{atime}) = convertDOSDate($m[0],$m[1]);
	
	my $jmp;
	if ($item{extver} == 0x03) {
		$jmp = 8;
	}
	elsif ($item{extver} == 0x07) {
		$jmp = 26;
	}
	elsif ($item{extver} == 0x08) {
		$jmp = 30;
	}
	else {
        $jmp = 34;
    }
	
	$ofs += $jmp;
	
	$str = substr($data,$ofs,length($data) - 30);
	my $longname = (split(/\x00\x00/,$str,2))[0];
	$longname =~ s/\x00//g;
	
	if ($longname ne "") {
		$item{name} = $longname;
	}
	else {
		$item{name} = $shortname;
	}
	return %item;
	
	
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
  return sprintf "{%08x-%x-%x-$d4-$d5}",$d1,$d2,$d3;
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
1;
