#-----------------------------------------------------------
# mountdev.pl
# Plugin for Registry Ripper; Access System hive file to get the
# MountedDevices
# 
# Change history
#  20221129 - updated output format
#  20200921 - MITRE updates
#  20200517 - updated date output format
#  20130530 - updated to output Disk Signature in correct format, thanks to
#             info provided by Tom Yarrish (see ref.)
#  20080324 - created
#
# References
#  http://blogs.technet.com/b/markrussinovich/archive/2011/11/08/3463572.aspx
# 
# copyright 2022 QAR, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package mountdev;
use strict;

my %config = (hive          => "System",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "",
			  output		=> "report",
              category      => "devices",
              version       => 20221129);

sub getConfig{return %config}
sub getShortDescr {
	return "Return contents of HKLM\\System\\MountedDevices key";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching mountdev v.".$VERSION);
	::rptMsg("mountdev v.".$VERSION);
	::rptMsg("(".getHive().") ".getShortDescr()."\n"); 
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key_path = 'MountedDevices';
	my $key;
	
	my %devices = ();
	my %volumes = ();
	my %drives  = ();
	
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite time = ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");
		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			foreach my $v (@vals) {
				my $data = $v->get_data();
				my $len = length($data);
				if ($len == 12) {
					my $sig = _translateBinary(substr($data,0,4));
					$drives{$v->get_name()} = "Drive Signature: ".$sig;
				}
				elsif ($len == 24) {
					my $d = ::parseGUID(substr($data,8,16));
					$volumes{$v->get_name()} = "Volume GUID: ".$d;
				}
				elsif ($len > 0x50) {
					$data =~ s/\00//g;
					$devices{$v->get_name()} = $data;
				}
				else {
					::logMsg("mountdev v.".$VERSION."\tData length = $len");
				}
			}
			
			if (scalar(keys %drives) > 0) {
				::rptMsg("Drives");
				foreach my $k (keys %drives) {
					::rptMsg(sprintf "-25s %-25s",$k,$drives{$k});
				
				}
				::rptMsg("");
			}
			
			if (scalar(keys %devices) > 0) {
				::rptMsg("Devices");
				foreach my $k (keys %devices) {
					::rptMsg(sprintf "%-55s %-70s", $k, $devices{$k});
				
				}
				::rptMsg("");
				::rptMsg("Analysis Tip: Look for MSFT Virtual_DVD-ROM devices that map to drive letters, as well as USB devices.");
				::rptMsg("");
			}
			
			if (scalar(keys %volumes) > 0) {
				::rptMsg("Volumes");
				foreach my $k (keys %volumes) {
					::rptMsg(sprintf "%-15s %-30s",$k,$volumes{$k});
				
				}
				::rptMsg("");
				::rptMsg("Analysis Tip: Map Volume GUIDs to user's BitBucket\\Volume subkeys, to get max capacity settings.");
				::rptMsg("");
			}
			
		}
		else {
			::rptMsg($key_path." has no values.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

sub _translateBinary {
	my $str = unpack("H*",$_[0]);
	my $len = length($str);
	my @nstr = split(//,$str,$len);
	my @list = ();
	foreach (0..($len/2)) {
		push(@list,$nstr[$_*2].$nstr[($_*2)+1]);
	}
	return join(' ',reverse @list);
}

1;