#-----------------------------------------------------------
# comdlg32.pl
# Plugin for Registry Ripper 
#
# Change history
#   20180702 - update to parseGUID function
#   20180627 - updated to address Win10, per input from Geoff Rempel
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
# copyright 2018 Quantum Analytics Research, LLC
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
              version       => 20180702);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets contents of user's ComDlg32 key";
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

my %folder_types = ("{724ef170-a42d-4fef-9f26-b60e846fba4f}" => "Administrative Tools",
    "{d0384e7d-bac3-4797-8f14-cba229b392b5}" => "Common Administrative Tools",
    "{de974d24-d9c6-4d3e-bf91-f4455120b917}" => "Common Files",
    "{c1bae2d0-10df-4334-bedd-7aa20b227a9d}" => "Common OEM Links",
    "{5399e694-6ce5-4d6c-8fce-1d8870fdcba0}" => "Control Panel",
    "{1ac14e77-02e7-4e5d-b744-2eb1ae5198b7}" => "CSIDL_SYSTEM",
    "{b4bfcc3a-db2c-424c-b029-7fe99a87c641}" => "Desktop",
    "{7b0db17d-9cd2-4a93-9733-46cc89022e7c}" => "Documents Library",
    "{a8cdff1c-4878-43be-b5fd-f8091c1c60d0}" => "Documents",
    "{fdd39ad0-238f-46af-adb4-6c85480369c7}" => "Documents",
    "{374de290-123f-4565-9164-39c4925e467b}" => "Downloads",
    "{de61d971-5ebc-4f02-a3a9-6c82895e5c04}" => "Get Programs",
    "{a305ce99-f527-492b-8b1a-7e76fa98d6e4}" => "Installed Updates",
    "{871c5380-42a0-1069-a2ea-08002b30309d}" => "Internet Explorer (Homepage)",
    "{031e4825-7b94-4dc3-b131-e946b44c8dd5}" => "Libraries",
    "{2112ab0a-c86a-4ffe-a368-0de96e47012e}" => "Music",
    "{1cf1260c-4dd0-4ebb-811f-33c572699fde}" => "Music",
    "{4bd8d571-6d19-48d3-be97-422220080e43}" => "Music",
    "{20d04fe0-3aea-1069-a2d8-08002b30309d}" => "My Computer",
    "{450d8fba-ad25-11d0-98a8-0800361b1103}" => "My Documents",
    "{ed228fdf-9ea8-4870-83b1-96b02cfe0d52}" => "My Games",
    "{208d2c60-3aea-1069-a2d7-08002b30309d}" => "My Network Places",
    "{f02c1a0d-be21-4350-88b0-7367fc96ef3c}" => "Network", 
    "{3add1653-eb32-4cb0-bbd7-dfa0abb5acca}" => "Pictures",
    "{33e28130-4e1e-4676-835a-98395c3bc3bb}" => "Pictures",
    "{a990ae9f-a03b-4e80-94bc-9912d7504104}" => "Pictures",
    "{7c5a40ef-a0fb-4bfc-874a-c0f2e0b9fa8e}" => "Program Files (x86)",
    "{905e63b6-c1bf-494e-b29c-65b732d3d21a}" => "Program Files",
    "{df7266ac-9274-4867-8d55-3bd661de872d}" => "Programs and Features",
    "{3214fab5-9757-4298-bb61-92a9deaa44ff}" => "Public Music",
    "{b6ebfb86-6907-413c-9af7-4fc2abf07cc5}" => "Public Pictures",
    "{2400183a-6185-49fb-a2d8-4a392a602ba3}" => "Public Videos",
    "{4336a54d-38b-4685-ab02-99bb52d3fb8b}"  => "Public",
    "{491e922f-5643-4af4-a7eb-4e7a138d8174}" => "Public",
    "{dfdf76a2-c82a-4d63-906a-5644ac457385}" => "Public",
    "{645ff040-5081-101b-9f08-00aa002f954e}" => "Recycle Bin",
    "{d65231b0-b2f1-4857-a4ce-a8e7c6ea7d27}" => "System32 (x86)",
    "{9e52ab10-f80d-49df-acb8-4330f5687855}" => "Temporary Burn Folder",
    "{f3ce0f7c-4901-4acc-8648-d5d44b04ef8f}" => "Users Files",
    "{59031a47-3f72-44a7-89c5-5595fe6b30ee}" => "Users",
    "{a0953c92-50dc-43bf-be83-3742fed03c9c}" => "Videos",
    "{b5947d7f-b489-4fde-9e77-23780cc610d1}" => "Virtual Machines",
    "{f38bf404-1d43-42f2-9305-67de0b28fc23}" => "Windows");

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
        return %str unless (defined $sz);
		$tag = 0 if (($sz == 0) || ($cnt + $sz > $len));
		
		my $dat = substr($data,$cnt,$sz);
		my $type = unpack("C",substr($dat,2,1));
