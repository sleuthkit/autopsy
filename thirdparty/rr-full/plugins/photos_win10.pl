#-----------------------------------------------------------
# photos_win10.pl
# Plugin for RegRipper 
#
# Parses Microsoft Photos (Windows App) key:
# - USRCLASS.DAT\Local Settings\Software\Microsoft\Windows\CurrentVersion\AppModel\SystemAppData\Microsoft.Windows.Photos_8wekyb3d8bbwe
# 
# On a live machine, the key path is:
# - HKEY_CLASSES_ROOT\Local Settings\Software\Microsoft\Windows\CurrentVersion\AppModel\SystemAppData\Microsoft.Windows.Photos_8wekyb3d8bbwe
#
# The script was tested on Windows 10 against:
#  - Microsoft.Windows.Photos_2017.37071.16410.0_x64__8wekyb3d8bbwe
#  - Microsoft.Windows.Photos_2018.18022.15810.1000_x64__8wekyb3d8bbwe
#
# The script code is based on:
#    - adoberdr.pl/landesk.pl/photos.pl by H. Carvey
#    - iexplore.pl by E. Rye esten@ryezone.net
#      http://www.ryezone.net/regripper-and-internet-explorer-1
#
# Change history
#   20180610 - First release
#
# To Dos
#   Extract value name "Link"
#
# References
#   https://forensenellanebbia.blogspot.com/2018/06/usrclassdat-stores-more-history-than.html
#   https://df-stream.com/2013/03/windows-8-tracking-opened-photos/
#
# copyright 2018 Gabriele Zambelli <forensenellanebbia@gmail.com> | Twitter: @gazambelli
#-----------------------------------------------------------

package photos_win10;
use strict;

my %config = (hive          => "USRCLASS\.DAT",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20180610);

sub getShortDescr { return "Get values from the user's Microsoft Photos Windows App key"; }
	
sub getDescr   {}
sub getRefs    {}
sub getHive    {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();
my (@ts,$d);

sub pluginmain {
	my $class = shift;
	my $hive  = shift;
	::rptMsg("photos_win10 v.".$VERSION); 
	::rptMsg("(".getHive().") ".getShortDescr()."\n");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	# First, let's find out which version of Microsoft Photos is installed
	my $version;
	my $tag = 0;
	my @globalitems = ();
	my $key_path = "Local Settings\\Software\\Microsoft\\Windows\\CurrentVersion\\AppModel\\SystemAppData\\Microsoft.Windows.Photos_8wekyb3d8bbwe\\Schemas";
	my $key = $root_key->get_subkey($key_path);
	if (defined($key)) {
		my %vals = getKeyValues($key);
		foreach my $v (keys %vals) {
			if ($v =~ m/^PackageFullName/) {
				#Version of Microsoft Photos App
				::rptMsg($key_path);
				::rptMsg("  PackageFullName => ".($vals{$v}));
				$tag = 1;
			}
		}
	}
	else {
	::rptMsg($key_path." not found.");
	}


	#Print SubKey, Last Write Time, Viewed Picture
	if ($tag) {
		my $key_path = "Local Settings\\Software\\Microsoft\\Windows\\CurrentVersion\\AppModel\\SystemAppData\\Microsoft.Windows.Photos_8wekyb3d8bbwe\\PersistedStorageItemTable\\ManagedByApp";
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			my %vals = getKeyValues($key);
			if (scalar(keys %vals) > 0) {
				foreach my $v (keys %vals) {
					::rptMsg("\t".$v." -> ".$vals{$v});
				}
			}
			my @sk = $key->get_list_of_subkeys();
			if (scalar(@sk) > 0) {
				::rptMsg("");
				::rptMsg($key_path);
				foreach my $s (@sk) {
					::rptMsg("");
					::rptMsg(" ".$s->get_name());
					::rptMsg("   KeyLastWrite   : ".gmtime($s->get_timestamp())." (UTC)");
					my %vals = getKeyValues($s);
					foreach my $v (keys %vals) {
						if ($v =~ m/^Metadata/) {
							#Metadata contains the path to the viewed picture
							::rptMsg("   Metadata       : ".$vals{$v});
						}
						if ($v =~ m/^LastUpdatedTime/) {
							#LastUpdatedTime
							@ts = unpack("VV",$s->get_value($v)->get_data());
							::rptMsg("   LastUpdatedTime: ".gmtime(::getTime($ts[0],$ts[1]))." (UTC)");
						}
					}
				}
			}
			else {
				::rptMsg("");
				::rptMsg($key_path." has no subkeys.");
			}
		}
		else {
			::rptMsg($key_path." not found.");
			::logMsg($key_path." not found.");
		}
	}

	#Print Viewed Picture | Write Time
	if ($tag) {
		my $key_path = "Local Settings\\Software\\Microsoft\\Windows\\CurrentVersion\\AppModel\\SystemAppData\\Microsoft.Windows.Photos_8wekyb3d8bbwe\\PersistedStorageItemTable\\ManagedByApp";
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			my %vals = getKeyValues($key);
			my @sk = $key->get_list_of_subkeys();
			if (scalar(@sk) > 0) {
			::rptMsg("");
			::rptMsg("");
			::rptMsg("## Microsoft Photos (Windows App): Recent Files ## (Tab-separated values)");
			::rptMsg("");
			my @sitems; #create new array for sorted items
			foreach my $s (@sk) {
					my %vals = getKeyValues($s);
					foreach my $v (keys %vals) {
						if ($v =~ m/^Metadata/) {
							if ($vals{$v} =~ m/^. /) { #find single character followed by a space at the beginning of the string
								my $sd; #single digit
								$sd = substr($vals{$v},0,1);
								$vals{$v} =~ s/^. / $sd /g; #change from "^\. " to "^ \. ", Microsoft Photos 2018 prepends a number in front of the path
								push @sitems, ($vals{$v}."\t".gmtime($s->get_timestamp()));
							}
							elsif ($vals{$v} =~ m/^.. /) { #find two characters followed by a space at the beginning of the string
								push @sitems, ($vals{$v}."\t".gmtime($s->get_timestamp()));
							}
							else {
								::rptMsg($vals{$v}."\t KeyLastWrite: ".gmtime($s->get_timestamp())." (UTC)");
								}
						}
					}
				}
				if (scalar(@sitems) > 0) {
					#sort alphabetically the items in the array
					::rptMsg("Metadata\tKeyLastWrite (UTC)"); #print header row
					foreach my $item (sort @sitems){
						::rptMsg($item);
					}
				}
			}
			::rptMsg("");
		}
		else {
			::rptMsg($key_path." not found.");
			::logMsg($key_path." not found.");
		}
	}	
}

sub getKeyValues {
	my $key = shift;
	my %vals;       
	my @vk = $key->get_list_of_values();
	if (scalar(@vk) > 0) {
		foreach my $v (@vk) {
			next if ($v->get_name() eq "" && $v->get_data() eq "");
			$vals{$v->get_name()} = $v->get_data();
		}
	}
	else {  
	}
	return %vals;
}

1;