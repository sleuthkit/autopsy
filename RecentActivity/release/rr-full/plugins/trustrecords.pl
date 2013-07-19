#-----------------------------------------------------------
# trustrecords.pl
# List Office documents for which the user explicitly opted to accept bypassing
#   the default security settings for the application 
#
# Change history
#  20120716 - created
#
# References
#  ForensicArtifacts.com posting by Andrew Case:
#    http://forensicartifacts.com/2012/07/ntuser-trust-records/
#  http://archive.hack.lu/2010/Filiol-Office-Documents-New-Weapons-of-Cyberwarfare-slides.pdf
# 
# copyright 2012 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package  trustrecords;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20120716);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets user's Office 2010 TrustRecords values";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching  trustrecords v.".$VERSION);
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;
	
	::rptMsg("trustrecords v.".$VERSION);
# First, let's find out which version of Office is installed
	my @version;
	my $key_path = "Software\\Microsoft\\Office";
	if (my $key = $root_key->get_subkey($key_path)) {
		my @subkeys = $key->get_list_of_subkeys();
		foreach my $s (@subkeys) {
			my $name = $s->get_name();
			push(@version,$name) if ($name =~ m/^\d/);
		}
	}
	
	my @v = reverse sort {$a<=>$b} @version;
#	::rptMsg("Office version = ".$v[0]);
	
# Now that we have the most recent version of Office installed, let's 
# start looking at the various subkeys
	my @apps = ("Word","PowerPoint","Excel","Access");	
	my $key_path = "Software\\Microsoft\\Office\\".$v[0];
	
	foreach my $app (@apps) {
		my $app_path = $key_path."\\".$app."\\Security\\Trusted Documents";
		::rptMsg($app);
		if (my $app_key = $root_key->get_subkey($app_path)) {
			my $lastpurge = $app_key->get_value("LastPurgeTime")->get_data();
			::rptMsg("LastPurgeTime = ".gmtime($lastpurge));
			
			if (my $trust = $app_key->get_subkey("TrustRecords")) {
				my @vals = $trust->get_list_of_values();
				
				foreach my $v (@vals) {
					my ($t0,$t1) = (unpack("VV",substr($v->get_data(),0,8)));
					my $t = ::getTime($t0,$t1);
					::rptMsg(gmtime($t)." -> ".$v->get_name());	
				}
			}
		}
		::rptMsg("");
	}
}
1;