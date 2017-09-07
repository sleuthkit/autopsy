#-----------------------------------------------------------
# renocide.pl
# Plugin to assist in the detection of malware per MMPC
#   blog post (References, below)
#
# Change History:
#   20130425 - added alertMsg() functionality
#   20110309 - created
#
# References
#   http://www.microsoft.com/security/portal/Threat/Encyclopedia/Entry.aspx?Name=Win32/Renocide
#
# copyright 2013 Quantum Analytics Research, LLC
#-----------------------------------------------------------
package renocide;
use strict;

my %config = (hive          => "Software",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20130425);

sub getConfig{return %config}

sub getShortDescr {
	return "Check for Renocide malware";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching renocide v.".$VERSION);
	::rptMsg("renocide v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = "Microsoft\\DRM\\amty";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("renocide");
		::rptMsg($key_path);
		::rptMsg("LastWrite: ".gmtime($key->get_timestamp()));
		::rptMsg("");
		::rptMst($key_path." found; possible Win32\\Renocide infection\.");
		::alertMsg($key_path." found; possible Win32\\Renocide infection\.");
		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			foreach my $v (@vals) {
				::rptMsg(sprintf "%-12s %-20s",$v->get_name(),$v->get_data());
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