#-----------------------------------------------------------
# trustrecords_tln.pl
# List Office documents for which the user explicitly opted to accept bypassing
#   the default security settings for the application 
#
# Change history
#  20160224 - modified per Mari's blog post
#  20120717 - created; modified from trustrecords.pl plugin
#
# References
#  http://az4n6.blogspot.com/2016/02/more-on-trust-records-macros-and.html
#  ForensicArtifacts.com posting by Andrew Case:
#    http://forensicartifacts.com/2012/07/ntuser-trust-records/
#  http://archive.hack.lu/2010/Filiol-Office-Documents-New-Weapons-of-Cyberwarfare-slides.pdf
# 
# copyright 2012 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package  trustrecords_tln;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              category      => "User Activity",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20160224);

sub getConfig{return %config}
sub getShortDescr {
	return "Get user's MSOffice TrustRecords values";	
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
	::logMsg("Launching  trustrecords_tln v.".$VERSION);
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;
	
#	::rptMsg("trustrecords v.".$VERSION);
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
	::rptMsg("Version: ".$office_version);
# Now that we have the most recent version of Office installed, let's 
# start looking at the various subkeys
	my @apps = ("Word","PowerPoint","Excel","Access");	
	$key_path = "Software\\Microsoft\\Office\\".$office_version;
	
	foreach my $app (@apps) {
		my $app_path = $key_path."\\".$app."\\Security\\Trusted Documents";
#		::rptMsg($app);
		if (my $app_key = $root_key->get_subkey($app_path)) {
			
			if (my $trust = $app_key->get_subkey("TrustRecords")) {
				my @vals = $trust->get_list_of_values();
				
				foreach my $v (@vals) {
					my $data = $v->get_data();
					my ($t0,$t1) = (unpack("VV",substr($data,0,8)));
					my $t = ::getTime($t0,$t1);
					my $descr = "TrustRecords - ".$v->get_name();
					my $e = unpack("V",substr($data, length($data) - 4, 4));
					$descr = $descr." [Enable Content button clicked]" if ($e == 2147483647);
					::rptMsg($t."|REG|||".$descr);
				}
			}
		}
#		::rptMsg("");
	}
}
1;