#		::rptMsg(sprintf "  Size: ".$sz."  Type: 0x%x",$type);
		
		if ($type == 0x1F) {
# System Folder 			
 			%item = parseSystemFolderEntry($dat);
 			$str .= "\\".$item{name};
 		}
 		elsif ($type == 0x2E) {
# 			probe($dat);
 			%item = parseDeviceEntry($dat);
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
# 			probe($dat);
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
# ---- Added 20180627, updated 20180702
  my $guid = sprintf "{%08x-%04x-%04x-$d4-$d5}",$d1,$d2,$d3;
  
  if (exists $folder_types{$guid}) {
  	return "CLSID_".$folder_types{$guid};
  }
  else {
  	return $guid;
  }
  
#  return sprintf "{%08x-%x-%x-$d4-$d5}",$d1,$d2,$d3;
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
        return %item unless (defined $s);
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
# Updated for Windows 10	
	elsif ($item{extver} == 0x09) {
		$jmp = 30;
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
#
#-----------------------------------------------------------
sub parseDeviceEntry {
	my $data = shift;
	my %item = ();

	my $ofs = unpack("v",substr($data,4,2));
	my $tag = unpack("V",substr($data,6,4));
	
#-----------------------------------------------------	
# DEBUG
#  ::rptMsg("parseDeviceEntry, tag = ".$tag);	
#-----------------------------------------------------		
	if ($tag == 0) {
		my $guid1 = parseGUID(substr($data,$ofs + 6,16));
		my $guid2 = parseGUID(substr($data,$ofs + 6 + 16,16));
		$item{name} = $guid1."\\".$guid2
	}
	elsif ($tag == 2) {
		$item{name} = substr($data,0x0a,($ofs + 6) - 0x0a);
		$item{name} =~ s/\00//g;
	}
	else {
    my $ver = unpack("C",substr($data,9,1));
		my $idx = unpack("C",substr($data,3,1));
		
		if ($idx == 0x80) {
			$item{name} = parseGUID(substr($data,4,16));
		}
# Version 3 = XP    
    elsif ($ver == 3) {
    	my $guid1 = parseGUID(substr($data,$ofs + 6,16));
			my $guid2 = parseGUID(substr($data,$ofs + 6 + 16,16));
			$item{name} = $guid1."\\".$guid2
    
    }
# Version 8 = Win7    
    elsif ($ver == 8) {
    	my $userlen = unpack("V",substr($data,30,4));
			my $devlen  = unpack("V",substr($data,34,4));
			my $user    = substr($data,0x28,$userlen * 2);
			$user =~ s/\00//g;
			my $dev = substr($data,0x28 + ($userlen * 2),$devlen * 2);
			$dev =~ s/\00//g;
			$item{name} = $user;	
		}
# Version unknown    
    else { 
    	$item{name} = "Device Entry - Unknown Version";
    } 
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