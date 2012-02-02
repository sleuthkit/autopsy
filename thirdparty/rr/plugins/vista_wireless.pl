#-----------------------------------------------------------
# vista_wireless
#
# Get Wireless info from Vista systems
#
# copyright 2009 H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package vista_wireless;
use strict;

my %config = (hive          => "Software",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20090514);

sub getConfig{return %config}
sub getShortDescr {
	return "Get Vista Wireless Info";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();
my $error;

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching vista_wireless v.".$VERSION);	
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key_path = "Microsoft\\Windows NT\\CurrentVersion\\NetworkList\\Profiles";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("");
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar(@subkeys) > 0) {
			foreach my $s (@subkeys) {
				my $name = $s->get_name();
				my $lastwrite = $s->get_timestamp();
				
				my $nametype;
				eval {
					$nametype = $s->get_value("NameType")->get_data();
				};
				if ($@) {
					
				}
				else {
					if ($nametype == 0x47) {
						my $profilename;
						my $descr;
						eval {
							::rptMsg("LastWrite = ".gmtime($lastwrite)." Z");
							$profilename = $s->get_value("ProfileName")->get_data();
							$descr       = $s->get_value("Description")->get_data();
							::rptMsg("  ".$profilename." [".$descr."]");
							
						};
					}
				}
				
				
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

1;