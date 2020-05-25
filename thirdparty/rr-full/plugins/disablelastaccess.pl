#-----------------------------------------------------------
# disablelastaccess.pl
#
# History:
#  20181207 - updated for Win10 v.1803 (Maxim, David Cohen)
#  20090118 - 
# 
# References:
#    https://twitter.com/errno_fail/status/1070838120545955840
#    https://dfir.ru/2018/12/08/the-last-access-updates-are-almost-back/
#    https://www.hecfblog.com/2018/12/daily-blog-557-changes-in.html
#		 http://support.microsoft.com/kb/555041
#    http://support.microsoft.com/kb/894372
#
# copyright 2008 H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package disablelastaccess;
use strict;

my %config = (hive          => "System",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20181207);

sub getConfig{return %config}

my %dla = (0x80000000 => "(User Managed, Updates Enabled)",
           0x80000001 => "(User Managed, Updates Disabled)",
           0x80000002 => "(System Managed, Updates Enabled)",
           0x80000003 => "(System Managed, Updates Disabled)");

sub getShortDescr {
	return "Get NTFSDisableLastAccessUpdate value";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching disablelastaccess v.".$VERSION);
	::rptMsg("disablelastaccess v.".$VERSION); # banner
    ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

# Code for System file, getting CurrentControlSet
 my $current;
	my $key_path = 'Select';
	my $key;
	my $ccs;
	if ($key = $root_key->get_subkey($key_path)) {
		$current = $key->get_value("Current")->get_data();
		$ccs = "ControlSet00".$current;
	}

	my $key_path = $ccs."\\Control\\FileSystem";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("NtfsDisableLastAccessUpdate");
		::rptMsg($key_path);
		my @vals = $key->get_list_of_values();
		my $found = 0;
		if (scalar(@vals) > 0) {
			foreach my $v (@vals) {
				if ($v->get_name() eq "NtfsDisableLastAccessUpdate") {
					my $dat = $v->get_data();
					::rptMsg(sprintf "NtfsDisableLastAccessUpdate = 0x%08x",$dat);
					$found = 1;
					
					if ($dat > 1) {
						::rptMsg($dla{$dat});
						eval {
							my $thresh = $key->get_value("NtfsLastAccessUpdatePolicyVolumeSizeThreshold")->get_data();
							::rptMsg(sprintf "NtfsLastAccessUpdatePolicyVolumeSizeThreshold value = 0x%08x",$thresh);
						};
						
					}
					
				}
			}
			::rptMsg("NtfsDisableLastAccessUpdate value not found.") if ($found == 0);
		}
		else {
			::rptMsg($key_path." has no values.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1;