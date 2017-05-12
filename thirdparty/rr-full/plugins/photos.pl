package photos;
#------------------------------------------------------------
# photos.pl - read data on images opened via Win8 Photos app
# 
# Change history
#  20130308 - created
#
# Ref:
#  http://dfstream.blogspot.com/2013/03/windows-8-tracking-opened-photos.html
#
# Copyright 2013 QAR, LLC
# Author: H. Carvey, keydet89@yahoo.com
#------------------------------------------------------------
use strict;

my %config = (hive          => "USRCLASS\.DAT",
							hivemask      => 32,
							output        => "report",
							category      => "User Activity",
              osmask        => 20, #not used at the moment
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20130102);

sub getConfig{return %config}

sub getShortDescr {
	return "Shell/BagMRU traversal in Win7 USRCLASS.DAT hives";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching photos v.".$VERSION);
	::rptMsg("photos v.".$VERSION); # banner
	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;


#\Local Settings\Software\Microsoft\Windows\CurrentVersion\AppModel\
#SystemAppData\microsoft.windowsphotos_8wekyb3d8bbwe\
#PersistedStorageItemTable\ManagedByApp

	my $key_path = "Local Settings\\Software\\Microsoft\\Windows\\CurrentVersion\\".
	               "AppModel\\SystemAppData\\microsoft\.windowsphotos_8wekyb3d8bbwe\\".
	               "PersistedStorageItemTable\\ManagedByApp";
	my $key;
	
	if ($key = $root_key->get_subkey($key_path)) {
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar(@subkeys) > 0) {
			foreach my $s (@subkeys) {
				my $name = $s->get_name();
				my $lw   = $s->get_timestamp();
				::rptMsg($name);
				::rptMsg("LastWrite: ".gmtime($lw)." UTC");
				
				eval {
					my $fp = $s->get_value("FilePath")->get_data();
					::rptMsg("FilePath: ".$fp);
				};
				
				eval {
					my $last = $s->get_value("LastUpdatedTime")->get_data();
					my ($v0,$v1) = unpack("VV",$last);
					my $l = ::getTime($v0,$v1);
					::rptMsg("LastUpdatedTime: ".gmtime($l)." UTC");
				};
				
				eval {
					my $flags = $s->get_value("Flags")->get_data();
					::rptMsg(sprintf "Flags: 0x%x",$flags);
					::rptMsg("  Removable media") if ($flags == 0x09);
					::rptMsg("  Local media") if ($flags == 0x0d);
				};
				::rptMsg("");
			}
		}
		else {
			::rptMsg($key_path." key has no subkeys\.");
		}
	}
	else {
		::rptMsg($key_path." key not found\.");
	}
}
1;
