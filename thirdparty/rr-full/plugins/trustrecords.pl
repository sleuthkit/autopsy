#-----------------------------------------------------------
# trustrecords.pl
# List Office documents for which the user explicitly opted to accept bypassing
#   the default security settings for the application 
#
# Change history
#  20190626 - updated to more recent versions of Office
#  20160224 - modified per Mari's blog post
#  20120716 - created
#
# References
# 20190626 updates
#  https://decentsecurity.com/block-office-macros
#  https://gist.github.com/PSJoshi/749cf1733217d8791cf956574a3583a2
#
#  http://az4n6.blogspot.com/2016/02/more-on-trust-records-macros-and.html
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
							category      => "User Activity",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20190626);

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
my %vba = (1 => "Enable all macros",
           2 => "Disable all macros w/ notification",
           3 => "Disalbe all macros except dig. signed macros",
           4 => "Disalbe all macros w/o notification");
           
sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching  trustrecords v.".$VERSION);
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;
	
	::rptMsg("trustrecords v.".$VERSION);
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
	
# Now that we have the most recent version of Office installed, let's 
# start looking at the various subkeys
	my @apps = ("Word","PowerPoint","Excel","Access");	
	my $key_path = "Software\\Microsoft\\Office\\".$office_version;
	
	foreach my $app (@apps) {
		::rptMsg("**".$app."**");
		::rptMsg("-" x 10);
		my $app_path = $key_path."\\".$app."\\Security";
		eval {
			if (my $sec = $root_key->get_subkey($app_path)) {
				::rptMsg("Security key LastWrite: ".gmtime($sec->get_timestamp())." Z");
				my $w = $sec->get_value("VBAWarnings")->get_data();
				::rptMsg("VBAWarnings = ".$vba{$w});
				::rptMsg("");
			}
		};

# Added 20190626		
		eval {
		  if (my $sec = $root_key->get_subkey($app_path)) {
				my $blk = $sec->get_value("blockcontentexecutionfrominternet")->get_data();
				::rptMsg("blockcontentexecutionfrominternet = ".$blk);
				::rptMsg("");
			}
	  };
	
# Trusted Documents/Trust Records		
		$app_path = $key_path."\\".$app."\\Security\\Trusted Documents";
		if (my $app_key = $root_key->get_subkey($app_path)) {
			if (my $trust = $app_key->get_subkey("TrustRecords")) {
				my @vals = $trust->get_list_of_values();
				::rptMsg("TrustRecords");
				foreach my $v (@vals) {
					my $data = $v->get_data();
					my ($t0,$t1) = (unpack("VV",substr($data,0,8)));
					my $t = ::getTime($t0,$t1);
					::rptMsg(gmtime($t)." Z : ".$v->get_name());	
					
					my $e = unpack("V",substr($data, length($data) - 4, 4));
					::rptMsg("**Enable Content button clicked.") if ($e == 2147483647);
				}
			}
		}
		::rptMsg("");

	}
}
1;