#------------------------------------
# revouninstall.pl
# Plugin for Registry Ripper, NTUSER.DAT - gets the information regarding the
# Revo Unistaller Pro application
#
# Change History:
# 	20200329 - Initial Development
#
# References
#
#
# Copyright 2020 Tiago Sousa tsousahs@gmail.com
# ------------------------------------
package revouninstaller;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
	            hasDescr      => 0,
	            hasRefs       => 0,
	            osmask        => 22,
	            version       => 20200329);

sub getConfig { return %config }
sub getShortDescr {
	return "Gets the information regarding revo unistaller execution";
}

sub getDescr {}
sub getRefs {}
sub getHive { return $config{ hive }; }
sub getVersion { return $config{ version }; }

my $VERSION = getVersion();


sub pluginmain {

	my $class = shift;
	my $ntuser = shift;
	
	::logMsg("Lauching revounistall v.".$VERSION);
	::rptMsg("revounistall v.".$VERSION);
	::rptMsg("(".getHive().") ".getShortDescr()."\n" );
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	# Browser Run	
	
	my @key_paths = ( 
			"Software\\VS Revo Group\\Revo Uninstaller Pro\\TrackCleaner\\Browsers",
			"Software\\VS Revo Group\\Revo Uninstaller Pro\\TrackCleaner\\Windows",
		  "Software\\VS Revo Group\\Revo Uninstaller Pro\\TrackCleaner\\MSOffice",
      "Software\\VS Revo Group\\Revo Uninstaller Pro\\Uninstaller\\AppBar",
		  "Software\\VS Revo Group\\Revo Uninstaller Pro\\Uninstaller"  
		        );

	my $key;
	my @vals;

	my @list_of_browsers;
	
	# Inside the browser key it may have separate sub keys for specific browsers
	$key = $root_key->get_subkey( @key_paths[0] );
	@list_of_browsers = $key->get_list_of_subkeys();
	
	
	foreach $key (@list_of_browsers) {
		push(@key_paths,$key_paths[0]."\\".$key->get_name());
	}
	
	# Remove the Browser key. it's not really needed anymore
	shift(@key_paths);

	
	foreach my $key_path (@key_paths) {

		$key = $root_key->get_subkey( $key_path );
		::rptMsg("\n\nName:".$key->get_name());
		::rptMsg("Last Write Time: ".gmtime($key->get_timestamp())." (UTC)\n");

		my @vals = $key->get_list_of_values();

		foreach my $v (@vals) {
			if ($v->get_data() eq 1) {
				::rptMsg($v->get_name()." : Enabled");
			} elsif ($v->get_data() eq 0){
				::rptMsg($v->get_name()." : Disabled");
			} else {
				::rptMsg($v->get_name()." : ".$v->get_data());
			}
		}
	}
}
