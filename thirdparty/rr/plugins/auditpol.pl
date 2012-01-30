#-----------------------------------------------------------
# auditpol
# Get the audit policy from the Security hive file
# 
# copyright 2008 H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package auditpol;
use strict;

my %config = (hive          => "Security",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 1,
              osmask        => 22,
              version       => 20080327);

sub getConfig{return %config}
sub getShortDescr {
	return "Get audit policy from the Security hive file";	
}
sub getDescr{}
sub getRefs {
	my %refs = ("How To Determine Audit Policies from the Registry" => 
	            "http://support.microsoft.com/default.aspx?scid=kb;EN-US;q246120");
	return %refs;	
}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

my %audit = (0 => "N",
             1 => "S",
             2 => "F",
             3 => "S/F");

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching auditpol v.".$VERSION);
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = "Policy\\PolAdtEv";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("auditpol");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		
		my $data;
		eval {
			$data = $key->get_value("")->get_data();
		};
		if ($@) {
			::rptMsg("Error occurred getting data from ".$key_path);
			::rptMsg(" - ".$@);
		}
		else {
# Check to see if auditing is enabled
			my $enabled = unpack("C",substr($data,0,1));
			if ($enabled) {
				::rptMsg("Auditing is enabled.");
# Get audit configuration settings			
				my @vals = unpack("V*",$data);
				::rptMsg("\tAudit System Events        = ".$audit{$vals[1]});
				::rptMsg("\tAudit Logon Events         = ".$audit{$vals[2]});
				::rptMsg("\tAudit Object Access        = ".$audit{$vals[3]});
				::rptMsg("\tAudit Privilege Use        = ".$audit{$vals[4]});
				::rptMsg("\tAudit Process Tracking     = ".$audit{$vals[5]});
				::rptMsg("\tAudit Policy Change        = ".$audit{$vals[6]});
				::rptMsg("\tAudit Account Management   = ".$audit{$vals[7]});
				::rptMsg("\tAudit Dir Service Access   = ".$audit{$vals[8]});
				::rptMsg("\tAudit Account Logon Events = ".$audit{$vals[9]});
			}
			else {
				::rptMsg("**Auditing is NOT enabled.");
			}
		}
	}
	else {
		::rptMsg($key_path." not found.");
		::logMsg($key_path." not found.");
	}
	
}
1;