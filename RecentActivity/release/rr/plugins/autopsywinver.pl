#-----------------------------------------------------------
# winver.pl
#
# copyright 2008-2009 H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package autopsywinver;
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
	#::logMsg("Launching winver v.".$VERSION);
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
		::rptMsg("<WinVersion>");
		::rptMsg("<mtime></mtime>");
		::rptMsg("<artifacts>");
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
			::rptMsg("<winver name=\"ProductName\">".$prod ."</winver>");
		}
		
		my $csd;
		eval {
			$csd = $key->get_value("CSDVersion")->get_data();
		};
		if ($@) {
#			::rptMsg("CSDVersion value not found.");
		}
		else {
			::rptMsg("<winver name=\"CSDVersion\">".$csd."</winver>");
		}
		
		
		my $build;
		eval {
			$build = $key->get_value("BuildName")->get_data();
		};
		if ($@) {
#			::rptMsg("BuildName value not found.");
		}
		else {
			::rptMsg("<winver name=\"BuildName\">".$build."</winver>");
		}
		
		my $buildex;
		eval {
			$buildex = $key->get_value("BuildNameEx")->get_data();
		};
		if ($@) {
#			::rptMsg("BuildName value not found.");
		}
		else {
			::rptMsg("<winver name=\"BuildNameEx\">".$buildex."</winver>");
		}
		
		
		my $install;
		eval {
			$install = $key->get_value("InstallDate")->get_data();
		};
		if ($@) {
#			::rptMsg("InstallDate value not found.");
		}
		else {
			::rptMsg("<winver name=\"InstallDate\">".gmtime($install)."</winver>");
		}

		my $regOwner;
		eval {
			$regOwner = $key->get_value("RegisteredOwner")->get_data();
		};
		if ($@) {
#			::rptMsg("RegisteredOwner value not found.");
		}
		else {
			::rptMsg("<winver name=\"RegisteredOwner\">".$regOwner."</winver>");
		}

		my $regOrg;
		eval {
			$regOrg = $key->get_value("RegisteredOrganization")->get_data();
		};
		if ($@) {
#			::rptMsg("RegisteredOrganization value not found.");
		}
		else {
			::rptMsg("<winver name=\"RegisteredOrganization\">".$regOrg."</winver>");
		}

		my $systemRoot;
		eval {
			$systemRoot = $key->get_value("SystemRoot")->get_data();
		};
		if ($@) {
#			::rptMsg("SystemRoot value not found.");
		}
		else {
			::rptMsg("<winver name=\"SystemRoot\">".$systemRoot."</winver>");
		}

		my $productId;
		eval {
			$productId = $key->get_value("ProductId")->get_data();
		};
		if ($@) {
#			::rptMsg("ProductId value not found.");
		}
		else {
			::rptMsg("<winver name=\"ProductId\">".$productId."</winver>");
		}
		
		
	}
	else {
		#::rptMsg($key_path." not found.");
		#::logMsg($key_path." not found.");
	}
	::rptMsg("</artifacts></WinVersion>");
}
1;
