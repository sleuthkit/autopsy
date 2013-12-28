#-----------------------------------------------------------
# winver.pl
#
# copyright 2008-2009 H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package winver;
use strict;

my %config = (hive          => "Software",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20081210);

sub getConfig{return %config}

sub getShortDescr {
	return "Get Windows version";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching winver v.".$VERSION);
	::rptMsg("winver v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = "Microsoft\\Windows NT\\CurrentVersion";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
#		::rptMsg("{name}");
#		::rptMsg($key_path);
#		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		
		my $prod;
		eval {
			$prod = $key->get_value("ProductName")->get_data();
		};
		if ($@) {
#			::rptMsg("ProductName value not found.");
		}
		else {
			::rptMsg("ProductName = ".$prod);
		}
		
		my $csd;
		eval {
			$csd = $key->get_value("CSDVersion")->get_data();
		};
		if ($@) {
#			::rptMsg("CSDVersion value not found.");
		}
		else {
			::rptMsg("CSDVersion  = ".$csd);
		}
		
		
		my $build;
		eval {
			$build = $key->get_value("BuildName")->get_data();
		};
		if ($@) {
#			::rptMsg("BuildName value not found.");
		}
		else {
			::rptMsg("BuildName = ".$build);
		}
		
		my $buildex;
		eval {
			$buildex = $key->get_value("BuildNameEx")->get_data();
		};
		if ($@) {
#			::rptMsg("BuildName value not found.");
		}
		else {
			::rptMsg("BuildNameEx = ".$buildex);
		}
		
		
		my $install;
		eval {
			$install = $key->get_value("InstallDate")->get_data();
		};
		if ($@) {
#			::rptMsg("InstallDate value not found.");
		}
		else {
			::rptMsg("InstallDate = ".gmtime($install));
		}
		
		
	}
	else {
		::rptMsg($key_path." not found.");
		::logMsg($key_path." not found.");
	}
	
}
1;