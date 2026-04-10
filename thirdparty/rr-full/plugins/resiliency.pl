#-----------------------------------------------------------
# resiliency.pl
# This plugin is in somewhat-testing mode; right now, it checks the StartupItems and DisabledItems
# subkeys only, as it seems that the DisabledItems subkey for Word (and possibly Excel) can contain
# references to files the user had open, or at least had knowledge of.
#
# Change history
#  20210325 - created 
#
# To-Do: Add "DocumentRecovery" subkey, look for other subkeys to add
#
#
# References
# 	https://twitter.com/SBousseaden/status/1366025094779256838
#   https://www.sophos.com/en-us/threat-center/threat-analyses/viruses-and-spyware/Troj~Docker-Gen/detailed-analysis.aspx
#   https://isc.sans.edu/diary/Interesting+VBA+Dropper/23016
# 
# copyright 2021 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package  resiliency;
use strict;

my %config = (hive          => "NTUSER\.DAT",
			  category      => "user activity",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "",
			  output		=> "report",
              version       => 20210325);

sub getConfig{return %config}
sub getShortDescr {
	return "Get user's MSOffice Resiliency subkey content";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();
my $office_version;
my @apps = ("Word","Excel","OneNote","OutLook");

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching  resiliency v.".$VERSION);
	::rptMsg("resiliency v.".$VERSION);
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;
	
	::rptMsg("resiliency v.".$VERSION);
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
# Determine MSOffice version in use	
		my @v = reverse sort {$a<=>$b} @version;
		foreach my $i (@v) {
			eval {
				if (my $o = $key->get_subkey($i."\\User Settings")) {
					$office_version = $i;
				}
			};
		}
	
		foreach my $app (@apps) {
			my $res_path = $office_version."\\".$app."\\Resiliency";
			if (my $id = $key->get_subkey($res_path)) {
				my @subkeys = ("StartupItems","DisabledItems");
				foreach my $s (@subkeys) {
				
					if (my $i = $id->get_subkey($s)) {
						my @vals = $i->get_list_of_values();
						if (scalar @vals > 0) {
							::rptMsg($key_path."\\".$office_version."\\".$app."\\Resiliency\\".$s);
							::rptMsg("LastWrite time: ".::format8601Date($i->get_timestamp())."Z");
							foreach my $v (@vals) {
								my $name = $v->get_name();
								my $data = $v->get_data();
								::rptMsg("Value: ".$name);
#								::probe($data);
								
								if ($s eq "StartupItems") {
									my ($t0,$t1) = unpack("VV",substr($data,16,8));
									::rptMsg("Time: ".::format8601Date(::getTime($t0,$t1))."Z");
								}
								
								if ($s eq "DisabledItems") {
									my $n = unpack("V",substr($data,4,4));
									my $i = ::getUnicodeStr(substr($data,12,$n));
									::rptMsg("String: ".$i);
								}					
								::rptMsg("");
							}
						}
					}
				}
			}
			else {
#				::rptMsg($res_path." not found.");
			}
		}
	}
	else {
		::rptMsg("MSOffice not found.");
	}
}