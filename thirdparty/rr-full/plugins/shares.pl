#-----------------------------------------------------------
# shares.pl
#
# Retrieve information about shares from a System hive file
#
# History
#  20140730 - added collection of NullSessionShares
#  20090112 - created
#
# References:
#   http://support.microsoft.com/kb/556023
#   For info about share types, see the Win32_Share  WMI class:
#      http://msdn.microsoft.com/en-us/library/aa394435(VS.85).aspx
#
# copyright 2014 QAR, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package shares;
use strict;

my %config = (hive          => "System",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20140730);

sub getConfig{return %config}

sub getShortDescr {
	return "Get list of shares from System hive file";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();
my $root_key;

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching shares v.".$VERSION);
	::rptMsg("shares v.".$VERSION); # banner
  ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	$root_key = $reg->get_root_key;

# Code for System file, getting CurrentControlSet
 	my $current;
 	my $ccs;
 	eval {
		my $key_path = 'Select';
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			$current = $key->get_value("Current")->get_data();
			$ccs = "ControlSet00".$current;
		}
	};
	if ($@) {
		::rptMsg("Problem locating proper controlset: $@");
		return;
	}
# First, connect to the Services key; some versions of Windows appear to
# spell the lanmanserver key as "lanmanserver" and others as "LanmanServer"
	my $key_path = $ccs."\\Services";
	my $key;
	my $tag = "lanmanserver";
	my $lanman = getKeyPath($key_path,$tag);
	if ($lanman ne "") {
		my $share_path = $key_path."\\".$lanman."\\Shares";
		my $share;
		if ($share = $root_key->get_subkey($share_path)) {
			my @vals = $share->get_list_of_values();
			if (scalar(@vals) > 0) {
				foreach my $v (@vals) {
					::rptMsg("  ".$v->get_name());
					my @data = $v->get_data();
					::rptMsg("    ".$data[2]);
					::rptMsg("    ".$data[4]);
					::rptMsg("    ".$data[5]);
					::rptMsg("");
				}
			}
			else {
				::rptMsg($share_path." has no values.");
			}
		}
		else {
			::rptMsg($share_path." not found.");
		}
	}
	else {
		::rptMsg($lanman." subkey not found.");
	}

# Determine of the AutoShareServer/Wks values have been set
	my $path = $key_path."\\".$lanman;
	$tag  = "parameters";
	my $para = getKeyPath($path,$tag);
	eval {
		if ($key = $root_key->get_subkey($path."\\".$para)) {
			my $auto_svr = $key->get_value("AutoShareServer")->get_data();
			::rptMsg("  AutoShareServer = ".$auto_svr);
		}
	};
	
	eval {
		if ($key = $root_key->get_subkey($path."\\".$para)) {
			my $auto_wks = $key->get_value("AutoShareWks")->get_data();
			::rptMsg("  AutoShareWks = ".$auto_wks);
		}
	};
	
	eval {
		if ($key = $root_key->get_subkey($path."\\".$para)) {
			my $auto_nss = $key->get_value("NullSessionShares")->get_data();
			::rptMsg("  NullSessionShares = ".$auto_nss);
    }
  };
}

# On different versions of Windows, subkeys such as lanmanserver
# and parameters are spelled differently; use this subroutine to get
# the correct spelling of the name of the subkey
# http://support.microsoft.com/kb/288164
sub getKeyPath {
	my $path = $_[0];
	my $tag  = $_[1];
	my $subkey;	
	if (my $key = $root_key->get_subkey($path)) {
		my @sk = $key->get_list_of_subkeys();
		foreach my $s (@sk) {
			my $name = $s->get_name();
			$subkey = $name if ($name =~ m/^$tag/i);
		}
	}
	return $subkey;
}

1;
