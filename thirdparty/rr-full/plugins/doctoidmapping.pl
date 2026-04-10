#-----------------------------------------------------------
# doctoidmapping.pl
# Value names extracted by this plugin appear to be associated with what the user
# types into Outlook search bar
#
# Change history
#  20201028 - created
#
# References
#
# 
# copyright 2020 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package doctoidmapping;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              category      => "user activity",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "",
			  output        => "report",
              version       => 20201020);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets user's DocToIdMapping value names";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching doctoidmapping v.".$VERSION);
	::rptMsg("doctoidmapping v.".$VERSION); # banner
  ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); 
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;
	
	my @version;
	my $office_version;
	my $key;
	my $key_path = "Software\\Microsoft\\Office";
	if ($key = $root_key->get_subkey($key_path)) {
		my @subkeys = $key->get_list_of_subkeys();
		foreach my $s (@subkeys) {
			my $name = $s->get_name();
			push(@version,$name) if ($name =~ m/^\d/);
		}
	}
# Determine MSOffice version in use	
	my @v = reverse sort {$a<=>$b} @version;
	foreach my $i (@v) {
		eval {
			if (my $o = $key->get_subkey($i."\\User Settings")) {
				$office_version = $i;
			}
		};
	}
	
	::rptMsg("MSOffice version could not be found.") if ($office_version == "");
		
	eval {
		if (my $doc = $key->get_subkey($office_version."\\Common\\Identity\\DocToIdMapping")) {
			my @subkeys = $doc->get_list_of_subkeys();
			if (scalar @subkeys > 0) {
				foreach my $s (@subkeys) {
					::rptMsg($s->get_name());
					::rptMsg("LastWrite time: ".::format8601Date($s->get_timestamp())."Z");
					my @vals = $s->get_list_of_values();
					if (scalar @vals > 0) {
						foreach my $v (@vals) {
							::rptMsg("  ".$v->get_name());
						}
					}
				}
			}
		}
		else {
			::rptMsg("DocToIdMapping key not found\.");
		}
	};
	::rptMsg("");
	::rptMsg("Analysis Tip: Value names have been found to align with items the user typed into the Outlook Search field.");
#	::rptMsg("");
}

1;