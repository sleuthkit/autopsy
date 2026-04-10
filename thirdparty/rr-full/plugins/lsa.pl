#-----------------------------------------------------------
# lsa.pl
# 
# Change history
#   20220302 - added RunAsPPL documentation
#   20210623 - added "Smoke Ham" check
#   20201025 - added Credential Guard check
#   20200831 - Added check for DisableRestrictedAdmin value
#   20200519 - added RunAsPPL value
#   20200517 - updated date output format
#   20140730 - added "EveryoneIncludesAnonymous"
#   20130307 - created
# 
# Reference: 
#   http://carnal0wnage.attackresearch.com/2013/09/stealing-passwords-every-time-they.html
#   https://www.csoonline.com/article/3393268/how-to-outwit-attackers-using-two-windows-registry-settings.html
#   https://docs.microsoft.com/en-us/windows-server/security/credentials-protection-and-management/configuring-additional-lsa-protection
#   https://www.stigviewer.com/stig/windows_paw/2017-11-21/finding/V-78161
#   https://labs.f-secure.com/blog/catching-lazarus-threat-intelligence-to-real-detection-logic-part-two  <- Credential Guard check
#
#  https://attack.mitre.org/techniques/T1003/001/
#
# copyright 2022 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package lsa;

my %config = (hive          => "system",
              hasShortDescr => 1,
              category      => "credential access",
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              MITRE         => "T1003\.001",
              version       => 20220302);

sub getConfig{return %config}
sub getShortDescr {
	return "Lists specific contents of LSA key";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

my @pkgs = ("Authentication Packages", "Notification Packages", "Security Packages",
            "EveryoneIncludesAnonymous");

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching lsa v.".$VERSION);
	::rptMsg("lsa v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr());  
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key();
# First thing to do is get the ControlSet00x marked current...this is
# going to be used over and over again in plugins that access the system
# file
	my $current;
	my $key_path = 'Select';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		$current = $key->get_value("Current")->get_data();
		my $ccs = "ControlSet00".$current;
		
		$key_path = $ccs.'\\Control\\LSA';
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg($key_path);
			::rptMsg("LastWrite: ".::format8601Date($key->get_timestamp())."Z");
			::rptMsg("");

# documentation added 20220302
# https://itm4n.github.io/lsass-runasppl/			
			eval {
				my $run = $key->get_value("RunAsPPL")->get_data();
				::rptMsg("RunAsPPL value = ".$run);
				::rptMsg("");
				::rptMsg("Per CSOOnline article, setting of \"1\" helps protect against pass-the-hash");
				::rptMsg("and mimikatz-style attacks");
				::rptMsg("");
			};
			
			eval {
				my $admin = $key->get_value("DisableRestrictedAdmin")->get_data();
				::rptMsg("DisableRestrictedAdmin value = ".$admin);
				::rptMsg("A value of \"1\" serves as an additional safeguard against pass-the-hash attacks.");
				::rptMsg("");
			};

# Credential Guard check, added 20201025
# https://labs.f-secure.com/blog/catching-lazarus-threat-intelligence-to-real-detection-logic-part-two	
# https://docs.microsoft.com/en-us/windows-hardware/customize/desktop/unattend/microsoft-windows-deviceguard-unattend-lsacfgflags		
			eval {
				my $cg = $key->get_value("LsaCfgFlags")->get_data();
				::rptMsg("LsaCfgFlags value = ".$cg);
				::rptMsg("");
				::rptMsg("Analysis Tip: If LsaCfgFlags is \"0\", Credential Guard has been disabled.");
				::rptMsg("");
			};

# LimitBlankPasswordUse check added 20210623
# https://www.fireeye.com/blog/threat-research/2021/06/darkside-affiliate-supply-chain-software-compromise.html
# DarkSide affiliate UNC2465 was observed using the "Smoked Ham" backdoor, which:
#  - creates a user account, adds it to local admins, and hides it from view on the Welcome Screen
#  - enables lateral movement via RDP
#  - limits blank passwords to console logins only, and enables the UseLogonCredential value
			eval {
				my $l = $key->get_value("LimitBlankPasswordUse")->get_data();
				::rptMsg("LimitBlankPasswordUse value = ".$l);
				::rptMsg("");
				::rptMsg("Analysis Tip: If LimitBlankPasswordUse is \"1\", functionality is enabled to limit local account use of blank ");
				::rptMsg("passwords to console logon only.");
				::rptMsg("");
			};
			
			foreach my $v (@pkgs) {
				eval {
					my $d = $key->get_value($v)->get_data();
					::rptMsg(sprintf "%-25s: ".$d,$v);
				};
			}
			::rptMsg("");
			::rptMsg("Analysis Tips:");
			::rptMsg("- Check Notification Packages value for unusual entries.");
			::rptMsg("- EveryoneIncludesAnonymous = 0 means that Anonymous users do not have the same");
			::rptMsg("  privileges as the Everyone Group.");
		}
		else {
			::rptMsg($key_path." not found.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

1;