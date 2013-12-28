#-----------------------------------------------------------
# drivers32
# Get values from Drivers32 key
# 
# History
#   20130408 - created by copying then modifying the soft_run plug-in
#
# References
#	Location of Windows NT Multimedia Drivers in the Registry
#		http://support.microsoft.com/kb/126054
# 
# copyright 2013 Corey Harrell (jIIr)
#-----------------------------------------------------------
package drivers32;
use strict;

my %config = (hive          => "Software",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 1,
              version       => 20130408);

sub getConfig{return %config}

sub getShortDescr {
	return "Get values from the Drivers32 key";	
}
sub getDescr{}
sub getRefs {
	my %refs = ("Location of Windows NT Multimedia Drivers in the Registry" =>
	            "http://support.microsoft.com/kb/126054");	
	return %refs;
}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching drivers32 v.".$VERSION);
	::rptMsg("drivers32 v.".$VERSION); # banner
	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my @paths = ("Microsoft\\Windows NT\\CurrentVersion\\Drivers32",
	             "Wow6432Node\\Microsoft\\Windows NT\\CurrentVersion\\Drivers32",
	             );
	
	foreach my $key_path (@paths) {
	
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg($key_path);
			::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		
			my %vals = getKeyValues($key);
			if (scalar(keys %vals) > 0) {
				foreach my $v (keys %vals) {
					::rptMsg("  ".$v." - ".$vals{$v});
				}
				::rptMsg("");
			}
			else {
				::rptMsg($key_path." has no values.");
			}
		
		}
		else {
			::rptMsg($key_path." not found.");
			::rptMsg("");
		}
	}
}

sub getKeyValues {
	my $key = shift;
	my %vals;
	
	my @vk = $key->get_list_of_values();
	if (scalar(@vk) > 0) {
		foreach my $v (@vk) {
			next if ($v->get_name() eq "" && $v->get_data() eq "");
			$vals{$v->get_name()} = $v->get_data();
		}
	}
	else {
	
	}
	return %vals;
}

1;