#-----------------------------------------------------------
# tsclient.pl
# Plugin for Registry Ripper
#
# Change history
#
#
# References
#   http://support.microsoft.com/kb/312169
# 
# copyright 2008 H. Carvey
#-----------------------------------------------------------
package tsclient;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 0,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20080324);

sub getConfig{return %config}
sub getShortDescr {
	return "Displays contents of user's Terminal Server Client\\Default key";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching tsclient v.".$VERSION);
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = 'Software\\Microsoft\\Terminal Server Client\\Default';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("TSClient");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			my %mrus;
			foreach my $v (@vals) {
				my $val = $v->get_name();
				my $data = $v->get_data();
				my $tag = (split(/MRU/,$val))[1];
				$mrus{$tag} = $val.":".$data;
			}
			foreach my $u (sort {$a <=> $b} keys %mrus) {
				my ($val,$data) = split(/:/,$mrus{$u},2);
				::rptMsg("  ".$val." -> ".$data);
			}
		}
		else {
			::rptMsg($key_path." has no values.");
			::logMsg($key_path." has no values.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
		::logMsg($key_path." not found.");
	}
}

1;