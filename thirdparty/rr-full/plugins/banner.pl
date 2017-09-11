#-----------------------------------------------------------
# banner
# Get banner information from the SOFTWARE hive file (if any)
# 
# Written By:
# Special Agent Brook William Minnick
# Brook_Minnick@doioig.gov
# U.S. Department of the Interior - Office of Inspector General
# Computer Crimes Unit
# 12030 Sunrise Valley Drive Suite 250
# Reston, VA 20191
#-----------------------------------------------------------
package banner;
use strict;

my %config = (hive          => "Software",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20081119);

sub getConfig{return %config}

sub getShortDescr {
	return "Get HKLM\\SOFTWARE.. Logon Banner Values";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching banner v.".$VERSION);
	::rptMsg("banner v.".$VERSION); # banner
    ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = "Microsoft\\Windows\\CurrentVersion\\policies\\system";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("Logon Banner Information");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");

# GET LEGALNOTICECAPTION --

		my $caption;
		eval {
			$caption = $key->get_value("Legalnoticecaption")->get_data();
		};
		if ($@) {
			::rptMsg("Legalnoticecaption value not found.");
		}
		else {
			::rptMsg("Legalnoticecaption value = ".$caption);
		}
		::rptMsg("");

# GET LEGALNOTICETEXT --

		my $banner;
		eval {
			$banner = $key->get_value("Legalnoticetext")->get_data();
		};
		if ($@) {
			::rptMsg("Legalnoticetext value not found.");
		}
		else {
			::rptMsg("Legalnoticetext value = ".$banner);
		}
		::rptMsg("");
	
	}
	else {
		::rptMsg($key_path." not found.");
		::logMsg($key_path." not found.");
	}

	$key_path = "Microsoft\\Windows NT\\CurrentVersion\\Winlogon";
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");

# GET LEGALNOTICECAPTION --

		my $caption2;
		eval {
			$caption2 = $key->get_value("Legalnoticecaption")->get_data();
		};
		if ($@) {
			::rptMsg("Legalnoticecaption value not found.");
		}
		else {
			::rptMsg("Legalnoticecaption value = ".$caption2);
		}
		::rptMsg("");

# GET LEGALNOTICETEXT --

		my $banner2;
		eval {
			$banner2 = $key->get_value("Legalnoticetext")->get_data();
		};
		if ($@) {
			::rptMsg("Legalnoticetext value not found.");
		}
		else {
			::rptMsg("Legalnoticetext value = ".$banner2);
		}
		::rptMsg("");
	
	}
	else {
		::rptMsg($key_path." not found.");
		::logMsg($key_path." not found.");
	}

}

1;
