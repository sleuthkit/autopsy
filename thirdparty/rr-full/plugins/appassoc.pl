#-----------------------------------------------------------
# appassoc.pl
#  
# Change history
#  20190513 - created
#
# References
#  https://twitter.com/EricRZimmerman/status/916422135987474433
# 
# copyright 2017 H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package appassoc;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20190513);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets contents of user's ApplicationAssociationToasts key";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching appassoc v.".$VERSION);
	::rptMsg("appassoc v.".$VERSION); # banner
  ::rptMsg("- ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = 'Software\\Microsoft\\Windows\\CurrentVersion\\ApplicationAssociationToasts';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			::rptMsg("LastWrite: ".gmtime($key->get_timestamp()));
			::rptMsg("");
			foreach my $v (@vals) {
				::rptMsg($v->get_name());
			}
		}
		else {
			::rptMsg($key_path." has no values.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

1;