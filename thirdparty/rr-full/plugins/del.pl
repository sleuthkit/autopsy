#! c:\perl\bin\perl.exe
#-----------------------------------------------------------
# del.pl
# 
#
# Change history
#   20140807 - created
#
# References:
#   
#
# 
# copyright 2014 QAR, LLC
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
              version       => 20140807);

sub getConfig{return %config}
sub getShortDescr {
	return "Parse hive, print deleted keys/values";	
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
	::logMsg("Launching del v.".$VERSION);
	::rptMsg("del v.".$VERSION); # banner
  ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	
	my $entry_iter = $reg->get_entry_iterator;
	while (defined(my $entry = $entry_iter->get_next)) {
		next if $entry->is_allocated;
#	printf "0x%x ", $entry->get_offset;
#	print $entry->unparsed()."\n";
  	my $tag = $entry->get_tag();
  	my $str = $entry->as_string();
  	next if ($str eq "(unidentified entry)");
  	
 		if ($tag eq "vk") {
 			::rptMsg("Value: ".$str);
 		}
 		elsif ($tag eq "nk") {
 			if ($entry->get_length() > 15) {
 				my ($t0,$t1) = unpack("VV",substr($entry->get_raw_bytes(),8,16));
 				my $lw = ::getTime($t0,$t1);
 				::rptMsg("Key: ".parseDelKeyName($str)."  LW: ".gmtime($lw)." Z");
 		
 			}
 		}
 		else {}
	}
}

sub parseDelKeyName {
	my $str = shift;
	my $name_str = (split(/\s\[/,$str))[0];
	my @list = split(/\\/,$name_str);
	shift(@list);
	return join('\\',@list);
}


1;