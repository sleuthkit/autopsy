#-----------------------------------------------------------
# shellbags_test.pl
#
#
# License: GPL v3 
# copyright 2012 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package shellbags_test;
use strict;

require 'shellitems.pl';

my %config = (hive          => "USRCLASS\.DAT",
							hivemask      => 32,
							output        => "report",
							category      => "User Activity",
              osmask        => 20, #Vista, Win7/Win2008R2
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20130528);

sub getConfig{return %config}

sub getShortDescr {
	return "Shell/BagMRU traversal in XP/Win7 user hives";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

my %item = ();
my $XP   = 0;
my $root_key;

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching shellbags_test v.".$VERSION);
	::rptMsg("shellbags_test v.".$VERSION); # banner
  ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner

	my $reg = Parse::Win32Registry->new($hive);
	$root_key = $reg->get_root_key;
	
	my %paths = ("Win7" => "Local Settings\\Software\\Microsoft\\Windows\\Shell\\BagMRU",
	             "XP"   => "Software\\Microsoft\\Windows\\ShellNoRoam\\BagMRU");
	my $key;
	
	if ($key = $root_key->get_subkey($paths{"Win7"})) {
		setup($key);
	}
	elsif ($key = $root_key->get_subkey($paths{"XP"})) {
		$XP = 1;
		setup($key);
	}
}

sub setup {
	my $key = shift;
	($XP == 1) ? ($item{path} = "ShellNoRoam\\BagMRU\\") : ($item{path} = "Shell\\BagMRU\\");
	$item{name} = "Desktop\\";
# Print header info
	::rptMsg(sprintf "%-20s |%-20s | %-20s | %-20s | %-20s |Resource","MRU Time","Modified","Accessed","Created","Zip_Subfolder");
	::rptMsg(sprintf "%-20s |%-20s | %-20s | %-20s | %-20s |"."-" x 12,"-" x 12,"-" x 12,"-" x 12,"-" x 12,"-" x 12);
	traverse($key,\%item);
}

sub traverse {
	my $key = shift;
	my $parent = shift;
	
	my %item = ();
  my @vals = $key->get_list_of_values();
   
  my %values;
  foreach my $v (@vals) {
  	my $name = $v->get_name();
  	$values{$name} = $v->get_data();
  }
  
  my $mru;
  if (exists $values{MRUListEx}) {
  	$mru = unpack("V",substr($values{MRUListEx},0,4));
  }
  delete $values{MRUListEx};
 	
 	foreach my $v (sort {$a <=> $b} keys %values) {
 		next unless ($v =~ m/^\d/);
		
		my $nodeslot = "";
		eval {
			$nodeslot = $key->get_subkey($v)->get_value("NodeSlot")->get_data();
		};
		
 		my $type = unpack("C",substr($values{$v},2,1));
		my $size = unpack("v",substr($values{$v},0,2));
#		probe($values{$v});
		
# Need to first check to see if the parent of the item was a zip folder
# and if the 'zipsubfolder' value is set to 1		
		if (exists ${$parent}{zipsubfolder} && ${$parent}{zipsubfolder} == 1) {
			if ($XP == 0) {
 				%item = parseZipSubFolderItem($values{$v});
 				$item{zipsubfolder} = 1;
 			}
 		} 
 		elsif (length($values{$v}) == 22 && $type != 0x47) {
 			$item{name} = parseGUID(substr($values{$v},4,16));
 		}
 		elsif (substr($values{$v},0x0d,2) =~ m/\x3a\x3a/){
 			%item = parseXPShellDeviceItem($values{$v});
		}
 		elsif ($type == 0x00) {
# Variable/Property Sheet 			
 			%item = parseVariableEntry($values{$v});	
 		}
 		elsif ($type == 0x01) {
#  			
 			%item = parse01ShellItem($values{$v});
 		}
 		elsif ($type == 0x1F) {
# System Folder 			
 			%item = parseSystemFolderEntry($values{$v});
 		}
 		elsif ($type == 0x2e) {
# Device
			%item = parseDeviceEntry($values{$v}); 		
 		}
 		elsif ($type == 0x2F) {
# Volume (Drive Letter) 			
 			%item = parseDriveEntry($values{$v});
 
 		}
 		elsif ($type == 0xc3 || $type == 0x41 || $type == 0x42 || $type == 0x46 || $type == 0x47) {
# Network stuff
			my $id = unpack("C",substr($values{$v},3,1));
			if ($type == 0xc3 && $id != 0x01) {
				%item = parseNetworkEntry($values{$v});
			}
			else {
				%item = parseNetworkEntry($values{$v}); 
			}
 		}
 		elsif ($type == 0x31 || $type == 0x32 || $type == 0xb1 || $type == 0x74) {
# Folder or Zip File			
 			%item = parseFolderEntry($values{$v}); 
# 			if (exists $item{mft_rec_num}) {
# 				print "MFT record number  : ".$item{mft_rec_num}."\n";
# 				print "MFT sequence number: ".$item{mft_seq_num}."\n";
# 			}
# 			probe($values{$v});
 		}
 		elsif ($type == 0x35) {
 			%item = parseFolderEntry2($values{$v});
 		}
 		elsif ($type == 0x64 || $type == 0x65 || $type == 0x69) {
 			%item = parseType64Item($values{$v});
 		} 
 		elsif ($type == 0x71) {
# Control Panel
			if ($size == 0x1e) {
				%item = parseControlPanelEntry($values{$v});
			} 			
			else {
				$item{name} = parseGUID(substr($values{$v},0xe,16));
			}
 		}
 		elsif ($type == 0x61) {
# URI type
			%item = parseURIEntry($values{$v});		
 		}
 		elsif ($type == 0x53) {
 			%item = parseTypex53($values{$v});	
 		}
 		else {
# Unknown type
			$item{name} = sprintf "Unknown Type (0x%x)",$type; 
#			probe($values{$v});	
 		}
 		
 		if ($type == 0x32) {
 			if (lc($item{name}) =~ m/\.zip$/) {
 				$item{zipsubfolder} = 1;
 			}
 		}
# for debug purposes
# 		$item{name} = $item{name}."[".$v."]";
#    ::rptMsg(${$parent}{path}.$item{name}); 	

		if ($mru != 4294967295 && ($v == $mru)) {
 			$item{mrutime} = $key->get_timestamp();
 			$item{mrutime_str} = $key->get_timestamp_as_string();
 			$item{mrutime_str} =~ s/T/ /;
 			$item{mrutime_str} =~ s/Z/ /;
 		}
 		else {
 			$item{mrutime_str} = "";
 		}
	
 		my ($m,$a,$c,$o) = "";
 		(exists $item{mtime_str} && $item{mtime_str} ne "0") ? ($m = $item{mtime_str}) : ($m = "");
 		(exists $item{atime_str} && $item{atime_str} ne "0") ? ($a = $item{atime_str}) : ($a = "");
 		(exists $item{ctime_str} && $item{ctime_str} ne "0") ? ($c = $item{ctime_str}) : ($c = "");
 		(exists $item{datetime} && $item{datetime} ne "N/A") ? ($o = $item{datetime}) : ($o = "");
 		
 		if ($item{name} eq "" || $item{name} =~ m/\\$/) {
 			
 		}
 		else {
 			$item{name} = $item{name}."\\";
 		}
 		$item{name} = ${$parent}{name}.$item{name};
 		$item{path} = ${$parent}{path}.$v."\\";
 		
 		my $resource = $item{name};
 		if (exists $item{filesize}) {
 			$resource .= " [".$item{filesize}."]";
 		}
 		
 		my $str = sprintf "%-20s |%-20s | %-20s | %-20s | %-20s |".$resource." [".$item{path}."]",$item{mrutime_str},$m,$a,$c,$o;
 		::rptMsg($str);

# For XP, check NodeSlot value
		if ($XP == 1 && $nodeslot ne "") {
			my %itempos = getItemPos($nodeslot);
			if (scalar(keys %itempos) > 0) {
				foreach my $name (keys %itempos) {
					my $n = $name;
					$n .= " [".$itempos{$name}{size}."]" if ($itempos{$name}{size} ne "");
					$n .= " [ShellNoRoam\\Bags\\".$nodeslot."\\Shell\\".$itempos{$name}{itempos}."]";
					my $str = sprintf "%-20s |%-20s | %-20s | %-20s | %-20s |  ","",$itempos{$name}{mtime_str},$itempos{$name}{atime_str},$itempos{$name}{ctime_str},"";
 					::rptMsg($str.$n);	
				}
			}
		}

 		traverse($key->get_subkey($v),\%item);
 	}
}

