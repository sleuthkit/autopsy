#-----------------------------------------------------------
# outlook_attach.pl
# List Office documents for which the user explicitly opted to accept bypassing
#   the default security settings for the application 
#
# Change history
#  20210504 - created 
#
# References
#   https://support.microsoft.com/en-us/topic/outlook-blocked-access-to-the-following-potentially-unsafe-attachments-c5c4a480-041e-2466-667f-e98d389ff822
#   https://www.slipstick.com/outlook/block-additional-attachment-types/ 
#
#
# copyright 2021 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package  outlook_attach;
use strict;

my %config = (hive          => "NTUSER\.DAT",
			  category      => "execution",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1204\.002",
			  output		=> "report",
              version       => 20210504);

sub getConfig{return %config}
sub getShortDescr {
	return "Get user's MSOffice content";	
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
	::logMsg("Launching outlook_attach v.".$VERSION);
	::rptMsg("outlook_attach v.".$VERSION);
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;
	
	::rptMsg("outlook_attach v.".$VERSION);
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
	
	my $key_path = "Software\\Microsoft\\Office\\".$office_version."\\Outlook\\Security";
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
		eval {
			my $l = $key->get_value("Level1Remove")->get_data();
			::rptMsg("Level1Remove value : ".$l);
		};
		
		eval {
			my $l = $key->get_value("Level1Add")->get_data();
			::rptMsg("Level1Add value    : ".$l);
		};
		
	}
	else {
		::rptMsg($key_path." not found.");
	}
	::rptMsg("");
	::rptMsg("Analysis Tip: Leve1Remove & Level1Add values control how Outlook attachments are treated, by extension.");
	::rptMsg("Level1Remove - Outlook issues a warning, allowing the user to save the file before launching");
	::rptMsg("Level1Add    - Completely block access to files with the extension");
	::rptMsg("");
}

1;