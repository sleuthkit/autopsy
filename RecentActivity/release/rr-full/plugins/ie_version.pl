#-----------------------------------------------------------
# ie_version
# Get IE version and build
# 
# copyright 2009 H. Carvey
#-----------------------------------------------------------
package ie_version;
use strict;

my %config = (hive          => "Software",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20091016);

sub getConfig{return %config}

sub getShortDescr {
	return "Get IE version and build";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching ie_version v.".$VERSION);
	::rptMsg("ie_version v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = "Microsoft\\Internet Explorer";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");

		my $version;
		my $build;
		eval {
			$build = $key->get_value("Build")->get_data();
			::rptMsg("IE Build   = ".$build);
		};
		
		eval {
			$version= $key->get_value("Version")->get_data();
			::rptMsg("IE Version = ".$version);
		};
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

1;