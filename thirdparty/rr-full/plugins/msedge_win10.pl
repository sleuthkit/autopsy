#-----------------------------------------------------------
# msedge_win10.pl
# Plugin for RegRipper 
#
# Parses Microsoft Edge (Windows App) key:
# -USRCLASS.DAT\Local Settings\Software\Microsoft\Windows\CurrentVersion\AppContainer\Storage\microsoft.microsoftedge_8wekyb3d8bbwe\MicrosoftEdge\TypedURLs
# -USRCLASS.DAT\Local Settings\Software\Microsoft\Windows\CurrentVersion\AppContainer\Storage\microsoft.microsoftedge_8wekyb3d8bbwe\MicrosoftEdge\TypedURLsTime
# -USRCLASS.DAT\Local Settings\Software\Microsoft\Windows\CurrentVersion\AppContainer\Storage\microsoft.microsoftedge_8wekyb3d8bbwe\MicrosoftEdge\TypedURLsVisitCount
# 
# On a live machine, the key path is found under HKEY_CLASSES_ROOT
#
# The script code is based on:
#    - adoberdr.pl/landesk.pl by H. Carvey
#    - iexplore.pl by E. Rye esten@ryezone.net
#      http://www.ryezone.net/regripper-and-internet-explorer-1
#
# Change history
#   20180610 - First release
#
# References
#   http://digitalforensicsurvivalpodcast.com/2017/04/11/dfsp-060-browsing-on-the-edge/
#   https://forensenellanebbia.blogspot.com/2018/06/usrclassdat-stores-more-history-than.html
#
# copyright 2018 Gabriele Zambelli <forensenellanebbia@gmail.com> | Twitter: @gazambelli
#-----------------------------------------------------------

package msedge_win10;
use strict;

my %config = (hive          => "USRCLASS\.DAT",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20180610);

sub getShortDescr { return "Get values from the user's Microsoft Edge Windows App key"; }
	
sub getDescr   {}
sub getRefs    {}
sub getHive    {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();
my (@ts,$d);

my @arr;

sub pluginmain {
	my $class = shift;
	my $hive  = shift;
	::rptMsg("msedge_win10 v.".$VERSION); 
	::rptMsg("(".getHive().") ".getShortDescr()."\n");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	# First, let's find out is Microsoft Edge was used to type any URL
	my $version;
	my $tag = 0;
	my @globalitems = ();
	my $key_path = "Local Settings\\Software\\Microsoft\\Windows\\CurrentVersion\\AppContainer\\Storage\\microsoft.microsoftedge_8wekyb3d8bbwe\\MicrosoftEdge\\TypedURLsVisitCount";
	my $key = $root_key->get_subkey($key_path);
	if (defined($key)) {
		$tag = 1;
		}
	else {
		::rptMsg($key_path." not found.");
	}

	#TypedURLs
	if ($tag) {
		my $key_path = "Local Settings\\Software\\Microsoft\\Windows\\CurrentVersion\\AppContainer\\Storage\\microsoft.microsoftedge_8wekyb3d8bbwe\\MicrosoftEdge\\TypedURLs";
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			my %vals = getKeyValues($key);
				foreach my $v (keys %vals) {
					push @arr,($v." (TypedURLs)           -> ".$vals{$v});
				}
		}
		else {
			::rptMsg("");
			::rptMsg($key_path." has no subkeys.");
		}
	}

	#TypedURLsTime
	if ($tag) {
		my $key_path = "Local Settings\\Software\\Microsoft\\Windows\\CurrentVersion\\AppContainer\\Storage\\microsoft.microsoftedge_8wekyb3d8bbwe\\MicrosoftEdge\\TypedURLsTime";
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			my %vals = getKeyValues($key);
			foreach my $v (keys %vals) {
				@ts = unpack("VV",$key->get_value($v)->get_data());
				push @arr, ($v." (TypedURLsTime)       -> ".gmtime(::getTime($ts[0],$ts[1]))." (UTC)");
			}
		}
		else {
			::rptMsg("");
			::rptMsg($key_path." has no subkeys.");
		}
	}

	#TypedURLsVisitCount
	if ($tag) {
		my $key_path = "Local Settings\\Software\\Microsoft\\Windows\\CurrentVersion\\AppContainer\\Storage\\microsoft.microsoftedge_8wekyb3d8bbwe\\MicrosoftEdge\\TypedURLsVisitCount";
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			my %vals = getKeyValues($key);
				foreach my $v (keys %vals) {
					push @arr, ($v." (TypedURLsVisitCount) -> ".$vals{$v}."\r\n");
				}
		}
		else {
			::rptMsg("");
			::rptMsg($key_path." has no subkeys.");
		}
	}
	
	if (scalar(@arr) > 0) {
	#sort items in the array
	::rptMsg("|-- \\Local Settings\\Software\\Microsoft\\Windows\\CurrentVersion\\AppContainer\\Storage\\microsoft.microsoftedge_8wekyb3d8bbwe");
	::rptMsg("|----- \\MicrosoftEdge\\TypedURLs");
	::rptMsg("|----- \\MicrosoftEdge\\TypedURLsTime");
	::rptMsg("|----- \\MicrosoftEdge\\TypedURLsVisitCount");
	::rptMsg("");
	foreach my $i (sort @arr){
		::rptMsg($i);
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