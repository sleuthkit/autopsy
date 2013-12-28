#-----------------------------------------------------------
# vista_bitbucket.pl
#   BitBucket settings for Vista $Recylce.bin are maintained on a 
#   per-user, per-volume basis 
#
# Change history
#   20110830 [fpi] + banner, no change to the version number
#
# References
#
# copyright 2008 H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package vista_bitbucket;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              osmask        => 192,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20080420);

sub getConfig{return %config}

sub getShortDescr {
	return "Get BitBucket settings from Vista via NTUSER\.DAT";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching vista_bitbucket v.".$VERSION);
    ::rptMsg("vista_bitbucket v.".$VERSION); # 20110830 [fpi] + banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # 20110830 [fpi] + banner

	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\BitBucket";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		
		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			foreach my $v (@vals) {
				::rptMsg($v->get_name()." : ".$v->get_data());
			}
			
		}
		else {
			::rptMsg($key_path." has no values.");
		}
		::rptMsg("");
		
		my @vols;
		eval {
			@vols = $key->get_subkey("Volume")->get_list_of_subkeys();
		};
		if ($@) {
			::rptMsg("Could not access ".$key_path."\\Volume subkey.");
			return;
		}
		
		if (scalar(@vols) > 0) {
			foreach my $v (@vols) {
				::rptMsg($v->get_name()." [".gmtime($v->get_timestamp())."] (UTC)");
				eval {
					::rptMsg(sprintf "  %-15s %-3s","NukeOnDelete",$v->get_value("NukeOnDelete")->get_data());
				};
				
				
			}
			
		}
		else {
			::rptMsg($key_path."\\Volume key has no subkeys.");
		}
		
		
	}
	else {
		::rptMsg($key_path." not found.");
		::logMsg($key_path." not found.");
	}
	
}
1;