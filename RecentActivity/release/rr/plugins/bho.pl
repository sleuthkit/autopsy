#-----------------------------------------------------------
# bho
#
# copyright 2008 H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package bho;
use strict;

my %config = (hive          => "Software",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 1,
              osmask        => 22,
              version       => 20080418);

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
	my %bhos;
	::logMsg("Launching bho v.".$VERSION);
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key_path = "Microsoft\\Windows\\CurrentVersion\\Explorer\\Browser Helper Objects";;
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("Browser Helper Objects");
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
				if ($clsid = $root_key->get_subkey($clsid_path)) {
					my $class;
					my $mod;
					my $lastwrite;
					
					eval {
						$class = $clsid->get_value("")->get_data();
						$bhos{$name}{class} = $class;
					};
					if ($@) {
						::logMsg("\tError getting Class name for CLSID\\".$name);
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
						::rptMsg("\tClass     => ".$bhos{$b}{class});
						::rptMsg("\tModule    => ".$bhos{$b}{module});
						::rptMsg("\tLastWrite => ".gmtime($bhos{$b}{lastwrite}));
						::rptMsg("");
					}
				}
				else {
					::rptMsg($clsid_path." not found.");
					::rptMsg("");
					::logMsg($clsid_path." not found.");
				}
			}
		}
		else {
			::rptMsg($key_path." has no subkeys.  No BHOs installed.");
			::logMsg($key_path." has no subkeys.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
		::logMsg($key_path." not found.");
	}
}
1;