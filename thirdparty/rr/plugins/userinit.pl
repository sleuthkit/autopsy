#-----------------------------------------------------------
# userinit
#
# copyright 2008 H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package userinit;
use strict;

my %config = (hive          => "Software",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 1,
              version       => 20080328);

sub getConfig{return %config}

sub getShortDescr {
	return "Gets UserInit value";	
}
sub getDescr{}
sub getRefs {
	my %refs = ("My Documents open at startup" => 
	            "http://support.microsoft.com/kb/555294",
	            "Userinit" => 
	            "http://www.microsoft.com/technet/prodtechnol/windows2000serv/reskit/regentry/12330.mspx?mfr=true");
	return %refs;
}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching userinit v.".$VERSION);
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = "Microsoft\\Windows NT\\CurrentVersion\\Winlogon";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		my $ui;
		eval {
			$ui = $key->get_value("Userinit")->get_data();
			::rptMsg("\tUserinit -> ".$ui);
		};
		::rptMsg("Error: ".$@) if ($@);
		::rptMsg("");
		::rptMsg("Per references, content should be %SystemDrive%\\system32\\userinit.exe,");
		::rptMsg("");
	}
	else {
		::rptMsg($key_path." not found.");
		::logMsg($key_path." not found.");
	}
	
}
1;