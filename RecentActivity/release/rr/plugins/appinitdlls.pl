#-----------------------------------------------------------
# appinitdlls
#
#
# copyright 2008 H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package appinitdlls;
use strict;

my %config = (hive          => "Software",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 1,
              osmask        => 22,
              version       => 20080324);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets contents of AppInit_DLLs value";	
}
sub getDescr{}
sub getRefs {
	my %refs = ("Working with the AppInit_DLLs Reg Value" => 
	            "http://support.microsoft.com/kb/q197571");
	return %refs;
}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching appinitdlls v.".$VERSION);
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = 'Microsoft\\Windows NT\\CurrentVersion\\Windows';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("AppInit_DLLs");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		my @vals = $key->get_list_of_values();
		foreach my $v (@vals) {
			my $name = $v->get_name();
			if ($name eq "AppInit_DLLs") {
				my $data = $v->get_data();
				$data = "{blank}" if ($data eq "");
				::rptMsg($name." -> ".$data);
			}
		}
	}
	else {
		::rptMsg($key_path." not found.");
		::logMsg($key_path." not found.");
	}
}
1;