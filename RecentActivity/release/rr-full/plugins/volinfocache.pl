#-----------------------------------------------------------
# volinfocache.pl
# 
# Note: Andrew Case pointed out this key to me on 16 July 2012,
# and after seeing what was in it, I just wrote up a plugin
#
# History:
#  20120822 - added drive types hash based on MS KB161300
#  20120716 - created
#
# copyright 2012 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package volinfocache;
use strict;

my %config = (hive          => "Software",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 1,
              osmask        => 22,
              version       => 20120822);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets VolumeInfoCache from Windows Search key";	
}
sub getDescr{}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	
	my %types = (0x0 => "Undetermined",
	             0x1 => "Root_not_exist",
	             0x2 => "Removable",
	             0x3 => "Fixed",
	             0x4 => "Remote",
	             0x5 => "CDROM",
	             0x6 => "RAMDISK");
	
	::logMsg("Launching volinfocache v.".$VERSION);
	::rptMsg("Launching volinfocache v.".$VERSION);
	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner 
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

  my $key;
  my $key_path = "Microsoft\\Windows Search\\VolumeInfoCache";
  if ($key = $root_key->get_subkey($key_path)) {
	  ::rptMsg($key_path);
	  my @subkeys = $key->get_list_of_subkeys();
	  if (scalar(@subkeys) > 0) {
	    foreach my $s (@subkeys) {
		  	my $name = $s->get_name();
		  	my $ts = $s->get_timestamp();
		  	::rptMsg($name." - LastWrite: ".gmtime($ts));
		  	
		  	my $type;
		  	eval {
		  		$type = $s->get_value("DriveType")->get_data();
		  		$type = $types{$type} if (exists $types{$type});
		  		::rptMsg("DriveType: ".$type);
		  	};
		  	
		  	my $label;
		  	eval {
		  		$label = $s->get_value("VolumeLabel")->get_data();
		  		::rptMsg("VolumeLabel: ".$label);
		  	};
		  	
		  	::rptMsg("");
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