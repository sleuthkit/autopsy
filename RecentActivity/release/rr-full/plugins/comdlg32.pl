#-----------------------------------------------------------
# comdlg32.pl
# Plugin for Registry Ripper 
#
# Change history
#   20121005 - updated to address shell item type 0x3A
#   20121005 - updated to parse shell item ID lists
#   20100409 - updated to include Vista and above
#   20100402 - updated IAW Chad Tilbury's post to SANS
#              Forensic Blog
#   20080324 - created
#
# References
#   Win2000 - http://support.microsoft.com/kb/319958
#   XP - http://support.microsoft.com/kb/322948/EN-US/
#		
# copyright 2012 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package comdlg32;
use strict;
use Time::Local;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20121008);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets contents of user's ComDlg32 key";
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching comdlg32 v.".$VERSION);
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;
	::rptMsg("comdlg32 v.".$VERSION);
	::rptMsg("");
# LastVistedMRU	
	my $key_path = "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\ComDlg32";
	my $key;
	my @vals;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		
		my @subkeys = $key->get_list_of_subkeys();
		
		if (scalar @subkeys > 0) {
			foreach my $s (@subkeys) {
				if ($s->get_name() eq "LastVisitedMRU") {
					::rptMsg("LastVisitedMRU");
					::rptMsg("LastWrite: ".gmtime($s->get_timestamp()));
					parseLastVisitedMRU($s); 
					::rptMsg("");
				}
				
				if ($s->get_name() eq "OpenSaveMRU") {
					::rptMsg("OpenSaveMRU");
					::rptMsg("LastWrite: ".gmtime($s->get_timestamp()));
					parseOpenSaveMRU($s); 
					::rptMsg("");
				}
				
				if ($s->get_name() eq "CIDSizeMRU") {
					::rptMsg("CIDSizeMRU");
					::rptMsg("LastWrite: ".gmtime($s->get_timestamp()));
					parseCIDSizeMRU($s);
					::rptMsg("");
				}
				
				if ($s->get_name() eq "FirstFolder") {
					::rptMsg("FirstFolder");
					::rptMsg("LastWrite: ".gmtime($s->get_timestamp()));
					parseFirstFolder($s);
					::rptMsg("");
				}
				
				if ($s->get_name() eq "LastVisitedPidlMRU" || $s->get_name() eq "LastVisitedPidlMRULegacy") {
					::rptMsg("LastVisitedPidlMRU");
					::rptMsg("LastWrite: ".gmtime($s->get_timestamp()));
					parseLastVisitedPidlMRU($s); 
					::rptMsg("");
				}
				
				if ($s->get_name() eq "OpenSavePidlMRU") {
					::rptMsg("OpenSavePidlMRU");
					::rptMsg("LastWrite: ".gmtime($s->get_timestamp()));
					parseOpenSavePidlMRU($s); 
					::rptMsg("");
				}
			}
		}
		else {
			::rptMsg($key_path." has no subkeys.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}	

sub parseLastVisitedMRU {
	my $key = shift;
	my %lvmru;
	my @mrulist;
	my @vals = $key->get_list_of_values();
	
	if (scalar(@vals) > 0) {
# First, read in all of the values and the data
		foreach my $v (@vals) {
			$lvmru{$v->get_name()} = $v->get_data();
		}
# Then, remove the MRUList value
		if (exists $lvmru{MRUList}) {
			::rptMsg("  MRUList = ".$lvmru{MRUList});
			@mrulist = split(//,$lvmru{MRUList});
			delete($lvmru{MRUList});
			foreach my $m (@mrulist) {
				my ($file,$dir) = split(/\00\00/,$lvmru{$m},2);
				$file =~ s/\00//g;
				$dir  =~ s/\00//g;
				::rptMsg("  ".$m." -> EXE: ".$file);
				::rptMsg("    -> Last Dir: ".$dir);
			}
		}
		else {
			::rptMsg("LastVisitedMRU key does not have an MRUList value.");
		}				
	}
	else {
		::rptMsg("LastVisitedMRU key has no values.");
	}	
	::rptMsg("");
}

sub parseOpenSaveMRU {
	my $key = shift;
	
	parseOpenSaveValues($key);
	::rptMsg("");
# Now, let's get the subkeys
	my @sk = $key->get_list_of_subkeys();
	if (scalar(@sk) > 0) {
		foreach my $s (@sk) {
			parseOpenSaveValues($s);
			::rptMsg("");
		}
	}
	else {
		::rptMsg("OpenSaveMRU key has no subkeys.");
	}	
	::rptMsg("");
}

sub parseOpenSaveValues {
	my $key = shift;
	::rptMsg("OpenSaveMRU\\".$key->get_name());
	::rptMsg("LastWrite Time: ".gmtime($key->get_timestamp())." Z");
	my %osmru;
	my @vals = $key->get_list_of_values();
	if (scalar(@vals) > 0) {
		map{$osmru{$_->get_name()} = $_->get_data()}(@vals);
		if (exists $osmru{MRUList}) {
			::rptMsg("  MRUList = ".$osmru{MRUList});
			my @mrulist = split(//,$osmru{MRUList});
			delete($osmru{MRUList});
			foreach my $m (@mrulist) {
				::rptMsg("  ".$m." -> ".$osmru{$m});
			}
		}
		else {
			::rptMsg($key->get_name()." does not have an MRUList value.");
		}	
	}
	else {
		::rptMsg($key->get_name()." has no values.");
	}	
}

sub parseCIDSizeMRU {
	my $key = shift;
	my %lvmru;
	my @mrulist;
	my @vals = $key->get_list_of_values();
	my %mru;
	my $count = 0;
		
	if (scalar(@vals) > 0) {
# First, read in all of the values and the data
		foreach my $v (@vals) {
			$lvmru{$v->get_name()} = $v->get_data();
		}
# Then, remove the MRUList value
		::rptMsg("Note: All value names are listed in MRUListEx order.");
		::rptMsg("");
		if (exists $lvmru{MRUListEx}) {
			my @mrulist = unpack("V*",$lvmru{MRUListEx});
			foreach my $n (0..(scalar(@mrulist) - 2)) {
				$mru{$count++} = $lvmru{$mrulist[$n]};
			}
			delete $mru{0xffffffff};	
			foreach my $m (sort {$a <=> $b} keys %mru) {
#				my $file = parseStr($mru{$m});
				my $file = (split(/\00\00/,$mru{$m},2))[0];
				$file =~ s/\00//g;
				::rptMsg("  ".$file);
			}
		}
		else {
#			::rptMsg($key_path." does not have an MRUList value.");
		}				
	}
	else {
#		::rptMsg($key_path." has no values.");
	}
}

sub parseFirstFolder {
	my $key = shift;
	my %lvmru;
	my @mrulist;
	my @vals = $key->get_list_of_values();
	my %mru;
	my $count = 0;
		
	if (scalar(@vals) > 0) {
# First, read in all of the values and the data
		foreach my $v (@vals) {
			$lvmru{$v->get_name()} = $v->get_data();
		}
# Then, remove the MRUList value
		::rptMsg("Note: All value names are listed in MRUListEx order.");
		::rptMsg("");
		if (exists $lvmru{MRUListEx}) {
			my @mrulist = unpack("V*",$lvmru{MRUListEx});
			foreach my $n (0..(scalar(@mrulist) - 2)) {
				$mru{$count++} = $lvmru{$mrulist[$n]};
			}
			delete $mru{0xffffffff};	
			foreach my $m (sort {$a <=> $b} keys %mru) {
#				my $file = parseStr($mru{$m});
				my @files = split(/\00\00/,$mru{$m});
				if (scalar(@files) == 0) {
					::rptMsg("  No files listed.");
				}
				elsif (scalar(@files) == 1) {
					$files[0] =~ s/\00//g;
					::rptMsg("  ".$files[0]);
				}
				elsif (scalar(@files) > 1) {
					my @files2;
					foreach my $file (@files) {
						$file =~ s/\00//g;
						push(@files2,$file);
					}
					::rptMsg("  ".join(' ',@files2));
				}
				else {
					
				}
			}
		}
		else {
#			::rptMsg($key_path." does not have an MRUList value.");
		}				
	}
	else {
#		::rptMsg($key_path." has no values.");
	}
}

sub parseLastVisitedPidlMRU {
	my $key = shift;
	my %lvmru;
	my @mrulist;
	my @vals = $key->get_list_of_values();
	my %mru;
	my $count = 0;
	
	if (scalar(@vals) > 0) {
# First, read in all of the values and the data
		foreach my $v (@vals) {
			$lvmru{$v->get_name()} = $v->get_data();
		}
# Then, remove the MRUList value
		::rptMsg("Note: All value names are listed in MRUListEx order.");
		::rptMsg("");	
		if (exists $lvmru{MRUListEx}) {
			my @mrulist = unpack("V*",$lvmru{MRUListEx});
			foreach my $n (0..(scalar(@mrulist) - 2)) {
				$mru{$count++} = $lvmru{$mrulist[$n]};
			}
			delete $mru{0xffffffff};	

			foreach my $m (sort {$a <=> $b} keys %mru) {
				my ($file,$shell) = split(/\00\00/,$mru{$m},2);
				$file =~ s/\00//g;
				$shell =~ s/^\00//;
				my $str = parseShellItem($shell);
				::rptMsg("  ".$file." - ".$str);
			}
		}
		else {
			::rptMsg("LastVisitedPidlMRU key does not have an MRUList value.");
		}				
	}
	else {
		::rptMsg("LastVisitedPidlMRU key has no values.");
	}	
}

#-----------------------------------------------------------
#
#-----------------------------------------------------------
sub parseOpenSavePidlMRU {
	my $key = shift;
	my @subkeys = $key->get_list_of_subkeys();
	
	if (scalar(@subkeys) > 0) {
		foreach my $s (@subkeys) {
			::rptMsg("OpenSavePidlMRU\\".$s->get_name());
			::rptMsg("LastWrite Time: ".gmtime($s->get_timestamp()));
			
			my @vals = $s->get_list_of_values();
			
			my %lvmru = ();
			my @mrulist = ();
			my %mru = ();
			my $count = 0;
			
			
			if (scalar(@vals) > 0) {
# First, read in all of the values and the data
				::rptMsg("Note: All value names are listed in MRUListEx order.");
				::rptMsg("");
				foreach my $v (@vals) {
					$lvmru{$v->get_name()} = $v->get_data();
				}
# Then, remove the MRUList value
				if (exists $lvmru{MRUListEx}) {
					my @mrulist = unpack("V*",$lvmru{MRUListEx});
					foreach my $n (0..(scalar(@mrulist) - 2)) {
						$mru{$count++} = $lvmru{$mrulist[$n]};
					}
					delete $mru{0xffffffff};	

					foreach my $m (sort {$a <=> $b} keys %mru) {
						my $str = parseShellItem($mru{$m});
						::rptMsg("  ".$str);
					}
				}
			}
			else {
				::rptMsg($s->get_name()." has no values.");
			}
			::rptMsg("");
		}
	}
	else {	
		::rptMsg($key->get_name()." has no subkeys.");
	}
}

#-----------------------------------------------------------
#
#-----------------------------------------------------------
sub parseShellItem {
	my $data = shift;
	my $len = length($data);
	my $str;
	
	my $tag = 1;
	my $cnt = 0;
	while ($tag) {
		my %item = ();
		my $sz = unpack("v",substr($data,$cnt,2));
		$tag = 0 if (($sz == 0) || ($cnt + $sz > $len));
		
		my $dat = substr($data,$cnt,$sz);
		my $type = unpack("C",substr($dat,2,1));
#		::rptMsg(sprintf "  Size: ".$sz."  Type: 0x%x",$type);
		
		if ($type == 0x1F) {
# System Folder 			
 			%item = parseSystemFolderEntry($dat);
 			$str .= "\\".$item{name};
 		}
 		elsif ($type == 0x2F) {
# Volume (Drive Letter) 			
 			%item = parseDriveEntry($dat);
 			$item{name} =~ s/\\$//;
 			$str .= "\\".$item{name};
 		}
 		elsif ($type == 0x31 || $type == 0x32 || $type == 0x3a || $type == 0x74) {
 			%item = parseFolderEntry($dat);
 			$str .= "\\".$item{name};
 		}
 		elsif ($type == 0x00) {
 			
 		}
 		elsif ($type == 0xc3 || $type == 0x41 || $type == 0x42 || $type == 0x46 || $type == 0x47) {
# Network stuff
			my $id = unpack("C",substr($dat,3,1));
			if ($type == 0xc3 && $id != 0x01) {
				%item = parseNetworkEntry($dat);
			}
			else {
				%item = parseNetworkEntry($dat); 
			}
			$str .= "\\".$item{name};
 		}
 		else {
 			$item{name} = sprintf "Unknown Type (0x%x)",$type;
 			$str .= "\\".$item{name};
 		}		
		$cnt += $sz;
	}
	$str =~ s/^\\//;
	return $str;
}

#-----------------------------------------------------------
#
#-----------------------------------------------------------
sub parseSystemFolderEntry {
	my $data     = shift;
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
#
#-----------------------------------------------------------
sub parseDriveEntry {
	my $data     = shift;
	my %item = ();
	$item{type} = unpack("C",substr($data,2,1));;
	$item{name} = substr($data,3,3);
	return %item;
}
#-----------------------------------------------------------
# parseNetworkEntry()
#
#-----------------------------------------------------------
sub parseNetworkEntry {
	my $data = shift;
	my %item = ();	
	$item{type} = unpack("C",substr($data,2,1));
	
	my @n = split(/\00/,substr($data,4,length($data) - 4));
	$item{name} = $n[0];
	$item{name} =~ s/^\W//;
	return %item;
}
#-----------------------------------------------------------
#
#-----------------------------------------------------------
sub parseFolderEntry {
	my $data     = shift;
	my %item = ();
	
	$item{type} = unpack("C",substr($data,2,1));
# Type 0x74 folders have a slightly different format	
	
	my $ofs_mdate;
	my $ofs_shortname;
	
	if ($item{type} == 0x74) {
		$ofs_mdate = 0x12;
	}
	elsif (substr($data,4,4) eq "AugM") {
		$ofs_mdate = 0x1c;
	}
	elsif ($item{type} == 0x31 || $item{type} == 0x32 || $item{type} == 0x3a) {
		$ofs_mdate = 0x08;
	}
	else {}
# some type 0x32 items will include a file size	
	if ($item{type} == 0x32) {
		my $size = unpack("V",substr($data,4,4));
		if ($size != 0) {
			$item{filesize} = $size;
		}
	}
	
	my @m = unpack("vv",substr($data,$ofs_mdate,4));
	($item{mtime_str},$item{mtime}) = convertDOSDate($m[0],$m[1]);
	
# Need to read in short name; nul-term ASCII
#	$item{shortname} = (split(/\00/,substr($data,12,length($data) - 12),2))[0];
	$ofs_shortname = $ofs_mdate + 6;	
	my $tag = 1;
	my $cnt = 0;
	my $str = "";
	while($tag) {
		my $s = substr($data,$ofs_shortname + $cnt,1);
		if ($s =~ m/\00/ && ((($cnt + 1) % 2) == 0)) {
			$tag = 0;
		}
		else {
			$str .= $s;
			$cnt++;
		}
	}
#	$str =~ s/\00//g;
	my $shortname = $str;
	my $ofs = $ofs_shortname + $cnt + 1;
# Read progressively, 1 byte at a time, looking for 0xbeef	
	my $tag = 1;
	my $cnt = 0;
	while ($tag) {
		if (unpack("v",substr($data,$ofs + $cnt,2)) == 0xbeef) {
			$tag = 0;
		}
		else {
			$cnt++;
		}
	}
	$item{extver} = unpack("v",substr($data,$ofs + $cnt - 4,2));
	
#	::rptMsg(sprintf "  BEEF Offset: 0x%x",$ofs + $cnt);
#	::rptMsg("  Version: ".$item{extver});
	
	$ofs = $ofs + $cnt + 2;
	
	my @m = unpack("vv",substr($data,$ofs,4));
	($item{ctime_str},$item{ctime}) = convertDOSDate($m[0],$m[1]);
	$ofs += 4;
	my @m = unpack("vv",substr($data,$ofs,4));
	($item{atime_str},$item{atime}) = convertDOSDate($m[0],$m[1]);
	$ofs += 4;
	
	my $jmp;
	if ($item{extver} == 0x03) {
		$jmp = 8;
	}
	elsif ($item{extver} == 0x07) {
		$jmp = 22;
	}
	elsif ($item{extver} == 0x08) {
		$jmp = 26;
	}
	else {}
	
	$ofs += $jmp;
#	::rptMsg(sprintf "  Offset: 0x%x",$ofs);
	
	my $str = substr($data,$ofs,length($data) - $ofs);
	
	my $longname = (split(/\00\00/,$str,2))[0];
	$longname =~ s/\00//g;

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