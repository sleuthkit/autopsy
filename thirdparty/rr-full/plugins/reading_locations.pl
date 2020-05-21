#-----------------------------------------------------------
# reading_locations.pl
# Plugin to get MS Office 2013 Reading Locations' subkey data from NTUSER.DAT 
#   
# Change history
#   20140130 - created
#   20190211 - added "paragraphID" int to hex conversion
#
# References
#   http://dfstream.blogspot.com/2014/01/ms-word-2013-reading-locations.html
#
# Author: Jason Hale <ntexaminer@gmail.com>
#-----------------------------------------------------------
package reading_locations;
use strict;

my %config = (hive          => "NTUSER\.DAT",
							#hivemask      => 32,
							output        => "report",
							category      => "User Activity",
              osmask        => 60, 
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20140130);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets user's MS Word 2013 Reading Locations";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching reading_locations v.".$VERSION);
	::rptMsg("reading_locations v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = 'Software\\Microsoft\\Office\\15.0\\Word\\Reading Locations';
	my $key;
		if ($key = $root_key->get_subkey($key_path)) {
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar(@subkeys) > 0) {
			foreach my $s (@subkeys) {
				my $name = $s->get_name();
				my $lw   = $s->get_timestamp();
				::rptMsg($name);
				::rptMsg("LastWrite: ".gmtime($lw)." UTC");
				
				eval {
					my $dt = $s->get_value("Datetime")->get_data();
					::rptMsg("Datetime: ".$dt);
				};
				
				eval {
					my $fp = $s->get_value("File Path")->get_data();
					::rptMsg("File Path: ".$fp);
				};
				
				eval {
					my $p = $s->get_value("Position")->get_data();
					my @ps = split(' ', $p);
					my $paraid = sprintf("%X", $ps[0]);
					::rptMsg("Position: ".$p." (ParagraphID: ".$paraid.")");
				};
				::rptMsg("");
			}
		}
		else {
			::rptMsg($key_path." key has no subkeys\.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

1;