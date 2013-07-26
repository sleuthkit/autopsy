#-----------------------------------------------------------
# streams.pl
#
# copyright 2008 H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package streams;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20081124);

sub getConfig{return %config}

sub getShortDescr {
	return "Parse Streams and StreamsMRU entries";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching streams v.".$VERSION);
	    ::rptMsg("streams v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = 'Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\StreamMRU';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("streamMRU");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		
		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			foreach my $i (0..10) {
				my $data = $key->get_value($i)->get_data();
				open(FH,">",$i);
				binmode(FH);
				print FH $data;
				close(FH);		
			}
		}
		else {
			::rptMsg($key_path." has no values.");
		}
		
	}
	else {
		::rptMsg($key_path." not found.");
		::logMsg($key_path." not found.");
	}
	
}
1;