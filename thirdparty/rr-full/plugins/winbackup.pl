#-----------------------------------------------------------
# winbackup.pl
#
# Change History
#   20120812 [fpi] % created from winver.pl
#
# References
#
# copyright 2012 M. DeGrazia, arizona4n6@gmail.com
#-----------------------------------------------------------
package winbackup;
use strict;

my %config = (hive          => "Software",
              osmask        => 16,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20120812);

sub getConfig{return %config}

sub getShortDescr {
	return "Get Windows Backup";
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching winbackup v.".$VERSION);
    ::rptMsg("winbackup v.".$VERSION);
    ::rptMsg("(".getHive().") ".getShortDescr()."\n");

	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = "Microsoft\\Windows\\CurrentVersion\\WindowsBackup\\ScheduleParams\\TargetDevice";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {

		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		
                
    my $name;
		eval {
			$name = $key->get_value("PresentableName")->get_data();
		};
		if ($@) {
#			::rptMsg("PresentableName value not found.");
		}
		else {
			::rptMsg("  PresentableName = ".$name);
		}

                my $uniquename;
		eval {
			$uniquename = $key->get_value("UniqueName")->get_data();
		};
		if ($@) {
#			::rptMsg("UniqueName value not found.");
		}
		else {
			::rptMsg("  UniqueName = ".$uniquename);
		}

                
    my $devlabel;
		eval {
			$devlabel = $key->get_value("Label")->get_data();
		};
		if ($@) {
#			::rptMsg("Label value not found.");
		}
		else {
			::rptMsg("  Label = ".$devlabel);
		}


    my $vendor;
		eval {
			$vendor = $key->get_value("DeviceVendor")->get_data();
		};
		if ($@) {
#			::rptMsg("DeviceVendor value not found.");
		}
		else {
			::rptMsg("  DeviceVendor  = ".$vendor);
		}

   	my $deviceproduct;
		eval {
			$deviceproduct = $key->get_value("DeviceProduct")->get_data();
		};
		if ($@) {
#			::rptMsg("DeviceVendor value not found.");
		}
		else {
			::rptMsg("  DeviceProduct  = ".$deviceproduct);
		}

    my $deviceversion;
		eval {
			$deviceversion = $key->get_value("DeviceVersion")->get_data();
		};
		if ($@) {
#			::rptMsg("DeviceVendor value not found.");
		}
		else {
			::rptMsg("  DeviceVersion  = ".$deviceversion);
		}


    my $devserial;
		eval {
			$devserial = $key->get_value("DeviceSerial")->get_data();
		};
		if ($@) {
#			::rptMsg("DeviceSerial value not found.");
		}
		else {
			::rptMsg("  DeviceSerial = ".$devserial);
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
	
#status

  ::rptMsg("");
	$key_path = "Microsoft\\Windows\\CurrentVersion\\WindowsBackup\\Status";
	if ($key = $root_key->get_subkey($key_path)) {
#		::rptMsg("{name}");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		
                my $lastresulttime;
		eval {
			$lastresulttime = $key->get_value("LastResultTime")->get_data();
		};
		if ($@) {
#			::rptMsg("LastSuccess value not found.");
		}
		else {
     	my @vals = unpack("VV",$lastresulttime);
			my $lrt = ::getTime($vals[0],$vals[1]);
			::rptMsg("  LastResultTime = ".gmtime($lrt)." (UTC)");
    }

    my $lastsuccess;
		eval {
			$lastsuccess = $key->get_value("LastSuccess")->get_data();
		};
		if ($@) {
#			::rptMsg("LastSuccess value not found.");
		}
		else {
      my @vals = unpack("VV",$lastsuccess);
			my $ls = ::getTime($vals[0],$vals[1]);
			::rptMsg("  LastSuccess = ".gmtime($ls)." (UTC)");
    }

    my $lasttarget;
		eval {
			$lasttarget = $key->get_value("LastResultTarget")->get_data();
		};
		if ($@) {
#			::rptMsg("LastResultTarget value not found.");
		}
		else {
			::rptMsg("  LastResultTarget = ".$lasttarget);
		}

		my $LRTPrestName;
		eval {
			$LRTPrestName = $key->get_value("LastResultTargetPresentableName")->get_data();
		};
		if ($@) {
#			::rptMsg("LastResultTargetPresentableName value not found.");
		}
		else {
			::rptMsg("  LastResultTargetPresentableName  = ".$LRTPrestName);
		}


		my $LRTTargetLabel;
		eval {
			$LRTTargetLabel = $key->get_value("LastResultTargetLabel")->get_data();
		};
		if ($@) {
#			::rptMsg("LastResultTargetLabel value not found.");
		}
		else {
			::rptMsg("  LastResultTargetLabel = ".$LRTTargetLabel);
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1;
