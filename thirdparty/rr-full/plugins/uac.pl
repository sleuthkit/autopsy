#-----------------------------------------------------------
# uac.pl
#   Gets the User Account Configuration settings from the SOFTWARE hive file 
#
# Change history
#   20220826 - added reference, updated MITRE ATT&CK
#   20200916 - MITRE updates
#   20200427 - updated output date format
#   20200409 - added reference
#   20130213  Created
#
# References
#  https://docs.microsoft.com/en-us/openspecs/windows_protocols/ms-gpsb/341747f5-6b5d-4d30-85fc-fa1cc04038d4
#  UAC Group Policy Settings and Registry Key Settings http://technet.microsoft.com/en-us/library/dd835564(v=ws.10).aspx
#  20220826: https://twitter.com/d4rksystem/status/1563226770962145280
#
# 
# Corey Harrell (Journey Into IR)
# maintained by H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package uac;
use strict;

my %config = (hive          => "Software",
              MITRE         => "T1562\.001",
              category      => "defense evasion",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              version       => 20220826);

sub getConfig{return %config}

sub getShortDescr {
    return "Get Select User Account Control (UAC) Values from HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Policies\\System";
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
    my $class = shift;
    my $hive = shift;
    ::logMsg("Launching uac v.".$VERSION);
    ::rptMsg("uac v.".$VERSION); 
    ::rptMsg("(".$config{hive}.") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
    my $reg = Parse::Win32Registry->new($hive);
    my $root_key = $reg->get_root_key;

    my $key_path = "Microsoft\\Windows\\CurrentVersion\\policies\\system";
    my $key;
    if ($key = $root_key->get_subkey($key_path)) {
        ::rptMsg("UAC Information");
        ::rptMsg($key_path);
        ::rptMsg("LastWrite Time: ".::format8601Date($key->get_timestamp())."Z");
        ::rptMsg("");

        # GET EnableLUA –

        my $enablelua;
        eval {
        	$enablelua = $key->get_value("EnableLUA")->get_data();
        };
        if ($@) {
        	::rptMsg("EnableLUA value not found.");
        }
        else {
        	::rptMsg("EnableLUA value = ".$enablelua);
					::rptMsg("");
					::rptMsg("User Account Control: Run all administrators in Admin Approval Mode");
					::rptMsg("0 = Disabled");
					::rptMsg("1 = Enabled (Default)");
        }
        ::rptMsg("");

# GET EnableVirtualization –

        my $enablevirtualization;
        eval {
        	$enablevirtualization = $key->get_value("EnableVirtualization")->get_data();
        };
        if ($@) {
        	::rptMsg("EnableVirtualization value not found.");
        }
        else {
        	::rptMsg("EnableVirtualization value = ".$enablevirtualization);
					::rptMsg("");
					::rptMsg("User Account Control: Virtualize file and registry write failures to per-user locations");
					::rptMsg("0 = Disabled");
					::rptMsg("1 = Enabled (Default)");
        }
        ::rptMsg("");
		
		# GET FilterAdministratorToken –

        my $filteradministratortoken;
        eval {
        	$filteradministratortoken = $key->get_value("FilterAdministratorToken")->get_data();
        };
        if ($@) {
        	::rptMsg("FilterAdministratorToken value not found.");
        }
        else {
        	::rptMsg("FilterAdministratorToken value = ".$filteradministratortoken);
					::rptMsg("");
					::rptMsg("User Account Control: Admin Approval Mode for the built-in Administrator account");
					::rptMsg("0 = Disabled (Default)");
					::rptMsg("1 = Enabled");
        }
        ::rptMsg("");
		
		# GET ConsentPromptBehaviorAdmin –

        my $consentpromptbehavioradmin;
        eval {
        	$consentpromptbehavioradmin = $key->get_value("ConsentPromptBehaviorAdmin")->get_data();
        };
        if ($@) {
        	::rptMsg("ConsentPromptBehaviorAdmin value not found.");
        }
        else {
        	::rptMsg("ConsentPromptBehaviorAdmin value = ".$consentpromptbehavioradmin);
					::rptMsg("");
					::rptMsg("User Account Control: Behavior of the elevation prompt for administrators in Admin Approval Mode");
					::rptMsg("0 = Elevate without prompting");
					::rptMsg("1 = Prompt for credentials on the secure desktop");
					::rptMsg("2 = Prompt for consent on the secure desktop");
					::rptMsg("3 = Prompt for credentials");
					::rptMsg("4 = Prompt for consent");
					::rptMsg("5 = Prompt for consent for non-Windows binaries (Default)");
        }
        ::rptMsg("");
		
		# GET ConsentPromptBehaviorUser –

        my $consentpromptbehavioruser;
        eval {
        	$consentpromptbehavioruser = $key->get_value("ConsentPromptBehaviorUser")->get_data();
        };
        if ($@) {
        	::rptMsg("ConsentPromptBehaviorUser value not found.");
        }
        else {
        	::rptMsg("ConsentPromptBehaviorUser value = ".$consentpromptbehavioruser);
					::rptMsg("");
					::rptMsg("User Account Control: Behavior of the elevation prompt for standard users");
					::rptMsg("0 = Automatically deny elevation requests");
					::rptMsg("1 = Prompt for consent on the secure desktop");
					::rptMsg("3 = Prompt for consent on the secure desktop (Default)");
        }
        ::rptMsg("");
		
    }
    else {
    	::rptMsg($key_path." not found.");
    }
}

1;
