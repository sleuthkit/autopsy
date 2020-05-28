#! c:\perl\bin\perl.exe
#-----------------------------------------------------------
# del.pl
# 
#
# Change history
#   20190506 - updated
#   20140807 - created
#
# References:
#   https://metacpan.org/pod/Parse::Win32Registry
#   https://github.com/msuhanov/regf/blob/master/Windows%20registry%20file%20format%20specification.md
#
# 
# copyright 2019 QAR, LLC
# Author: H. Carvey
#-----------------------------------------------------------
package del;
use strict;

my %config = (hive          => "All",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              category      => "deleted",
              version       => 20190506);

sub getConfig{return %config}
sub getShortDescr {
	return "Parse hive, print deleted keys/values";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

my %data_types = (0 => "REG_NONE",
                  1 => "REG_SZ",
                  2 => "REG_EXPAND_SZ",
                  3 => "REG_BINARY",
                  4 => "REG_DWORD",
                  5 => "REG_DWORD_BIG_ENDIAN",
                  6 => "REG_LINK",
                  7 => "REG_MULTI_SZ",
                  8 => "REG_RESOURCE_LIST",
                  9 => "REG_FULL_RESOURCE_DESCRIPTOR",
                  10 => "REG_RESOURCE_REQUIREMENTS_LIST",
                  11 => "REG_QWORD");

my %regkeys;

sub pluginmain {
	my $class = shift;
	my $file = shift;
	my $reg = Parse::Win32Registry->new($file);
	::logMsg("Launching del v.".$VERSION);
	::rptMsg("del v.".$VERSION); # banner
  ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	
	my $entry_iter = $reg->get_entry_iterator;
	while (defined(my $entry = $entry_iter->get_next)) {
		next if $entry->is_allocated;
#		printf "0x%x ", $entry->get_offset;
#		print $entry->unparsed()."\n";
		my $data = $entry->get_raw_bytes();
		my $len = length($data);		
		next if ($len <= 8);
	 ::rptMsg("------------- Deleted Data ------------");
# Value node header is 20 bytes, w/o name string
# Key node header is 76 bytes, w/o name string	
		if ($len >= 20) {
			my $cursor = 0;
			while ($cursor < $len) {
				if (unpack("v",substr($data,$cursor,2)) == 0x6b76) {
#					::rptMsg("Value node found at ".$cursor);
					parseValueNode($data,$cursor);
					$cursor += 0x12;
				}
				elsif (unpack("v",substr($data,$cursor,2)) == 0x6b6e) {
#					::rptMsg("Key node found at ".$cursor);
					parseKeyNode($data,$cursor);
					$cursor += 0x4a;
				}
				else {
					$cursor++;
				}
			}
					
		}
		::rptMsg($entry->unparsed());
	}
}

sub parseValueNode {
	my $data = shift;
	my $ofs  = shift;
	
	my $name_len = unpack("v",substr($data,$ofs + 0x02,2));
	my $data_len = unpack("V",substr($data,$ofs + 0x04,4));
	my $data_ofs = unpack("V",substr($data,$ofs + 0x08,4));
	my $data_type = unpack("V",substr($data,$ofs + 0x0c,4));
	my $data_flag = unpack("v",substr($data,$ofs + 0x10,2));
	
	my $name;
	if (($ofs + 0x14 + $name_len) <= length($data)) {
		$name = substr($data,$ofs + 0x14,$name_len);
		::rptMsg("Value Name: ".$name);
		::rptMsg(sprintf "Data Length: 0x%x  Data Offset: 0x%x  Data Type: ".$data_types{$data_type},$data_len,$data_ofs);
	}
}

sub parseKeyNode {
	my $data = shift;
	my $ofs  = shift;
	my $len = length($data);

	if ($len > 75 && $ofs >= 4) {
		
		my $size   = unpack("i",substr($data,$ofs - 4,4));
		$size = ($size * -1) if ($size < 0);
#		::rptMsg("Key node size = ".$size);
		
		my $type = unpack("v",substr($data,$ofs + 0x02,2));
#		::rptMsg(sprintf "Node Type = 0x%x",$type);
		
		my ($t1,$t2) = unpack("VV",substr($data,$ofs + 0x04,8));
		my $lw = ::getTime($t1,$t2);
#		::rptMsg("Key LastWrite time = ".gmtime($lw)." UTC");

		my $parent_ofs = unpack("V",substr($data,$ofs + 0x10,4));
		
		my $sk = unpack("V",substr($data,$ofs + 0x14,4));
#		::rptMsg("Number of subkeys: ".$sk);
		
		my $vals = unpack("V",substr($data,$ofs + 0x24,4));
#		::rptMsg("Number of values: ".$vals);
		
		my $len_name = unpack("V",substr($data,$ofs + 0x48,4));
#		print "Name Length: ".$len_name."\n";
		
		my $name;
		if (($ofs + 0x4c + $len_name) <= $len) {
			$name = substr($data,$ofs + 0x4c,$len_name);
			::rptMsg("Key name: ".$name);
		}
		::rptMsg("Key LastWrite time = ".gmtime($lw)." UTC");
		::rptMsg(sprintf "Offset to parent: 0x%x",$parent_ofs);
	}
}

1;