#-----------------------------------------------------------
# getItemPos()
#-----------------------------------------------------------
sub getItemPos {
	my $nodeslot = shift;
	my %item = ();
	my $key_path = "Software\\Microsoft\\Windows\\ShellNoRoam\\Bags\\".$nodeslot."\\Shell";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			foreach my $v (@vals) {
				my $name = $v->get_name();
				if ($name =~ m/^ItemPos/) {
					%item = parseBagEntry($v->get_data(),$name);
				}
			}
		}
	}
	else {
		::rptMsg($key_path." not found\.");
	}
	return %item;
}

#-----------------------------------------------------------
# parseBagEntry()
#-----------------------------------------------------------
sub parseBagEntry {
	my $data = shift;
	my $name = shift;
	my $ofs = 24;
	my $len = length($data);
	my %bag = ();
	
	while ($ofs < $len) {
		my %item = ();
		my $sz = unpack("v",substr($data,$ofs,2));
		my $dat = substr($data,$ofs,$sz);
		my $type = unpack("C",substr($dat,2,1));
		
		if ($type == 0x1f) {
			%item = parseSystemBagItem($dat);
			$bag{$item{name}}{itempos} = $name;
			$bag{$item{name}}{mtime_str} = "";
			$bag{$item{name}}{atime_str} = "";
			$bag{$item{name}}{ctime_str} = "";
			$bag{$item{name}}{size} = "";
		}
		elsif ($type == 0x31 || $type == 0x32 || $type == 0x3a) {	
			%item = parseFolderItem($dat);
			$bag{$item{name}}{itempos} = $name;
 			(exists $item{mtime_str} && $item{mtime_str} ne "0") ? ($bag{$item{name}}{mtime_str} = $item{mtime_str}) : ($bag{$item{name}}{mtime_str} = "");
 			(exists $item{atime_str} && $item{atime_str} ne "0") ? ($bag{$item{name}}{atime_str} = $item{atime_str}) : ($bag{$item{name}}{atime_str} = "");
 			(exists $item{ctime_str} && $item{ctime_str} ne "0") ? ($bag{$item{name}}{ctime_str} = $item{ctime_str}) : ($bag{$item{name}}{ctime_str} = "");
			$bag{$item{name}}{size} = $item{size};
		}
		else {
			
		}
		$ofs += $sz + 8;
	}
	return %bag;
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
	$ofs = $ofs + $cnt + 2;
	
	my @m = unpack("vv",substr($data,$ofs,4));
	($item{ctime_str},$item{ctime}) = convertDOSDate($m[0],$m[1]);
	$ofs += 4;
	my @m = unpack("vv",substr($data,$ofs,4));
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
	else {}
	
	$ofs += $jmp;
	
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

1;