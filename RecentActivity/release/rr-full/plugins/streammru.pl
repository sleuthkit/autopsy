#-----------------------------------------------------------
# streammru.pl
#
# copyright 2009 H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package streammru;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20090205);

sub getConfig{return %config}

sub getShortDescr {
	return "streammru";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching streammru v.".$VERSION);
	::rptMsg("streammru v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\StreamMRU";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("");
		::rptMsg($key_path);
		::rptMsg("");
		
		my $data = $key->get_value("5")->get_data();
		
		my $drive = substr($data, 0x16,4);
		::rptMsg("Drive = ".$drive);
		::rptMsg("");
		
		my $size = substr($data, 0x2d, 1);
		::rptMsg("Size of first object: ".unpack("c",$size)." bytes");
		::rptMsg("");
		
		
		
		
		
		
	}
	else {
		::rptMsg($key_path." not found.");
		::logMsg($key_path." not found.");
	}
	
}
1;