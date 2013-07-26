#-----------------------------------------------------------
# liveContactsGUID.pl
#
# Change history
#   20110221 [pbo] % created
#   20110830 [fpi] + banner, no change to the version number
#
# References
#
# (C) 2011 Pierre-Yves Bonnetain - B&A Consultants
# expert-judiciaire@ba-consultants.fr
#-----------------------------------------------------------
package liveContactsGUID;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20110221);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets user Windows Live Messenger GUIDs";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching liveContactsGUID v." . $VERSION);
    ::rptMsg("liveContactsGUID v.".$VERSION); # 20110830 [fpi] + banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # 20110830 [fpi] + banner 
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = "Software\\Microsoft\\Windows Live Contacts\\Database";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		
		my @subvals = $key->get_list_of_values();
		if (scalar(@subvals) > 0) {
		    foreach my $valeur (@subvals) {
			::rptMsg($valeur->get_data . " : " . $valeur->get_name);
		    }
		} else {
		    ::rptMsg($key_path." has no subvalues.");
		    ::logMsg($key_path." has no subvalues.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
		::logMsg($key_path." not found.");
	}
}

1;
