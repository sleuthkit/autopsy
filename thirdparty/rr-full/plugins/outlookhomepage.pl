#-----------------------------------------------------------
# outlookhomepage.pl
#
# Change history
#  20201103 - updated with analysis tips
#  20201102 - created 
#
# References
# 	https://www.fireeye.com/blog/threat-research/2019/12/breaking-the-rules-tough-outlook-for-home-page-attacks.html
#   https://docs.microsoft.com/en-us/microsoft-365/security/office-365-security/detect-and-remediate-outlook-rules-forms-attack?view=o365-worldwide
#   https://attack.mitre.org/techniques/T1137/004/
#
# copyright 2020 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
# *based on a plugin written and contributed by Mr. Hobbits
#-----------------------------------------------------------
package  outlookhomepage;
use strict;

my %config = (hive          => "NTUSER\.DAT",
			  category      => "persistence",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              MITRE         => "T1137\.004",
              version       => 20201103);

sub getConfig{return %config}
sub getShortDescr {
	return "Get Outlook WebView Homepage settings";	
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
	::logMsg("Launching  outlookhomepage v.".$VERSION);
	::rptMsg("outlookhomepage v.".$VERSION);
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;
	
	::rptMsg("outlookhomepage v.".$VERSION);
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

# First, let's check the URL values for the various WebView subkeys	
  my $flag = 0;
	my $key_path = "Software\\Microsoft\\Office\\".$office_version."\\Outlook\\WebView";
# https://support.microsoft.com/en-us/office/outlook-home-page-feature-is-missing-in-folder-properties-d207edb7-aa02-46c5-b608-5d9dbed9bd04	
	my @views = ("Inbox","Calendar","Contacts","Deleted Items","Drafts","Journal","Junk E-mail","Notes","Outbox",
	             "RSS","Sent Mail","Tasks");
	
	foreach my $v (@views) {
		if ($key = $root_key->get_subkey($key_path."\\".$v)) {
			::rptMsg($key_path."\\".$v);
			::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
			eval {
				my $url = $key->get_value("URL")->get_data();
				::rptMsg("URL value = ".$url);
				$flag = 1;
			};
			
		}
		else {
#			::rptMsg($key_path."\\".$v." not found.");
		}
#		::rptMsg("");
	}
	if ($flag) {
		::rptMsg("Analysis Tip: Outlook WebView homepages, particularly Inbox and Calendar, have been used to maintain persistence by");
		::rptMsg("pointing to pages with malicious code embedded.  Look for unusual or suspicious URLs. This technique rolls back the");
		::rptMsg("CVE-2017-11774 patch.");
		::rptMsg("");
		::rptMsg("Ref: https://www.fireeye.com/blog/threat-research/2019/12/breaking-the-rules-tough-outlook-for-home-page-attacks.html");
		::rptMsg("");
	}

# check UserDefinedURL value
	my $key_path = "Software\\Microsoft\\Office\\".$office_version."\\Outlook\\Today";		
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
		
		eval {
			my $u = $key->get_value("UserDefinedUrl")->get_data();
			::rptMsg("UserDefinedUrl value = ".$u);
			::rptMsg("");
			::rptMsg("Analysis Tip: Pointing this value to a malicious web page has been used by actors to maintain persistence.");
			::rptMsg("Look for unusual values.");
			::rptMsg("");
		};
	}
	else {
#		::rptMsg($key_path." not found.");
	}
	
# check Security values
	my $key_path = "Software\\Microsoft\\Office\\".$office_version."\\Outlook\\Security";	
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
		
		my @vals = $key->get_list_of_values();
		if (scalar @vals > 0) {
			foreach my $v (@vals) {				
				::rptMsg(sprintf "%-40s %-10s",$v->get_name(),$v->get_data());
			}
			::rptMsg("");
			::rptMsg("Analysis Tip: When set to 1, several values serve to roll-back the CVE-2017-11774 patch and expose unsafe options.");
			::rptMsg("");
			::rptMsg("EnableRoamingFolderHomepages = 1: Exposes unsafe options in Outlook, re-enabling the original home page tab and ");
			::rptMsg("  roaming home page behavior in the Outlook UI.");
			::rptMsg("NonDefaultStoreScript        = 1: Allow for folders within non-default mailboxes to leverage a custom home page.");
			::rptMsg("EnableUnsafeClientMailRules  = 1: Allows for \“Run as a Script\” and \“Start Application\” rules to be re-enabled");
			::rptMsg("");
		}
	}
	else {
#		::rptMsg($key_path." not found.");
	}
}

1;