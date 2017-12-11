#-----------------------------------------------------------
# clampi.pl
# Checks keys/values set by new version of Trojan.Clampi
#
# Change history
#   20091019 - created
#
# NOTE: This is purely a test plugin, and based solely on the below
#       reference.  It has not been tested on any systems that were
#       known to be infected.
#
# References
#   http://www.symantec.com/connect/blogs/inside-trojanclampi-stealing-your-information
# 
# copyright 2009 H. Carvey
#-----------------------------------------------------------
package clampi;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20091019);

sub getConfig{return %config}
sub getShortDescr {
	return "TEST - Checks for keys set by Trojan.Clampi PROT module";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching clampi v.".$VERSION);
	 ::rptMsg("clampi v.".$VERSION); # banner
    ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;
	
	my $count = 0;
	
	my $key_path = 'Software\\Microsoft\\Internet Explorer\\Main';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		
		my ($form1, $form2, $form3);
		
		eval {
			$form1 = $key->get_value("Use FormSuggest")->get_data();
			::rptMsg("\tUse FormSuggest = ".$form1);
			$count++ if ($form1 eq "true");
		};
		
		eval {
			$form2 = $key->get_value("FormSuggest_Passwords")->get_data();
			::rptMsg("\tFormSuggest_Passwords = ".$form2);
			$count++ if ($form2 eq "true");
		};
		
		eval {
			$form3 = $key->get_value("FormSuggest_PW_Ask")->get_data();
			::rptMsg("\tUse FormSuggest = ".$form3);
			$count++ if ($form3 eq "no");
		};
	}
	else {
		::rptMsg($key_path." not found.");
	}
	::rptMsg("");
	$key_path = "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\AutoComplete";
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		my $auto;
		eval {
			$auto = $key->get_value("AutoSuggest")->get_data();
			::rptMsg("\tAutoSuggest = ".$auto);
			$count++ if ($auto eq "true");
		};
	}
	else {
		::rptMsg($key_path." not found.");
	}
	::rptMsg("");
	$key_path = "Software\\Microsoft\\Internet Account Manager\\Accounts";
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		my $prompt;
		eval {
			$prompt = $key->get_value("POP3 Prompt for Password")->get_data();
			::rptMsg("\tPOP3 Prompt for Password = ".$prompt);
			$count++ if ($prompt eq "true");
		};
	}
	else {
		::rptMsg($key_path." not found.");
	}
	::rptMsg("");
	if ($count == 5) {
		::rptMsg("The system may have been infected with the Trojan.Clampi PROT module.");
	}
	else {
		::rptMsg("The system does not appear to have been infected with the Trojan.Clampi");
		::rptMsg("PROT module.");
	}
}
1;
