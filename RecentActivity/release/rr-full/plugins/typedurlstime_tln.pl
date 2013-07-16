#! c:\perl\bin\perl.exe
#-----------------------------------------------------------
# typedurlstime_tln.pl
# Plugin for Registry Ripper, NTUSER.DAT edition - gets the 
# TypedURLsTime values/data from Windows 8 systems
#
# Change history
#   20120613 - created
#
# References
#   http://dfstream.blogspot.com/2012/05/windows-8-typedurlstime.html
# 
# Notes:  New entries aren't added to the key until the current
#         instance of IE is terminated.
# 
# copyright 2012 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package typedurlstime_tln;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 1,
              osmask        => 22,
              version       => 20120613);

sub getConfig{return %config}
sub getShortDescr {
	return "Returns contents of Win8 user's TypedURLsTime key (TLN).";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching typedurlstime v.".$VERSION);
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;
	
	my $key_path = 'Software\\Microsoft\\Internet Explorer\\TypedURLsTime';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
#		::rptMsg("TypedURLsTime");
#		::rptMsg($key_path);
#		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			my %urls;
# Retrieve values and load into a hash for sorting			
			foreach my $v (@vals) {
				my $val = $v->get_name();
				my ($t0,$t1) = unpack("VV",$v->get_data());
				my $data = ::getTime($t0,$t1);
				my $tag = (split(/url/,$val))[1];
				$urls{$tag} = $val.":".$data;
			}
# Print sorted content to report file			
			foreach my $u (sort {$a <=> $b} keys %urls) {
				my ($val,$data) = split(/:/,$urls{$u},2);
				
				my $url;
				eval {
					$url = $root_key->get_subkey('Software\\Microsoft\\Internet Explorer\\TypedURLs')->get_value($val)->get_data();
				};
				
				if ($data == 0) {
# Do nothing					
#					::rptMsg("  ".$val." -> ".$data);
				}
				else {
#					::rptMsg("  ".$val." -> ".gmtime($data)." Z (".$url.")");
					::rptMsg($data."|REG|||TypedURLsTime ".$val." (".$url.")");
				}
			}
		}
		else {
#			::rptMsg($key_path." has no values.");
		}
	}
	else {
#		::rptMsg($key_path." not found.");
	}
}

1;