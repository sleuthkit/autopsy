#-----------------------------------------------------------
# onenote.pl
# 
#
# Change history
#  20230306 - created 
#
# References
#  https://www.bleepingcomputer.com/news/security/how-to-prevent-microsoft-onenote-files-from-infecting-windows-with-malware/
#  https://labs.withsecure.com/publications/detecting-onenote-abuse
# 
# copyright 2023 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package  onenote;
use strict;

my %config = (hive          => "NTUSER\.DAT",
			  category      => "user activity",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "",
			  output		=> "report",
              version       => 20230306);

sub getConfig{return %config}
sub getShortDescr {
	return "Check OneNote settings";	
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
	::logMsg("Launching  onenote v.".$VERSION);
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;
	
	::rptMsg("onenote v.".$VERSION);
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
	
	if ($office_version ne "") {
	
		if ($key->get_subkey("SOFTWARE\\Policies\\Microsoft\\Office\\".$office_version."\\Onenote\\Options")) {
# https://admx.help/?Category=Office2007&Policy=onent12.Office.Microsoft.Policies.Windows::L_Disableembeddedfiles		
			eval {
				my $e = $key->get_value("DisableEmbeddedFiles")->get_data();
				::rptMsg("DisableEmbeddedFiles value: ".$e);
				::rptMsg("");
				::rptMsg("Analysis Tip: This value disables the ability to embed files within a OneNote file.");
				::rptMsg("1 - Embedding files disabled");
				::rptMsg("0 - Embedding files enabled (default)");
				::rptMsg("");
			};
	
		}
		else {
			::rptMsg("SOFTWARE\\Policies\\Microsoft\\Office\\".$office_version."\\Onenote\\Options key not found");
		}
	
		if ($key->get_subkey("SOFTWARE\\Policies\\Microsoft\\Office\\".$office_version."\\Onenote\\Options\\EmbeddedFileOpenOptions")) {
		
# https://labs.withsecure.com/publications/detecting-onenote-abuse
			eval {
				my $e = $key->get_value("EmbeddedFileOpenWarningDisabled")->get_data();
				::rptMsg("EmbeddedFileOpenWarningDisabled value: ".$e);
				::rptMsg("");
				::rptMsg("Analysis Tip: This value may be set to \"1\" if the user clicked the \"Don't show me this again\" checkbox in the");
				::rptMsg("Warning dialog box when opening attachments\.");
				::rptMsg("");	
			};

# https://admx.help/?Category=Office2007&Policy=onent12.Office.Microsoft.Policies.Windows::L_EmbeddedFilesBlockedExtensions			
			eval {
				my $b = $key->get_value("BlockedExtensions")->get_data();
				::rptMsg("BlockedExtensions value: ".$b);
				::rptMsg("");
				::rptMsg("Analysis Tip: The BlockedExtensions value provides a list of file extensions that should be blocked");
				::rptMsg("if they're embedded within the OneNote file.");
				::rptMsg("");
			};
	
		}
		else {
			::rptMsg("SOFTWARE\\Policies\\Microsoft\\Office\\".$office_version."\\Onenote\\Options\\EmbeddedFileOpenOptions key not found.");
		}
	}
	else {
		::rptMsg("MS Office does not appear to be installed on this system; the Office version could not be determined.");
	}
}

1;