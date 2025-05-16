#-----------------------------------------------------------
# emdmgmt.pl
#
# History
#   20200911 - MITRE updates
#   20200511 - updated date output format
#		20190827 - updated
#   20120207 - created
#
# This plugin does not lend itself to a *_tln version; in practice, many of the subkey LastWrite
# times are the same, or close together, indicating that some other action or event, besides 
# connecting/disconnecting the device causes it to be updated.  Often, the "Last Tested Time" may
# fall significantly outside the time window of interest.  As such, it is best to use the volume
# information to tie to other data sources.
#
# copyright 2019 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package emdmgmt;
use strict;

my %config = (hive          => "Software",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 1,
              MITRE         => "",
			  output        => "report",
              category      => "devices",
              version       => 20200911);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets contents of EMDMgmt subkeys and values";	
}
sub getDescr{}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching emdmgmt v.".$VERSION);
	::rptMsg("emdmgmt v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = 'Microsoft\\Windows NT\\CurrentVersion\\EMDMgmt';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("EMDMgmt");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");
		my @sk = $key->get_list_of_subkeys();
		foreach my $s (@sk) {
			my $name = $s->get_name();
			if ($name =~ m/^_\?\?_USBSTOR/) {
				my ($usb,$sn,$vol) = (split(/#/,$name,4))[1,2,3];
				::rptMsg($usb);
#				::rptMsg("  LastWrite: ".gmtime($s->get_timestamp())." Z");
				::rptMsg("  SN: ".$sn);
				$vol =~ s/{53f56307-b6bf-11d0-94f2-00a0c91efb8b}//;
				my ($volname,$vsn) = split(/_/,$vol,2);
				$vsn = uc(sprintf "%x",$vsn);
				if (length($vsn) >= 8) {
					my ($f,$l) = unpack("(A4)*",$vsn);
					$vsn = $f."-".$l;
				}
				::rptMsg("  Vol Name: ".$volname) if ($volname ne "");
				::rptMsg("  VSN: ".$vsn);
				
				eval {
					my $last = $s->get_value_data("LastTestedTime");
					my ($t0,$t1) = unpack("VV",$last);
					if ($t0 != 0 && $t1 != 0) {
						::rptMsg("  LastTestedTime: ".::format8601Date(::getTime($t0,$t1))."Z");
					}
				};
				::rptMsg("");	
			}
			else {
				my @n = split(/_/,$name);
				my $t = scalar(@n);
				my $volname = $n[$t - 2];
				my $vsn = $n[$t - 1];
				$vsn = uc(sprintf "%x",$vsn);
				if (length($vsn) >= 8) {
					my ($f,$l) = unpack("(A4)*",$vsn);
					$vsn = $f."-".$l;
				}
				$volname = "Unknown Volume" unless ($volname ne "");
				::rptMsg($volname);
#				::rptMsg("  LastWrite: ".gmtime($s->get_timestamp())." Z");
				::rptMsg("  VSN: ".$vsn);
				
				eval {
					my $last = $s->get_value_data("LastTestedTime");
					my ($t0,$t1) = unpack("VV",$last);
					if ($t0 != 0 && $t1 != 0) {
						::rptMsg("  LastTestedTime: ".::format8601Date(::getTime($t0,$t1))."Z");
					}
				};
				::rptMsg("");
			}
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1;