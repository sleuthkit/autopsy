#-----------------------------------------------------------
# runmru.pl
# Plugin for Registry Ripper, NTUSER.DAT edition - gets the 
# RunMru values 
#
# Change history
#   20201005 - MITRE update
#   20200525 - updated date output format
#   20080324 - created
#
# References
#
# 
# copyright 2020 Quantum Analytics Research, LLC
# author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package runmru;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              category      => "execution",
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              MITRE         => "T1204",
              version       => 20201005);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets contents of user's RunMRU key";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching runmru v.".$VERSION);
	::rptMsg("runmru v.".$VERSION); 
    ::rptMsg("(".getHive().") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = 'Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\RunMRU';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("RunMru");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".::format8601Date($key->get_timestamp())."Z");
		my @vals = $key->get_list_of_values();
		my %runvals;
		my $mru;
		if (scalar(@vals) > 0) {
			foreach my $v (@vals) {
				$runvals{$v->get_name()} = $v->get_data() unless ($v->get_name() eq "MRUList");
				$mru = $v->get_data() if ($v->get_name() eq "MRUList");
			}
			::rptMsg("MRUList = ".$mru);
			foreach my $r (sort keys %runvals) {
				::rptMsg($r."   ".$runvals{$r});
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