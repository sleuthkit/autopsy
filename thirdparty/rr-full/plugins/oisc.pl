#-----------------------------------------------------------
# oisc.pl
# Plugin for Registry Ripper 
#
# Change history
#   20091125 - modified by H. Carvey
#   20091110 - created
#
# References
#   http://support.microsoft.com/kb/838028
#   http://support.microsoft.com/kb/916658
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
              osmask        => 22,
              version       => 20091125);

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
	::rptMsg("oisc v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;
# First, let's find out which version of Office is installed
	my $version;
	my $tag = 0;
	my @versions = ("7\.0","8\.0", "9\.0", "10\.0", "11\.0","12\.0");
	foreach my $ver (@versions) {
		my $key_path = "Software\\Microsoft\\Office\\".$ver."\\Common\\Internet\\Server Cache";
		if (defined($root_key->get_subkey($key_path))) {
			$version = $ver;
			$tag = 1;
		}
	}
	
	if ($tag) {
		
		my %isc;
		
		::rptMsg("MSOffice version ".$version." located.");
		my $key_path = "Software\\Microsoft\\Office\\".$version."\\Common\\Internet\\Server Cache";			
		my $sc_key;
		if ($sc_key = $root_key->get_subkey($key_path)) {
# Attempt to retrieve Servers Cache subkeys
			my @sc = ($sc_key->get_list_of_subkeys());
			if (scalar(@sc) > 0) {
				foreach my $s (@sc) {
					my $name = $s->get_name();
					$isc{$name}{lastwrite} = $s->get_timestamp();
					
					eval {
						my $t = $s->get_value("Type")->get_data();
						(exists $types{$t}) ? ($isc{$name}{type} = $types{$t})
						                    : ($isc{$name}{type} = $t);
					};
					
					eval {
						my $p = $s->get_value("Protocol")->get_data();
						(exists $prot{$p}) ? ($isc{$name}{protocol} = $prot{$p}) 
						                   : ($isc{$name}{protocol} = $p);
					};
					
					eval {
						my @e = unpack("VV",$s->get_value("Expiration")->get_data());
						$isc{$name}{expiry} = ::getTime($e[0],$e[1]);
					};
				}
				::rptMsg("");
				foreach my $i (keys %isc) {
					::rptMsg($i);
					::rptMsg("  LastWrite : ".gmtime($isc{$i}{lastwrite})." UTC");
					::rptMsg("  Expiry    : ".gmtime($isc{$i}{expiry})." UTC");
					::rptMsg("  Protocol  : ".$isc{$i}{protocol});
					::rptMsg("  Type      : ".$isc{$i}{type});
					::rptMsg("");
				}
			}
			else {
				::rptMsg($key_path." has no subkeys.");
			}
		}
		else {
			::rptMsg($key_path." not found.");
		}
	}
	else {
		::rptMsg("MSOffice version not found.");
	}
}
1;