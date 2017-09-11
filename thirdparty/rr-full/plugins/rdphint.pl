#-----------------------------------------------------------
# rdphint.pl - http://www.regripper.net/
# Gathers servers logged onto via RDP and last successful username
#
# by Brandon Nesbit, Trustwave
#-----------------------------------------------------------
package rdphint;
use strict;

my %config = (hive => "NTUSER",
              osmask => 22,
              hasShortDescr => 1,
              hasDescr => 0,
              hasRefs => 0,
              version => 20090715);

sub getConfig{return %config}
sub getShortDescr { return "Gets hosts logged onto via RDP and the Domain\\Username";}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching rdphint v.".$VERSION);
	::rptMsg("rdphint v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key_path = 'Software\\Microsoft\\Terminal Server Client\\Servers';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("Terminal Server Client\\Servers");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar(@subkeys) > 0) {
			foreach my $s (@subkeys) {
				my $path;
				eval {
					$path = $s->get_value("UsernameHint")->get_data();
				};
				::rptMsg("");
				::rptMsg("Hostname: ".$s->get_name());
				::rptMsg("Domain/Username: ".$path);
				::rptMsg("LastWrite: ".gmtime($s->get_timestamp())." (UTC)");
				::rptMsg("");
			}
		}
		else {
			::rptMsg($key_path." has no subkeys.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1;