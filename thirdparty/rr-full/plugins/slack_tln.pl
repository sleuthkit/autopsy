#! c:\perl\bin\perl.exe
#-----------------------------------------------------------
# slack_tln.pl
# 
#
# Change history
#   20190506 - slack_tln.pl created
#   20180926 - original slack.pl created
#
# References:
#   
#
# 
# copyright 2019 QAR, LLC
# Author: H. Carvey
#-----------------------------------------------------------
package slack_tln;
use strict;

my %config = (hive          => "All",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              category      => "slack",
              version       => 20190506);

sub getConfig{return %config}
sub getShortDescr {
	return "Parse hive, print slack space, retrieve keys/values";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

my %regkeys;

sub pluginmain {
	my $class = shift;
	my $file = shift;
	my $reg = Parse::Win32Registry->new($file);
	::logMsg("Launching slack v.".$VERSION);
	::rptMsg("slack v.".$VERSION); 
  ::rptMsg("(".getHive().") ".getShortDescr()."\n"); 
	
	my $block_iter = $reg->get_block_iterator;
	while (my $block = $block_iter->get_next) {
		my $entry_iter = $block->get_entry_iterator;
		while (my $entry = $entry_iter->get_next) {
			if ($entry->is_allocated()) {
				
				my $data = $entry->get_raw_bytes();
#				::rptMsg("------------- Slack Data ------------");
# Value node header is 20 bytes, w/o name string
# Key node header is 76 bytes, w/o name string	
				my $len = length($data);		
				if ($len >= 74) {
					my $cursor = 0;
					while ($cursor < $len) {
						if (unpack("v",substr($data,$cursor,2)) == 0x6b6e) {
#							::rptMsg("Key node found at ".$cursor);
							parseKeyNode($data,$cursor);
							$cursor += 74;
						}
						else {
							$cursor++;
						}
					}
				}
#				::rptMsg($entry->unparsed());
			}
		}
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
		
		my $sk = unpack("V",substr($data,$ofs + 0x14,4));
#		::rptMsg("Number of subkeys: ".$sk);
		
		my $vals = unpack("V",substr($data,$ofs + 0x24,4));
#		::rptMsg("Number of values: ".$vals);
		
		my $len_name = unpack("V",substr($data,$ofs + 0x48,4));
#		print "Name Length: ".$len_name."\n";
		
		my $name;
		if (($ofs + 0x4c + $len_name) <= $len) {
			$name = substr($data,$ofs + 0x4c,$len_name);
#			::rptMsg("Key name: ".$name);
		}
		
		::rptMsg($lw."|||| Key found in hive slack: ".$name);
	}
}


1;