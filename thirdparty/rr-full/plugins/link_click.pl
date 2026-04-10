#-----------------------------------------------------------
# link_click.pl
# Display last link user clicked in Office document or Outlook 
#
# Change history
#  20200730 - MITRE ATT&CK updates
#  20200518 - created 
#
# References
# 
#  https://attack.mitre.org/techniques/T1204/001/
#
# copyright 2020 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package  link_click;
use strict;

my %config = (hive          => "NTUSER\.DAT",
			  category      => "execution",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1204\.001",
			  output  		=> "report",
              version       => 20200730);

sub getConfig{return %config}
sub getShortDescr {
	return "Get UseRWHlinkNavigation value data";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}
sub getMitre {return $config{MITRE};}
	
my $VERSION = getVersion();
my $office_version;
           
sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching  link_click v.".$VERSION);
	::rptMsg("link_click v.".$VERSION);
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;
	
	::rptMsg("link_click v.".$VERSION);
	::rptMsg("MITRE ATT&CK subtechnique ".getMitre());
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
	
# Check for UseRWHlinkNavigation value	
# https://support.microsoft.com/en-us/help/4013793/specified-message-identity-is-invalid-error-when-you-open-delivery-rep
	eval {
		if (my $id = $key->get_subkey($office_version."\\Common\\Internet")) {
			my $lw   = $id->get_timestamp();
			my $rw = $id->get_value("UseRWHlinkNavigation")->get_data();
			::rptMsg("Software\\Microsoft\\Office\\".$office_version."\\Common\\Internet");
			::rptMsg("LastWrite time: ".::format8601Date($lw)."Z");
			::rptMsg("UseRWHlinkNavigation value = ".$rw);
		}
	};	
	
}

1;