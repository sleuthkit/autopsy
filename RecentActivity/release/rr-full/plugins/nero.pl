#-----------------------------------------------------------
# nero.pl
#   **Very Beta!  Based on one sample hive file only!
#
# Change history
#   20100218 - created
#
# References
#    
#
# copyright 2010 Quantum Analytics Research, LLC
#-----------------------------------------------------------
package nero;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20100218);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets contents of Ahead\\Nero Recent File List subkeys";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

my @nerosubkeys = ("Cover Designer","FlmgPlg","Nero PhotoSnap",
                   "NSPluginMgr","PhotoEffects","XlmgPlg");

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	my %hist;
	::logMsg("Launching nero v.".$VERSION);
	::rptMsg("nero v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = 'Software\\Ahead';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("");
		foreach my $nsk (@nerosubkeys) {
			eval {
				my $nk;
				if ($nk = $key->get_subkey($nsk."\\Recent File List")) {
					my @vals = $nk->get_list_of_values();
					if (scalar @vals > 0) {
						::rptMsg($nsk."\\Recent File List");
						::rptMsg("LastWrite Time ".gmtime($nk->get_timestamp())." (UTC)");
						foreach my $v (@vals) {
							::rptMsg("  ".$v->get_name()." -> ".$v->get_data());
						}
						::rptMsg("");
					}
					else {
						::rptMsg($nsk."\\Recent File List has no values.");
					}
				}
			};
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

1;