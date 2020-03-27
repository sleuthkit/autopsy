#-----------------------------------------------------------
# adoberdr.pl
# Plugin for Registry Ripper 
# Parse Adobe Reader MRU keys
#
# Change history
#   20150717 - updated IAW Jason Hale's blog post (see ref), added
#              .csv output format
#   20120716 - added version 10.0 to @versions
#   20100218 - added checks for versions 4.0, 5.0, 9.0
#   20091125 - modified output to make a bit more clear
#
# References
#   http://dfstream.blogspot.com/2015/07/adobe-readers-not-so-crecentfiles.html
#
# Note: LastWrite times on c subkeys will all be the same,
#       as each subkey is modified as when a new entry is added
#
# copyright 2015 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package adoberdr;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20150717);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets user's Adobe Reader cRecentFiles values";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching adoberdr v.".$VERSION);
	::rptMsg("adoberdr v.".$VERSION); # banner
  ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;
	::rptMsg("Adoberdr v.".$VERSION);
# First, let's find out which version of Adobe Acrobat Reader is installed
	my $version;
	my $tag = 0;
	my @versions = ("4\.0","5\.0","6\.0","7\.0","8\.0","9\.0","10\.0","11\.0","12\.0","13\.0","14\.0", "DC");
	foreach my $ver (@versions) {		
		my $key_path = "Software\\Adobe\\Acrobat Reader\\".$ver."\\AVGeneral\\cRecentFiles";
		if (defined($root_key->get_subkey($key_path))) {
			$version = $ver;
			$tag = 1;
		}
	}
	
	if ($tag) {
		::rptMsg("Adobe Acrobat Reader version ".$version." located."); 
		my $key_path = "Software\\Adobe\\Acrobat Reader\\".$version."\\AVGeneral\\cRecentFiles";   
		my $key = $root_key->get_subkey($key_path);
		if ($key) {
			::rptMsg($key_path);
			::rptMsg("");
#			::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
			my %arkeys;
			my @subkeys = $key->get_list_of_subkeys();
			if (scalar @subkeys > 0) {
				foreach my $s (@subkeys) {
					my $num = $s->get_name();
					my $data = $s->get_value('sDI')->get_data();
					$num =~ s/^c//;
					$arkeys{$num}{lastwrite} = $s->get_timestamp();
					$arkeys{$num}{data} = $data;
					
					eval {
						$arkeys{$num}{tDIText} = $s->get_value('tDIText')->get_data();
					};
					
					eval {
						$arkeys{$num}{sDate} = $s->get_value('sDate')->get_data();
						$arkeys{$num}{sDate} =~ s/^D://;
					};
					
					eval {
						$arkeys{$num}{uFileSize} = $s->get_value('uFileSize')->get_data();
					};
					
					eval {
						$arkeys{$num}{uPageCount} = $s->get_value('uPageCount')->get_data();
					};
					
					
				}
				::rptMsg("Most recent PDF opened: ".gmtime($arkeys{1}{lastwrite})." (UTC)");
				::rptMsg("Key name,file name,sDate,uFileSize,uPageCount");
				foreach my $k (sort {$a <=> $b} keys %arkeys) {
					::rptMsg("c".$k.',"'.$arkeys{$k}{data}.'",'.$arkeys{$k}{sDate}.",".$arkeys{$k}{uFileSize}.",".$arkeys{$k}{uPageCount});
				}
			}
			else {
				::rptMsg($key_path." has no subkeys.");
			}
		}
		else {
			::rptMsg("Could not access ".$key_path);
		}
	}
	else {
		::rptMsg("Adobe Acrobat Reader version not found.");
	}
}

1;