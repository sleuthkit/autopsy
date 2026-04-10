#-----------------------------------------------------------
# adobe.pl
# Plugin for Registry Ripper 
# Parse Adobe Reader MRU keys
#
# Change history
#   20200903 - updates
#   20200622 - Updated code to check for app version
#   20200620 - renamed "adoberdr.pl" to "adobe.pl", to capture Acrobat data, as well
#   20200520 - minor updates
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
# copyright 2020 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package adobe;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output        => "report",
              category      => "user activity",
              MITRE         => "",
              version       => 20200803);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets user's Adobe app cRecentFiles values";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching adobe v.".$VERSION);
	::rptMsg("adobe v.".$VERSION); # banner
  ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); 
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;
	::rptMsg("adobe v.".$VERSION);
	
	my @apps = ("Adobe Acrobat","Acrobat Reader");
	foreach my $app (@apps) {
# First, determine app version
		my $version;
		my $tag = 0;
		my $path = "Software\\Adobe\\".$app;
		if (my $key = $root_key->get_subkey($path)) {
			my @subkeys = $key->get_list_of_subkeys();
			if (scalar @subkeys > 0) {
				foreach my $s (@subkeys) {
					my $name = $s->get_name();
					if (defined($root_key->get_subkey($path."\\".$name."\\AVGeneral\\cRecentFiles"))) {
						$version = $name;
					}
				}
			}
		}
	
#		::rptMsg($app." version ".$version." located."); 
		my $key_path = "Software\\Adobe\\".$app."\\".$version."\\AVGeneral\\cRecentFiles";   
		my $key = $root_key->get_subkey($key_path);
		if ($key) {
			::rptMsg($key_path);
#			::rptMsg("");
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
#				::rptMsg("Most recent PDF opened: ".gmtime($arkeys{1}{lastwrite})." (UTC)");
				::rptMsg("Key name,file name,sDate,uFileSize,uPageCount");
				foreach my $k (sort {$a <=> $b} keys %arkeys) {
					::rptMsg("c".$k.",".$arkeys{$k}{data}.",".$arkeys{$k}{sDate}.",".$arkeys{$k}{uFileSize}.",".$arkeys{$k}{uPageCount});
				}
			}
			else {
				::rptMsg($key_path." has no subkeys.");
			}
		}
		else {
			::rptMsg("Could not access ".$key_path);
		}
		::rptMsg("");
	}
}

1;