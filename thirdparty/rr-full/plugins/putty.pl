#-----------------------------------------------------------
# putty.pl
#   Extracts the saved SshHostKeys for PuTTY
#
# Change history
#   20200924 - MITRE update
#   20200515 - date output format updated
#   20110830 - created
#
# References
#
# copyright 2020 Quantum Analytics Research, LLC
# author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package putty;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1021",
              category      => "lateral movement",
			  output		=> "report",
              version       => 20200924);

my $VERSION = getVersion();

sub getDescr {}
sub getConfig {return %config}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}
sub getShortDescr {
	return "Extracts the saved SshHostKeys for PuTTY.";
}
sub getRefs {}

sub pluginmain {
	my $class = shift;
	my $hive = shift;

	::logMsg("Launching putty v.".$VERSION);
	::rptMsg("putty v.".$VERSION); 
	::rptMsg("(".getHive().") ".getShortDescr());
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");	

	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key;
	my $key_path = "Software\\SimonTatham\\PuTTY\\SshHostKeys";

	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("PuTTY");
		::rptMsg($key_path);
		::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");

		my %keys;
		my @vals = $key->get_list_of_values();

		if (scalar(@vals) > 0) {
			foreach my $v (@vals) {
				$keys{$v->get_name()} = $v->get_data();
				::rptMsg($v->get_name()." -> ".$v->get_data());
			}
		} 
		else {
			::rptMsg($key_path." has no values.");
		}
	} 
	else {
		::rptMsg($key_path." not found.");
	}
	::rptMsg("");
}

1;
