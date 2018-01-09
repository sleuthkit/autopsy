#-----------------------------------------------------------
# bho
#
#
# Change history:
#  20130408 - updated to include Wow6432Node; formating updates
#  20080418 - created
#
#
# copyright 2013 QAR, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package bho;
use strict;

my %config = (hive          => "Software",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 1,
              osmask        => 22,
              version       => 20130408);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets Browser Helper Objects from Software hive";	
}
sub getDescr{}
sub getRefs {
	my %refs = ("Browser Helper Objects" => 
	             "http://msdn2.microsoft.com/en-us/library/bb250436.aspx");	
	return %refs;
}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching bho v.".$VERSION);
	::rptMsg("bho v.".$VERSION); # banner
  ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	my @paths = ("Microsoft\\Windows\\CurrentVersion\\Explorer\\Browser Helper Objects",
	             "Wow6432Node\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Browser Helper Objects");
	
	foreach my $key_path (@paths) {
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg($key_path);
			::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
			::rptMsg("");
			my @subkeys = $key->get_list_of_subkeys();
			if (scalar (@subkeys) > 0) {
				foreach my $s (@subkeys) {
					my $name = $s->get_name();
					next if ($name =~ m/^-/);
					my $clsid_path = "Classes\\CLSID\\".$name;
					my $clsid;
					my %bhos;
					if ($clsid = $root_key->get_subkey($clsid_path)) {
						my $class;
						my $mod;
						my $lastwrite;
					
						eval {
							$class = $clsid->get_value("")->get_data();
							$bhos{$name}{class} = $class;
						};
						if ($@) {
							::logMsg("Error getting Class name for CLSID\\".$name);
							::logMsg("\t".$@);
						}
						eval {
							$mod = $clsid->get_subkey("InProcServer32")->get_value("")->get_data();
							$bhos{$name}{module} = $mod;
						};
						if ($@) {
							::logMsg("\tError getting Module name for CLSID\\".$name);
							::logMsg("\t".$@);
						}
						eval{
							$lastwrite = $clsid->get_subkey("InProcServer32")->get_timestamp();
							$bhos{$name}{lastwrite} = $lastwrite;
						};
						if ($@) {
							::logMsg("\tError getting LastWrite time for CLSID\\".$name);
							::logMsg("\t".$@);
						}
					
						foreach my $b (keys %bhos) {
							::rptMsg($b);
							::rptMsg("  Class     => ".$bhos{$b}{class});
							::rptMsg("  Module    => ".$bhos{$b}{module});
							::rptMsg("  LastWrite => ".gmtime($bhos{$b}{lastwrite}));
							::rptMsg("");
						}
					}
					else {
						::rptMsg($clsid_path." not found.");
						::rptMsg("");
					}
				}
			}
			else {
				::rptMsg($key_path." has no subkeys.  No BHOs installed.");
			}
		}
		else {
			::rptMsg($key_path." not found.");
		}
	}
}
1;