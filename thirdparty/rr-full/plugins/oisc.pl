#-----------------------------------------------------------
# oisc.pl
# Plugin for Registry Ripper 
#
# Change history
#   20220530 - updated with references
#   20200922 - MITRE update
#   20091125 - modified by H. Carvey
#   20091110 - created
#
# References
#   http://support.microsoft.com/kb/838028
#   http://support.microsoft.com/kb/916658
#	https://twitter.com/RonnyTNL/status/1435918945349931008 - CVE-2021-40444
#	https://twitter.com/keydet89/status/1531385090026221568 - msdt/Follina 
#   https://github.com/NVISOsecurity/nviso-cti/blob/master/advisories/29052022%20-%20msdt-0-day.md
#
# Derived from the officeDocs plugin
# copyright 2008-2009 H. Carvey, mangled 2009 M. Tarnawsky
#
# Michael Tarnawsky
# forensics@mialta.com
#-----------------------------------------------------------
package oisc;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1566\.001",
              category      => "initial access",
			  output		=> "report",
              version       => 20220530);

my %prot = (0 => "Read-only HTTP",
            1 => "WEC to FPSE-enabled web folder",
            2 => "DAV to DAV-ext. web folder");

my %types = (0 => "no collaboration",
            1 => "SharePoint Team Server",
            2 => "Exchange 2000 Server",
            3 => "SharePoint Portal 2001 Server",
            4 => "SharePoint 2001 enhanced folder",
            5 => "Windows SharePoint Server/SharePoint Portal 2003 Server");

sub getConfig{return %config}
sub getShortDescr {
	return "Gets contents of user's Office Internet Server Cache";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching oisc v.".$VERSION);
	::rptMsg("oisc v.".$VERSION); 
	::rptMsg("(".getHive().") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;
# First, let's find out which version of Office is installed
	my @version = ();
	my $office_version = ();
	my $key = ();
	
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
#	::rptMsg("Office Version: ".$office_version);
	
	if ($key = $root_key->get_subkey($key_path."\\".$office_version."\\Common\\Internet\\Server Cache")) {
	::rptMsg($key_path."\\".$office_version."\\Common\\Internet\\Server Cache");
# Attempt to retrieve Servers Cache subkeys
		my @subkeys = ($key->get_list_of_subkeys());
		if (scalar(@subkeys) > 0) {
			foreach my $s (@subkeys) {
				::rptMsg($s->get_name());
				::rptMsg("LastWrite time: ".::format8601Date($s->get_timestamp())."Z");
				
				eval {
					my $expiry = $s->get_value("Expiration")->get_data();
					my ($t0,$t1) = unpack("VV",$expiry);
					::rptMsg("Expiration    : ".::format8601Date(::getTime($t0,$t1))."Z");
				};
				
				eval {
					my $web = $s->get_value("WebURL")->get_data();
					::rptMsg("WebURL: ".$web) if ($web ne "");
				};

				::rptMsg("");
			}
		}
		else {
			::rptMsg($key_path."\\".$office_version."\\Common\\Internet\\Server Cache has no subkeys.");
		}
	}
	else {
		::rptMsg($key_path."\\".$office_version."\\Common\\Internet\\Server Cache not found.");
	}
}
1;