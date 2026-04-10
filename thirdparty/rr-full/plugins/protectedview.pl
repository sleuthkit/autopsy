#-----------------------------------------------------------
# protectedview.pl
# Get MSOffice settings for ProtectedView
#
# Change history
#  20220301 - created 
#
# References
# https://www.huntress.com/blog/targeted-apt-activity-babyshark-is-out-for-blood
# https://admx.help/?Category=Office2016&Policy=excel16.Office.Microsoft.Policies.Windows::L_TurnOffProtectedViewForAttachmentsOpenedFromOutlook
# 
# copyright 2022 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package  protectedview;
use strict;

my %config = (hive          => "NTUSER\.DAT",
			  category      => "defense evasion",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1562\.001",
			  output		=> "report",
              version       => 20220301);

sub getConfig{return %config}
sub getShortDescr {
	return "Get MSOffice ProtectedView settings";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();
my $office_version;
           
sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching  protectedview v.".$VERSION);
	::rptMsg("protectedview v.".$VERSION);
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;
	
	::rptMsg("protectedview v.".$VERSION);
	::rptMsg("(".$config{hive}.") ".getShortDescr());
	::rptMsg("MITRE ATT&CK: ".$config{MITRE}." (".$config{category}.")\n");
	::rptMsg("");
# First, let's find out which version of Office is installed
	my @version;
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
	
# System: Software\Microsoft\Office\$office_version\Word\Security\ProtectedView
# GPO   : Software\Policies\Microsoft\Office\$office_version\Excel\Security\ProtectedView	

	my @vals_to_query = ("DisableAttachmentsinPV", "DisableInternetFilesinPV", "DisableUnsafeLocationsinPV");
	my @apps = ("Word","Excel");
	my @paths = ("Software\\Microsoft\\Office","Software\\Policies\\Microsoft\\Office");
	
	foreach my $p (@paths) {
		foreach my $a (@apps) {
			my $key_path = $p."\\".$office_version."\\".$a."\\Security\\ProtectedView";
			if (my $key = $root_key->get_subkey($key_path)) {
				::rptMsg($key_path);
				::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
				::rptMsg("");
				foreach my $v (@vals_to_query) {	
					eval {
						my $d = $key->get_value($v)->get_data();
						::rptMsg(sprintf "%-25s %-2s",$v,$d);
					};
					::rptMsg($v." value not found.") if ($@);
				}
			}
			else {
				::rptMsg($key_path." not found.");
			}
		}
	}
	::rptMsg("");
	::rptMsg("Analysis Tip: Huntress analyst's write-up on BABYSHARK indicates that the threat actors modify these");
	::rptMsg("Registry values.");
	::rptMsg("");
	::rptMsg("Ref: https://www.huntress.com/blog/targeted-apt-activity-babyshark-is-out-for-blood");
}

1;