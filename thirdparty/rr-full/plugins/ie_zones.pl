#-----------------------------------------------------------
# ie_zones.pl
# Checks keys/values set by new version of Trojan.Clampi
#
# Change history
#   20140611 - created
#
#
# References
#   http://support.microsoft.com/kb/182569
#
# Info on ZoneMaps:
#  http://blogs.technet.com/b/heyscriptingguy/archive/2005/05/02/how-can-i-add-a-site-to-internet-explorer-s-restricted-sites-zone.aspx
# 
# copyright 2014 H. Carvey
#-----------------------------------------------------------
package ie_zones;
use strict;

my %config = (hive          => "NTUSER\.DAT;Software",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20140611);

sub getConfig{return %config}
sub getShortDescr {
	return "Get IE Zone settings";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching ie_zones v.".$VERSION);
	::rptMsg("ie_zones v.".$VERSION); # banner
	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;
	my ($key,$key_path,$zone);
	
	my %zones = (0 => "Permitted",
	             1 => "Prompt",
	             3 => "Prohibited");
	
	
	my @paths = ('Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings',
	             'Microsoft\\Windows\\CurrentVersion\\Internet Settings');
	
	foreach $key_path (@paths) {
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg($key_path);
			::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
			::rptMsg("");
# Get Zones and various security settings			
			foreach my $n (0..4) {
				$zone = $key->get_subkey('Zones\\'.$n);
				::rptMsg("Zone ".$n.":  ".$zone->get_value("PMDisplayName")->get_data()." - ".$zone->get_value("Description")->get_data());
				::rptMsg("LastWrite: ".gmtime($zone->get_timestamp()." UTC"));
				
				my @vals = $zone->get_list_of_values();
				if (scalar(@vals) > 0) {
					foreach my $v (@vals) {
						my $name = $v->get_name();
						next unless (length($name) == 4 && $name ne "Icon"); 
						my $data = $v->get_data();
						$name = "**".$name if ($name eq "1609" && $data == 0);
						my $str = sprintf "%6s  0x%08x",$name,$data;
#						::rptMsg("  ".$name."  ".$data."  ".$zones{$data});
						::rptMsg($str."  ".$zones{$data});
					}
				}
				::rptMsg("");
			}
# Now, get ZoneMap settings
			my $zonemap = $key->get_subkey('ZoneMap\\Domains');
			my @domains = $zonemap->get_list_of_subkeys();
			if (scalar(@domains) > 0) {
				foreach my $d (@domains) {
					::rptMsg("Domain: ".$d->get_name());
					
					my @vals = $d->get_list_of_values();
					if (scalar(@vals) > 0) {
						foreach my $v (@vals) {
							::rptMsg("  ".$v->get_name()."  ".$v->get_data());
						}
					}
					::rptMsg("");
				}
			}			
		}
		else {
#			::rptMsg($key_path." not found.");
		}
	}	
}
1;